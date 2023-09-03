package io.github.freya022.mediathor.dl.ui.view

import io.github.freya022.mediathor.dl.ui.controller.DownloadController
import io.github.freya022.mediathor.ui.utils.withMainContext
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.stage.Stage
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess

class DownloadView private constructor() {
    companion object {
        suspend fun createView(stage: Stage): DownloadController = withMainContext {
            DownloadController().also { controller ->
                val root: Parent = FXMLLoader().apply {
                    setRoot(controller)
                    setController(controller)
                }.load(DownloadView::class.java.getResourceAsStream("/view/DownloadView.fxml"))

                stage.apply {
                    scene = Scene(root)
                    setOnCloseRequest {
                        val buttonType = Alert(
                            Alert.AlertType.WARNING,
                            "Are you sure you want to close the app ?",
                            ButtonType.YES, ButtonType.NO
                        ).showAndWait().getOrNull()

                        when (buttonType) {
                            ButtonType.YES -> {
                                close()
                                exitProcess(0)
                            }
                            else -> it.consume()
                        }
                    }
                }.show()
            }
        }
    }
}