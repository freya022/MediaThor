package io.github.freya022.mediathor.record.obs.data.connect

import io.github.freya022.mediathor.record.obs.data.OpCodeData

data class IdentifyData(val rpcVersion: Int, val authentication: String) : OpCodeData
