package io.github.freya022.mediathor.record

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.suspendCoroutine

// https://discord.com/channels/125227483518861312/125227483518861312/1142476285893935114
class Sequencer(private val scope: CoroutineScope) {
    private val channel = Channel<suspend () -> Unit>()

    init {
        scope.launch {
            while (true) {
                val task = channel.receive()
                task()
            }
        }
    }

    suspend fun <T: Any> runTask(task: suspend () -> T): T = suspendCoroutine { sink ->
        scope.launch {
            channel.send { sink.resumeWith(runCatching { task() }) }
        }
    }
}