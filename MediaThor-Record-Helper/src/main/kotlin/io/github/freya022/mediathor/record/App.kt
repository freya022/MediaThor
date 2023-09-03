package io.github.freya022.mediathor.record

import atlantafx.base.theme.CupertinoDark
import io.github.freya022.mediathor.record.memfs.WinFspMemFS
import io.github.freya022.mediathor.record.obs.OBS
import io.github.freya022.mediathor.record.ui.controller.RecordHelperController
import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.stopKoin
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

class App : Application(), KoinComponent {
    override fun start(primaryStage: Stage) {
        setUserAgentStylesheet(CupertinoDark().userAgentStylesheet)

        val root = RecordHelperController()
        primaryStage.scene = Scene(root)
        primaryStage.show()
    }

    override fun stop() {
        get<OBS>().close()
        mediaPlayers.forEach {
            it.controls().stop()
            it.release()
        }
        mediaPlayerFactory.release()
        get<WinFspMemFS>().unmountLocalDrive()
        stopKoin()
    }

    companion object {
        private val mediaPlayerFactory = MediaPlayerFactory()
        private val mediaPlayers: MutableList<MediaPlayer> = arrayListOf()

        fun newMediaPlayer(): EmbeddedMediaPlayer {
            val mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()
            mediaPlayers += mediaPlayer

            return mediaPlayer
        }

        fun destroyMediaPlayer(mediaPlayer: MediaPlayer) {
            mediaPlayers -= mediaPlayer
            mediaPlayer.controls().stop()
            mediaPlayer.release()
        }
    }
}