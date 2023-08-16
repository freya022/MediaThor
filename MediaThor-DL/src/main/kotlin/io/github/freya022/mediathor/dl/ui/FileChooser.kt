package io.github.freya022.mediathor.dl.ui

import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import javafx.stage.Window
import java.nio.file.Path

inline fun <R> Window.fileChooser(
    title: String,
    initialDirectory: Path? = null,
    vararg extensions: Pair<String, Array<String>>,
    block: (Path) -> R
): R? {
    return FileChooser().apply {
        this.extensionFilters += extensions.map { (name, extensions) -> ExtensionFilter(name, *extensions) }
        this.title = title
        this.initialDirectory = initialDirectory?.toFile()
    }.showSaveDialog(this)?.toPath()?.let(block)
}