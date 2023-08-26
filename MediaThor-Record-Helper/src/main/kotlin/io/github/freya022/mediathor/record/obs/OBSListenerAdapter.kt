package io.github.freya022.mediathor.record.obs

import io.github.freya022.mediathor.record.obs.data.events.Event
import io.github.freya022.mediathor.record.obs.data.events.ReplayBufferStateChangedEvent

open class OBSListenerAdapter : OBSListener {
    open fun onReplayBufferStateChanged(event: ReplayBufferStateChangedEvent) {}

    override suspend fun onEvent(event: Event) {
        when (event) {
            is ReplayBufferStateChangedEvent -> onReplayBufferStateChanged(event)
        }
    }
}