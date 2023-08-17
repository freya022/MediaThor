package io.github.freya022.mediathor.record.memfs

import mu.two.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom
import kotlin.io.path.copyTo
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.math.min
import kotlin.random.asKotlinRandom
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object FSTestMain {
    private val logger = KotlinLogging.logger { }

    @JvmStatic
    fun main(args: Array<String>) {
        val root = FSMain.root

        testReadsWrites(root)

        Files.walk(root)
            .filter { it != root }
            .sorted(Comparator.reverseOrder())
            .forEach { it.deleteExisting() }
    }

    private fun testReadsWrites(root: Path) {
        val file = root.resolve("file.bin")
        val sibling = file.resolveSibling("file (copy).bin")

        val originalBytes = ThreadLocalRandom.current().asKotlinRandom().nextBytes(1024 * 1024 * 100)
//        val originalBytes = "abcdefghijklmnopqrs ".repeat(10).encodeToByteArray()

        testBufferedWrite(originalBytes)

        testWrite(file, originalBytes)
        file.deleteExisting()
        testWrite(file, originalBytes)

        testCopy(file, sibling)

        val (fileBytes, fileReadDuration) = measureTimedValue { file.readBytes() }
        val (siblingBytes, siblingReadDuration) = measureTimedValue { sibling.readBytes() }

        logger.warn("Read file took ${fileReadDuration.toString(DurationUnit.MILLISECONDS, 3)}")
        logger.warn("Read sibling took ${siblingReadDuration.toString(DurationUnit.MILLISECONDS, 3)}")

        logger.info("fileBytes.contentEquals(originalBytes) = ${fileBytes.contentEquals(originalBytes)}")
        logger.info("fileBytes.contentEquals(siblingBytes) = ${fileBytes.contentEquals(siblingBytes)}")
    }

    private fun testBufferedWrite(originalBytes: ByteArray) {
        val testStream = ByteArrayOutputStream()
        testStream.use { writeBytes(originalBytes, it) }
        logger.info("Custom buffer works: ${testStream.toByteArray().contentEquals(originalBytes)}")
    }

    private fun testCopy(file: Path, sibling: Path) {
        logger.info("Copying file")
        val copyTime = measureTime { file.copyTo(sibling) }
        logger.warn("Copy took ${copyTime.toString(DurationUnit.MILLISECONDS, 3)}")
    }

    private fun testWrite(file: Path, originalBytes: ByteArray) {
        logger.info("Writing file")
        val writeTime = measureTime {
            file.outputStream().use { writeBytes(originalBytes, it) }
        }
        logger.warn("Write took ${writeTime.toString(DurationUnit.MILLISECONDS, 3)}")
    }

    private fun writeBytes(originalBytes: ByteArray, it: OutputStream) {
        var offset = 0
        while (offset != originalBytes.size) {
            val toWrite = min(8192, originalBytes.size - offset)
            it.write(originalBytes, offset, toWrite)
            offset += toWrite
        }
    }
}