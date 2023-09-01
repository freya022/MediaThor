package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.record.App
import io.github.freya022.mediathor.record.watcher.*
import io.github.freya022.mediathor.ui.utils.loadFxml
import io.github.freya022.mediathor.ui.utils.onClick
import io.github.freya022.mediathor.ui.utils.toggleStyleClass
import io.github.freya022.mediathor.utils.launchVlcContext
import javafx.animation.AnimationTimer
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField.*
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toKotlinDuration

private const val selectedClass = "bg-subtle"
private val timestampFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .appendLiteral(' ')
    .appendValue(HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(MINUTE_OF_HOUR, 2)
    .optionalStart()
    .appendLiteral(':')
    .appendValue(SECOND_OF_MINUTE, 2)
    .toFormatter(Locale.getDefault());

class ClipController(
    private val recordHelperController: RecordHelperController,
    val clip: Clip
) : VBox(), KoinComponent {
    @FXML
    private lateinit var durationLabel: Label

    @FXML
    private lateinit var contentPane: StackPane

    @FXML
    private lateinit var thumbnailView: ImageView

    @FXML
    private lateinit var progressBar: ProgressBar

    @FXML
    private lateinit var timestampLabel: Label

    private val recordWatcher: RecordWatcher = get()

    private val embeddedMediaPlayer = App.newMediaPlayer()
    private var mediaLength: Long = 0

    val isSelected: Boolean get() = selectedClass in styleClass

    init {
        loadFxml(this, "_Clip")
    }

    @FXML
    private fun initialize() {
        durationLabel.text = clip.duration.toString()
        val timer = object : AnimationTimer() {
            // ZoneId should not be stored in theory
            private val timezone = ZoneOffset.systemDefault()
            private val createdAt = clip.createdAt.atZone(timezone)
            private val createdAtStr = createdAt.format(timestampFormatter)
            override fun handle(clock: Long) {
                val now = ZonedDateTime.now(timezone)
                //TODO dynamic duration
                timestampLabel.text = "$createdAtStr - ${Duration.between(createdAt, now).toKotlinDuration().toInt(DurationUnit.MINUTES)} minutes ago"
            }
        }
        timer.start()

        launchVlcContext {
            embeddedMediaPlayer.videoSurface().set(ImageViewVideoSurface(thumbnailView))
            embeddedMediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    mediaLength = newLength
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    progressBar.progress = newTime.toDouble() / mediaLength.toDouble()
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    launchVlcContext {
                        mediaPlayer.controls().setTime(0)
                    }
                }
            })

            progressBar.onClick {
                launchVlcContext {
                    embeddedMediaPlayer.controls().setPosition((it.x / progressBar.width).toFloat())
                }
            }

            //TODO add global volume
            embeddedMediaPlayer.media().startPaused(clip.path.toString())
            contentPane.hoverProperty().addListener { _, _, isHover ->
                launchVlcContext {
                    embeddedMediaPlayer.controls().setPause(!isHover)
                }
            }
        }

        recordWatcher.addListener(object : RecordWatcherListenerAdapter() {
            override suspend fun onClipGroupRemoved(clipGroup: ClipGroup) {
                if (this@ClipController.clip.group === clipGroup) {
                    timer.stop()
                    App.destroyMediaPlayer(embeddedMediaPlayer)
                }
            }
        })

        clip.group.addListener(object : ClipGroupListenerAdapter() {
            override suspend fun onClipRemoved(clip: Clip) {
                if (this@ClipController.clip === clip) {
                    timer.stop()
                    App.destroyMediaPlayer(embeddedMediaPlayer)
                }
            }
        })

        onClick {
            toggleStyleClass(selectedClass)
            recordHelperController.updateButtons()
        }
    }
}