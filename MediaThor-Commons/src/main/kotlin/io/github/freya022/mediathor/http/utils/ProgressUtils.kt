package io.github.freya022.mediathor.http.utils

import io.github.freya022.mediathor.http.utils.ProgressUtils.ProgressListener
import okhttp3.*
import okio.*
import java.io.IOException

object ProgressUtils {
    fun OkHttpClient.Builder.addProgressTracking(progressListener: ProgressListener): OkHttpClient.Builder =
        addNetworkInterceptor { chain ->
            val originalResponse: Response = chain.proceed(chain.request())
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body, originalResponse.request.url, progressListener))
                .build()
        }

    private class ProgressResponseBody(
        private val responseBody: ResponseBody,
        private val url: HttpUrl,
        private val progressListener: ProgressListener
    ) : ResponseBody() {
        private var bufferedSource: BufferedSource? = null
        override fun contentType(): MediaType? {
            return responseBody.contentType()
        }

        override fun contentLength(): Long {
            return responseBody.contentLength()
        }

        override fun source(): BufferedSource {
            if (bufferedSource == null) {
                bufferedSource = source(responseBody.source()).buffer()
            }
            return bufferedSource!!
        }

        private fun source(source: Source): Source {
            return object : ForwardingSource(source) {
                var totalBytesRead = 0L

                @Throws(IOException::class)
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                    progressListener.onUpdate(url, totalBytesRead, responseBody.contentLength(), bytesRead == -1L)
                    return bytesRead
                }
            }
        }
    }

    fun interface ProgressListener {
        fun onUpdate(url: HttpUrl, bytesRead: Long, contentLength: Long, done: Boolean)

        companion object {
            val NOOP = ProgressListener { _, _, _, _ -> }
        }
    }
}