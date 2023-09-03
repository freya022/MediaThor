package io.github.freya022.mediathor.dl

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import mu.two.KotlinLogging
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

object DownloaderTestServer {
    private val logger = KotlinLogging.logger { }

    private const val KB = 1024
    private const val MB = KB * KB
    private const val BUFFER_SIZE = 32 * KB
    private const val SLEEP_DURATION: Long = (1000 * BUFFER_SIZE / (5 * MB)).toLong()

    @JvmStatic
    fun main(args: Array<String>) {
        val segmentsDirectory = Path(args[0])
        if (segmentsDirectory.notExists()) {
            throw IOException("Segments directory ${segmentsDirectory.absolutePathString()} does not exist")
        }

        val parent = SupervisorJob()
        val handler = CoroutineExceptionHandler { _, throwable ->
            logger.error("Uncaught exception from coroutine", throwable)
            if (throwable is Error) {
                parent.cancel()
                throw throwable
            }
        }
        val scope = CoroutineScope(Dispatchers.IO + parent + handler)

        val server = HttpServer.create(InetSocketAddress(25567), 0)
        println("http://localhost:${server.address.port}/master.m3u8")

        server.createContext("/") { exchange: HttpExchange ->
            scope.launch {
                exchange.use {
                    if (!exchange.remoteAddress.address.isLoopbackAddress) {
                        logger.warn("A non-loopback address tried to connect: {}", exchange.remoteAddress)
                        return@launch
                    }

                    val path = exchange.requestURI.path
                    val file = segmentsDirectory.resolve(path.substring(1))
                    if (!file.startsWith(segmentsDirectory)) {
                        logger.warn(
                            "Tried to access a file outside of the target directory: '{}', from {}",
                            file,
                            exchange.remoteAddress
                        )
                        return@launch
                    }

                    if (exchange.requestMethod == "HEAD") {
                        exchange.responseHeaders["Content-Length"] = file.fileSize().toString()
                        exchange.sendResponseHeaders(200, -1)
                        return@launch
                    }

                    exchange.responseBody.buffered().use { output ->
                        if (file.exists()) {
                            val attributes = file.readAttributes<BasicFileAttributes>()

                            if (attributes.isRegularFile) {
                                exchange.sendResponseHeaders(200, attributes.size())
                                file.inputStream().buffered().use { input ->
                                    var transferred: Long = 0
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var read: Int
                                    while (input.read(buffer, 0, BUFFER_SIZE).also { read = it } >= 0) {
                                        //Simulate a connection issue
                                        if (Math.random() > 0.999) {
                                            return@launch
                                        }
                                        output.write(buffer, 0, read)
                                        transferred += read.toLong()
                                        delay(SLEEP_DURATION)
                                    }
                                }

                                return@launch
                            }
                        }

                        exchange.sendResponseHeaders(404, "404 (Not Found)".length.toLong())
                        output.write("404 (Not Found)".toByteArray())
                    }
                }
            }
        }

        server.start()
    }
}