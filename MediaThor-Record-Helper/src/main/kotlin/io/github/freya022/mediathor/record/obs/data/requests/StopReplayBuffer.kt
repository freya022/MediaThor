package io.github.freya022.mediathor.record.obs.data.requests

import io.github.freya022.mediathor.record.obs.OBS

class StopReplayBuffer(obs: OBS) : Request<StopReplayBuffer.Data>(obs) {
    data object Data : RequestData

    override val requestType: String = "StopReplayBuffer"
    override val requestData: Data = Data
}