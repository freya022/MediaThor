package io.github.freya022.mediathor.record.obs.data.requests

import io.github.freya022.mediathor.record.obs.data.OpCodeData
import io.github.freya022.mediathor.record.obs.data.receiveGateway
import io.ktor.client.plugins.websocket.*

interface RequestResponseData

data class RequestResponse<T : RequestResponseData>(
    val requestType: String,
    val requestId: String,
    val requestStatus: Status,
    val responseData: T?
) : OpCodeData {
    data class Status(val result: Boolean, val code: Int, val comment: String?)
}

suspend inline fun <reified T : RequestResponseData> DefaultClientWebSocketSession.receiveRequestResponse(): RequestResponse<T> =
    receiveGateway<RequestResponse<T>>()