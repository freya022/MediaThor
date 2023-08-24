package io.github.freya022.mediathor.record.watcher

interface RecordWatcherListener {
    fun onClipGroupAdded(clipGroup: ClipGroup)

    fun onClipGroupRemoved(clipGroup: ClipGroup)
}