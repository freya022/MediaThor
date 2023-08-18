// Originally from https://github.com/jnr-winfsp-team/jnr-winfsp/blob/c7be8901e8f983a91eea19f84dc373373885aa93/src/main/java/com/github/jnrwinfspteam/jnrwinfsp/memfs/FileObj.java

package io.github.freya022.mediathor.record.memfs

import com.github.jnrwinfspteam.jnrwinfsp.api.FileAttributes
import com.github.jnrwinfspteam.jnrwinfsp.api.NTStatusException
import com.github.jnrwinfspteam.jnrwinfsp.api.ReparsePoint
import com.github.jnrwinfspteam.jnrwinfsp.api.WinSysTime
import jnr.ffi.Pointer
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

private const val ALLOCATION_UNIT = 512

class FileObj(
    private val memFS: WinFspMemFS,
    parent: DirObj,
    path: Path,
    securityDescriptor: ByteArray,
    reparsePoint: ReparsePoint?
) : MemoryObj(parent, path, securityDescriptor, reparsePoint) {
    private val lock = ReentrantLock()

    private var data = ByteArray(0)
    private var dataSize = 0

    @get:Synchronized
    override val allocationSize get() = dataSize

    @get:Synchronized
    override var fileSize: Int = 0
        private set

    init {
        fileAttributes += FileAttributes.FILE_ATTRIBUTE_ARCHIVE
    }

    @Synchronized
    fun setFileSize(fileSize: Int) {
    fun setFileSize(fileSize: Int): Unit = lock.withLock {
        val prevFileSize = this.fileSize
        if (fileSize < prevFileSize) {
            for (i in fileSize..<prevFileSize) {
                data[i] = 0.toByte()
            }
        } else if (fileSize > allocationSize) {
            adaptAllocationSize(fileSize)
        }
        this.fileSize = fileSize
    }

    @Synchronized
    fun adaptAllocationSize(fileSize: Int): Unit = lock.withLock {
        val units = (Math.addExact(fileSize, ALLOCATION_UNIT) - 1) / ALLOCATION_UNIT
        setAllocationSize(units * ALLOCATION_UNIT)
    }

    @Synchronized
    fun setAllocationSize(newAllocationSize: Int): Unit = lock.withLock {
        if (newAllocationSize != allocationSize) {
            // truncate or extend the data buffer
            val newFileSize = min(this.fileSize, newAllocationSize)
            if (data.size < newAllocationSize) {
                if (newAllocationSize > memFS.maxFileSize)
                    throw NTStatusException(-0x3fffff81) // STATUS_DISK_FULL

                data = data.copyOf(min(newAllocationSize * 2, memFS.maxFileSize))
                dataSize = data.size
            }
            fileSize = newFileSize
        }
    }

    @Synchronized
    @Throws(NTStatusException::class)
    fun read(buffer: Pointer, offsetL: Long, size: Int): Int = lock.withLock {
        val offset = Math.toIntExact(offsetL)
        if (offset >= this.fileSize)
            throw NTStatusException(-0x3fffffef) // STATUS_END_OF_FILE

        val bytesToRead = min(this.fileSize - offset, size)
        buffer.put(0, data, offset, bytesToRead)

        setReadTime()

        return bytesToRead
    }

    @Synchronized
    fun write(buffer: Pointer, offsetL: Long, size: Int, writeToEndOfFile: Boolean): Int = lock.withLock {
        var begOffset = Math.toIntExact(offsetL)
        if (writeToEndOfFile)
            begOffset = this.fileSize

        val endOffset = Math.addExact(begOffset, size)
        if (endOffset > this.fileSize)
            setFileSize(endOffset)

        buffer.get(0, data, begOffset, size)

        setWriteTime()

        return size
    }

    @Synchronized
    fun constrainedWrite(buffer: Pointer, offsetL: Long, size: Int): Int = lock.withLock {
        val begOffset = Math.toIntExact(offsetL)
        if (begOffset >= this.fileSize)
            return 0

        val endOffset = min(this.fileSize, Math.addExact(begOffset, size))
        val transferredLength = endOffset - begOffset

        buffer.get(0, data, begOffset, transferredLength)

        setWriteTime()

        return transferredLength
    }

    private fun setReadTime(): Unit = lock.withLock {
        lastAccessTime = WinSysTime.now()
    }

    private fun setWriteTime(): Unit = lock.withLock {
        lastWriteTime = WinSysTime.now()
    }
}