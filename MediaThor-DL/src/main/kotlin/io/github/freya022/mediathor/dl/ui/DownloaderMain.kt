package io.github.freya022.mediathor.dl.ui

import io.github.freya022.mediathor.dl.ui.view.DownloadView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object DownloaderMain {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking(Dispatchers.Main) {
            DownloadView.createView()
        }
    }
}