package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.record.watcher.Clip
import io.github.freya022.mediathor.record.watcher.ClipGroup
import io.github.freya022.mediathor.record.watcher.ClipGroupListener
import io.github.freya022.mediathor.ui.CustomTitledPane
import io.github.freya022.mediathor.ui.utils.*
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import java.net.URL
import java.util.*
import kotlin.collections.*
import kotlin.time.Duration

class ClipGroupController(
    private val recordHelperController: RecordHelperController,
    private val clipGroup: ClipGroup
) : CustomTitledPane(), ClipGroupListener {
    @FXML
    private lateinit var clipsPane: ScrollPane

    @FXML
    private lateinit var clipsBox: HBox

    @FXML
    private lateinit var mergeButton: Button

    private val controllerByClip: MutableMap<Clip, ClipController> = hashMapOf()

    init {
        loadFxml(this, "_ClipGroup")
    }

    val selections: List<Clip>
        get() = controllerByClip.values
            .filter { it.isSelected }
            .map { it.clip }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        super.initialize(location, resources)

        clipGroup.addListener(this@ClipGroupController)
        clipsBox.childrenUnmodifiable.addListListener {
            update()
        }
    }

    @FXML
    fun onMergeAction(event: ActionEvent) = launchMainContext {
        mergeButton.withDebounce("Merging...") {
            recordHelperController.mergeClips(listOf(clipGroup))
        }
    }

    override suspend fun onClipAdded(clip: Clip) = withMainContext {
        val clipController = ClipController(recordHelperController, clip)
        controllerByClip[clip] = clipController
        clipsBox.children += clipController
    }

    override suspend fun onClipRemoved(clip: Clip) = withMainContext {
        clipsBox.children -= controllerByClip.remove(clip)
    }

    private suspend fun update() = withMainContext {
        val clipsSize = clipGroup.clips.size
        val clipText = if (clipsSize == 1) "clip" else "clips"
        val totalDuration = clipGroup.clips.fold(Duration.ZERO) { acc, clip -> acc + clip.duration }
        titleLabel.text = "$clipsSize $clipText - $totalDuration -> ${clipGroup.outputPath}"
    }
}