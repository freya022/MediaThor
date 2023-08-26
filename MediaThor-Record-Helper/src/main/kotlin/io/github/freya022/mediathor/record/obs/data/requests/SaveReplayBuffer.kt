package io.github.freya022.mediathor.record.obs.data.requests

import io.github.freya022.mediathor.record.obs.OBS

class SaveReplayBuffer(obs: OBS) : Request<SaveReplayBuffer.Data>(obs) {
    data object Data : RequestData

    override val requestType: String = "SaveReplayBuffer"
    override val requestData: Data = Data
}