package io.github.freya022.mediathor.ui.utils

import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.collections.ListChangeListener.Change
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope

fun <E : Any> ObservableList<E>.addListListener(block: suspend CoroutineScope.(Change<out E>) -> Unit): ListChangeListener<E> {
    val listener = ListChangeListener<E> { change ->
        launchMainContext { block(change) }
    }
    addListener(listener)
    return listener
}

fun <T : Any> ObservableValue<T>.listener(block: suspend CoroutineScope.(observable: ObservableValue<out T>, oldValue: T, newValue: T) -> Unit): ChangeListener<T> {
    val listener = ChangeListener<T> { observable, oldValue, newValue ->
        launchMainContext { block(observable, oldValue, newValue) }
    }
    addListener(listener)
    return listener
}