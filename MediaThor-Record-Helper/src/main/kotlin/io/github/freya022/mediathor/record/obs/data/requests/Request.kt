package io.github.freya022.mediathor.record.obs.data.requests

import io.github.freya022.mediathor.record.obs.data.OpCode
import io.github.freya022.mediathor.record.obs.data.OpCodeData
import java.util.*

interface RequestData

sealed class Request<T : RequestData> : OpCodeData {
    abstract val requestType: String
    val requestId: String = UUID.randomUUID().toString()
    abstract val requestData: T

    fun toOpCode() = OpCode(6, this)
}
