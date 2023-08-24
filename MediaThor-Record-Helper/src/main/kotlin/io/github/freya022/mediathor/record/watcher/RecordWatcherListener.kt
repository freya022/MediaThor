package io.github.freya022.mediathor.record.watcher

interface RecordWatcherListener {
    suspend fun onClipGroupAdded(clipGroup: ClipGroup)

    suspend fun onClipGroupRemoved(clipGroup: ClipGroup)
}