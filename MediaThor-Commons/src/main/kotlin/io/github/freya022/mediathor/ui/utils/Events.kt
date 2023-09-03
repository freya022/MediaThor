package io.github.freya022.mediathor.ui.utils

import javafx.event.ActionEvent
import javafx.event.Event
import javafx.event.EventType
import javafx.scene.Node
import javafx.scene.input.MouseEvent
import kotlinx.coroutines.CoroutineScope

fun <T : Event> Node.onEvent(eventType: EventType<T>, block: suspend CoroutineScope.(T) -> Unit) {
    addEventHandler(eventType) {
        launchMainContext { block(it) }
    }
}

fun Node.onClick(block: suspend CoroutineScope.(MouseEvent) -> Unit) = onEvent(MouseEvent.MOUSE_CLICKED, block)

fun Node.onAction(block: suspend CoroutineScope.(ActionEvent) -> Unit) = onEvent(ActionEvent.ACTION, block)