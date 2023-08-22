package io.github.freya022.mediathor.record.obs

import io.github.freya022.mediathor.record.Config
import io.github.freya022.mediathor.record.obs.data.connect.Hello
import io.github.freya022.mediathor.record.obs.data.connect.identify
import io.github.freya022.mediathor.record.obs.data.receiveGateway
import io.github.freya022.mediathor.record.obs.data.requests.GetStatsData
import io.github.freya022.mediathor.record.obs.data.requests.getStats
import io.github.freya022.mediathor.record.obs.data.requests.receiveRequestResponse
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.gson.*
import io.ktor.util.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

object ObsWSMain {
    private val client = HttpClient {
        install(WebSockets) {
            contentConverter = GsonWebsocketContentConverter()
        }
    }

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        client.webSocket(host = "127.0.0.1", port = 4455, request = {
            header("Sec-WebSocket-Protocol", "obswebsocket.json")
        }) {
            try {
                val hello = receiveGateway<Hello>()

                sendSerialized(identify(createAuthenticationString(hello.authentication, Config.config.obsPassword)))

                incoming.receive() // Don't care about Identified OpCode 2

                // Can now send requests
                sendSerialized(getStats())
                println(receiveRequestResponse<GetStatsData>())
            } catch (e: ClosedReceiveChannelException) {
                println(closeReason.await())
            }
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
}