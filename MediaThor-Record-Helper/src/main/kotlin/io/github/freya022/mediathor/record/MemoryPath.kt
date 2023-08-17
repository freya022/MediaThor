package io.github.freya022.mediathor.record

import ru.serce.jnrfuse.struct.FileStat

sealed class MemoryPath(
    var name: String? = null,
    var parent: MemoryDirectory? = null
) {
    constructor(name: String) : this(name, null)

    @Synchronized
    fun delete() {
        if (parent != null) {
            parent!!.deleteChild(this)
            parent = null
        }
    }

    open fun find(path: String): MemoryPath? {
        var path = path
        while (path.startsWith("/")) {
            path = path.substring(1)
        }
        return if (path == name || path.isEmpty()) {
            this
        } else null
    }

    protected abstract fun getattr(stat: FileStat)

    fun rename(newName: String) {
        var newName = newName
        while (newName.startsWith("/")) {
            newName = newName.substring(1)
        }
        name = newName
    }
}