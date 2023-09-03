package io.github.freya022.mediathor.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.two.KLogger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull

fun ProcessBuilder.directory(path: Path): ProcessBuilder = directory(path.toFile())

context(CoroutineScope)
fun Process.redirectOutputs(outputStream: OutputStream, errorStream: OutputStream): Process = this.apply {
    launch { redirectStream(outputStream, this@redirectOutputs.inputStream) }
    launch { redirectStream(errorStream, this@redirectOutputs.errorStream) }
}

suspend fun Process.waitForSuspend(): Int = withContext(Dispatchers.IO) { waitFor() }

suspend fun Process.waitFor(
    logger: KLogger,
    outputStream: ByteArrayOutputStream,
    errorStream: ByteArrayOutputStream
): Process = withContext(Dispatchers.IO) {
    val exitCode = waitForSuspend()
    if (exitCode != 0) {
        val outputString = outputStream.toByteArray().decodeToString()
        when {
            outputString.isNotBlank() -> logger.warn("Output:\n$outputString")
            else -> logger.warn("No output")
        }

        val errorString = errorStream.toByteArray().decodeToString()
        when {
            errorString.isNotBlank() -> logger.error("Error output:\n$errorString")
            else -> logger.warn("No error output")
        }

        logger.error { "Process exited with code: $exitCode" }
    }

    this@waitFor
}

fun Process.throwOnExitCode() {
    val exitCode = exitValue()
    if (exitCode != 0) {
        val info = info()
        val commandLine = info.commandLine().getOrNull() ?: "<no command line available>"
        throw IOException("Process exited with code $exitCode: $commandLine")
    }
}

suspend fun redirectStream(outputStream: OutputStream, processStream: InputStream) = withContext(Dispatchers.IO) {
    outputStream.bufferedWriter().use { writer ->
        processStream.bufferedReader().use { reader ->
            var readLine: String?
            while (reader.readLine().also { readLine = it } != null) {
                writer.append(readLine + System.lineSeparator())
            }
        }
    }
}