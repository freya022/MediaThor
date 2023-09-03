package io.github.freya022.mediathor.record.obs.data.connect

import io.github.freya022.mediathor.record.obs.data.OpCodeData

data class Hello(
    val obsWebSocketVersion: String,
    val rpcVersion: Int,
    val authentication: Authentication
) : OpCodeData {
    data class Authentication(val challenge: String, val salt: String)
}