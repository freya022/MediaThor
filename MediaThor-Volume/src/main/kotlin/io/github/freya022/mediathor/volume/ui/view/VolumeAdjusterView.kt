package io.github.freya022.mediathor.volume.ui.view

import atlantafx.base.theme.NordDark
import io.github.freya022.mediathor.ui.utils.withMainContext
import io.github.freya022.mediathor.volume.ui.controller.VolumeAdjusterController
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import kotlin.system.exitProcess

class VolumeAdjusterView private constructor() {
    companion object {
        suspend fun createView(): VolumeAdjusterController = withMainContext {
            Application.setUserAgentStylesheet(NordDark().userAgentStylesheet)

            VolumeAdjusterController().also { controller ->
                val root: Parent = FXMLLoader().apply {
                    setRoot(controller)
                    setController(controller)
                }.load(VolumeAdjusterView::class.java.getResourceAsStream("/view/VolumeAdjusterView.fxml"))

                Stage().apply {
                    scene = Scene(root)

                    setOnCloseRequest {
                        close()
                        exitProcess(0)
                    }
                }.show()
            }
        }
    }
}