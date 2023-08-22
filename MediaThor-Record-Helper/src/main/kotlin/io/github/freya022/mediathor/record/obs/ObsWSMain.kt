package io.github.freya022.mediathor.record.obs

import io.github.freya022.mediathor.record.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

object ObsWSMain {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val obs = OBS("127.0.0.1", 4455, Config.config.obsPassword).start()

        println("stats = ${obs.getStats()}")

        obs.startReplayBuffer()

        delay(5.seconds)

        obs.stopReplayBuffer()

        println("stats = ${obs.getStats()}")

        obs.close()

        println("obs closed")
    }
}