package io.github.freya022.mediathor.volume.ui

import io.github.freya022.mediathor.volume.ui.view.VolumeAdjusterView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object VolumeAdjusterMain {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking(Dispatchers.Main) {
            VolumeAdjusterView.createView()
        }
    }
}