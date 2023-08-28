package io.github.freya022.mediathor.ui.utils

import javafx.collections.ListChangeListener
import javafx.collections.ListChangeListener.Change
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope

fun <E : Any> ObservableList<E>.addListListener(block: suspend CoroutineScope.(Change<out E>) -> Unit) {
    addListener(ListChangeListener { change ->
        launchMainContext { block(change) }
    })
}