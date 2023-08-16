package io.github.freya022.mediathor.dl.ui.model

import io.github.freya022.mediathor.dl.utils.*
import io.lindstrom.m3u8.model.MediaPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Executors
import kotlin.math.max

class Media(
    val mediaPlaylist: MediaPlaylist,
    val segmentStates: Map<String, SegmentState>
) {
    class Segment(totalSize: Long) : SegmentState {
        private var state = SegmentState.initial(totalSize)

        override val totalSize: Long
            get() = state.totalSize

        override var size: Long
            get() = state.size
            set(value) { state.size = value }
        override val speed: Long
            get() = state.speed

        override val failed: Boolean
            get() = state.failed

        override fun fail(): SegmentState {
            state = state.fail()
            return this
        }

        override fun done(): SegmentState {
            state = state.done()
            return this
        }
    }

    interface SegmentState {
        private class Initial(override val totalSize: Long) : SegmentState {
            private var start: Long = -1

            override var size = 0L
                set(value) {
                    if (field == 0L) start = System.currentTimeMillis()
                    field = value
                }
            override val failed = false
            override val speed: Long
                get() = (size / max(1, System.currentTimeMillis() - start)) * 1000

            override fun fail() = Failed(totalSize)
            override fun done() = Done(totalSize)
        }

        private class Done(override val totalSize: Long) : SegmentState {
            override var size: Long
                get() = totalSize
                set(_) = throw IllegalStateException("Done")
            override val speed = 0L
            override val failed = false

            override fun fail() = throw IllegalStateException("Done")
            override fun done() = this
        }

        private class Failed(override val totalSize: Long) : SegmentState {
            override var size: Long
                get() = 0
                set(_) = throw IllegalStateException("Failed")
            override val speed = 0L
            override val failed = true

            override fun fail() = this
            override fun done() = throw IllegalStateException("Failed")
        }

        val totalSize: Long
        var size: Long
        val speed: Long

        val failed: Boolean

        fun fail(): SegmentState
        fun done(): SegmentState

        companion object {
            fun initial(totalSize: Long): SegmentState = Initial(totalSize)
        }
    }

    val downloadedSegments: Int
        get() = segmentStates.values.count { it.size == it.totalSize }
    val totalSegments = segmentStates.size

    val downloadedSize: Long
        get() = segmentStates.values.sumOf { it.size }
    val totalSize = segmentStates.values.sumOf { it.totalSize }

    val fails: Int
        get() = segmentStates.values.count { it.failed }

    val averageSpeed: Long
        get() = segmentStates.values.sumOf { it.speed }

    init {
        require(mediaPlaylist.mediaSegments().size == segmentStates.size)
    }

    companion object {
        @Suppress("OPT_IN_USAGE")
        suspend fun getSegmentStates(mediaPlaylist: MediaPlaylist): Map<String, SegmentState> =
            withContext(Dispatchers.IO.limitedParallelism(16)) {
                val client = CachedHttpClient.sharedClient.client.newBuilder()
                    .dispatcher(Dispatcher(Executors.newFixedThreadPool(16)))
                    .build()
                val orderedMap: MutableMap<Int, SegmentState> = ConcurrentSkipListMap()
                coroutineScope {
                    mediaPlaylist.mediaSegments()
                        .map { it.uri() }
                        .forEachIndexed { i, url ->
                            launch {
                                orderedMap[i] = getContentLength(client, url, i)
                            }
                        }
                }

                orderedMap.mapKeys { (k, _) -> mediaPlaylist.mediaSegments()[k].uri() }
            }

        private suspend fun getContentLength(client: OkHttpClient, url: String, index: Int): Segment {
            runCatchingUntil(tries = 10, errorSupplier = { "Unable to get content length for fragment #$index at $url" }) {
                return request {
                    url(url)
                    head()
                }.newCall(client)
                    .await().headers["Content-Length"]!!
                    .toLong()
                    .let { Segment(it) }
            }
        }
    }
}