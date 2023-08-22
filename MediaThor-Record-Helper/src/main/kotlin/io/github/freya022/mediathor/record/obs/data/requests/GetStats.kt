package io.github.freya022.mediathor.record.obs.data.requests

class GetStats : Request<GetStats.Data>() {
    data object Data : RequestData

    override val requestType: String = "GetStats"
    override val requestData: Data = Data
}

fun getStats() = GetStats().toOpCode()

data class GetStatsData(
    val cpuUsage: Double,
    val memoryUsage: Double,
    val availableDiskSpace: Long,
    val activeFps: Double,
    val averageFrameRenderTime: Double,
    val renderSkippedFrames: Long,
    val renderTotalFrames: Long,
    val outputSkippedFrames: Long,
    val outputTotalFrames: Long,
) : RequestResponseData