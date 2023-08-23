package io.github.freya022.mediathor.record.ui.controller

import io.github.freya022.mediathor.ui.utils.loadFxml
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.VBox

class RecordHelperClip : VBox() {
    @FXML
    private lateinit var durationLabel: Label

    @FXML
    private lateinit var thumbnailView: ImageView

    @FXML
    private lateinit var timestampLabel: Label

    init {
        loadFxml(this, "RecordHelperClip")
    }

    @FXML
    private fun initialize() {

    }
}