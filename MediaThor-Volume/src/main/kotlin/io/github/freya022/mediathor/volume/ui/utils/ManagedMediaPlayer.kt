package io.github.freya022.mediathor.volume.ui.utils

import io.github.freya022.mediathor.ui.utils.launchMainContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.two.KotlinLogging
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.MediaPlayerEventListener
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

private val logger = KotlinLogging.logger { }

class ManagedMediaPlayer(volume: Int) {
    private val mutex = Mutex()
    private val mediaPlayerFactory: MediaPlayerFactory = MediaPlayerFactory()
    private val mediaPlayer: EmbeddedMediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()

    private var mediaPlayerEventListener: MediaPlayerEventListener? = null
    private var currentMedia: String? = null

    var volume: Int = volume
        set(value) = runBlocking {
            mutex.withLock {
                field = value
                mediaPlayer.audio().setVolume(value)
            }
        }

    val isPlaying: Boolean get() = mediaPlayer.status().isPlaying

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                dispose()
            }
        })
    }

    fun isCurrentPlayer(mediaPlayerEventListener: MediaPlayerEventListener): Boolean =
        this.mediaPlayerEventListener === mediaPlayerEventListener

    suspend fun play(url: String, startMs: Long, mediaPlayerEventListener: MediaPlayerEventListener): Unit = mutex.withLock {
        // When switching, let the current player know it has stopped before switching event listener
        if (currentMedia != url) {
            if (mediaPlayer.status().isPlaying) {
                // The pausing is done asynchronously,
                // so we have to wait to receive the event before continuing
                // Otherwise the AnimationTimer would not get stopped for the previous media source,
                // as it did not get enough time to receive the pause event
                mediaPlayer.awaitMediaPlayerEvent(MediaPlayerEventListener::paused) {
                    mediaPlayer.controls().pause()
                }
            }

            this.mediaPlayerEventListener?.let { mediaPlayer.events().removeMediaPlayerEventListener(it) }
            this.mediaPlayerEventListener = mediaPlayerEventListener
            mediaPlayer.events().addMediaPlayerEventListener(mediaPlayerEventListener)
        }

        // If the source isn't the same, then prepare to play
        if (currentMedia != url) {
            mediaPlayer.media().prepare(url)
            currentMedia = url
        }
        mediaPlayer.controls().play()

        // Cannot use seek function as we just changed the media player
        // Using the seek function will cause the player to seek at the wrong place
        // while still displaying the correct time
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                // Do not call VLC functions in event handler => deadlock
                launchMainContext {
                    seek(startMs)
                }
                mediaPlayer.events().removeMediaPlayerEventListener(this)
            }
        })
    }

    suspend fun pause(): Unit = mutex.withLock {
        mediaPlayer.controls().pause()
    }

    suspend fun stop(): Unit = mutex.withLock {
        mediaPlayer.controls().stop()
    }

    suspend fun invalidate(): Unit = mutex.withLock {
        mediaPlayer.controls().stop()
        currentMedia = null
    }

    suspend fun dispose(): Unit = mutex.withLock {
        mediaPlayer.release()
        mediaPlayerFactory.release()
    }

    suspend fun seek(timeMs: Long): Unit = mutex.withLock {
        mediaPlayer.controls().setTime(timeMs)
    }
}