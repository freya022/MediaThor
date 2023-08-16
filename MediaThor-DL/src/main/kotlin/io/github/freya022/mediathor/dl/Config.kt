package io.github.freya022.mediathor.dl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.freya022.mediathor.adapters.PathAdapter
import java.nio.file.Path
import kotlin.io.path.*

class Config private constructor(val tempDirectory: Path) {
    companion object {
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Path::class.java, PathAdapter)
            .create()

        val config: Config by lazy {
            gson
                .fromJson(Path("Config.json").readText(), Config::class.java).also { config ->
                    if (config.tempDirectory.parent.notExists())
                        throw IllegalArgumentException("${config.tempDirectory.parent} does not exist")
                    config.tempDirectory.createDirectories()
                }
        }
    }
}