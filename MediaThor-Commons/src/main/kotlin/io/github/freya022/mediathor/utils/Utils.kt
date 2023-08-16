package io.github.freya022.mediathor.utils

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

fun <K, V : Comparable<V>> Map<K, V>.sortByValueTo(map: MutableMap<K, V>) = toList().sortedBy { it.second }.toMap(map)
fun <K, V : Comparable<V>> Map<K, V>.sortByValue() = sortByValueTo(linkedMapOf())

fun <K, V : Comparable<V>> Map<K, V>.sortDescendingByValueTo(map: MutableMap<K, V>) = toList().sortedByDescending { it.second }.toMap(map)
fun <K, V : Comparable<V>> Map<K, V>.sortDescendingByValue() = sortDescendingByValueTo(linkedMapOf())

fun countingThreadFactory(block: Thread.(threadNumber: Int) -> Unit) = object : ThreadFactory {
    private var threadNumber = 0

    override fun newThread(r: Runnable): Thread {
        val thread = Thread(r)
        block(thread, threadNumber++)
        return thread
    }
}

fun newExecutor(corePoolSize: Int, block: Thread.(threadNumber: Int) -> Unit) =
    Executors.newScheduledThreadPool(corePoolSize, countingThreadFactory(block))
