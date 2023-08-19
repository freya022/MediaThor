package io.github.freya022.mediathor.record

import io.github.freya022.mediathor.record.memfs.FileObj
import io.github.freya022.mediathor.record.memfs.MemFSListener
import io.github.freya022.mediathor.record.memfs.WinFspMemFS
import io.github.freya022.mediathor.utils.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import mu.two.KotlinLogging
import java.io.ByteArrayOutputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

private val logger = KotlinLogging.logger { }

class RecordWatcher(private val memFS: WinFspMemFS) : MemFSListener {
    private val scope = getDefaultScope(newExecutor(1) { threadNumber -> name = "MediaThor record watch thread #$threadNumber" }.asCoroutineDispatcher())

    init {
        memFS.addListener(this)
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
    override fun onNewFileClosed(fileObj: FileObj): Unit = scope.launch {
    override fun onNewFileClosed(fileObj: FileObj): Unit = scope.launch(Dispatchers.IO) {
        //TODO mutex
        try {
            val newFile = fileObj.absolutePath

            if (newFile.extension != "mkv" || newFile.parent != memFS.mountPointPath) return@launch

            logger.debug { "Extracting keyframes from $newFile" }

            val keyframesFolder = memFS.mountPointPath.resolve("${newFile.nameWithoutExtension} - Keyframes").createDirectory()

            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()
            ProcessBuilder()
                .directory(memFS.mountPointPath)
                .command("ffmpeg", "-skip_frame", "nokey", "-i", newFile.absolutePathString(), "-vsync", "0", "-f", "image2", "${keyframesFolder.absolutePathString()}/keyframe-0%3d.png")
                .start()
                .redirectOutputs(outputStream, errorStream)
                .waitFor(logger, outputStream, errorStream)

            logger.info { "Extracted keyframes from $newFile" }

//            val hash = withContext(Dispatchers.IO) {
//                fileObj.toArray().let { CryptoUtils.hash(it) }
//            }
//            val hash2 = withContext(Dispatchers.IO) {
//                fileObj.absolutePath.inputStream().use { CryptoUtils.hash(it, 1024 * 1024) }
//            }
//
//            println("hash = $hash")
//            println("hash2 = $hash2")
        } catch (e: Exception) {
            logger.catching(e)
        }
    }.let { }
}