package io.github.freya022.mediathor.ui.utils

import io.github.freya022.mediathor.utils.getDefaultScope
import javafx.animation.AnimationTimer
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.control.Labeled
import kotlinx.coroutines.*

private object FXUtils {}

val uiScope = getDefaultScope(Dispatchers.Main.immediate)

fun launchMainContext(block: suspend CoroutineScope.() -> Unit): Job {
    return uiScope.launch(block = block)
}

suspend fun <T> withMainContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.Main.immediate, block)
}

fun <T> loadFxml(controller: T, name: String): T = FXMLLoader().apply {
    setRoot(controller)
    setController(controller)
}.load(FXUtils::class.java.getResourceAsStream("/view/$name.fxml"))

fun Node.toggleStyleClass(styleClass: String) {
    val classes = getStyleClass()
    val idx: Int = classes.indexOf(styleClass)
    if (idx >= 0) {
        classes.removeAt(idx)
    } else {
        classes.add(styleClass)
    }
}

inline fun <R> withAnimationTimer(crossinline action: () -> Unit, block: () -> R): R {
    val animationTimer = object : AnimationTimer() {
        override fun handle(now: Long) = action()
    }
    animationTimer.start()
    return try {
        block()
    } finally {
        animationTimer.stop()
    }
}

inline fun <R> Labeled.withDebounce(text: String, vararg disabledNodes: Node = arrayOf(this), block: () -> R): R {
    val oldText = this.text
    disabledNodes.forEach { it.isDisable = true }
    this.text = text
    return try {
        block()
    } finally {
        disabledNodes.forEach { it.isDisable = false }
        this.text = oldText
    }
}