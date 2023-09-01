package io.github.freya022.mediathor.record

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.freya022.mediathor.adapters.PathAdapter
import java.nio.file.Path
import kotlin.io.path.*

class Config private constructor(val videosFolder: Path, val obsFolder: Path, val obsPassword: String) {
    companion object {
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Path::class.java, PathAdapter)
            .create()

        val config: Config by lazy {
            gson
                .fromJson(Path("Config.json").readText(), Config::class.java).also { config ->
                    if (config.videosFolder.notExists())
                        throw IllegalArgumentException("${config.videosFolder.parent} does not exist")
                    if (config.obsFolder.notExists())
                        throw IllegalArgumentException("${config.obsFolder} does not exist")
                }
        }
    }
}