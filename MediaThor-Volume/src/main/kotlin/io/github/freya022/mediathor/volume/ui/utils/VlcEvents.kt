package io.github.freya022.mediathor.volume.ui.utils

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import mu.two.KotlinLogging
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.MediaPlayerEventListener
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.resume
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

private val logger = KotlinLogging.logger { }

class MediaPlayerEventListenerProxy(
    private val mediaPlayer: MediaPlayer,
    private val func: KFunction<Unit>,
    private val continuation: CancellableContinuation<Unit>
) : InvocationHandler {
    private val adapter = object : MediaPlayerEventAdapter() {}

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return when {
            func.javaMethod == method -> onEvent(proxy)
            method == Object::equals.javaMethod -> proxy === args!![0]
            method == Object::hashCode.javaMethod -> adapter.hashCode()
            args == null -> method.invoke(adapter)
            else -> method.invoke(adapter, *args)
        }
    }

    private fun onEvent(proxy: Any) {
        mediaPlayer.events().removeMediaPlayerEventListener(proxy as MediaPlayerEventListener)
        continuation.resume(Unit)
    }
}

suspend fun MediaPlayer.awaitMediaPlayerEvent(func: KFunction<Unit>, eventEmitter: () -> Unit): Unit = suspendCancellableCoroutine {
    val listener = Proxy.newProxyInstance(
        this.javaClass.classLoader,
        arrayOf(MediaPlayerEventListener::class.java),
        MediaPlayerEventListenerProxy(this, func, it)
    ) as MediaPlayerEventListener

    events().addMediaPlayerEventListener(listener)
    it.invokeOnCancellation { events().removeMediaPlayerEventListener(listener) }

    eventEmitter()
}