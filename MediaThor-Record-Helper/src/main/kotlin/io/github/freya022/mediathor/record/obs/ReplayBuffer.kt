package io.github.freya022.mediathor.record.obs

import io.github.freya022.mediathor.record.obs.data.events.ReplayBufferStateChangedEvent
import io.github.freya022.mediathor.record.obs.data.requests.*
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent

class ReplayBuffer(val obs: OBS) : KoinComponent {
    var isActive: Boolean = false
        private set
    var outputState: String = ""
        private set

    init {
        obs.listener<ReplayBufferStateChangedEvent> {
            this.isActive = it.outputActive
            this.outputState = it.outputState
        }

        runBlocking {
            try {
                isActive = GetReplayBufferStatus(obs).await<GetReplayBufferStatusResponse>().outputActive
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun start(): NullRequestResponse = StartReplayBuffer(obs).await()
    suspend fun stop(): NullRequestResponse = StopReplayBuffer(obs).await()
    suspend fun save(): NullRequestResponse = SaveReplayBuffer(obs).await()
}