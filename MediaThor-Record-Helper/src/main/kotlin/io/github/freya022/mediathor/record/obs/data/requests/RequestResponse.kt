package io.github.freya022.mediathor.record.obs.data.requests

import com.google.gson.reflect.TypeToken
import io.github.freya022.mediathor.record.obs.data.OpCode
import io.github.freya022.mediathor.record.obs.data.OpCodeData

interface RequestResponseData

data object NullRequestResponse : RequestResponseData

data class RequestResponse<T : RequestResponseData>(
    val requestType: String,
    val requestId: String,
    val requestStatus: Status,
    val responseData: T?
) : OpCodeData {
    data class Status(val result: Boolean, val code: Int, val comment: String?)
}

inline fun <reified R : RequestResponseData> getResponseTypeToken(): TypeToken<OpCode<RequestResponse<R>>> =
    object : TypeToken<OpCode<RequestResponse<R>>>() {}