package io.github.freya022.mediathor.record.obs.data.requests

class StopReplayBuffer : Request<StopReplayBuffer.Data>() {
    data object Data : RequestData

    override val requestType: String = "StopReplayBuffer"
    override val requestData: Data = Data
}