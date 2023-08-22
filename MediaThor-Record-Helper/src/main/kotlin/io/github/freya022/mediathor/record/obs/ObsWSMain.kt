package io.github.freya022.mediathor.record.obs

import io.github.freya022.mediathor.record.Config
import kotlinx.coroutines.runBlocking

object ObsWSMain {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val obs = OBS("127.0.0.1", 4455, Config.config.obsPassword).start()

        val stats = obs.getStats()
        println("stats = $stats")

        obs.close()

        println("obs closed")
    }
}