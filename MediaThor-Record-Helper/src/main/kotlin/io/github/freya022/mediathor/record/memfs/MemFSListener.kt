package io.github.freya022.mediathor.record.memfs

interface MemFSListener {
    fun onNewFileClosed(fileObj: FileObj)
}