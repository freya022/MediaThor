package io.github.freya022.mediathor.volume.ui.controller

import atlantafx.base.controls.ProgressSliderSkin
import io.github.freya022.mediathor.ui.utils.listener
import io.github.freya022.mediathor.volume.Data
import io.github.freya022.mediathor.volume.VolumeAdjusterData
import io.github.freya022.mediathor.volume.VolumeAdjusterData.AdjustmentData
import io.github.freya022.mediathor.volume.VolumeAdjusterData.OutputFolder
import io.github.freya022.mediathor.volume.ui.utils.ManagedMediaPlayer
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import java.math.BigDecimal
import kotlin.io.path.*
import kotlin.math.roundToInt

private val supportedExtensions = setOf("mp3", "opus", "ogg")

class VolumeAdjusterController : VBox() {
    private val data = VolumeAdjusterData.instance
    private val inputDirectory = Data.volumeInputDirectory
    private val outputDirectory = Data.volumeOutputDirectory

    @FXML
    private lateinit var masterVolumeSlider: Slider
    @FXML
    private lateinit var masterVolumeLabel: Label
    @FXML
    private lateinit var outputsGrid: GridPane

    lateinit var mediaPlayer: ManagedMediaPlayer

    @FXML
    @OptIn(ExperimentalPathApi::class)
    fun initialize() {
        masterVolumeSlider.skin = ProgressSliderSkin(masterVolumeSlider)
        masterVolumeLabel.textProperty().bind(masterVolumeSlider.valueProperty().asString("%.0f %%"))

        mediaPlayer = ManagedMediaPlayer(masterVolumeSlider.value.roundToInt())
        masterVolumeSlider.valueProperty().listener { _, _, newVolume -> mediaPlayer.volume = newVolume.toInt() }

        inputDirectory
            .walk()
            .filter { it.extension in supportedExtensions }
            .sortedBy { it.nameWithoutExtension }
            .forEachIndexed { i, inputFile ->
                val outputFileName = "${inputFile.nameWithoutExtension}.opus"
                val outputPath = outputDirectory.resolve(outputFileName)
                //Copy input file if output does not exist, is the same as if zero transformations were applied
                if (outputPath.notExists()) {
                    inputFile.copyTo(outputPath)
                }

                val outputFolder = data.outputs.computeIfAbsent(outputDirectory) { OutputFolder(hashMapOf()) }
                val adjustmentData = outputFolder.files.computeIfAbsent(outputFileName) { AdjustmentData(BigDecimal.ZERO) }

                outputsGrid.addRow(i, Label(inputFile.nameWithoutExtension), VolumeAdjusterItemController(inputFile, outputPath, adjustmentData, this))
            }
    }
}