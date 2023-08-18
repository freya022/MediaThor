// Originally from https://github.com/jnr-winfsp-team/jnr-winfsp/blob/c7be8901e8f983a91eea19f84dc373373885aa93/src/main/java/com/github/jnrwinfspteam/jnrwinfsp/memfs/DirObj.java

package io.github.freya022.mediathor.record.memfs

import com.github.jnrwinfspteam.jnrwinfsp.api.FileAttributes
import com.github.jnrwinfspteam.jnrwinfsp.api.ReparsePoint
import java.nio.file.Path

class DirObj(
    memFS: WinFspMemFS,
    parent: DirObj?,
    path: Path,
    securityDescriptor: ByteArray,
    reparsePoint: ReparsePoint?
) : MemoryObj(memFS, parent, path, securityDescriptor, reparsePoint) {
    init {
        fileAttributes.add(FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
    }

    override val allocationSize: Int = 0

    override val fileSize: Int = 0
}