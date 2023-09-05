package io.github.freya022.mediathor.interceptor

import mu.two.KotlinLogging
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.openqa.selenium.Platform
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.devtools.v116.network.Network
import org.openqa.selenium.edge.EdgeDriver
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.logging.Level
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter

private val logger = KotlinLogging.logger { }

class Interceptor {
    private val writer = outputPath.bufferedWriter(options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))

    init {
        val driver = when (Platform.getCurrent().family()) {
            Platform.WINDOWS -> EdgeDriver()
            else -> ChromeDriver()
        }
        driver.setLogLevel(Level.INFO)

        Runtime.getRuntime().addShutdownHook(Thread {
            writer.close()
            driver.quit()
        })

        val devTools = driver.devTools
        devTools.createSession()
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))

        devTools.addListener(Network.requestWillBeSent()) { futureRequest ->
            val url = futureRequest.request.url
            if (!url.startsWith("http")) {
                logger.trace { "Discarding $url as it is not an http url" }
                return@addListener
            }

            if (!url.toHttpUrl().pathSegments.last().endsWith(".m3u8")) {
                logger.trace { "Discarding $url as it is not a '*.m3u8' url" }
                return@addListener
            }

            logger.info { "Added $url" }
            writer.appendLine(url)
        }
    }

    companion object {
        val outputPath = Path("master_playlists.txt")
    }
}