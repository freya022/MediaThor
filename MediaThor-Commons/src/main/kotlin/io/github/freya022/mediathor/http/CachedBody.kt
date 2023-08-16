package io.github.freya022.mediathor.http

import okhttp3.ResponseBody
import java.nio.file.Path

class CachedBody(val path: Path, lazyBody: Lazy<ResponseBody>) {
    val body: ResponseBody by lazyBody

    fun string() = body.string()
    fun byteStream() = body.byteStream()
    fun bytes() = body.bytes()
}