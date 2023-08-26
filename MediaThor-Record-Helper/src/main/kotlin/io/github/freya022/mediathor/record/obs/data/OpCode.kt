package io.github.freya022.mediathor.record.obs.data

import io.ktor.client.plugins.websocket.*

data class OpCode<T : OpCodeData>(val op: Int, val d: T) {
    companion object {
        const val IDENTIFY = 1
        const val EVENT = 5
        const val REQUEST = 6
        const val REQUEST_RESPONSE = 7
    }
}

suspend inline fun <reified T : OpCodeData> DefaultClientWebSocketSession.receiveGateway(): T =
    receiveDeserialized<OpCode<T>>().d