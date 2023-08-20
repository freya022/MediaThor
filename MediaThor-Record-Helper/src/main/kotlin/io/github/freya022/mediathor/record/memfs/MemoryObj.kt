// Originally from https://github.com/jnr-winfsp-team/jnr-winfsp/blob/c7be8901e8f983a91eea19f84dc373373885aa93/src/main/java/com/github/jnrwinfspteam/jnrwinfsp/memfs/MemoryObj.java

package io.github.freya022.mediathor.record.memfs

import com.github.jnrwinfspteam.jnrwinfsp.api.*
import java.nio.file.Path
import java.util.*

sealed class MemoryObj(
    val memFS: WinFspMemFS,
    var parent: MemoryObj?,
    var fsLocalPath: Path,
    var securityDescriptor: ByteArray,
    reparsePoint: ReparsePoint?
) {
    val fileAttributes: EnumSet<FileAttributes> = EnumSet.noneOf(FileAttributes::class.java)
    var reparseData: ByteArray? = null
    var reparseTag = 0
    var creationTime: WinSysTime = WinSysTime.now()
    var lastAccessTime: WinSysTime = creationTime
    var lastWriteTime: WinSysTime = creationTime
    var changeTime: WinSysTime = creationTime
    var indexNumber: Long = 0

    abstract val allocationSize: Int
    abstract val fileSize: Int

    val name: String
        get() = when {
            fsLocalPath.nameCount > 0 -> fsLocalPath.fileName.toString()
            else -> throw AssertionError("Getting name on a root ${this.javaClass.simpleName} is not permitted")
        }

    val absolutePath: Path get() = Path.of(memFS.mountPoint!!).resolve(fsLocalPath.toString().removePrefix("\\")).toAbsolutePath()

    init {
        if (reparsePoint != null) {
            reparseData = reparsePoint.data
            reparseTag = reparsePoint.tag
            fileAttributes.add(FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT)
        }
    }

    @JvmOverloads
    fun generateFileInfo(filePath: String = fsLocalPath.toString()): FileInfo = FileInfo(filePath).apply {
        fileAttributes += this@MemoryObj.fileAttributes
        allocationSize = this@MemoryObj.allocationSize.toLong()
        fileSize = this@MemoryObj.fileSize.toLong()
        creationTime = this@MemoryObj.creationTime
        lastAccessTime = this@MemoryObj.lastAccessTime
        lastWriteTime = this@MemoryObj.lastWriteTime
        changeTime = this@MemoryObj.changeTime
        reparseTag = this@MemoryObj.reparseTag
        indexNumber = this@MemoryObj.indexNumber
    }

    fun touch() {
        val now = WinSysTime.now()
        lastAccessTime = now
        lastWriteTime = now
        changeTime = now
    }

    fun touchParent() {
        this.parent?.touch()
    }
}

fun MemoryObj.getSecurityDescriptorAsString(): String = SecurityDescriptorHandler.securityDescriptorToString(securityDescriptor)
fun ByteArray.decodeSecurityDescriptorToString(): String = SecurityDescriptorHandler.securityDescriptorToString(this)