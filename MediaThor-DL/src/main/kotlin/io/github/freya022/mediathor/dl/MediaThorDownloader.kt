package io.github.freya022.mediathor.dl

import io.github.freya022.mediathor.dl.utils.sharedClient
import io.github.freya022.mediathor.http.toCachedHttpClient
import io.github.freya022.mediathor.http.utils.HttpForbiddenException
import io.github.freya022.mediathor.http.utils.ProgressUtils
import io.github.freya022.mediathor.http.utils.ProgressUtils.addProgressTracking
import io.github.freya022.mediathor.http.utils.runCatchingUntil
import io.github.freya022.mediathor.http.utils.toDispatcher
import io.github.freya022.mediathor.utils.directory
import io.github.freya022.mediathor.utils.newExecutor
import io.github.freya022.mediathor.utils.redirectOutputs
import io.github.freya022.mediathor.utils.waitFor
import io.lindstrom.m3u8.model.MediaPlaylist
import io.lindstrom.m3u8.model.MediaSegment
import io.lindstrom.m3u8.model.Variant
import io.lindstrom.m3u8.parser.MasterPlaylistParser
import io.lindstrom.m3u8.parser.MediaPlaylistParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import mu.two.KotlinLogging
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.*

private val logger = KotlinLogging.logger { }
//Nine download threads at most for all instances
private val segmentDispatcher = newExecutor(9) { threadNumber -> name = "MediaThor download thread #$threadNumber" }.toDispatcher()
private val variantComparator: Comparator<Variant> =
    Comparator.comparing<Variant, Int> { it.resolution().get().width() }.reversed()
        .thenComparing(Comparator.comparing<Variant, Long> { it.bandwidth() }.reversed())

private val baseClient = OkHttpClient.Builder()
    .dispatcher(segmentDispatcher)
    .build()

class MediaThorDownloader private constructor(private val outputPath: Path, private val listener: DownloaderListener) {
    private val segmentClient = baseClient
        .newBuilder()
        .addProgressTracking(ProgressUtils.ProgressListener { url, bytesRead, contentLength, _ ->
            listener.onDownloadProgress(url.toString(), bytesRead, contentLength)
        })
        .build()
        .toCachedHttpClient(Data.cacheFolder)

    private suspend fun fromMediaPlaylist(mediaPlaylist: MediaPlaylist) = withContext(Dispatchers.IO) {
        val segmentsPaths: Array<Path?> = arrayOfNulls(mediaPlaylist.mediaSegments().size)
        coroutineScope {
            //Three download threads at most
            val semaphore = Semaphore(3)
            mediaPlaylist.mediaSegments().forEachIndexed { index, segment ->
                // This is a construct such as it forces the order at which the segments get downloaded
                semaphore.acquire()
                launch {
                    try {
                        downloadSegment(mediaPlaylist, segment).getOrNull()?.let { segmentsPaths[index] = it }
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }

        concatSegments(segmentsPaths.filterNotNull())
    }

    private suspend fun concatSegments(segmentsPath: List<Path>) {
        val concatListPath = createTempFile(prefix = "ffmpeg_concat_list")
        segmentsPath
            .joinToString("\n") { "file '${it.absolutePathString()}'" }
            .let { concatListPath.writeText(it) }

        withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()

            ProcessBuilder()
                .directory(Data.tempDirectory)
                .command("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", concatListPath.absolutePathString(), "-c", "copy", outputPath.absolutePathString())
                .start()
                .redirectOutputs(outputStream, errorStream)
                .waitFor(logger, outputStream, errorStream)

            concatListPath.deleteExisting()
        }
    }

    private fun redirectStream(arrayStream: ByteArrayOutputStream, processStream: InputStream) {
        arrayStream.bufferedWriter().use { writer ->
            processStream.bufferedReader().use { reader ->
                var readLine: String?
                while (reader.readLine().also { readLine = it } != null) {
                    writer.append(readLine + System.lineSeparator())
                }
            }
        }
    }

    private suspend fun downloadSegment(mediaPlaylist: MediaPlaylist, segment: MediaSegment): Result<Path> {
        val totalSegments = mediaPlaylist.mediaSegments().size
        val humanIndex = mediaPlaylist.mediaSegments().indexOf(segment) + 1

        return runCatching {
            downloadSegment(segment.uri(), humanIndex, totalSegments).also {
                logger.info {
                    "Downloaded %.3f MB segment $humanIndex/$totalSegments".format(it.fileSize() / 1024.0 / 1024.0)
                }
                listener.onDownloadDone(segment.uri(), it)
            }
        }.onFailure {
            if (it is CancellationException) return@onFailure
            try {
                listener.onDownloadFail(segment.uri())
                if (it is HttpForbiddenException) throw it
            } finally {
                logger.error("Failed to download media segment #$humanIndex", it)
            }
        }
    }

    private suspend fun downloadSegment(url: String, humanIndex: Int, totalSegments: Int): Path {
        logger.info { "Downloading segment $humanIndex/$totalSegments" }
        runCatchingUntil(tries = 10, errorSupplier = { "Unable to download media segment from $url" }) {
            return segmentClient.requestCached(url, folder = "media_segments").path
        }
    }

    companion object {
        suspend fun fromMasterPlaylist(
            mediaPlaylist: MediaPlaylist,
            outputPath: Path,
            listener: DownloaderListener = DownloaderListener.NOOP
        ): Path {
            when {
                outputPath.exists() -> logger.info("Output already exists: ${outputPath.absolutePathString()}")
                else -> MediaThorDownloader(outputPath, listener).fromMediaPlaylist(mediaPlaylist)
            }

            return outputPath
        }

        suspend fun getMediaPlaylist(masterUrl: String): MediaPlaylist = withContext(Dispatchers.IO) {
            val masterPlaylist = sharedClient.requestCached(masterUrl, folder = "master_playlist")
                .byteStream().use {
                    MasterPlaylistParser().readPlaylist(it)
                }

            val bestVariant = masterPlaylist.variants()
                .sortedWith(variantComparator)
                .first()
            logger.trace { "Picked variant: $bestVariant" }

            val uri = URI(masterUrl).resolve(bestVariant.uri()).toString()
            return@withContext sharedClient.requestCached(uri, folder = "media_playlist")
                .byteStream().use {
                    MediaPlaylistParser().readPlaylist(it)
                }
        }
    }
}