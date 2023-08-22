package io.github.freya022.mediathor.record

import java.nio.file.Path

object Data {
    val videosFolder: Path = Config.config.videosFolder.toAbsolutePath()
}