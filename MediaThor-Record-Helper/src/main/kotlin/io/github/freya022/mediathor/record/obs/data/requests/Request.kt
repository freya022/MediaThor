package io.github.freya022.mediathor.record.obs.data.requests

import io.github.freya022.mediathor.record.obs.data.OpCode
import io.github.freya022.mediathor.record.obs.data.OpCodeData
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface RequestData

sealed class Request<T : RequestData> : OpCodeData {
    abstract val requestType: String
    val requestId: String = nextId()
    abstract val requestData: T

    fun toOpCode() = OpCode(OpCode.REQUEST, this)

    companion object {
        private val lock = ReentrantLock()
        private var currentId: Long = 0

        fun nextId(): String = lock.withLock { currentId++ }.toString()
    }
}
