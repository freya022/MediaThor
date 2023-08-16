package io.github.freya022.mediathor.dl.utils

import io.github.freya022.mediathor.dl.Data
import io.github.freya022.mediathor.utils.CryptoUtils
import mu.two.KotlinLogging
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

private val logger = KotlinLogging.logger { }

class CachedHttpClient(private val cacheFolder: Path, val client: OkHttpClient = OkHttpClient()) {
    suspend fun requestCached(url: String, folder: String, name: String = url): CachedBody {
        return client.newCall(Request(url.toHttpUrl())).executeCached(folder, name)
    }

    suspend fun requestCached(request: Request, folder: String, name: String = request.url.toString()): CachedBody {
        return client.newCall(request).executeCached(folder, name)
    }

    private suspend fun Call.executeCached(folder: String, name: String): CachedBody {
        val nameHash = CryptoUtils.hash(name)
        val folderPath = cacheFolder.resolve(folder).createDirectories()
        val filePath = folderPath.resolve(nameHash)
        return when {
            filePath.notExists() -> {
                logger.debug { "Downloading @ ${request().url}" }
                await()
                    .also {
                        if (it.code == 403) {
                            throw HttpForbiddenException()
                        } else if (!it.isSuccessful) {
                            throw IOException("HTTP request ${it.request.url} returned ${it.code}")
                        }
                    }
                    .body
                    .bytes()
                    .also { filePath.writeBytes(it) }
                    .let { CachedBody(filePath, lazy { it.toResponseBody() }) }
            }
            else -> filePath.let { CachedBody(it, lazy { it.readBytes().toResponseBody() }) }
        }
    }

    companion object {
        val sharedClient = CachedHttpClient(Data.cacheFolder)
    }
}

fun OkHttpClient.toCachedHttpClient(cacheFolder: Path) = CachedHttpClient(cacheFolder, this)