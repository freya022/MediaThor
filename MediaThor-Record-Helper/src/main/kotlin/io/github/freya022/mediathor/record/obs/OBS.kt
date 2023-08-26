package io.github.freya022.mediathor.record.obs

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.github.freya022.mediathor.record.obs.data.OpCode
import io.github.freya022.mediathor.record.obs.data.connect.Hello
import io.github.freya022.mediathor.record.obs.data.connect.IdentifyData
import io.github.freya022.mediathor.record.obs.data.events.Event
import io.github.freya022.mediathor.record.obs.data.events.ReplayBufferStateChangedEvent
import io.github.freya022.mediathor.record.obs.data.receiveGateway
import io.github.freya022.mediathor.record.obs.data.requests.*
import io.github.freya022.mediathor.utils.getDefaultScope
import io.github.freya022.mediathor.utils.newExecutor
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.gson.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.trySendBlocking
import mu.two.KotlinLogging
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val logger = KotlinLogging.logger { }

class OBS(private val host: String, private val port: Int, private val password: String, private val gson: Gson = Gson()) {
    private class RequestContinuation(
        val typeToken: TypeToken<OpCode<RequestResponse<*>>>,
        val continuation: CancellableContinuation<RequestResponseData>
    )

    private val client = HttpClient {
        install(WebSockets) {
            contentConverter = GsonWebsocketContentConverter()
        }
    }

    private val readDispatcher = newExecutor(1, daemon = false) { name = "OBS WS read thread" }.asCoroutineDispatcher()
    private val readScope = getDefaultScope(readDispatcher)
    private lateinit var readJob: Job

    private val writeDispatcher = newExecutor(1, daemon = false) { name = "OBS WS write thread" }.asCoroutineDispatcher()
    private val writeScope = getDefaultScope(writeDispatcher)

    private val opCodeChannel = Channel<OpCode<*>>()

    private val requestContinuations: MutableMap<String, RequestContinuation> = hashMapOf()

    private val listeners: MutableList<OBSListener> = CopyOnWriteArrayList()

    init {
        runBlocking(Dispatchers.IO) {
            start()
        }
    }

    val replayBuffer = ReplayBuffer(this)

    fun addListener(listener: OBSListener) {
        listeners += listener
    }

    private suspend fun start(): OBS = suspendCoroutine { continuation ->
        readScope.launch {
            runCatching {
                client.webSocket(host = host, port = port, request = {
                    header("Sec-WebSocket-Protocol", "obswebsocket.json")
                }) {
                    try {
                        val hello = receiveGateway<Hello>()

                        val authentication = createAuthenticationString(hello.authentication, password)
                        sendSerialized(OpCode(OpCode.IDENTIFY, IdentifyData(1, authentication)))

                        incoming.receive() // Don't care about Identified OpCode 2
                    } catch (e: ClosedReceiveChannelException) {
                        val closeReason = closeReason.await()
                        logger.error { "Channel was closed with: $closeReason" }
                        throw e
                    }

                    continuation.resume(this@OBS)

                    readJob = readScope.reader { runInputLoop() }
                    val writeJob = writeScope.writer { runOutputLoop() }

                    readJob.join()
                    writeJob.cancelAndJoin()
                    requestContinuations.values.forEach { it.continuation.cancel() }
                }
            }.onFailure(continuation::resumeWithException)

            writeDispatcher.close()
            readDispatcher.close()
            client.close()
        }
    }

    private fun createAuthenticationString(authentication: Hello.Authentication, password: String): String {
        val sha256 = MessageDigest.getInstance("SHA256")

        val pwdSalt = password + authentication.salt
        val b64Secret = sha256
            .digest(pwdSalt.encodeToByteArray())
            .encodeBase64()

        val b64SecretChallenge = b64Secret + authentication.challenge
        return sha256
            .digest(b64SecretChallenge.encodeToByteArray())
            .encodeBase64()
    }

    context(CoroutineScope)
    private suspend fun DefaultClientWebSocketSession.runOutputLoop() {
        while (true) {
            try {
                val opCode = opCodeChannel.receive()
                sendSerialized(opCode)
            } catch (e: ClosedReceiveChannelException) {
                val closeReason = closeReason.await()
                logger.error(e) { "Channel was closed with: $closeReason" }
                break
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.catching(e)
            }
        }
    }

    context(CoroutineScope)
    private suspend fun DefaultClientWebSocketSession.runInputLoop() {
        while (true) {
            try {
                val text = (incoming.receive() as Frame.Text).readText()
                val obj = gson.fromJson(text, JsonObject::class.java)
                val op = obj["op"].asInt
                val d = obj["d"].asJsonObject
                if (op == OpCode.REQUEST_RESPONSE) {
                    val requestId: String = d["requestId"].asString
                    val continuation = requestContinuations.remove(requestId)
                    if (continuation == null) {
                        logger.warn { "Could not find RequestContinuation '$requestId'" }
                        continue
                    }

                    val (_, _, status, data) = gson.fromJson(text, continuation.typeToken).d
                    if (!status.result) {
                        val (_, code, comment) = status
                        continuation.continuation.resumeWithException(OBSException(code, comment))
                    }
                    continuation.continuation.resume(data ?: NullRequestResponse)
                } else if (op == OpCode.EVENT) {
                    val eventType = d["eventType"].asString
                    val event: Event? = when (eventType) {
                        "ReplayBufferStateChanged" -> gson.fromJson(d["eventData"], ReplayBufferStateChangedEvent::class.java)
                        else -> null
                    }
                    if (event == null) {
                        logger.debug { "Unknown event type: $eventType" }
                        continue
                    }
                    listeners.forEach { it.onEvent(event) }
                }
            } catch (e: ClosedReceiveChannelException) {
                val closeReason = closeReason.await()
                logger.error(e) { "Channel was closed with: $closeReason" }
                break
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.catching(e)
            }
        }
    }

    fun close() {
        if (::readJob.isInitialized) {
            readJob.cancel()
        }
    }

    suspend fun getStats(): GetStatsData = GetStats(this).await()

    @Suppress("UNCHECKED_CAST")
    suspend fun <R : RequestResponseData> await(
        request: Request<*>,
        typeToken: TypeToken<OpCode<RequestResponse<R>>>
    ): R = suspendCancellableCoroutine {
        it.invokeOnCancellation { requestContinuations.remove(request.requestId) }

        requestContinuations[request.requestId] = RequestContinuation(typeToken as TypeToken<OpCode<RequestResponse<*>>>, it)
        val exception = opCodeChannel.trySendBlocking(request.toOpCode()).exceptionOrNull()
        if (exception != null) {
            requestContinuations.remove(request.requestId)
            return@suspendCancellableCoroutine it.resumeWithException(exception)
        }
    } as R
}