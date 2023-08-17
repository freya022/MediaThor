package io.github.freya022.mediathor.record

import kotlin.io.path.Path

object FSMain {
    val root = Path("X:\\")

    @JvmStatic
    fun main(args: Array<String>) {
        val memFS = MemoryFS()

        try {
            memFS.mount(root, true, false)
        } finally {
            memFS.umount()
        }
    }
}