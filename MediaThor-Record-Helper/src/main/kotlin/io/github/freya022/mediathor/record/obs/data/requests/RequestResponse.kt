package io.github.freya022.mediathor.record.obs.data.requests

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