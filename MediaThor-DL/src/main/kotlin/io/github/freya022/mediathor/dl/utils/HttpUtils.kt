package io.github.freya022.mediathor.dl.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import mu.two.KotlinLogging
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun Request.Builder.url(url: String, block: HttpUrl.Builder.() -> Unit) {
    url(url.toHttpUrl().newBuilder().apply(block).build())
}

fun request(block: Request.Builder.() -> Unit): Request = Request.Builder().apply(block).build()

fun Request.newCall(client: OkHttpClient) = client.newCall(this)

inline fun runCatchingUntil(tries: Int, errorSupplier: () -> String, block: () -> Unit): Nothing {
    repeat(tries) {
        try {
            block()
        } catch (e: HttpForbiddenException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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