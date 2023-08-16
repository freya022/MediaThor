package io.github.freya022.mediathor.volume

import java.math.BigDecimal
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private typealias OutputFolderPath = Path

private val savePath = Path("Volumes.json")

data class VolumeAdjusterData(
    val outputs: MutableMap<OutputFolderPath, OutputFolder>
) {
    data class OutputFolder(val files: MutableMap<String, AdjustmentData>)

    data class AdjustmentData(var adjustmentValue: BigDecimal)

    fun save() = savePath.writeText(Config.gson.toJson(this), Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

    companion object {
        val instance = when {
            savePath.exists() -> Config.gson.fromJson(savePath.readText(), VolumeAdjusterData::class.java)
            else -> VolumeAdjusterData(hashMapOf())
        }

        init {
            Runtime.getRuntime().addShutdownHook(Thread {
                instance.save()
            })
        }
    }
}