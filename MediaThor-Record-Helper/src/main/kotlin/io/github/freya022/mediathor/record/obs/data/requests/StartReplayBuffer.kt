package io.github.freya022.mediathor.record.obs.data.requests

class StartReplayBuffer : Request<StartReplayBuffer.Data>() {
    data object Data : RequestData

    override val requestType: String = "StartReplayBuffer"
    override val requestData: Data = Data
}