package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.record.watcher.*
import io.github.freya022.mediathor.ui.utils.loadFxml
import io.github.freya022.mediathor.ui.utils.toggleStyleClass
import javafx.animation.AnimationTimer
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
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
    private lateinit var thumbnailView: ImageView

    @FXML
    private lateinit var timestampLabel: Label

    private val recordWatcher: RecordWatcher = get()

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

        recordWatcher.addListener(object : RecordWatcherListenerAdapter() {
            override suspend fun onClipGroupRemoved(clipGroup: ClipGroup) {
                if (this@ClipController.clip.group === clipGroup) {
                    timer.stop()
                }
            }
        })

        clip.group.addListener(object : ClipGroupListenerAdapter() {
            override suspend fun onClipRemoved(clip: Clip) {
                if (this@ClipController.clip === clip) {
                    timer.stop()
                }
            }
        })

        setOnMouseClicked {
            toggleStyleClass(selectedClass)
            recordHelperController.updateButtons()
        }
    }
}