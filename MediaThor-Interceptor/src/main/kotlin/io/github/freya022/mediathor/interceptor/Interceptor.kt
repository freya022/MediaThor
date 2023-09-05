package io.github.freya022.mediathor.interceptor

import mu.two.KotlinLogging
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.openqa.selenium.Platform
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.devtools.v116.network.Network
import org.openqa.selenium.devtools.v116.target.Target
import org.openqa.selenium.edge.EdgeDriver
import org.openqa.selenium.remote.DriverCommand
import org.openqa.selenium.remote.RemoteWebDriver
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.logging.Level
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.jvm.optionals.getOrNull

private val logger = KotlinLogging.logger { }

class Interceptor private constructor() {
    private val writer = outputPath.bufferedWriter(options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))

    private lateinit var continuation: Continuation<Unit>

    private val driver = when (Platform.getCurrent().family()) {
        Platform.WINDOWS -> EdgeDriver()
        else -> ChromeDriver()
    }

    private val hook = Thread {
        driver.maybeGetDevTools().getOrNull()?.clearListeners()
        driver.quit()
        close()
    }

    init {
        driver.setLogLevel(Level.INFO)
        Runtime.getRuntime().addShutdownHook(hook)

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

        devTools.addListener(Target.detachedFromTarget()) {
            logger.info { "Exiting" }
            Runtime.getRuntime().removeShutdownHook(hook)
            continuation.resume(Unit)
        }
    }

    private suspend fun awaitTermination(): Unit = suspendCoroutine {
        continuation = it
    }

    // This method is only used when the browser is already closed.
    private fun close() {
        writer.close()
    }

    companion object {
        val outputPath = Path("master_playlists.txt")

        suspend fun intercept() {
            val interceptor = Interceptor()
            interceptor.awaitTermination()
            // If the browser is closed, the driver is still running
            // Using the QUIT command makes it exit
            val executeMethod = RemoteWebDriver::class.java.getDeclaredMethod("execute", String::class.java)
            executeMethod.isAccessible = true
            executeMethod.invoke(interceptor.driver, DriverCommand.QUIT)
            interceptor.close()
        }
    }
}