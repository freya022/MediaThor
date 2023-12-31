package io.github.freya022.mediathor.record.obs.data.requests

import io.github.freya022.mediathor.record.obs.OBS

class GetStats(obs: OBS) : Request<GetStats.Data>(obs) {
    data object Data : RequestData

    override val requestType: String = "GetStats"
    override val requestData: Data = Data
}

data class GetStatsData(
    val cpuUsage: Double,
    val memoryUsage: Double,
    val availableDiskSpace: Double,
    val activeFps: Double,
    val averageFrameRenderTime: Double,
    val renderSkippedFrames: Long,
    val renderTotalFrames: Long,
    val outputSkippedFrames: Long,
    val outputTotalFrames: Long,
) : RequestResponseData