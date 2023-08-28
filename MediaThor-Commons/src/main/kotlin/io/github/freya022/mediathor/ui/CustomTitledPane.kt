package io.github.freya022.mediathor.ui

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Insets
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.HBox
import java.net.URL
import java.util.*

/**
 * Requires `titleBox` [HBox] and `titleLabel` [Label]
 */
abstract class CustomTitledPane : TitledPane(), Initializable {
    @FXML
    protected lateinit var titleBox: HBox

    @FXML
    protected lateinit var titleLabel: Label

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        // Prevent text edits as they are not shown, prefer titleLabel
        contentDisplay = ContentDisplay.GRAPHIC_ONLY
        textProperty().bind(SimpleStringProperty(""))

        // https://stackoverflow.com/questions/52457813/javafx-11-add-a-graphic-to-a-titledpane-on-the-right-side
        titleBox.padding = Insets(0.0, 40.0, 0.0, 0.0)
        val titleBoxParentMinX = Bindings.createDoubleBinding({ titleBox.boundsInParent.minX }, titleBox.boundsInParentProperty().map { it.minX })
        titleBox.minWidthProperty().bind(widthProperty().subtract(titleBoxParentMinX))
    }
}