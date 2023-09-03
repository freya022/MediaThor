package io.github.freya022.mediathor.dl.ui

import atlantafx.base.theme.NordDark
import io.github.freya022.mediathor.dl.ui.view.DownloadView
import io.github.freya022.mediathor.ui.utils.launchMainContext
import javafx.application.Application
import javafx.stage.Stage

class App : Application() {
    init {
        instance = this
    }

    override fun start(primaryStage: Stage) {
        setUserAgentStylesheet(NordDark().userAgentStylesheet)

        launchMainContext {
            DownloadView.createView(primaryStage)
        }
    }

    companion object {
        lateinit var instance: App
    }
}