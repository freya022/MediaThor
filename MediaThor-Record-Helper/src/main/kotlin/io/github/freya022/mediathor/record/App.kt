package io.github.freya022.mediathor.record

import atlantafx.base.theme.CupertinoDark
import io.github.freya022.mediathor.record.obs.OBS
import io.github.freya022.mediathor.record.ui.controller.RecordHelperController
import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.stopKoin

class App : Application(), KoinComponent {
    override fun start(primaryStage: Stage) {
        setUserAgentStylesheet(CupertinoDark().userAgentStylesheet)

        val root = RecordHelperController()
        primaryStage.scene = Scene(root)
        primaryStage.show()
    }

    override fun stop() {
        get<OBS>().close()
        stopKoin()
    }
}