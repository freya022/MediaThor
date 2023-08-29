package io.github.freya022.mediathor.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.two.KotlinLogging
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private val logger = KotlinLogging.logger { }

fun Number.toFFMpegSize(): Int = when (val int = toInt()) {
    0 -> -1
    else -> int
}

// "ffmpeg -ss [seconds] -i [source] -frames:v 1 -s [Width]x[Height] -update true output.png"
suspend fun extractThumbnail(path: Path, width: Int, height: Int, outputPath: Path): Process = withContext(Dispatchers.IO) {
    val outputStream = ByteArrayOutputStream()
    val errorStream = ByteArrayOutputStream()
    ProcessBuilder()
        .command(
            "ffmpeg", "-y",
            "-ss", "1",
            "-i", path.absolutePathString(),
            "-frames:v", "1",
            "-vf", "scale=${width}:${height}:flags=lanczos",
            "-update", "true",
            outputPath.absolutePathString()
        )
        .start()
        .redirectOutputs(outputStream, errorStream)
        .waitFor(logger, outputStream, errorStream)
}