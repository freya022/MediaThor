package io.github.freya022.mediathor.record.obs.data

import io.ktor.client.plugins.websocket.*

data class OpCode<T : OpCodeData>(val op: Int, val d: T)

suspend inline fun <reified T : OpCodeData> DefaultClientWebSocketSession.receiveGateway(): T =
    receiveDeserialized<OpCode<T>>().d