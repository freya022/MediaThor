package io.github.freya022.mediathor.volume

import io.github.freya022.mediathor.http.utils.await
import io.github.freya022.mediathor.utils.CryptoUtils
import io.github.freya022.mediathor.volume.utils.sharedClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.two.KotlinLogging
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.jsoup.Jsoup
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.io.path.*

private val logger = KotlinLogging.logger { }
private val volumeCacheFolder = Data.tempDirectory.resolve("volume_cache")

class VolumeAdjuster(
    private val input: Path,
    private val output: Path,
    private val adjustmentValue: BigDecimal
) {
    enum class OperationType(val value: String) {
        INCREASE("increase"),
        DECREASE("decrease")
    }

    enum class ChannelType(val value: String) {
        LEFT("left"),
        RIGHT("right"),
        ALL("all")
    }

    private val client = sharedClient.client
    private val absAdjustmentValue = adjustmentValue.abs()
    private val channels = ChannelType.ALL
    private val operationType: OperationType = if (adjustmentValue.signum() == 1) OperationType.INCREASE else OperationType.DECREASE

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
            logger.debug { "Returning cached output at ${output.absolutePathString()} for $operationType ${absAdjustmentValue.toPlainString()}" }
            cachePath.copyTo(output, overwrite = true)
            return@withContext
        }

        val htmlOutput = sendFile()

        val document = Jsoup.parse(htmlOutput)
        val elements = document.select("article > div.alert-success > p > a").map { it.attr("href") }
        val downloadLink = elements.find { "/download.php" in it }
            ?: throwParseError(htmlOutput, "Failed to get download link")
        val deleteLink = elements.find { "/delete-file.php" in it }
            ?: throwParseError(htmlOutput, "Failed to get delete link")

        downloadOutput(downloadLink)
        output.copyTo(cachePath)

        runCatching {
            deleteRemoteInput(deleteLink)
        }.onFailure {
            logger.error("Unable delete remote input using $deleteLink", it)
        }
    }

    private suspend fun downloadOutput(downloadLink: String) = withContext(Dispatchers.IO) {
        output.outputStream().buffered().use { outputStream ->
            client.newCall(Request(downloadLink.toHttpUrl())).await().use { response ->
                response.body.byteStream().copyTo(outputStream)
            }
        }

        logger.debug { "Download to ${output.absolutePathString()} successfully for $operationType ${absAdjustmentValue.toPlainString()}" }
    }

    private suspend fun deleteRemoteInput(deleteLink: String) = withContext(Dispatchers.IO) {
        client.newCall(Request(deleteLink.toHttpUrl())).await().close()

        logger.debug { "Deleted remote input ${input.absolutePathString()} successfully" }
    }

    private fun throwParseError(htmlOutput: String, message: String): Nothing {
        val errorFile = createTempFile(Path(""), prefix = input.nameWithoutExtension, suffix = ".html")
        errorFile.writeText(htmlOutput)
        throw IllegalArgumentException("$message, see $errorFile")
    }

    private suspend fun sendFile(): String {
        val formBody = MultipartBody.Builder()
            .addFormDataPart("upfile", "input.mp3", input.toFile().asRequestBody("audio/mpeg".toMediaType()))
            .addFormDataPart("optradio", operationType.value)
            .addFormDataPart("level", absAdjustmentValue.toPlainString())
            .addFormDataPart("channels", channels.value)
            .addFormDataPart("submitfile", "")
            .setType(MultipartBody.FORM)
            .build()

        val request = Request.Builder()
            .url("https://www.mp3louder.com/")
            .post(formBody)
            .build()

        return client.newCall(request).await().use { it.body.string() }
    }
}