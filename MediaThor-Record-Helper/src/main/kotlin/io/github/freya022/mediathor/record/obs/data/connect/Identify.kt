package io.github.freya022.mediathor.record.obs.data.connect

import io.github.freya022.mediathor.record.obs.data.OpCode
import io.github.freya022.mediathor.record.obs.data.OpCodeData

data class IdentifyData(val rpcVersion: Int, val authentication: String) : OpCodeData

fun identify(authentication: String) = OpCode(OpCode.IDENTIFY, IdentifyData(1, authentication))