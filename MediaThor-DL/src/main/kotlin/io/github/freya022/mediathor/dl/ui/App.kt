package io.github.freya022.mediathor.dl.ui

import atlantafx.base.theme.NordDark
import io.github.freya022.mediathor.dl.ui.view.DownloadView
import io.github.freya022.mediathor.ui.utils.launchMainContext
import javafx.application.Application
import javafx.scene.image.Image
import javafx.stage.Stage

class App : Application() {
    init {
        instance = this
    }

    override fun start(primaryStage: Stage) {
        setUserAgentStylesheet(NordDark().userAgentStylesheet)

        primaryStage.title = "MediaThor Downloader"
        primaryStage.icons += Image(App::class.java.getResourceAsStream("/icon.png"))

        launchMainContext {
            DownloadView.createView(primaryStage)
        }
    }

    companion object {
        lateinit var instance: App
    }
}