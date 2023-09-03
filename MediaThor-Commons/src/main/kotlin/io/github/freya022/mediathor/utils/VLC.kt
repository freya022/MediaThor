package io.github.freya022.mediathor.utils

import kotlinx.coroutines.*

@OptIn(ExperimentalCoroutinesApi::class)
private val vlcDispatcher = Dispatchers.IO.limitedParallelism(1)
@Suppress("UnusedReceiverParameter")
val Dispatchers.VLC: CoroutineDispatcher
    get() = vlcDispatcher

val vlcScope = getDefaultScope(vlcDispatcher)

suspend fun <R> withVlcContext(block: suspend CoroutineScope.() -> R): R = withContext(vlcDispatcher, block)

fun launchVlcContext(block: suspend CoroutineScope.() -> Unit) {
    vlcScope.launch(block = block)
}