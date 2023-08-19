package io.github.freya022.mediathor.record

import io.github.freya022.mediathor.record.memfs.FileObj
import io.github.freya022.mediathor.record.memfs.MemFSListener
import io.github.freya022.mediathor.record.memfs.WinFspMemFS
import io.github.freya022.mediathor.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.two.KotlinLogging
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.*

private typealias KeyframeIndex = Int
private typealias KeyframeHash = String

private val logger = KotlinLogging.logger { }

class RecordWatcher(private val memFS: WinFspMemFS) : MemFSListener {
    private class Clip(val path: Path, val keyframeIndexByHash: Map<KeyframeHash, KeyframeIndex>)

    private val scope = getDefaultScope(newExecutor(1) { threadNumber -> name = "MediaThor record watch thread #$threadNumber" }.asCoroutineDispatcher())
    private val lock = ReentrantLock()
    private val clips: MutableList<Clip> = arrayListOf()

    init {
        memFS.addListener(this)
    }

    override fun onNewFileClosed(fileObj: FileObj) {
        try {
            val newFile = fileObj.absolutePath
            if (newFile.extension != "mkv" || newFile.parent != memFS.mountPointPath)
                return

            lock.lock()
            scope.launch(Dispatchers.IO) {
                try {
                    onNewVideo(fileObj, newFile)
                } finally {
                    lock.unlock()
                }
            }
        } catch (e: Exception) {
            logger.catching(e)
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
     *    - Run `ffprobe -v warning -select_streams v -show_frames -skip_frame nokey -of json input.mkv`
     *    - Read output as JSON, find the timestamps of the keyframes (the frame type is I)
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
    private suspend fun onNewVideo(fileObj: FileObj, newFile: Path) {
        val keyframesFolder = extractKeyframes(newFile)

        val keyframeIndexByHash = hashKeyframes(newFile, keyframesFolder)

        val currentClip = Clip(newFile, keyframeIndexByHash)
        clips += currentClip

        checkMismatchedKeyframe(currentClip) {
            // Merge previous videos to disk
        }
    }

    private suspend fun extractKeyframes(newFile: Path): Path = withContext(Dispatchers.IO) {
        logger.debug { "Extracting keyframes from $newFile" }

        val keyframesFolder = memFS.mountPointPath
            .resolve("${newFile.nameWithoutExtension} - Keyframes")
            .createDirectory()

        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        ProcessBuilder()
            .directory(memFS.mountPointPath)
            .command(
                "ffmpeg",
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

    @OptIn(ExperimentalPathApi::class)
    private fun hashKeyframes(newFile: Path, keyframesFolder: Path): Map<String, Int> {
        logger.debug { "Hashing keyframes from $newFile" }

        //TODO optimize using direct FileObj access
        val keyframeIndexByHash = keyframesFolder
            .walk()
            .filter { it != keyframesFolder }
            .associate { path ->
                val keyframeNumber = path.nameWithoutExtension.substringAfterLast('-').toInt()
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
            val currentHashes = currentClip.keyframeIndexByHash.keys
            val previousHashes = previousClip.keyframeIndexByHash.keys
            val hasMatchingHash = currentHashes.any { currentKeyframeHash -> currentKeyframeHash in previousHashes }

            if (hasMatchingHash) {
                logger.info { "Matched keyframe between ${currentClip.path} and ${previousClip.path}, keeping videos" }
            } else {
                logger.info { "Found no matched keyframe between ${currentClip.path} and ${previousClip.path}, flushing previous videos to disk" }
                onMismatch()
            }
        }
    }
}