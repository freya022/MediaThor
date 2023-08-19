package io.github.freya022.mediathor.record

import io.github.freya022.mediathor.record.memfs.FileObj
import io.github.freya022.mediathor.record.memfs.MemFSListener
import io.github.freya022.mediathor.record.memfs.WinFspMemFS
import io.github.freya022.mediathor.utils.*
import kotlinx.coroutines.*
import mu.two.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.io.path.*

private typealias ClipPath = Path
private typealias KeyframeIndex = Int
private typealias KeyframeTimestamp = String
private typealias KeyframeHash = String

private val logger = KotlinLogging.logger { }

class RecordWatcher(private val memFS: WinFspMemFS) : MemFSListener {
    private class Clip(val path: Path, val keyframeIndexByHash: Map<KeyframeHash, KeyframeIndex>)
    private class InputClip(val clip: Clip, val keyframeTimestamps: List<KeyframeTimestamp>, val audioStreams: Int) {
        val path: Path get() = clip.path

        var startTimestamp: String? = null
        var endTimestamp: String? = null
    }

    private val scope = getDefaultScope(newExecutor(1) { threadNumber -> name = "MediaThor record watch thread #$threadNumber" }.asCoroutineDispatcher())
    private val clips: MutableList<Clip> = arrayListOf()

    private val newVideoSequencer = Sequencer(scope)

    init {
        memFS.addListener(this)
    }

    override fun onNewFileClosed(fileObj: FileObj) {
        scope.launch(Dispatchers.IO) {
            try {
                val newFile = fileObj.absolutePath
                if (newFile.extension != "mkv" || newFile.parent != memFS.mountPointPath)
                    return@launch

                newVideoSequencer.runTask {
                    onNewVideo(fileObj, newFile)
                }
            } catch (e: Exception) {
                logger.catching(e)
            }
        }
    }

    /**
     * How to concat N videos sharing common content:
     *  1. Extract all keyframes
     *  2. Make a hash of all those keyframes (so you don't have to read their content to compare)
     *  3. Wait for a new video with no matching hashes
     *
     * Once you have a video with no common keyframes, for each previous video:
     *  1. Get all key frame timestamps
     *    - `ffprobe -v warning -select_streams v -show_entries frame=pts_time -skip_frame nokey -of csv=p=0 input.mkv`
     *  2. Get number of audio tracks, this will be useful to make the next filter
     *    - `ffprobe -v warning -select_streams a -show_entries stream=index -of csv=p=0 clip.mkv`
     *  3. Construct concatenation command, first input's length will be the timestamp of its own matching keyframe,
     *     while second input's start time will be the timestamp of its own matching keyframe,
     *     if there is another input, then the length is the duration between its own matching start keyframe, and the matched end keyframe.
     *
     *     Example: `ffmpeg -v warning -t 4.167000 -i 'clip.mkv' -ss 4.167000 -i 'clip.mkv' -filter_complex "[0:v:0][0:a:0][0:a:1][0:a:2][1:a:3][1:v:0][1:a:0][1:a:1][1:a:2][1:a:3]concat=n=2:v=1:a=4[outv][outa1][outa2][outa3][outa4]" -map "[outv]" -map "[outa1]" -map "[outa2]" -map "[outa3]" -map "[outa4]" reassembled.mp4`
     *
     *     **Note:** Sticking to a .mp4 output container is critical,
     *               there is an issue either in ffmpeg writing,
     *               or davinci resolve not being able to read what ffmpeg writes,
     *               as the audio tracks are simply missing, despite other MKV files working.
     *               VLC works fine though.
     */
    context(CoroutineScope)
    private suspend fun onNewVideo(fileObj: FileObj, newFile: Path) {
        val keyframesFolder = extractKeyframes(newFile)

        val keyframeIndexByHash = hashKeyframes(newFile, keyframesFolder)

        val currentClip = Clip(newFile, keyframeIndexByHash)
        clips += currentClip

        checkMismatchedKeyframe(currentClip) { onMismatchedKeyframe() }
    }

    context(CoroutineScope)
    @OptIn(ExperimentalPathApi::class)
    private suspend fun onMismatchedKeyframe() = withContext(Dispatchers.IO) {
        val previousVideos = clips.dropLast(1)
        clips.clear()

        val copyPath = Data.videosFolder.resolve(previousVideos.last().path.name)

        // If there is only one previous clip, copy to disk
        if (previousVideos.size == 1) {
            previousVideos.single().path.moveTo(copyPath)
            logger.info { "Moved ${previousVideos.single().path} into $copyPath" }
            return@withContext
        }

        // Merge previous videos to disk
        logger.debug { "Merging ${previousVideos.joinToString { it.path.name }}" }

        val mergePath = copyPath.resolveSibling(copyPath.nameWithoutExtension + ".mp4")

        val inputs = previousVideos.map {
            val timestamps = getKeyframeTimestamps(it.path)
            val audioStreams = getClipAudioStreams(it.path)
            InputClip(it, timestamps, audioStreams)
        }
        // Check all the same number of audio streams
        val audioStreams = inputs[0].audioStreams
        inputs.all { it.audioStreams == audioStreams }

        // Assign all start/end timestamps to all N/N+1 couples
        (0..<previousVideos.size - 1).forEach { index ->
            // For each N/N+1 couples, get their common keyframe
            // Then assign their timestamps to:
            //   - N'th video: end
            //   - N+1'th video: start
            val n = inputs[index]
            val n1 = inputs[index + 1]

            val matchingNKeyframeIndex = n.clip.matchKeyframe(n1.clip)
                ?: throw AssertionError("No matched keyframe between ${n.path} and ${n1.path}")
            val matchingN1KeyframeIndex = n1.clip.matchKeyframe(n.clip)
                ?: throw AssertionError("No matched keyframe between ${n1.path} and ${n.path}")

            n.endTimestamp = n.keyframeTimestamps[matchingNKeyframeIndex]
            n1.startTimestamp = n1.keyframeTimestamps[matchingN1KeyframeIndex]
        }

        val inputArgs = inputs.flatMap { inputClip ->
            buildList {
                inputClip.startTimestamp?.let {
                    add("-ss")
                    add(it)
                }

                inputClip.endTimestamp?.let {
                    val endTimestamp = it.toBigDecimal()
                    val length = endTimestamp - (inputClip.startTimestamp?.toBigDecimal() ?: BigDecimal.ZERO)

                    add("-t")
                    add(length.toPlainString())
                }

                add("-i")
                add(inputClip.path.absolutePathString())
            }
        }

        val outputs = buildList {
            add("[outv]")
            for (audioIndex in 0..<audioStreams) {
                add("[outa$audioIndex]")
            }
        }

        val filter = buildString {
            inputs.forEachIndexed { inputIndex, inputClip ->
                append("[$inputIndex:v:0]")
                for (audioIndex in 0..<inputClip.audioStreams) {
                    append("[$inputIndex:a:$audioIndex]")
                }
            }

            append("concat=n=${inputs.size}:v=1:a=$audioStreams")

            append(outputs.joinToString(""))
        }

        val mappings = outputs.flatMap { listOf("-map", it) }

        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        ProcessBuilder()
            .directory(memFS.mountPointPath)
            .command(
                "ffmpeg",
                "-v", "warning",
                *inputArgs.toTypedArray(),
                "-filter_complex", filter,
                *mappings.toTypedArray(),
                "-c:v", "h264_nvenc",
                "-rc", "constqp",
                "-qmin", "0",
                "-cq", "32",
                "-preset", "p6",
                "-multipass", "0",
                "-profile:v", "high",
                "-tune", "hq",
                mergePath.absolutePathString()
            )
            .start()
            .redirectOutputs(outputStream, errorStream)
            .waitFor(logger, outputStream, errorStream)

        logger.info { "Merged ${previousVideos.joinToString { it.path.name }} into $mergePath" }

        logger.debug { "Deleting source files: ${previousVideos.joinToString { it.path.absolutePathString() }}" }
        previousVideos.forEach {
            getKeyframeFolder(it.path).deleteRecursively()
            it.path.deleteExisting()
        }
        logger.info { "Deleted source files: ${previousVideos.joinToString { it.path.absolutePathString() }}" }
    }

    context(CoroutineScope)
    private suspend fun getKeyframeTimestamps(path: ClipPath): List<KeyframeTimestamp> = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        ProcessBuilder()
            .directory(memFS.mountPointPath)
            .command(
                "ffprobe",
                "-v", "warning",
                "-select_streams", "v",
                "-show_entries", "frame=pts_time",
                "-skip_frame", "nokey",
                "-of", "csv=p=0",
                path.absolutePathString())
            .start()
            .redirectOutputs(outputStream, errorStream)
            .waitFor(logger, outputStream, errorStream)
            .also { if (it.exitValue() != 0) throw IOException() }

        // Read each line as a timestamp
        outputStream.toByteArray().decodeToString().trim().lines()
    }

    context(CoroutineScope)
    private suspend fun getClipAudioStreams(path: ClipPath): Int = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        ProcessBuilder()
            .directory(memFS.mountPointPath)
            .command(
                "ffprobe",
                "-v", "warning",
                "-select_streams", "a",
                "-show_entries", "stream=index",
                "-of", "csv=p=0",
                path.absolutePathString())
            .start()
            .redirectOutputs(outputStream, errorStream)
            .waitFor(logger, outputStream, errorStream)
            .also { if (it.exitValue() != 0) throw IOException() }

        // Read the last line as an integer
        outputStream.toByteArray().decodeToString().trim().lines().last().toInt()
    }

    private fun Clip.matchKeyframe(other: Clip): KeyframeIndex? {
        val hashes = this.keyframeIndexByHash.keys
        val otherHashes = other.keyframeIndexByHash.keys
        return hashes
            .find { it in otherHashes }
            .let { this.keyframeIndexByHash[it] }
    }

    private suspend fun extractKeyframes(newFile: Path): Path = withContext(Dispatchers.IO) {
        logger.debug { "Extracting keyframes from $newFile" }

        val keyframesFolder = getKeyframeFolder(newFile).createDirectory()

        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        ProcessBuilder()
            .directory(memFS.mountPointPath)
            .command(
                "ffmpeg",
                "-v", "warning",
                "-skip_frame", "nokey",
                "-i", newFile.absolutePathString(),
                "-vsync", "0",
                "-f", "image2",
                "${keyframesFolder.absolutePathString()}/keyframe-0%3d.png")
            .start()
            .redirectOutputs(outputStream, errorStream)
            .waitFor(logger, outputStream, errorStream)

        logger.info { "Extracted keyframes of $newFile" }
        keyframesFolder
    }

    private fun getKeyframeFolder(clipPath: ClipPath): Path =
        memFS.mountPointPath.resolve("${clipPath.nameWithoutExtension} - Keyframes")

    @OptIn(ExperimentalPathApi::class)
    private fun hashKeyframes(newFile: Path, keyframesFolder: Path): Map<String, Int> {
        logger.debug { "Hashing keyframes from $newFile" }

        //TODO optimize using direct FileObj access
        val keyframeIndexByHash = keyframesFolder
            .walk()
            .filter { it != keyframesFolder }
            .associate { path ->
                // FFMpeg counts starting at 1 :c
                val keyframeNumber = path.nameWithoutExtension.substringAfterLast('-').toInt() - 1
                val hash = path.inputStream().use { CryptoUtils.hash(it, 1024 * 1024) }
                hash to keyframeNumber
            }

        logger.info { "Hashed keyframes of $newFile" }
        return keyframeIndexByHash
    }

    private suspend fun checkMismatchedKeyframe(currentClip: Clip, onMismatch: suspend () -> Unit) {
        // Try to find a video with matching keyframe
        // If the previous video doesn't have any matching keyframe,
        // then flush previous videos, optionally combining them if there is more than 2
        if (clips.size > 1) {
            // Find matching keyframe with previous video
            val previousClip = clips[clips.size - 2]

            logger.debug { "Trying to match keyframes between ${currentClip.path} and ${previousClip.path}" }

            // Simply check if two frames match, no info is required
            val hasMatchingHash = currentClip.matchKeyframe(previousClip) != null

            if (hasMatchingHash) {
                logger.info { "Matched keyframe between ${currentClip.path} and ${previousClip.path}, keeping videos" }
            } else {
                logger.info { "Found no matched keyframe between ${currentClip.path} and ${previousClip.path}, flushing previous videos to disk" }
                onMismatch()
            }
        }
    }
}