package io.github.freya022.mediathor.utils

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import mu.two.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger { }

fun <K, V : Comparable<V>> Map<K, V>.sortByValueTo(map: MutableMap<K, V>) = toList().sortedBy { it.second }.toMap(map)
fun <K, V : Comparable<V>> Map<K, V>.sortByValue() = sortByValueTo(linkedMapOf())

fun <K, V : Comparable<V>> Map<K, V>.sortDescendingByValueTo(map: MutableMap<K, V>) = toList().sortedByDescending { it.second }.toMap(map)
fun <K, V : Comparable<V>> Map<K, V>.sortDescendingByValue() = sortDescendingByValueTo(linkedMapOf())

fun getDefaultScope(
    context: CoroutineContext,
    job: Job? = null,
    errorHandler: CoroutineExceptionHandler? = null
): CoroutineScope {
    val parent = job ?: SupervisorJob()
    val handler = errorHandler ?: CoroutineExceptionHandler { _, throwable ->
        logger.error("Uncaught exception from coroutine", throwable)
        if (throwable is Error) {
            parent.cancel()
            throw throwable
        }
    }
    return CoroutineScope(context + parent + handler)
}

fun countingThreadFactory(block: Thread.(threadNumber: Int) -> Unit) = object : ThreadFactory {
    private var threadNumber = 0

    override fun newThread(r: Runnable): Thread {
        val thread = Thread(r)
        block(thread, threadNumber++)
        return thread
    }
}

fun newExecutor(corePoolSize: Int, block: Thread.(threadNumber: Int) -> Unit): ScheduledExecutorService =
    Executors.newScheduledThreadPool(corePoolSize, countingThreadFactory(block))
