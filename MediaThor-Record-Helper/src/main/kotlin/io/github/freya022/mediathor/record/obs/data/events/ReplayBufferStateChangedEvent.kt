package io.github.freya022.mediathor.record.obs.data.events

data class ReplayBufferStateChangedEvent(val outputActive: Boolean, val outputState: String) : Event()