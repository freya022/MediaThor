package io.github.freya022.mediathor.dl.ui.controller

import io.github.freya022.mediathor.dl.DownloaderListener
import io.github.freya022.mediathor.dl.MediaThorDownloader
import io.github.freya022.mediathor.dl.ui.App
import io.github.freya022.mediathor.dl.ui.model.Media
import io.github.freya022.mediathor.ui.utils.*
import io.github.freya022.mediathor.utils.extractThumbnail
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import javafx.scene.layout.StackPane
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.two.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds

class DownloadItemController(
    private val masterUrl: String,
    private val path: Path
) : StackPane(), DownloaderListener {
    @FXML
    private lateinit var pathLabel: Label
    @FXML
    private lateinit var segmentFailLabel: Label
    @FXML
    private lateinit var segmentsLabel: Label
    @FXML
    private lateinit var sizeSpeedLabel: Label
    @FXML
    private lateinit var progressBar: ProgressBar
    @FXML
    private lateinit var previewImage: ImageView

    private lateinit var media: Media

    private lateinit var downloadJob: Job

    override fun onDownloadProgress(url: String, bytesRead: Long, contentLength: Long) {
        val segmentState = media.segmentStates[url]!!
        if (contentLength != segmentState.totalSize)
            throw AssertionError("OkHttp expected $contentLength, but HEAD has ${segmentState.totalSize}")
        segmentState.size = bytesRead
    }

    override suspend fun onDownloadDone(url: String, path: Path) {
        media.segmentStates[url]!!.done()

        if (media.mediaPlaylist.mediaSegments().indexOfFirst { s -> s.uri() == url } == 0) {
            //Do not let the code resume on the download thread
            withMainContext {
                logger.trace { "Getting thumbnail of ${path.absolutePathString()}" }
                val outputPath = path.toAbsolutePath().resolveSibling("${path.nameWithoutExtension}.png")
                val exitCode = extractThumbnail(path, previewImage.fitWidth.toInt(), previewImage.fitHeight.toInt(), outputPath).exitValue()

                if (exitCode == 0) {
                    outputPath.inputStream().buffered().use { stream ->
                        previewImage.image = Image(stream)
                    }
                }
            }
        }
    }

    override fun onDownloadFail(url: String) {
        media.segmentStates[url]!!.fail()
    }

    fun startAsync() {
        downloadJob = launchMainContext {
            streamSemaphore.withPermit {
                fetchMasterPlaylist().onSuccess {
                    withAnimationTimer(::onUpdate) {
                        MediaThorDownloader.fromMasterPlaylist(media.mediaPlaylist, path, this@DownloadItemController)
                    }
                    onUpdate(done = true)
                }
            }
        }
    }

    private suspend fun fetchMasterPlaylist() = runCatching {
        logger.info { "Downloading from $masterUrl" }
        val mediaPlaylist = MediaThorDownloader.getMediaPlaylist(masterUrl)
        val segmentStates = Media.getSegmentStates(mediaPlaylist)
        media = Media(mediaPlaylist, segmentStates)

        onUpdate()
    }.onFailure {
        logger.error("Failed to initialize download item with master url '$masterUrl', at '${path.absolutePathString()}'", it)
        onUpdate(fail = true)
    }

    @FXML
    private fun initialize() {
        pathLabel.text = path.absolutePathString()
    }

    private fun onUpdate(fail: Boolean = false, done: Boolean = false) {
        this.style = ""
        sizeSpeedLabel.style = ""

        if (fail) {
            segmentsLabel.text = ""
            segmentFailLabel.text = ""
            sizeSpeedLabel.style = "-fx-text-fill: red"
            sizeSpeedLabel.text = "Failed to initialize"
            progressBar.progress = 0.0
        } else {
            segmentsLabel.text = "${media.downloadedSegments}/${media.totalSegments} segments"
            segmentFailLabel.text = "${media.fails} segment fail"
            if (done) {
                sizeSpeedLabel.text = "%.1f/%.1f MB @ Completed".format(
                    media.downloadedSize / 1000000.0,
                    media.totalSize / 1000000.0
                )
            } else {
                sizeSpeedLabel.text = "%.1f/%.1f MB @ %.1f MB/s".format(
                    media.downloadedSize / 1000000.0,
                    media.totalSize / 1000000.0,
                    media.averageSpeed / 1000000.0
                )
            }
            progressBar.progress = media.downloadedSize / media.totalSize.toDouble()
        }
    }

    @FXML
    private fun onGoToDirectoryAction(event: ActionEvent) {
        App.instance.hostServices.showDocument(path.parent.absolutePathString())
    }

    @FXML
    private fun onCopyUrlAction(event: ActionEvent) = launchMainContext {
        (event.target as Button).withDebounce("Copied !") {
            Clipboard.getSystemClipboard().setContent(mapOf(DataFormat.PLAIN_TEXT to masterUrl))
            delay(2.seconds)
        }
    }

    @FXML
    private fun onRetryAction(event: ActionEvent) = launchMainContext {
        if (::downloadJob.isInitialized) {
            (event.target as Button).withDebounce("Waiting...") {
                downloadJob.cancel("Will retry")
                downloadJob.join()
            }
        }
        startAsync()
    }

    companion object {
        private val logger = KotlinLogging.logger { }
        private val streamSemaphore = Semaphore(3)

        suspend fun createView(masterUrl: String, path: Path): DownloadItemController = withMainContext {
            loadFxml(DownloadItemController(masterUrl, path), "_DownloadItem")
        }
    }
}