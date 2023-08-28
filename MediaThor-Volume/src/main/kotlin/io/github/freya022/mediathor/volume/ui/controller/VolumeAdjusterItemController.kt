package io.github.freya022.mediathor.volume.ui.controller

import atlantafx.base.controls.ProgressSliderSkin
import io.github.freya022.mediathor.ui.utils.launchMainContext
import io.github.freya022.mediathor.ui.utils.withDebounce
import io.github.freya022.mediathor.volume.VolumeAdjuster
import io.github.freya022.mediathor.volume.VolumeAdjusterData.AdjustmentData
import javafx.animation.AnimationTimer
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.*
import javafx.scene.layout.HBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2RoundMZ
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Path
import java.text.NumberFormat
import kotlin.io.path.copyTo
import kotlin.time.Duration.Companion.milliseconds

class VolumeAdjusterItemController(
    private val inputFile: Path,
    private val outputFile: Path,
    private val adjustmentData: AdjustmentData,
    volumeAdjusterController: VolumeAdjusterController,
) : HBox() {
    enum class PlayingMedia {
        INPUT,
        OUTPUT
    }

    private inner class MediaPlayerListener : MediaPlayerEventAdapter() {
        private var timer: AnimationTimer? = null

        override fun playing(mediaPlayer: MediaPlayer) {
            launchMainContext {
                timer?.stop()
                timer = object : AnimationTimer() {
                    init { start() }

                    override fun handle(now: Long) {
                        val timeMs = mediaPlayer.status().time()
                        playbackTimestampLabel.text = timeMs.playbackTest

                        playbackSlider.isValueChanging = true
                        playbackSlider.value = timeMs.toDouble()
                        playbackSlider.isValueChanging = false
                    }
                }
                playbackIcon.iconCode = Material2RoundMZ.PAUSE
            }
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            launchMainContext {
                timer?.stop()
                playbackIcon.iconCode = Material2RoundMZ.PLAY_ARROW
            }
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            launchMainContext {
                timer?.stop()
                playbackIcon.iconCode = Material2RoundMZ.PLAY_ARROW
            }
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            launchMainContext {
                timer?.stop()
                playbackIcon.iconCode = Material2RoundMZ.PLAY_ARROW
            }
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            launchMainContext {
                playbackSlider.max = newLength.toDouble()
                playbackDurationLabel.text = newLength.playbackTest
            }
        }

        private val format = NumberFormat.getIntegerInstance().apply {
            maximumIntegerDigits = 2
            minimumIntegerDigits = 2
            isGroupingUsed = false
        }

        private val Long.playbackTest get() = this.milliseconds.toComponents { minutes, seconds, _ ->
            "${format.format(minutes)}:${format.format(seconds)}"
        }
    }

    @FXML
    private lateinit var adjustmentSpinner: Spinner<BigDecimal>
    @FXML
    private lateinit var playbackDurationLabel: Label
    @FXML
    private lateinit var playbackSlider: Slider
    @FXML
    private lateinit var playbackTimestampLabel: Label
    @FXML
    private lateinit var switchSourceButton: Button
    @FXML
    private lateinit var playbackIcon: FontIcon

    private var playingMedia = PlayingMedia.OUTPUT
    private var currentMedia = outputFile.toUri().toString()
    private val mediaPlayer = volumeAdjusterController.mediaPlayer
    private val mediaPlayerListener = MediaPlayerListener()

    init {
        FXMLLoader().apply {
            setRoot(this@VolumeAdjusterItemController)
            setController(this@VolumeAdjusterItemController)
        }.load<HBox>(this.javaClass.getResourceAsStream("/view/_VolumeAdjusterItemView.fxml"))
    }

    @FXML
    fun initialize() {
        playbackSlider.skin = ProgressSliderSkin(playbackSlider)

        adjustmentSpinner.valueFactory = object : SpinnerValueFactory<BigDecimal>() {
            init {
                value = adjustmentData.adjustmentValue
            }

            override fun decrement(steps: Int) {
                value -= BigDecimal("0.5") * steps.toBigDecimal()
                if (value.toDouble() > -1 && value.toDouble() < 1) {
                    value = BigDecimal.ONE.negate()
                } else if (value.toDouble() < -50) {
                    value = BigDecimal(-50)
                }
            }

            override fun increment(steps: Int) {
                value += BigDecimal("0.5") * steps.toBigDecimal()
                if (value.toDouble() > -1 && value.toDouble() < 1) {
                    value = BigDecimal.ONE
                } else if (value.toDouble() > 50) {
                    value = BigDecimal(50)
                }
            }
        }

        playbackSlider.valueProperty().addListener { _, _, newValue ->
            // Ignore VLC updates
            if (!playbackSlider.isValueChanging && mediaPlayer.isCurrentPlayer(mediaPlayerListener)) {
                launchMainContext {
                    mediaPlayer.seek(newValue.toLong())
                }
            }
        }
    }

    @FXML
    fun onAdjustAction(event: ActionEvent) = launchMainContext {
        (event.target as Button).withDebounce("Adjusting...", this@VolumeAdjusterItemController) {
            pause()
            mediaPlayer.invalidate()

            if (adjustmentSpinner.value.unscaledValue() == BigInteger.ZERO) {
                withContext(Dispatchers.IO) {
                    inputFile.copyTo(outputFile, overwrite = true)
                }
            } else {
                VolumeAdjuster(inputFile, outputFile, adjustmentSpinner.value).adjust()
            }

            adjustmentData.adjustmentValue = adjustmentSpinner.value
            updateSource()
            play()
        }
    }

    @FXML
    fun onPlaybackPlay(event: ActionEvent) = launchMainContext {
        if (mediaPlayer.isPlaying && mediaPlayer.isCurrentPlayer(mediaPlayerListener)) pause() else play()
    }

    @FXML
    fun onSwitchSourceAction(event: ActionEvent) = launchMainContext {
        playingMedia = when (playingMedia) {
            PlayingMedia.INPUT -> PlayingMedia.OUTPUT
            PlayingMedia.OUTPUT -> PlayingMedia.INPUT
        }

        updateSource()
        play()
    }

    private fun updateSource() {
        currentMedia = when (playingMedia) {
            PlayingMedia.INPUT -> inputFile.toUri().toString()
            PlayingMedia.OUTPUT -> outputFile.toUri().toString()
        }

        switchSourceButton.text = when (playingMedia) {
            PlayingMedia.INPUT -> "Switch to adjusted"
            PlayingMedia.OUTPUT -> "Switch to original"
        }
    }

    private suspend fun play() {
        mediaPlayer.play(currentMedia, playbackSlider.value.toLong(), mediaPlayerListener)
    }

    private suspend fun pause() {
        mediaPlayer.pause()
    }
}