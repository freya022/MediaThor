package io.github.freya022.mediathor.record.obs

import io.github.freya022.mediathor.record.obs.data.events.ReplayBufferStateChangedEvent
import io.github.freya022.mediathor.record.obs.data.requests.NullRequestResponse
import io.github.freya022.mediathor.record.obs.data.requests.SaveReplayBuffer
import io.github.freya022.mediathor.record.obs.data.requests.StartReplayBuffer
import io.github.freya022.mediathor.record.obs.data.requests.StopReplayBuffer
import org.koin.core.component.KoinComponent

class ReplayBuffer(private val obs: OBS) : KoinComponent {
    var isActive: Boolean = false
        private set
    var outputState: String = ""
        private set

    init {
        obs.listener<ReplayBufferStateChangedEvent> {
            this.isActive = it.outputActive
            this.outputState = it.outputState
        }
    }

    suspend fun start(): NullRequestResponse = StartReplayBuffer(obs).await()
    suspend fun stop(): NullRequestResponse = StopReplayBuffer(obs).await()
    suspend fun save(): NullRequestResponse = SaveReplayBuffer(obs).await()
}