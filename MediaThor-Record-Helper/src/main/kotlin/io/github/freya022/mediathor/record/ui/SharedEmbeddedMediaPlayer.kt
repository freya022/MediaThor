package io.github.freya022.mediathor.record.ui

import io.github.freya022.mediathor.record.App
import io.github.freya022.mediathor.utils.withVlcContext
import javafx.scene.image.ImageView
import mu.two.KotlinLogging
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.media.callback.seekable.RandomAccessFileMedia
import uk.co.caprica.vlcj.player.base.MediaPlayerEventListener
import java.nio.file.Path
import java.util.*

private val logger = KotlinLogging.logger { }

class SharedEmbeddedMediaPlayer {
    private class State(val surface: ImageViewVideoSurface, val media: RandomAccessFileMedia, var time: Long) {
        lateinit var listener: MediaPlayerEventListener
    }

    private val initPlayer = App.newMediaPlayer()
    private val embeddedMediaPlayer = App.newMediaPlayer()

    private val stateMap: MutableMap<ImageView, State> = IdentityHashMap()
    private lateinit var currentState: State

    suspend fun initialFrame(path: Path, view: ImageView): Unit = withVlcContext {
        val media = RandomAccessFileMedia(path.toFile(), 1024 * 1024)
        val surface = ImageViewVideoSurface(view)
        initPlayer.videoSurface().set(surface)
        initPlayer.media().startPaused(media)

        stateMap[view] = State(ImageViewVideoSurface(view), media, 0)
    }

    suspend fun play(path: Path, view: ImageView, listener: MediaPlayerEventListener, pause: Boolean = false): Unit = withVlcContext {
        val state = stateMap[view]
            ?: return@withVlcContext logger.error("$view was not initialized for $this")
        if (!::currentState.isInitialized || state !== currentState) {
            // Unregister old listeners and save playback time
            if (::currentState.isInitialized) {
                currentState.time = embeddedMediaPlayer.status().time()
                embeddedMediaPlayer.events().removeMediaPlayerEventListener(currentState.listener)
            }

            // Replace with current state
            state.listener = listener
            currentState = state

            // Reuse player with the new video player target
            embeddedMediaPlayer.videoSurface().set(currentState.surface)
            embeddedMediaPlayer.events().addMediaPlayerEventListener(listener)

            // Start playing
            embeddedMediaPlayer.media().start(state.media, "start-time=${currentState.time / 1000f}")
        } else {
            embeddedMediaPlayer.controls().setPause(pause)
        }
    }

    suspend fun setPosition(position: Float): Unit = withVlcContext {
        embeddedMediaPlayer.controls().setPosition(position)
    }

    fun stop() {
        // This also causes the handle to be released,
        // but only if the source is a callback media
        embeddedMediaPlayer.controls().stop()
    }
}