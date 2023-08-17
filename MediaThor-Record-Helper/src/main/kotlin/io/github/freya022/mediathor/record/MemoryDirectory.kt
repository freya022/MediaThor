package io.github.freya022.mediathor.record

import jnr.ffi.Pointer
import ru.serce.jnrfuse.FuseFillDir
import ru.serce.jnrfuse.struct.FileStat

class MemoryDirectory(private val memoryFS: MemoryFS, name: String, parent: MemoryDirectory?) : MemoryPath(name, parent) {
    constructor(memoryFS: MemoryFS, name: String) : this(memoryFS, name, null)

    private val contents: MutableList<MemoryPath> = arrayListOf()

    @Synchronized
    fun add(p: MemoryPath) {
        contents.add(p)
        p.parent = this
    }

    @Synchronized
    fun deleteChild(child: MemoryPath) {
        contents.remove(child)
    }

    override fun find(path: String): MemoryPath? {
        var path = path
        if (super.find(path) != null) {
            return super.find(path)
        }
        while (path.startsWith("/")) {
            path = path.substring(1)
        }
        synchronized(this) {
            if (!path.contains("/")) {
                for (p in contents) {
                    if (p.name == path) {
                        return p
                    }
                }
                return null
            }
            val nextName = path.substring(0, path.indexOf("/"))
            val rest = path.substring(path.indexOf("/"))
            for (p in contents) {
                if (p.name == nextName) {
                    return p.find(rest)
                }
            }
        }
        return null
    }

    override fun getattr(stat: FileStat) {
        stat.st_mode.set(FileStat.S_IFDIR or 511)
        stat.st_uid.set(memoryFS.context.uid.get())
        stat.st_gid.set(memoryFS.context.gid.get())
    }

    @Synchronized
    fun mkdir(lastComponent: String) {
        contents.add(MemoryDirectory(memoryFS, lastComponent, this))
    }

    @Synchronized
    fun mkfile(lastComponent: String) {
        contents.add(MemoryFile(memoryFS, lastComponent, this))
    }

    @Synchronized
    fun read(buf: Pointer?, filler: FuseFillDir) {
        for (p in contents) {
            filler.apply(buf, p.name, null, 0)
        }
    }
}