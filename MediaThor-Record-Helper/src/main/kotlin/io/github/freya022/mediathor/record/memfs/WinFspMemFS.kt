// Originally from https://github.com/jnr-winfsp-team/jnr-winfsp/blob/c7be8901e8f983a91eea19f84dc373373885aa93/src/main/java/com/github/jnrwinfspteam/jnrwinfsp/memfs/WinFspMemFS.java

package io.github.freya022.mediathor.record.memfs

import com.github.jnrwinfspteam.jnrwinfsp.api.*
import com.github.jnrwinfspteam.jnrwinfsp.util.NaturalOrderComparator
import jnr.ffi.Pointer
import mu.two.KotlinLogging
import java.nio.file.Path
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Predicate
import kotlin.concurrent.withLock

private const val ROOT_SECURITY_DESCRIPTOR = "O:BAG:BAD:PAR(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)(A;OICI;FA;;;WD)"
private val NATURAL_ORDER: Comparator<String> = NaturalOrderComparator()

private const val MAX_FILE_NODES: Long = 1024
private val ROOT_PATH = Path.of("\\").normalize()

private val logger = KotlinLogging.logger { }

@OptIn(ExperimentalStdlibApi::class)
class WinFspMemFS(
    private var volumeLabel: String,
    val maxFileSize: Int,
    val totalSize: Long
) : WinFspStubFS() {
    private val lock = ReentrantLock()

    private val objects: MutableMap<String, MemoryObj> = hashMapOf()
    private val listeners: MutableList<MemFSListener> = arrayListOf()

    private var nextIndexNumber: Long = 1L

    val mountPointPath: Path get() = Path.of(mountPoint)

    init {
        objects[ROOT_PATH.toString()] = DirObj(
            this,
            null,
            ROOT_PATH,
            SecurityDescriptorHandler.securityDescriptorToBytes(ROOT_SECURITY_DESCRIPTOR),
            null
        )
    }

    fun addListener(listener: MemFSListener) {
        listeners += listener
    }

    override fun getVolumeInfo(): VolumeInfo = lock.withLock {
        logger.trace("== GET VOLUME INFO ==")

        return logger.exit(generateVolumeInfo())
    }

    override fun setVolumeLabel(volumeLabel: String): VolumeInfo = lock.withLock {
        logger.trace { "== SET VOLUME LABEL == $volumeLabel" }

        this.volumeLabel = volumeLabel
        return logger.exit(generateVolumeInfo())
    }

    @Throws(NTStatusException::class)
    override fun getSecurityByName(fileName: String): Optional<SecurityResult> = lock.withLock {
        logger.trace { "== GET SECURITY BY NAME == $fileName" }

        val filePath = getPath(fileName)
        if (!hasObject(filePath))
            return Optional.empty()

        val obj = getObject(filePath)
        val securityDescriptor = obj.securityDescriptor
        val info = obj.generateFileInfo()

        logger.trace {
            val securityDescriptorStr = obj.getSecurityDescriptorAsString()
            "== GET SECURITY BY NAME RETURNED == $securityDescriptorStr $info"
        }

        return logger.exit(Optional.of(SecurityResult(securityDescriptor, EnumSet.copyOf(obj.fileAttributes))))
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
    ): FileInfo = lock.withLock {
        logger.trace {
            val securityDescriptorStr = securityDescriptor.decodeSecurityDescriptorToString()
            "== CREATE == co=$createOptions ga=${grantedAccess.toHexString()} fa=$fileAttributes sd=$securityDescriptorStr as=$allocationSize rp=$reparsePoint"
        }

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
                DirObj(this, parent, filePath, securityDescriptor, reparsePoint)

            else ->
                FileObj(this, parent, filePath, securityDescriptor, reparsePoint)
                    .apply { setAllocationSize(Math.toIntExact(allocationSize)) }
        }

        obj.fileAttributes.addAll(fileAttributes)
        obj.indexNumber = nextIndexNumber++
        putObject(obj)

        if (obj is FileObj) {
            createdFileOpenCountMap[fileName] = 1
        }

        val info = obj.generateFileInfo()

        return logger.exit(info)
    }

    // Map that counts the number of opened handles to a newly-created file
    private val createdFileOpenCountMap: MutableMap<String, Int> = hashMapOf()

    @Throws(NTStatusException::class)
    override fun open(
        fileName: String,
        createOptions: Set<CreateOptions>,
        grantedAccess: Int
    ): FileInfo = lock.withLock {
        logger.trace { "== OPEN == $fileName co=$createOptions ga=${grantedAccess.toHexString()}" }

        val filePath = getPath(fileName)
        val obj = getObject(filePath)

        // Only increment file opens that come from newly-created files
        createdFileOpenCountMap.computeIfPresent(fileName) { _, oldValue -> oldValue + 1 }

        return logger.exit(obj.generateFileInfo())
    }

    @Throws(NTStatusException::class)
    override fun overwrite(
        fileName: String,
        fileAttributes: MutableSet<FileAttributes>,
        replaceFileAttributes: Boolean,
        allocationSize: Long
    ): FileInfo = lock.withLock {
        logger.trace { "== OVERWRITE == $fileName fa=$fileAttributes replaceFA=$replaceFileAttributes as=$allocationSize" }

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

        return logger.exit(file.generateFileInfo())
    }

    override fun cleanup(ctx: OpenContext, flags: Set<CleanupFlags>): Unit = lock.withLock {
        logger.trace { "== CLEANUP == $ctx cf=$flags" }

        try {
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
                removeObject(memObj.fsLocalPath)

                logger.trace("== CLEANUP DELETED FILE/DIR ==")
            }
            logger.exit()
        } catch (e: NTStatusException) {
            // we have no way to pass an error status via cleanup
        }
    }

    override fun close(ctx: OpenContext): Unit = lock.withLock {
        logger.trace { "== CLOSE == $ctx" }

        val count: Int? = createdFileOpenCountMap.computeIfPresent(ctx.path) { _, oldValue -> oldValue - 1 }
        if (count == 0) {
            createdFileOpenCountMap.remove(ctx.path)

            logger.debug { "Definitely closed new file $ctx" }
            val memoryObj = objects[ctx.path] as? FileObj
                ?: return logger.warn { "No file object at $ctx, available: ${objects.keys}" }
            listeners.forEach { it.onNewFileClosed(memoryObj) }
        }

        //TODO maybe add space reclamation
    }

    @Throws(NTStatusException::class)
    override fun read(fileName: String, pBuffer: Pointer, offset: Long, length: Int): Long = lock.withLock {
        logger.trace { "$fileName off=$offset len=$length" }

        val filePath = getPath(fileName)
        val file = getFileObject(filePath)

        val bytesRead = file.read(pBuffer, offset, length)
        return logger.exit(bytesRead.toLong())
    }

    @Throws(NTStatusException::class)
    override fun write(
        fileName: String,
        pBuffer: Pointer,
        offset: Long,
        length: Int,
        writeToEndOfFile: Boolean,
        constrainedIo: Boolean
    ): WriteResult = lock.withLock {
        logger.trace { "$fileName off=$offset len=$length writeToEnd=$writeToEndOfFile constrained=$constrainedIo" }

        val filePath = getPath(fileName)
        val file = getFileObject(filePath)
        val bytesTransferred = when {
            constrainedIo -> file.constrainedWrite(pBuffer, offset, length).toLong()
            else -> file.write(pBuffer, offset, length, writeToEndOfFile).toLong()
        }

        val info = file.generateFileInfo()

        logger.trace { "== WRITE RETURNED == bytes=$bytesTransferred $info" }
        return WriteResult(bytesTransferred, info)
    }

    @Throws(NTStatusException::class)
    override fun flush(fileName: String?): FileInfo? = lock.withLock {
        logger.trace { "== FLUSH == $fileName" }

        if (fileName == null)
            return null // whole volume is being flushed

        val filePath = getPath(fileName)
        val obj: MemoryObj = getFileObject(filePath)

        return logger.exit(obj.generateFileInfo())
    }

    @Throws(NTStatusException::class)
    override fun getFileInfo(ctx: OpenContext): FileInfo = lock.withLock {
        logger.trace { "== GET FILE INFO == $ctx" }

        val filePath = getPath(ctx.path)
        val obj = getObject(filePath)

        val info = obj.generateFileInfo()

        logger.trace { "== GET FILE INFO RETURNED == $info" }
        return info
    }

    @Throws(NTStatusException::class)
    override fun setBasicInfo(
        ctx: OpenContext,
        fileAttributes: Set<FileAttributes>,
        creationTime: WinSysTime,
        lastAccessTime: WinSysTime,
        lastWriteTime: WinSysTime,
        changeTime: WinSysTime
    ): FileInfo = lock.withLock {
        logger.trace { "== SET BASIC INFO == $ctx fa=$fileAttributes ct=$creationTime ac=$lastAccessTime wr=$lastWriteTime ch=$changeTime" }

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

        return logger.exit(obj.generateFileInfo())
    }

    @Throws(NTStatusException::class)
    override fun setFileSize(fileName: String, newSize: Long, setAllocationSize: Boolean): FileInfo = lock.withLock {
        logger.trace { "== SET FILE SIZE == $fileName size=$newSize setAlloc=$setAllocationSize" }

        val filePath = getPath(fileName)
        val file = getFileObject(filePath)

        if (setAllocationSize) {
            file.setAllocationSize(Math.toIntExact(newSize))
        } else {
            file.setFileSize(Math.toIntExact(newSize))
        }

        return logger.exit(file.generateFileInfo())
    }

    @Throws(NTStatusException::class)
    override fun canDelete(ctx: OpenContext): Unit = lock.withLock {
        logger.trace { "== CAN DELETE == $ctx" }

        val filePath = getPath(ctx.path)
        val memObj = getObject(filePath)

        if (isNotEmptyDirectory(memObj))
            throw NTStatusException(-0x3ffffeff) // STATUS_DIRECTORY_NOT_EMPTY

        logger.exit()
    }

    @Throws(NTStatusException::class)
    override fun rename(ctx: OpenContext, oldFileName: String, newFileName: String, replaceIfExists: Boolean): Unit = lock.withLock {
        logger.trace { "== RENAME == $oldFileName -> $newFileName" }

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
            if (obj.fsLocalPath.startsWith(oldFilePath)) {
                val relativePath = oldFilePath.relativize(obj.fsLocalPath)
                val newObjPath = newFilePath.resolve(relativePath)
                val newObj = removeObject(obj.fsLocalPath)

                newObj!!.fsLocalPath = newObjPath
                putObject(newObj)
            }
        }

        logger.exit()
    }

    @Throws(NTStatusException::class)
    override fun getSecurity(ctx: OpenContext): ByteArray = lock.withLock {
        logger.trace { "== GET SECURITY == $ctx" }

        val filePath = getPath(ctx.path)
        val memObj = getObject(filePath)

        val securityDescriptor = memObj.securityDescriptor

        logger.trace { "== GET SECURITY RETURNED == ${securityDescriptor.decodeSecurityDescriptorToString()}" }
        return securityDescriptor
    }

    @Throws(NTStatusException::class)
    override fun setSecurity(ctx: OpenContext, securityDescriptor: ByteArray): Unit = lock.withLock {
        logger.trace { "== SET SECURITY == $ctx sd=${securityDescriptor.decodeSecurityDescriptorToString()}" }

        val filePath = getPath(ctx.path)
        val memObj = getObject(filePath)
        memObj.securityDescriptor = securityDescriptor

        logger.exit()
    }

    @Throws(NTStatusException::class)
    override fun readDirectory(
        fileName: String,
        pattern: String?,
        marker: String?,
        consumer: Predicate<FileInfo>
    ): Unit = lock.withLock {
        logger.trace { "== READ DIRECTORY == $fileName pa=$pattern ma=$marker" }

        var marker: String? = marker

        val filePath = getPath(fileName)
        val dir = getDirObject(filePath)

        // only add the "." and ".." entries if the directory is not root
        if (dir.fsLocalPath != ROOT_PATH) {
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
            .filter { obj -> obj.parent != null && obj.parent.fsLocalPath == dir.fsLocalPath }
            .sortedWith(Comparator.comparing(MemoryObj::name, NATURAL_ORDER))
            .dropWhile { obj -> isBeforeMarker(obj.name, finalMarker) }
            .map { obj -> obj.generateFileInfo(obj.name) }
            .takeWhile(consumer::test)
            .forEach { _ -> }

        logger.exit()
    }

    @Throws(NTStatusException::class)
    override fun getDirInfoByName(parentDirName: String, fileName: String): FileInfo = lock.withLock {
        logger.trace { "== GET DIR INFO BY NAME == $parentDirName / $fileName" }

        val parentDirPath = getPath(parentDirName)
        getDirObject(parentDirPath) // ensure parent directory exists

        val filePath = parentDirPath.resolve(fileName).normalize()
        val memObj = getObject(filePath)

        return logger.exit(memObj.generateFileInfo(memObj.name))
    }

    @Throws(NTStatusException::class)
    override fun getReparsePointData(ctx: OpenContext): ByteArray = lock.withLock {
        logger.trace { "== GET REPARSE POINT DATA == $ctx" }

        val filePath = getPath(ctx.path)
        val memObj = getObject(filePath)

        if (FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT !in memObj.fileAttributes)
            throw NTStatusException(-0x3ffffd8b) // STATUS_NOT_A_REPARSE_POINT

        val reparseData = memObj.reparseData ?: throw AssertionError("Should have thrown NTStatusException")

        logger.trace { "== GET REPARSE POINT DATA RETURNED == ${reparseData.contentToString()}" }
        return reparseData
    }

    @Throws(NTStatusException::class)
    override fun setReparsePoint(ctx: OpenContext, reparseData: ByteArray, reparseTag: Int): Unit = lock.withLock {
        logger.trace { "== SET REPARSE POINT == $ctx rd=${reparseData.contentToString()} rt=$reparseTag" }

        val filePath = getPath(ctx.path)
        val memObj = getObject(filePath)

        if (isNotEmptyDirectory(memObj))
            throw NTStatusException(-0x3ffffeff) // STATUS_DIRECTORY_NOT_EMPTY

        memObj.reparseData = reparseData
        memObj.reparseTag = reparseTag
        memObj.fileAttributes += FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT

        logger.exit()
    }

    @Throws(NTStatusException::class)
    override fun deleteReparsePoint(ctx: OpenContext): Unit = lock.withLock {
        logger.trace { "== DELETE REPARSE POINT == $ctx" }

        val filePath = getPath(ctx.path)
        val memObj = getObject(filePath)

        if (FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT !in memObj.fileAttributes)
            throw NTStatusException(-0x3ffffd8b) // STATUS_NOT_A_REPARSE_POINT

        memObj.reparseData = null
        memObj.reparseTag = 0
        memObj.fileAttributes.remove(FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT)

        logger.exit()
    }

    private fun isNotEmptyDirectory(dir: MemoryObj): Boolean {
        if (dir is DirObj) {
            return objects.values
                .any { obj -> obj.fsLocalPath.startsWith(dir.fsLocalPath) && obj.fsLocalPath != dir.fsLocalPath }
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
        objects[getPathKey(obj.fsLocalPath)] = obj
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
            totalSize - objects.values.sumOf { it.allocationSize },
            volumeLabel
        )
    }

    companion object {
        private fun isBeforeMarker(name: String, marker: String?): Boolean {
            return marker != null && NATURAL_ORDER.compare(name, marker) <= 0
        }
    }
}
