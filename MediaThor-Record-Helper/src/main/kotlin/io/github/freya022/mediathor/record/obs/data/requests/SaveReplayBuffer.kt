package io.github.freya022.mediathor.record.obs.data.requests

class SaveReplayBuffer : Request<SaveReplayBuffer.Data>() {
    data object Data : RequestData

    override val requestType: String = "SaveReplayBuffer"
    override val requestData: Data = Data
}