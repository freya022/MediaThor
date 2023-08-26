package io.github.freya022.mediathor.record.obs

import io.github.freya022.mediathor.record.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

object ObsWSMain {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val obs = OBS("127.0.0.1", 4455, Config.config.obsPassword)

        println("stats = ${obs.getStats()}")

        obs.replayBuffer.start()

        delay(5.seconds)

        obs.replayBuffer.stop()

        println("stats = ${obs.getStats()}")

        obs.close()

        println("obs closed")
    }
}