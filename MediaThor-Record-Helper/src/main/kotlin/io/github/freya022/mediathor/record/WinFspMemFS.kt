// Originally from https://github.com/jnr-winfsp-team/jnr-winfsp/blob/c7be8901e8f983a91eea19f84dc373373885aa93/src/main/java/com/github/jnrwinfspteam/jnrwinfsp/memfs/WinFspMemFS.java
package io.github.freya022.mediathor.record

import com.github.jnrwinfspteam.jnrwinfsp.api.*
import com.github.jnrwinfspteam.jnrwinfsp.util.NaturalOrderComparator
import jnr.ffi.Pointer
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate

const val KB = 1024
const val MB = KB * KB
const val GB = KB * KB * KB

private const val ROOT_SECURITY_DESCRIPTOR = "O:BAG:BAD:PAR(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)(A;OICI;FA;;;WD)"
private val NATURAL_ORDER: Comparator<String> = NaturalOrderComparator()

private const val MAX_FILE_NODES: Long = 1024
private val ROOT_PATH = Path.of("\\").normalize()

class WinFspMemFS(
    private var volumeLabel: String,
    val maxFileSize: Int,
    val totalSize: Long,
    verbose: Boolean
) : WinFspStubFS() {
    private val objects: MutableMap<String, MemoryObj> = hashMapOf()

    private var nextIndexNumber: Long = 1L
    private val verboseOut: PrintStream

    init {
        objects[ROOT_PATH.toString()] = DirObj(
            null,
            ROOT_PATH,
            SecurityDescriptorHandler.securityDescriptorToBytes(ROOT_SECURITY_DESCRIPTOR),
            null
        )
        verboseOut = if (verbose) System.out else PrintStream(OutputStream.nullOutputStream())
    }

    override fun getVolumeInfo(): VolumeInfo {
        verboseOut.println("== GET VOLUME INFO ==")
        synchronized(objects) { return generateVolumeInfo() }
    }

    override fun setVolumeLabel(volumeLabel: String): VolumeInfo {
        verboseOut.printf("== SET VOLUME LABEL == %s%n", volumeLabel)
        synchronized(objects) {
            this.volumeLabel = volumeLabel
            return generateVolumeInfo()
        }
    }

    @Throws(NTStatusException::class)
    override fun getSecurityByName(fileName: String): Optional<SecurityResult> {
        verboseOut.printf("== GET SECURITY BY NAME == %s%n", fileName)
        synchronized(objects) {
            val filePath = getPath(fileName)
            if (!hasObject(filePath))
                return Optional.empty()

            val obj = getObject(filePath)
            val securityDescriptor = obj.securityDescriptor
            val info = obj.generateFileInfo()
            verboseOut.printf(
                "== GET SECURITY BY NAME RETURNED == %s %s%n",
                SecurityDescriptorHandler.securityDescriptorToString(securityDescriptor), info
            )

            return Optional.of(SecurityResult(securityDescriptor, EnumSet.copyOf(obj.fileAttributes)))
        }
    }

    @Throws(NTStatusException::class)
    override fun create(
        fileName: String,
        createOptions: Set<CreateOptions>,
        grantedAccess: Int,
        fileAttributes: Set<FileAttributes>,
        securityDescriptor: ByteArray,
        allocationSize: Long,
        reparsePoint: ReparsePoint?
    ): FileInfo {
        verboseOut.printf(
            "== CREATE == %s co=%s ga=%X fa=%s sd=%s as=%d rp=%s%n",
            fileName, createOptions, grantedAccess, fileAttributes,
            SecurityDescriptorHandler.securityDescriptorToString(securityDescriptor), allocationSize, reparsePoint
        )
        synchronized(objects) {
            val filePath = getPath(fileName)

            // Check for duplicate file/folder
            if (hasObject(filePath))
                throw NTStatusException(-0x3fffffcb) // STATUS_OBJECT_NAME_COLLISION

            // Ensure the parent object exists and is a directory
            val parent = getParentObject(filePath)

            if (objects.size >= MAX_FILE_NODES)
                throw NTStatusException(-0x3ffffd16) // STATUS_CANNOT_MAKE
            if (allocationSize > maxFileSize)
                throw NTStatusException(-0x3fffff81) // STATUS_DISK_FULL

            val obj: MemoryObj = when {
                CreateOptions.FILE_DIRECTORY_FILE in createOptions ->
                    DirObj(parent, filePath, securityDescriptor, reparsePoint)

                else ->
                    FileObj(this, parent, filePath, securityDescriptor, reparsePoint)
                        .apply { setAllocationSize(Math.toIntExact(allocationSize)) }
            }

            obj.fileAttributes.addAll(fileAttributes)
            obj.indexNumber = nextIndexNumber++
            putObject(obj)

            val info = obj.generateFileInfo()
            verboseOut.printf("== CREATE RETURNED == %s%n", info)

            return info
        }
    }

    @Throws(NTStatusException::class)
    override fun open(
        fileName: String,
        createOptions: Set<CreateOptions>,
        grantedAccess: Int
    ): FileInfo {
        verboseOut.printf("== OPEN == %s co=%s ga=%X%n", fileName, createOptions, grantedAccess)
        synchronized(objects) {
            val filePath = getPath(fileName)
            val obj = getObject(filePath)

            val info = obj.generateFileInfo()
            verboseOut.printf("== OPEN RETURNED == %s%n", info)

            return info
        }
    }

    @Throws(NTStatusException::class)
    override fun overwrite(
        fileName: String,
        fileAttributes: MutableSet<FileAttributes>,
        replaceFileAttributes: Boolean,
        allocationSize: Long
    ): FileInfo {
        verboseOut.printf(
            "== OVERWRITE == %s fa=%s replaceFA=%s as=%d%n",
            fileName, fileAttributes, replaceFileAttributes, allocationSize
        )
        synchronized(objects) {
            val filePath = getPath(fileName)
            val file = getFileObject(filePath)

            fileAttributes.add(FileAttributes.FILE_ATTRIBUTE_ARCHIVE)
            if (replaceFileAttributes)
                file.fileAttributes.clear()
            file.fileAttributes.addAll(fileAttributes)

            file.setAllocationSize(Math.toIntExact(allocationSize))
            file.setFileSize(0)

            val now = WinSysTime.now()
            file.lastAccessTime = now
            file.lastWriteTime = now
            file.changeTime = now

            val info = file.generateFileInfo()
            verboseOut.printf("== OVERWRITE RETURNED == %s%n", info)

            return info
        }
    }

    override fun cleanup(ctx: OpenContext, flags: Set<CleanupFlags>) {
        verboseOut.printf("== CLEANUP == %s cf=%s%n", ctx, flags)
        try {
            synchronized(objects) {
                val filePath = getPath(ctx.path)
                val memObj = getObject(filePath)

                if (CleanupFlags.SET_ARCHIVE_BIT in flags && memObj is FileObj)
                    memObj.fileAttributes.add(FileAttributes.FILE_ATTRIBUTE_ARCHIVE)

                val now = WinSysTime.now()

                if (CleanupFlags.SET_LAST_ACCESS_TIME in flags)
                    memObj.lastAccessTime = now

                if (CleanupFlags.SET_LAST_WRITE_TIME in flags)
                    memObj.lastWriteTime = now

                if (CleanupFlags.SET_CHANGE_TIME in flags)
                    memObj.changeTime = now

                if (CleanupFlags.SET_ALLOCATION_SIZE in flags && memObj is FileObj)
                    memObj.adaptAllocationSize(memObj.fileSize)

                if (CleanupFlags.DELETE in flags) {
                    if (isNotEmptyDirectory(memObj))
                        return  // abort if trying to remove a non-empty directory
                    removeObject(memObj.path)

                    verboseOut.println("== CLEANUP DELETED FILE/DIR ==")
                }
                verboseOut.println("== CLEANUP RETURNED ==")
            }
        } catch (e: NTStatusException) {
            // we have no way to pass an error status via cleanup
        }
    }

    override fun close(ctx: OpenContext) {
        verboseOut.printf("== CLOSE == %s%n", ctx)
        //TODO maybe add space reclamation
    }

    @Throws(NTStatusException::class)
    override fun read(fileName: String, pBuffer: Pointer, offset: Long, length: Int): Long {
        verboseOut.printf("== READ == %s off=%d len=%d%n", fileName, offset, length)
        synchronized(objects) {
            val filePath = getPath(fileName)
            val file = getFileObject(filePath)

            val bytesRead = file.read(pBuffer, offset, length)
            verboseOut.printf("== READ RETURNED == bytes=%d%n", bytesRead)

            return bytesRead.toLong()
        }
    }

    @Throws(NTStatusException::class)
    override fun write(
        fileName: String,
        pBuffer: Pointer,
        offset: Long,
        length: Int,
        writeToEndOfFile: Boolean,
        constrainedIo: Boolean
    ): WriteResult {
        verboseOut.printf(
            "== WRITE == %s off=%d len=%d writeToEnd=%s constrained=%s%n",
            fileName, offset, length, writeToEndOfFile, constrainedIo
        )
        synchronized(objects) {
            val filePath = getPath(fileName)
            val file = getFileObject(filePath)
            val bytesTransferred = when {
                constrainedIo -> file.constrainedWrite(pBuffer, offset, length).toLong()
                else -> file.write(pBuffer, offset, length, writeToEndOfFile).toLong()
            }

            val info = file.generateFileInfo()
            verboseOut.printf("== WRITE RETURNED == bytes=%d %s%n", bytesTransferred, info)

            return WriteResult(bytesTransferred, info)
        }
    }

    @Throws(NTStatusException::class)
    override fun flush(fileName: String?): FileInfo? {
        verboseOut.printf("== FLUSH == %s%n", fileName)
        synchronized(objects) {
            if (fileName == null)
                return null // whole volume is being flushed

            val filePath = getPath(fileName)
            val obj: MemoryObj = getFileObject(filePath)

            val info = obj.generateFileInfo()
            verboseOut.printf("== FLUSH RETURNED == %s%n", info)

            return info
        }
    }

    @Throws(NTStatusException::class)
    override fun getFileInfo(ctx: OpenContext): FileInfo {
        verboseOut.printf("== GET FILE INFO == %s%n", ctx)
        synchronized(objects) {
            val filePath = getPath(ctx.path)
            val obj = getObject(filePath)

            val info = obj.generateFileInfo()
            verboseOut.printf("== GET FILE INFO RETURNED == %s%n", info)

            return info
        }
    }

    @Throws(NTStatusException::class)
    override fun setBasicInfo(
        ctx: OpenContext,
        fileAttributes: Set<FileAttributes>,
        creationTime: WinSysTime,
        lastAccessTime: WinSysTime,
        lastWriteTime: WinSysTime,
        changeTime: WinSysTime
    ): FileInfo {
        verboseOut.printf(
            "== SET BASIC INFO == %s fa=%s ct=%s ac=%s wr=%s ch=%s%n",
            ctx, fileAttributes, creationTime, lastAccessTime, lastWriteTime, changeTime
        )
        synchronized(objects) {
            val filePath = getPath(ctx.path)
            val obj = getObject(filePath)

            if (FileAttributes.INVALID_FILE_ATTRIBUTES !in fileAttributes) {
                obj.fileAttributes.clear()
                obj.fileAttributes.addAll(fileAttributes)
            }

            if (creationTime.get() != 0L)
                obj.creationTime = creationTime
            if (lastAccessTime.get() != 0L)
                obj.lastAccessTime = lastAccessTime
            if (lastWriteTime.get() != 0L)
                obj.lastWriteTime = lastWriteTime
            if (changeTime.get() != 0L)
                obj.changeTime = changeTime

            val info = obj.generateFileInfo()
            verboseOut.printf("== SET BASIC INFO RETURNED == %s%n", info)

            return info
        }
    }

    @Throws(NTStatusException::class)
    override fun setFileSize(fileName: String, newSize: Long, setAllocationSize: Boolean): FileInfo {
        verboseOut.printf("== SET FILE SIZE == %s size=%d setAlloc=%s%n", fileName, newSize, setAllocationSize)
        synchronized(objects) {
            val filePath = getPath(fileName)
            val file = getFileObject(filePath)

            if (setAllocationSize) {
                file.setAllocationSize(Math.toIntExact(newSize))
            } else {
                file.setFileSize(Math.toIntExact(newSize))
            }

            val info = file.generateFileInfo()
            verboseOut.printf("== SET FILE SIZE RETURNED == %s%n", info)

            return info
        }
    }

    @Throws(NTStatusException::class)
    override fun canDelete(ctx: OpenContext) {
        verboseOut.printf("== CAN DELETE == %s%n", ctx)
        synchronized(objects) {
            val filePath = getPath(ctx.path)
            val memObj = getObject(filePath)

            if (isNotEmptyDirectory(memObj))
                throw NTStatusException(-0x3ffffeff) // STATUS_DIRECTORY_NOT_EMPTY

            verboseOut.println("== CAN DELETE RETURNED ==")
        }
    }

    @Throws(NTStatusException::class)
    override fun rename(ctx: OpenContext, oldFileName: String, newFileName: String, replaceIfExists: Boolean) {
        verboseOut.printf("== RENAME == %s -> %s%n", oldFileName, newFileName)
        synchronized(objects) {
            val oldFilePath = getPath(oldFileName)
            val newFilePath = getPath(newFileName)

            if (hasObject(newFilePath) && oldFileName != newFileName) {
                if (!replaceIfExists)
                    throw NTStatusException(-0x3fffffcb) // STATUS_OBJECT_NAME_COLLISION

                val newMemObj = getObject(newFilePath)
                if (newMemObj is DirObj)
                    throw NTStatusException(-0x3fffffde) // STATUS_ACCESS_DENIED
            }

            // Rename file or directory (and all existing descendants)
            for (obj in objects.values.toList()) {
                if (obj.path.startsWith(oldFilePath)) {
                    val relativePath = oldFilePath.relativize(obj.path)
                    val newObjPath = newFilePath.resolve(relativePath)
                    val newObj = removeObject(obj.path)

                    newObj!!.path = newObjPath
                    putObject(newObj)
                }
            }

            verboseOut.println("== RENAME RETURNED ==")
        }
    }

    @Throws(NTStatusException::class)
    override fun getSecurity(ctx: OpenContext): ByteArray {
        verboseOut.printf("== GET SECURITY == %s%n", ctx)
        synchronized(objects) {
            val filePath = getPath(ctx.path)
            val memObj = getObject(filePath)

            val securityDescriptor = memObj.securityDescriptor
            verboseOut.printf(
                "== GET SECURITY RETURNED == %s%n",
                SecurityDescriptorHandler.securityDescriptorToString(securityDescriptor)
            )

            return securityDescriptor
        }
    }

    @Throws(NTStatusException::class)
    override fun setSecurity(ctx: OpenContext, securityDescriptor: ByteArray) {
        verboseOut.printf(
            "== SET SECURITY == %s sd=%s%n",
            ctx,
            SecurityDescriptorHandler.securityDescriptorToString(securityDescriptor)
        )
        synchronized(objects) {
            val filePath = getPath(ctx.path)
            val memObj = getObject(filePath)
            memObj.securityDescriptor = securityDescriptor

            verboseOut.println("== SET SECURITY RETURNED ==")
        }
    }

    @Throws(NTStatusException::class)
    override fun readDirectory(
        fileName: String,
        pattern: String?,
        marker: String?,
        consumer: Predicate<FileInfo>
    ) {
        var marker: String? = marker

        verboseOut.printf("== READ DIRECTORY == %s pa=%s ma=%s%n", fileName, pattern, marker)
        synchronized(objects) {
            val filePath = getPath(fileName)
            val dir = getDirObject(filePath)

            // only add the "." and ".." entries if the directory is not root
            if (dir.path != ROOT_PATH) {
                if (marker == null)
                    if (!consumer.test(dir.generateFileInfo(".")))
                        return
                if (marker == null || marker == ".") {
                    val parentDir = getParentObject(filePath)
                    if (!consumer.test(parentDir.generateFileInfo("..")))
                        return
                    marker = null
                }
            }

            val finalMarker = marker
            objects.values.asSequence()
                .filter { obj -> obj.parent != null && obj.parent.path == dir.path }
                .sortedWith(Comparator.comparing(MemoryObj::name, NATURAL_ORDER))
                .dropWhile { obj -> isBeforeMarker(obj.name, finalMarker) }
                .map { obj -> obj.generateFileInfo(obj.name) }
                .takeWhile(consumer::test)
                .forEach { _ -> }
        }
    }

    @Throws(NTStatusException::class)
    override fun getDirInfoByName(parentDirName: String, fileName: String): FileInfo {
        verboseOut.printf("== GET DIR INFO BY NAME == %s / %s%n", parentDirName, fileName)
        synchronized(objects) {
            val parentDirPath = getPath(parentDirName)
            getDirObject(parentDirPath) // ensure parent directory exists

            val filePath = parentDirPath.resolve(fileName).normalize()
            val memObj = getObject(filePath)

            val info = memObj.generateFileInfo(memObj.name)
            verboseOut.printf("== GET DIR INFO BY NAME RETURNED == %s%n", info)

            return info
        }
    }

    @Throws(NTStatusException::class)
    override fun getReparsePointData(ctx: OpenContext): ByteArray {
        verboseOut.printf("== GET REPARSE POINT DATA == %s%n", ctx)
        synchronized(objects) {
            val filePath = getPath(ctx.path)
            val memObj = getObject(filePath)

            if (FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT !in memObj.fileAttributes)
                throw NTStatusException(-0x3ffffd8b) // STATUS_NOT_A_REPARSE_POINT

            val reparseData = memObj.reparseData ?: throw AssertionError("Should have thrown NTStatusException")
            verboseOut.printf("== GET REPARSE POINT DATA RETURNED == %s%n", reparseData.contentToString())

            return reparseData
        }
    }

    @Throws(NTStatusException::class)
    override fun setReparsePoint(ctx: OpenContext, reparseData: ByteArray, reparseTag: Int) {
        verboseOut.printf(
            "== SET REPARSE POINT == %s rd=%s rt=%d%n",
            ctx, reparseData.contentToString(), reparseTag
        )
        synchronized(objects) {
            val filePath = getPath(ctx.path)
            val memObj = getObject(filePath)

            if (isNotEmptyDirectory(memObj))
                throw NTStatusException(-0x3ffffeff) // STATUS_DIRECTORY_NOT_EMPTY

            memObj.reparseData = reparseData
            memObj.reparseTag = reparseTag
            memObj.fileAttributes += FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT
        }
    }

    @Throws(NTStatusException::class)
    override fun deleteReparsePoint(ctx: OpenContext) {
        verboseOut.printf("== DELETE REPARSE POINT == %s%n", ctx)
        synchronized(objects) {
            val filePath = getPath(ctx.path)
            val memObj = getObject(filePath)

            if (FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT !in memObj.fileAttributes)
                throw NTStatusException(-0x3ffffd8b) // STATUS_NOT_A_REPARSE_POINT

            memObj.reparseData = null
            memObj.reparseTag = 0
            memObj.fileAttributes.remove(FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT)
        }
    }

    private fun isNotEmptyDirectory(dir: MemoryObj): Boolean {
        if (dir is DirObj) {
            return objects.values
                .any { obj -> obj.path.startsWith(dir.path) && obj.path != dir.path }
        }
        return false
    }

    private fun getPath(filePath: String): Path {
        return Path.of(filePath).normalize()
    }

    private fun getPathKey(filePath: Path): String = filePath.toString()

    private fun hasObject(filePath: Path): Boolean = getPathKey(filePath) in objects

    @Throws(NTStatusException::class)
    private fun getObject(filePath: Path): MemoryObj {
        val obj = objects[getPathKey(filePath)]
        if (obj == null) {
            getParentObject(filePath) // may throw exception with different status code
            throw NTStatusException(-0x3fffffcc) // STATUS_OBJECT_NAME_NOT_FOUND
        }
        return obj
    }

    @Throws(NTStatusException::class)
    private fun getParentObject(filePath: Path): DirObj {
        val parentObj = objects[getPathKey(filePath.parent)]
            ?: throw NTStatusException(-0x3fffffc6) // STATUS_OBJECT_PATH_NOT_FOUND
        if (parentObj !is DirObj)
            throw NTStatusException(-0x3ffffefd) // STATUS_NOT_A_DIRECTORY

        return parentObj
    }

    private fun putObject(obj: MemoryObj) {
        objects[getPathKey(obj.path)] = obj
        obj.touchParent()
    }

    private fun removeObject(filePath: Path): MemoryObj? {
        val obj = objects.remove(getPathKey(filePath))
        obj?.touchParent()
        return obj
    }

    @Throws(NTStatusException::class)
    private fun getFileObject(filePath: Path): FileObj {
        return getObject(filePath) as? FileObj
            ?: throw NTStatusException(-0x3fffff46) // STATUS_FILE_IS_A_DIRECTORY
    }

    @Throws(NTStatusException::class)
    private fun getDirObject(filePath: Path): DirObj {
        return getObject(filePath) as? DirObj
            ?: throw NTStatusException(-0x3ffffefd) // STATUS_NOT_A_DIRECTORY
    }

    private fun generateVolumeInfo(): VolumeInfo {
        return VolumeInfo(
            totalSize,
            totalSize - objects.values.sumOf { it.fileSize },
            volumeLabel
        )
    }

    companion object {
        private fun isBeforeMarker(name: String, marker: String?): Boolean {
            return marker != null && NATURAL_ORDER.compare(name, marker) <= 0
        }
    }
}
