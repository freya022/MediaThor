package io.github.freya022.mediathor.volume

import io.github.freya022.mediathor.utils.CryptoUtils
import io.github.freya022.mediathor.utils.redirectOutputs
import io.github.freya022.mediathor.utils.throwOnExitCode
import io.github.freya022.mediathor.utils.waitFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.two.KotlinLogging
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

private val logger = KotlinLogging.logger { }
private val volumeCacheFolder = Data.tempDirectory.resolve("volume_cache")

class VolumeAdjuster(
    private val input: Path,
    private val output: Path,
    private val adjustmentValue: BigDecimal
) {
    init {
        // Check if volume adjustment is divisible by 0.5
        if ((adjustmentValue % BigDecimal("0.5")).unscaledValue() != BigDecimal.ZERO.unscaledValue()) {
            throw IllegalArgumentException("Adjustment value must be divisible by 0.5, given: $adjustmentValue")
        } else if (adjustmentValue !in 1.toBigDecimal()..50.toBigDecimal() && adjustmentValue !in (-50).toBigDecimal()..(-1).toBigDecimal()) {
            throw IllegalArgumentException("Adjustment value must be between 1 and 50, given: $adjustmentValue")
        }

        volumeCacheFolder.createDirectories()
    }

    suspend fun adjust(): Unit = withContext(Dispatchers.IO) {
        val cachePath = volumeCacheFolder.resolve("${CryptoUtils.hash(input.readBytes())}+${adjustmentValue.toPlainString()}")
        if (cachePath.exists()) {
            logger.debug { "Returning cached output at ${output.absolutePathString()} with ${adjustmentValue.toPlainString()}" }
            cachePath.copyTo(output, overwrite = true)
            return@withContext
        }

        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        val tmpOutput = Files.createTempFile(output.nameWithoutExtension, ".${output.extension}")
        ProcessBuilder()
            .command("ffmpeg", "-y", "-v", "quiet",
                "-i", input.absolutePathString(),
                "-filter:a", "volume=${adjustmentValue.toPlainString()}dB",
                tmpOutput.absolutePathString()
            )
            .start()
            .redirectOutputs(outputStream, errorStream)
            .waitFor(logger, outputStream, errorStream)
            .throwOnExitCode()

        tmpOutput.moveTo(cachePath, overwrite = true)
        cachePath.copyTo(output, overwrite = true)
    }
}