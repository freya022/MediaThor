package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.record.watcher.ClipGroup
import io.github.freya022.mediathor.record.watcher.RecordWatcher
import io.github.freya022.mediathor.record.watcher.RecordWatcherListener
import io.github.freya022.mediathor.ui.utils.loadFxml
import io.github.freya022.mediathor.ui.utils.withMainContext
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class RecordHelperController : HBox(), KoinComponent, RecordWatcherListener {
    @FXML
    private lateinit var clipGroupsBox: VBox

    @FXML
    private lateinit var deleteButton: Button

    @FXML
    private lateinit var flushButton: Button

    @FXML
    private lateinit var replayBufferButton: Button

    private val recordWatcher: RecordWatcher = get()
    private val controllerByClipGroup: MutableMap<ClipGroup, ClipGroupController> = hashMapOf()

    init {
        loadFxml(this, "RecordHelperView")
        recordWatcher.addListener(this)
    }

    override suspend fun onClipGroupAdded(clipGroup: ClipGroup) = withMainContext {
        val clipGroupController = ClipGroupController(this@RecordHelperController, clipGroup)
        controllerByClipGroup[clipGroup] = clipGroupController
        clipGroupsBox.children += clipGroupController
    }

    override suspend fun onClipGroupRemoved(clipGroup: ClipGroup) = withMainContext {
        clipGroupsBox.children -= controllerByClipGroup.remove(clipGroup)
    }

    fun updateButtons() {
        val hasSelectedClips = controllerByClipGroup.values.any { it.selections.isNotEmpty() }
        deleteButton.isDisable = !hasSelectedClips
        flushButton.isDisable = !hasSelectedClips
    }

    @FXML
    private fun initialize() {

    }

    @FXML
    fun onDeleteAction(event: ActionEvent) {

    }

    @FXML
    fun onFlushAction(event: ActionEvent) {

    }

    @FXML
    fun onReplayBufferAction(event: ActionEvent) {

    }
}