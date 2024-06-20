package io.github.freya022.mediathor.interceptor

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

object InterceptorMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (Interceptor.outputPath.exists() && "-y" !in args)
            throw IOException("${Interceptor.outputPath.absolutePathString()} already exists")

        runBlocking {
            Interceptor.intercept()
        }
    }
}