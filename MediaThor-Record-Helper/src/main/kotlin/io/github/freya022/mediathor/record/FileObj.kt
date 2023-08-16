package io.github.freya022.mediathor.record

import com.github.jnrwinfspteam.jnrwinfsp.api.FileAttributes
import com.github.jnrwinfspteam.jnrwinfsp.api.NTStatusException
import com.github.jnrwinfspteam.jnrwinfsp.api.ReparsePoint
import com.github.jnrwinfspteam.jnrwinfsp.api.WinSysTime
import com.github.jnrwinfspteam.jnrwinfsp.memfs.DirObj
import com.github.jnrwinfspteam.jnrwinfsp.memfs.MemoryObj
import it.unimi.dsi.fastutil.bytes.ByteArrayList
import jnr.ffi.Pointer
import java.nio.file.Path
import kotlin.math.min

private const val ALLOCATION_UNIT = 512

class FileObj(
    parent: DirObj,
    path: Path,
    securityDescriptor: ByteArray,
    reparsePoint: ReparsePoint?
) : MemoryObj(parent, path, securityDescriptor, reparsePoint) {
    private var data = ByteArrayList()
    private var fileSize: Int = 0

    init {
        fileAttributes += FileAttributes.FILE_ATTRIBUTE_ARCHIVE
    }

    @Synchronized
    override fun getAllocationSize(): Int {
        return data.size
    }

    @Synchronized
    override fun getFileSize(): Int {
        return fileSize
    }

    @Synchronized
    fun setFileSize(fileSize: Int) {
        val prevFileSize = getFileSize()
        if (fileSize < prevFileSize) {
            data.removeElements(fileSize, prevFileSize)
        } else if (fileSize > allocationSize) {
            adaptAllocationSize(fileSize)
        }
        this.fileSize = fileSize
    }

    @Synchronized
    fun adaptAllocationSize(fileSize: Int) {
        val units = (Math.addExact(fileSize, ALLOCATION_UNIT) - 1) / ALLOCATION_UNIT
        allocationSize = units * ALLOCATION_UNIT
    }

    @Synchronized
    fun setAllocationSize(newAllocationSize: Int) {
        if (newAllocationSize != allocationSize) {
            // truncate or extend the data buffer
            val newFileSize = min(getFileSize(), newAllocationSize)
            fileSize = newFileSize
        }
    }

    @Synchronized
    @Throws(NTStatusException::class)
    fun read(buffer: Pointer, offsetL: Long, size: Int): Int {
        val offset = Math.toIntExact(offsetL)
        if (offset >= getFileSize())
            throw NTStatusException(-0x3fffffef) // STATUS_END_OF_FILE

        val bytesToRead = min(getFileSize() - offset, size)
        buffer.put(0, data, offset, bytesToRead)

        setReadTime()

        return bytesToRead
    }

    @Synchronized
    fun write(buffer: Pointer, offsetL: Long, size: Int, writeToEndOfFile: Boolean): Int {
        var begOffset = Math.toIntExact(offsetL)
        if (writeToEndOfFile)
            begOffset = getFileSize()

        val endOffset = Math.addExact(begOffset, size)
        if (endOffset > getFileSize())
            setFileSize(endOffset)

        buffer.get(0, data, begOffset, size)

        setWriteTime()

        return size
    }

    @Synchronized
    fun constrainedWrite(buffer: Pointer, offsetL: Long, size: Int): Int {
        val begOffset = Math.toIntExact(offsetL)
        if (begOffset >= getFileSize())
            return 0

        val endOffset = min(getFileSize(), Math.addExact(begOffset, size))
        val transferredLength = endOffset - begOffset

        buffer.get(0, data, begOffset, transferredLength)

        setWriteTime()

        return transferredLength
    }

    private fun setReadTime() {
        setAccessTime(WinSysTime.now())
    }

    private fun setWriteTime() {
        setWriteTime(WinSysTime.now())
    }
}

fun Pointer.put(dstOff: Int, src: ByteArrayList, srcOff: Int, len: Int) {
    val tmpSrc = ByteArray(len)
    src.getElements(srcOff, tmpSrc, 0, tmpSrc.size)
    this.put(dstOff.toLong(), tmpSrc, srcOff, len)
}

fun Pointer.get(srcOff: Long, dst: ByteArrayList, dstOff: Int, len: Int) {
    val tmpDst = ByteArray(len)
    this.get(srcOff, tmpDst, 0, tmpDst.size)
    dst.addElements(dstOff, tmpDst)
}