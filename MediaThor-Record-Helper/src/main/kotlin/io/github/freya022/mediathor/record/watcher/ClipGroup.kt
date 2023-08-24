package io.github.freya022.mediathor.record.watcher

import io.github.freya022.mediathor.record.Data
import java.nio.file.Path
import java.util.*
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

interface ClipGroupListener {
    suspend fun onClipAdded(clip: Clip)

    suspend fun onClipRemoved(clip: Clip)
}

interface ClipGroup {
    val clips: List<Clip>
    val outputPath: Path

    fun addListener(listener: ClipGroupListener)

    suspend fun deleteClip(clip: Clip): Boolean
}

class ClipGroupImpl : ClipGroup {
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

    private val listeners: MutableList<ClipGroupListener> = arrayListOf()

    override fun addListener(listener: ClipGroupListener) {
        listeners += listener
    }

    override suspend fun deleteClip(clip: Clip): Boolean {
        return _clips.remove(clip).also { removed ->
            if (removed) {
                listeners.forEach { it.onClipRemoved(clip) }
            }
        }
    }

    suspend fun addClip(clip: Clip) {
        _clips += clip
        listeners.forEach { it.onClipAdded(clip) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClipGroupImpl

        if (_clips != other._clips) return false
        if (listeners != other.listeners) return false

        return true
    }

    override fun hashCode(): Int {
        var result = _clips.hashCode()
        result = 31 * result + listeners.hashCode()
        return result
    }

    override fun toString(): String {
        return "ClipGroupImpl(clips=$_clips)"
    }
}