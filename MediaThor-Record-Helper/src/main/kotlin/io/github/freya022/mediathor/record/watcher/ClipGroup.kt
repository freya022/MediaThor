package io.github.freya022.mediathor.record.watcher

import java.util.*

interface ClipGroupListener {
    fun onClipAdded(clip: Clip)

    fun onClipRemoved(clip: Clip)
}

interface ClipGroup {
    val clips: List<Clip>

    fun addListener(listener: ClipGroupListener)

    fun deleteClip(clip: Clip): Boolean
}

class ClipGroupImpl : ClipGroup {
    private val _clips: MutableList<Clip> = arrayListOf()
    override val clips: List<Clip> get() = Collections.unmodifiableList(_clips)

    private val listeners: MutableList<ClipGroupListener> = arrayListOf()

    override fun addListener(listener: ClipGroupListener) {
        listeners += listener
    }

    override fun deleteClip(clip: Clip): Boolean {
        return _clips.remove(clip).also { removed ->
            if (removed) {
                listeners.forEach { it.onClipRemoved(clip) }
            }
        }
    }

    fun addClip(clip: Clip) {
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