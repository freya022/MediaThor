package io.github.freya022.mediathor.dl

import java.nio.file.Path

interface DownloaderListener {
    object NOOP : DownloaderListener {
        override fun onDownloadProgress(url: String, bytesRead: Long, contentLength: Long) {}
        override suspend fun onDownloadDone(url: String, path: Path) {}
        override fun onDownloadFail(url: String) {}
    }

    fun onDownloadProgress(url: String, bytesRead: Long, contentLength: Long)
    suspend fun onDownloadDone(url: String, path: Path)
    fun onDownloadFail(url: String)
}