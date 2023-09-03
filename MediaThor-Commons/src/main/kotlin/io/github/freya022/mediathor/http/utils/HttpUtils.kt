package io.github.freya022.mediathor.http.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import mu.two.KotlinLogging
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.ExecutorService
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun ExecutorService.toDispatcher() = Dispatcher(this)

fun Request.Builder.url(url: String, block: HttpUrl.Builder.() -> Unit) {
    url(url.toHttpUrl().newBuilder().apply(block).build())
}

fun request(block: Request.Builder.() -> Unit): Request = Request.Builder().apply(block).build()

fun Request.newCall(client: OkHttpClient) = client.newCall(this)

inline fun runCatchingUntil(tries: Int, errorSupplier: () -> String, block: () -> Unit): Nothing {
    repeat(tries) {
        try {
            block()
        } catch (e: Exception) {
            // native-image has issues with multiple catch blocks
            if (e is HttpForbiddenException || e is CancellationException) {
                throw e
            }

            KotlinLogging.logger { }.error(errorSupplier(), e)
        }
    }

    throw RuntimeException(errorSupplier())
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}

class HttpForbiddenException : RuntimeException()