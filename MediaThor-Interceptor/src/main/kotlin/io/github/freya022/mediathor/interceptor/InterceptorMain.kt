package io.github.freya022.mediathor.interceptor

import mu.two.KotlinLogging
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.openqa.selenium.Platform
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.devtools.HasDevTools
import org.openqa.selenium.devtools.v116.network.Network
import org.openqa.selenium.edge.EdgeDriver
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.IOException
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.exists

private val logger = KotlinLogging.logger { }

object InterceptorMain {
    private val outputPath = Path("master_playlists.txt")

    init {
        if (outputPath.exists())
            throw IOException("${outputPath.absolutePathString()} already exists")
    }

    private val writer = outputPath.bufferedWriter()

    @JvmStatic
    fun main(args: Array<String>) {
        val driver = when (Platform.getCurrent().family()) {
            Platform.WINDOWS -> EdgeDriver()
            else -> ChromeDriver()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            writer.close()
            driver.quit()
        })

        run(driver)
    }

    private fun <T> run(driver: T) where T : HasDevTools, T : RemoteWebDriver {
        val devTools = driver.devTools
        devTools.createSession()
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))

        devTools.addListener(Network.requestWillBeSent()) { futureRequest ->
            val url = futureRequest.request.url
            if (!url.toHttpUrl().pathSegments.last().endsWith(".m3u8")) {
                logger.trace { "Discarding $url as it is not a '*.m3u8' url" }
                return@addListener
            }

            logger.info { "Added $url" }
            writer.appendLine(url)
        }
    }
}