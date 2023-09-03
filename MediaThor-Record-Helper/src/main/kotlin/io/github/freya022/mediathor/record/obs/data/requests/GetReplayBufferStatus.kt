package io.github.freya022.mediathor.record.obs.data.requests

import io.github.freya022.mediathor.record.obs.OBS

class GetReplayBufferStatus(obs: OBS) : Request<GetReplayBufferStatus.Data>(obs) {
    data object Data : RequestData

    override val requestType: String = "GetReplayBufferStatus"
    override val requestData: Data = Data
}

class GetReplayBufferStatusResponse(val outputActive: Boolean) : RequestResponseData