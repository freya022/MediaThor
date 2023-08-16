package io.github.freya022.mediathor.dl

import kotlin.io.path.absolute

object Data {
    val tempDirectory = Config.config.tempDirectory.absolute()
    val cacheFolder = Config.config.tempDirectory.resolve("web_cache")
}