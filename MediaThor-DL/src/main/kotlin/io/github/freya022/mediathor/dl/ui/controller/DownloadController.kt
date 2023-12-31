package io.github.freya022.mediathor.dl.ui.controller

import io.github.freya022.mediathor.ui.utils.fileChooser
import io.github.freya022.mediathor.ui.utils.launchMainContext
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import java.nio.file.Path

class DownloadController : VBox() {
    @FXML private lateinit var downloadsBox: VBox
    @FXML private lateinit var playlistField: TextField
    @FXML private lateinit var downloadButton: Button

    private var initialDirectory: Path? = null

    private val downloadedUrls: MutableSet<String> = hashSetOf()
    private val downloadedPaths: MutableSet<Path> = hashSetOf()

    @FXML
    private fun initialize() {
        downloadButton.disableProperty().bind(playlistField.textProperty().isEmpty)
    }

    @FXML
    private fun onDownloadAction(event: ActionEvent) = launchMainContext {
        val masterUrl = playlistField.text
        if (masterUrl in downloadedUrls) {
            return@launchMainContext Alert(Alert.AlertType.ERROR, "This has already been downloaded")
                .apply { initOwner(this@DownloadController.scene.window) }
                .show()
        }

        scene.window.fileChooser(
            title = "Download video",
            initialDirectory = initialDirectory,
            "Videos" to arrayOf("*.mp4")
        ) { path ->
            initialDirectory = path.parent

            if (path in downloadedPaths) {
                return@launchMainContext Alert(Alert.AlertType.ERROR, "This has already been scheduled for download")
                    .apply { initOwner(this@DownloadController.scene.window) }
                    .show()
            }

            playlistField.clear()

            val downloadItemController = DownloadItemController.createView(masterUrl, path)
            downloadsBox.children += downloadItemController

            downloadItemController.startAsync()
            downloadedPaths.add(path)
            downloadedUrls.add(masterUrl)
        }
    }
}