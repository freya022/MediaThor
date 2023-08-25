package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.record.watcher.Clip
import io.github.freya022.mediathor.record.watcher.ClipGroup
import io.github.freya022.mediathor.record.watcher.ClipGroupListener
import io.github.freya022.mediathor.ui.utils.loadFxml
import io.github.freya022.mediathor.ui.utils.withMainContext
import javafx.fxml.FXML
import javafx.scene.control.TitledPane
import javafx.scene.layout.HBox
import kotlin.time.Duration

class ClipGroupController(
    private val recordHelperController: RecordHelperController,
    private val clipGroup: ClipGroup
) : TitledPane(), ClipGroupListener {
    @FXML
    private lateinit var clipBox: HBox

    private val controllerByClip: MutableMap<Clip, ClipController> = hashMapOf()

    init {
        loadFxml(this, "_ClipGroup")
    }

    val selections: List<Clip>
        get() = clipBox.childrenUnmodifiable
            .map { it as ClipController }
            .filter { it.isSelected }
            .map { it.clip }

    @FXML
    private fun initialize() {
        clipGroup.addListener(this@ClipGroupController)
    }

    override suspend fun onClipAdded(clip: Clip) = withMainContext {
        val clipController = ClipController(recordHelperController, clip)
        controllerByClip[clip] = clipController
        clipBox.children += clipController

        update()
    }

    override suspend fun onClipRemoved(clip: Clip) = withMainContext {
        clipBox.children -= controllerByClip.remove(clip)
        update()
    }

    private suspend fun update() = withMainContext {
        val clipsSize = clipGroup.clips.size
        val clipText = if (clipsSize == 1) "clip" else "clips"
        val totalDuration = clipGroup.clips.fold(Duration.ZERO) { acc, clip -> acc + clip.duration }
        text = "$clipsSize $clipText - $totalDuration -> ${clipGroup.outputPath}"
    }
}