package io.github.freya022.mediathor.ui.utils

import javafx.animation.AnimationTimer
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.control.Labeled
import kotlinx.coroutines.*
import mu.two.KotlinLogging
import kotlin.coroutines.CoroutineContext

private object FXUtils {}

private val logger = KotlinLogging.logger { }

private val uiScope = getDefaultScope(Dispatchers.Main.immediate)

private fun getDefaultScope(
    context: CoroutineContext,
    job: Job? = null,
    errorHandler: CoroutineExceptionHandler? = null
): CoroutineScope {
    val parent = job ?: SupervisorJob()
    val handler = errorHandler ?: CoroutineExceptionHandler { _, throwable ->
        logger.error("Uncaught exception from coroutine", throwable)
        if (throwable is Error) {
            parent.cancel()
            throw throwable
        }
    }
    return CoroutineScope(context + parent + handler)
}

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

inline fun <R> Labeled.withDebounce(text: String, disabledNode: Node = this, block: () -> R): R {
    val oldText = this.text
    disabledNode.isDisable = true
    this.text = text
    return try {
        block()
    } finally {
        disabledNode.isDisable = false
        this.text = oldText
    }
}