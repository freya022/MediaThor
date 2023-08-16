package io.github.freya022.mediathor.volume

import kotlin.io.path.absolute

object Data {
    val tempDirectory = Config.config.tempDirectory.absolute()
    val cacheFolder = Config.config.tempDirectory.resolve("web_cache")
    val volumeInputDirectory = Config.config.volumeInputDirectory.absolute()
    val volumeOutputDirectory = Config.config.volumeOutputDirectory.absolute()
}