package io.github.freya022.mediathor.record.obs.data.requests

import io.github.freya022.mediathor.record.obs.OBS

class StartReplayBuffer(obs: OBS) : Request<StartReplayBuffer.Data>(obs) {
    data object Data : RequestData

    override val requestType: String = "StartReplayBuffer"
    override val requestData: Data = Data
}