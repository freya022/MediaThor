package io.github.freya022.mediathor.record

import jnr.ffi.Pointer
import ru.serce.jnrfuse.struct.FileStat
import kotlin.math.max
import kotlin.math.min

// Just add 10 MB on every extension
private const val ALLOCATION_SIZE = 1024 * 1024 * 10

class MemoryFile(private val memoryFS: MemoryFS, name: String, parent: MemoryDirectory?) : MemoryPath(name, parent) {
    constructor(memoryFS: MemoryFS, name: String) : this(memoryFS, name, null)
    constructor(memoryFS: MemoryFS, name: String, text: String) : this(memoryFS, name, null) {
        data = text.encodeToByteArray()
        dataSize = data.size
    }

    private var data = ByteArray(0)
    private var dataSize = 0

    override fun getattr(stat: FileStat) {
        stat.st_mode.set(FileStat.S_IFREG or 511)
        stat.st_size.set(dataSize)
        stat.st_uid.set(memoryFS.context.uid.get())
        stat.st_gid.set(memoryFS.context.gid.get())
    }

    @Synchronized
    fun read(buffer: Pointer, size: Long, offset: Long): Int {
        val bytesToRead = min(dataSize - offset, size).toInt()
        buffer.put(0, data, offset.toInt(), bytesToRead)
        return bytesToRead
    }

    @Synchronized
    fun truncate(size: Long) {
        if (size < dataSize) {
            // Need to create a new, smaller buffer
            data = data.copyOf(size.toInt())
            dataSize = data.size
        }
    }

    @Synchronized
    fun write(buffer: Pointer, bufSize: Long, writeOffset: Long): Int {
        val maxWriteIndex = (writeOffset + bufSize).toInt()
        if (maxWriteIndex > data.size) {
            data = data.copyOf(max(data.size + ALLOCATION_SIZE, maxWriteIndex))
        }
        dataSize = maxWriteIndex
        buffer.get(0, data, writeOffset.toInt(), bufSize.toInt())
        return bufSize.toInt()
    }
}