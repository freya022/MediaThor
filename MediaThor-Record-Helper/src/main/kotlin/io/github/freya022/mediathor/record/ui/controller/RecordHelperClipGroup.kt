package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.ui.utils.loadFxml
import javafx.fxml.FXML
import javafx.scene.control.TitledPane
import javafx.scene.layout.HBox

class RecordHelperClipGroup : TitledPane() {
    @FXML
    private lateinit var clipBox: HBox

    init {
        loadFxml(this, "RecordHelperClipGroup")
    }

    @FXML
    private fun initialize() {

    }
}