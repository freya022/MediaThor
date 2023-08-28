package io.github.freya022.mediathor.record.watcher

import io.github.freya022.mediathor.record.Sequencer
import io.github.freya022.mediathor.record.memfs.FileObj
import io.github.freya022.mediathor.record.memfs.MemFSListener
import io.github.freya022.mediathor.record.memfs.WinFspMemFS
import io.github.freya022.mediathor.utils.*
import kotlinx.coroutines.*
import mu.two.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private typealias ClipPath = Path
private typealias KeyframeFolder = Path
private typealias KeyframeTimestamp = String

private val logger = KotlinLogging.logger { }

interface RecordWatcher {
    fun addListener(listener: RecordWatcherListener)

    suspend fun removeClip(clip: Clip)
    suspend fun removeClipGroup(clipGroup: ClipGroup)

    suspend fun flushGroup(clipGroup: ClipGroup)
}

class RecordWatcherImpl : KoinComponent, MemFSListener, RecordWatcher {
    private class InputClip(val clip: Clip, val keyframeTimestamps: List<KeyframeTimestamp>, val audioStreams: Int) {
        val path: Path get() = clip.path

        var startTimestamp: String? = null
        var endTimestamp: String? = null
    }

    private val memFS: WinFspMemFS = get()

    private val scope = getDefaultScope(newExecutor(1, daemon = true) { threadNumber -> name = "RecordWatcher thread #$threadNumber" }.asCoroutineDispatcher())
    private val clipGroups: MutableList<ClipGroup> = arrayListOf()
    private val listeners: MutableList<RecordWatcherListener> = CopyOnWriteArrayList()

    private val newVideoSequencer = Sequencer(scope)

    init {
        memFS.addListener(this)
    }

    override fun addListener(listener: RecordWatcherListener) {
        listeners += listener
    }

    override fun onNewFileClosed(fileObj: FileObj) {
        scope.launch(Dispatchers.IO) {
            try {
                val newFile = fileObj.absolutePath
                if (newFile.extension != "mkv" || newFile.parent != memFS.mountPointPath)
                    return@launch

                newVideoSequencer.runTask {
                    onNewVideo(newFile)
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
    private suspend fun onNewVideo(clipPath: ClipPath) {
        val keyframeIndexByHash = hashKeyframes(clipPath)
        val createdAt = (clipPath.getAttribute("creationTime") as FileTime).toInstant()
        val duration = getClipDuration(clipPath)

        val clipGroup = findClipGroup(keyframeIndexByHash) ?: createClipGroup()

        val currentClip = Clip(clipPath, clipGroup, createdAt, duration, keyframeIndexByHash)
        clipGroup.addClip(currentClip)
    }

    private suspend fun createClipGroup(): ClipGroupImpl {
        val clipGroup = ClipGroupImpl()
        clipGroups += clipGroup
        clipGroup.addListener(object : ClipGroupListener {
            override suspend fun onClipAdded(clip: Clip) {}

            override suspend fun onClipRemoved(clip: Clip) {
                cleanup(listOf(clip))
            }
        })
        listeners.forEach { it.onClipGroupAdded(clipGroup) }
        return clipGroup
    }

    override suspend fun removeClip(clip: Clip) {
        if (clip.group.clips.size > 1) {
            clip.group.deleteClip(clip)
        } else {
            removeClipGroup(clip.group)
        }
    }

    override suspend fun removeClipGroup(clipGroup: ClipGroup) {
        clipGroups -= clipGroup
        cleanup(clipGroup.clips)
        listeners.forEach { it.onClipGroupRemoved(clipGroup) }
    }

    private fun cleanup(clips: List<Clip>) {
        logger.debug { "Deleting source files: ${clips.joinToString { it.path.absolutePathString() }}" }
        clips.forEach { it.path.deleteExisting() }
        logger.info { "Deleted source files: ${clips.joinToString { it.path.absolutePathString() }}" }
    }

    private fun findClipGroup(keyframeIndexByHash: Map<KeyframeHash, KeyframeIndex>): ClipGroupImpl? {
        // Find a group where a clip's keyframes match one of ours
        return clipGroups
            .find { group ->
                group.clips.any { it.sharesKeyframe(keyframeIndexByHash) }
            } as? ClipGroupImpl
    }

    private suspend fun getClipDuration(path: ClipPath): Duration = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()
            ProcessBuilder()
                .directory(memFS.mountPointPath)
                .command(
                    "ffprobe",
                    "-v", "warning",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    path.absolutePathString())
                .start()
                .redirectOutputs(outputStream, errorStream)
                .waitFor(logger, outputStream, errorStream)

            val durationSeconds = outputStream.toByteArray().decodeToString().trim().toBigDecimal()
            val durationMilliseconds = (durationSeconds * 1000.toBigDecimal()).longValueExact()
            durationMilliseconds.toDuration(DurationUnit.MILLISECONDS)
        } catch (e: Exception) {
            logger.catching(e)
            Duration.ZERO
        }
    }

    override suspend fun flushGroup(clipGroup: ClipGroup) = withContext(Dispatchers.IO) {
        val clips = clipGroup.clips

        val outputPath = clipGroup.outputPath

        // If there is only one previous clip, copy to disk
        if (clips.size == 1) {
            val previousVideoPath = clips.single().path
            logger.debug { "Moving $previousVideoPath into $outputPath" }

            // Copy
            previousVideoPath.moveTo(outputPath)

            logger.info { "Moved $previousVideoPath into $outputPath" }

            // Delete group and cleanup
            removeClipGroup(clipGroup)

            return@withContext
        }

        // Merge previous videos to disk
        logger.debug { "Merging ${clips.joinToString { it.path.name }}" }

        val inputs = clips.map {
            val timestamps = getKeyframeTimestamps(it.path)
            val audioStreams = getClipAudioStreams(it.path)
            InputClip(it, timestamps, audioStreams)
        }
        // Check all the same number of audio streams
        val audioStreams = inputs[0].audioStreams
        inputs.all { it.audioStreams == audioStreams }

        // Assign all start/end timestamps to all N/N+1 couples
        (0..<clips.size - 1).forEach { index ->
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
                outputPath.absolutePathString()
            )
            .start()
            .redirectOutputs(outputStream, errorStream)
            .waitFor(logger, outputStream, errorStream)
            .throwOnExitCode()

        logger.info { "Merged ${clips.joinToString { it.path.name }} into $outputPath" }

        removeClipGroup(clipGroup)
    }

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
            .throwOnExitCode()

        // Read each line as a timestamp
        outputStream.toByteArray().decodeToString().trim().lines()
    }

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
            .throwOnExitCode()

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

    private fun Clip.sharesKeyframe(otherKeyframeIndexByHash: Map<KeyframeHash, KeyframeIndex>): Boolean {
        val hashes = this.keyframeIndexByHash.keys
        val otherHashes = otherKeyframeIndexByHash.keys
        return hashes.any { it in otherHashes }
    }

    private suspend fun extractKeyframes(clipPath: ClipPath): KeyframeFolder = withContext(Dispatchers.IO) {
        logger.debug { "Extracting keyframes from $clipPath" }

        val keyframesFolder = memFS.mountPointPath
            .resolve("${clipPath.nameWithoutExtension} - Keyframes")
            .createDirectory()

        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        ProcessBuilder()
            .directory(memFS.mountPointPath)
            .command(
                "ffmpeg",
                "-v", "warning",
                "-skip_frame", "nokey",
                "-i", clipPath.absolutePathString(),
                "-vsync", "0",
                "-f", "image2",
                "${keyframesFolder.absolutePathString()}/keyframe-0%3d.png"
            )
            .start()
            .redirectOutputs(outputStream, errorStream)
            .waitFor(logger, outputStream, errorStream)
        // Don't throw on error, this is not fatal and will fall back to a singleton group

        logger.info { "Extracted keyframes of $clipPath" }
        keyframesFolder
    }

    @OptIn(ExperimentalPathApi::class)
    private suspend fun hashKeyframes(clipPath: ClipPath): Map<String, Int> = withContext(Dispatchers.IO) {
        val keyframesFolder = extractKeyframes(clipPath)

        logger.debug { "Hashing keyframes from $clipPath" }

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

        logger.info { "Hashed keyframes of $clipPath" }

        logger.debug { "Cleaning up keyframes" }
        keyframesFolder.deleteRecursively()
        logger.info { "Cleaned up keyframes" }

        keyframeIndexByHash
    }
}