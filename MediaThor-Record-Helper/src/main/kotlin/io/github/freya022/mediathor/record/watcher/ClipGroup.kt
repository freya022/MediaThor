package io.github.freya022.mediathor.record.watcher

import io.github.freya022.mediathor.record.Data
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

interface ClipGroupListener {
    suspend fun onClipAdded(clip: Clip)

    suspend fun onClipRemoved(clip: Clip)
}

abstract class ClipGroupListenerAdapter : ClipGroupListener {
    override suspend fun onClipAdded(clip: Clip) { }
    override suspend fun onClipRemoved(clip: Clip) { }
}

interface ClipGroup {
    val clips: List<Clip>
    val outputPath: Path

    fun addListener(listener: ClipGroupListener)

    suspend fun deleteClip(clip: Clip): Boolean
}

class ClipGroupImpl : ClipGroup {
    private val id = nextId()

    private val _clips: MutableList<Clip> = arrayListOf()
    override val clips: List<Clip> get() = Collections.unmodifiableList(_clips)

    override val outputPath: Path
        get() {
            val copyPath = Data.videosFolder.resolve(clips.last().path.name)
            return when (_clips.size) {
                1 -> copyPath
                // Use a .mp4 merged path
                else -> copyPath.resolveSibling(copyPath.nameWithoutExtension + ".mp4")
            }
        }

    private val listeners: MutableList<ClipGroupListener> = CopyOnWriteArrayList()

    override fun addListener(listener: ClipGroupListener) {
        this.listeners += listener
    }

    override suspend fun deleteClip(clip: Clip): Boolean {
        return _clips.remove(clip).also { removed ->
            if (removed) {
                this.listeners.forEach { it.onClipRemoved(clip) }
            }
        }
    }

    suspend fun addClip(clip: Clip) {
        _clips += clip
        this.listeners.forEach { it.onClipAdded(clip) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClipGroupImpl

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "ClipGroupImpl(id=$id, clips=$_clips)"
    }

    companion object {
        private val lock = ReentrantLock()
        private var currentId: Long = 0

        fun nextId(): Long = lock.withLock { currentId++ }
    }
}