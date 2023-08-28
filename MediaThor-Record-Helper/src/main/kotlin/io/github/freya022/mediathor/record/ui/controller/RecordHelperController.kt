package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.record.obs.ReplayBuffer
import io.github.freya022.mediathor.record.obs.data.events.ReplayBufferStateChangedEvent
import io.github.freya022.mediathor.record.obs.listener
import io.github.freya022.mediathor.record.watcher.ClipGroup
import io.github.freya022.mediathor.record.watcher.RecordWatcher
import io.github.freya022.mediathor.record.watcher.RecordWatcherListener
import io.github.freya022.mediathor.ui.utils.*
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.runBlocking
import mu.two.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.kordamp.ikonli.javafx.FontIcon

private val logger = KotlinLogging.logger { }

class RecordHelperController : HBox(), KoinComponent, RecordWatcherListener {
    @FXML
    private lateinit var clipGroupsBox: VBox

    @FXML
    private lateinit var deleteButton: Button

    @FXML
    private lateinit var flushButton: Button

    @FXML
    private lateinit var replayBufferButton: Button

    private val replayBuffer: ReplayBuffer = get()

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
        val controller = controllerByClipGroup.remove(clipGroup)
            ?: return@withMainContext logger.error { "Could not find controller of clip group $clipGroup" }
        clipGroupsBox.children -= controller
    }

    fun updateButtons() = runBlocking(uiScope.coroutineContext) {
        val hasSelectedClips = controllerByClipGroup.values.any { it.selections.isNotEmpty() }
        deleteButton.isDisable = !hasSelectedClips
        flushButton.isDisable = !hasSelectedClips

        replayBufferButton.text = when {
            replayBuffer.isActive -> "Stop replay buffer"
            else -> "Start replay buffer"
        }
        (replayBufferButton.graphic as FontIcon).iconLiteral = when {
            replayBuffer.isActive -> "mdrmz-pause"
            else -> "mdrmz-play_arrow"
        }
    }

    @FXML
    private fun initialize() {
        updateButtons()
        replayBuffer.obs.listener<ReplayBufferStateChangedEvent> {
            // The internal state is already updated at this point
            updateButtons()
        }
    }

    @FXML
    fun onDeleteAction(event: ActionEvent) = launchMainContext {
        deleteButton.withDebounce("Deleting...", deleteButton, flushButton) {
            val selectedClips = controllerByClipGroup.values.flatMap { it.selections }
            selectedClips.forEach {
                recordWatcher.removeClip(it)
            }
        }
    }

    @FXML
    fun onFlushAction(event: ActionEvent) = launchMainContext {
        flushButton.withDebounce("Merging...", flushButton, deleteButton) {
            val selectedGroups = controllerByClipGroup.values.flatMap { it.selections }.map { it.group }.distinct()
            selectedGroups.forEach {
                recordWatcher.flushGroup(it)
            }
        }
    }

    @FXML
    fun onReplayBufferAction(event: ActionEvent) = launchMainContext {
        replayBufferButton.withDebounce(replayBufferButton.text) {
            if (replayBuffer.isActive) {
                replayBuffer.stop()
            } else {
                replayBuffer.start()
            }
        }
        updateButtons()
    }
}