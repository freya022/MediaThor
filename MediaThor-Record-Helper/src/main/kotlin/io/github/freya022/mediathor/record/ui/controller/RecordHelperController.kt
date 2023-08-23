package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.ui.utils.loadFxml
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

class RecordHelperController : HBox() {
    @FXML
    private lateinit var clipGroupsBox: VBox

    @FXML
    private lateinit var deleteButton: Button

    @FXML
    private lateinit var flushButton: Button

    @FXML
    private lateinit var replayBufferButton: Button

    init {
        loadFxml(this, "RecordHelperView")
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