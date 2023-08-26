package io.github.freya022.mediathor.record.obs

import io.github.freya022.mediathor.record.obs.data.events.Event

interface OBSListener {
    suspend fun onEvent(event: Event)
}

inline fun <reified T : Event> OBS.listener(crossinline block: suspend (T) -> Unit): OBSListener {
    val listener = object : OBSListener {
        override suspend fun onEvent(event: Event) {
            if (event is T) {
                block(event)
            }
        }
    }
    addListener(listener)
    return listener
}