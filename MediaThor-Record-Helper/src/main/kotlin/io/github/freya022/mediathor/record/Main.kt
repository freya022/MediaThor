package io.github.freya022.mediathor.record

import mu.two.KotlinLogging
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object Main {
    private val logger = KotlinLogging.logger { }

    @JvmStatic
    fun main(args: Array<String>) {
        val memFS = MemoryFS()
        val root = Path("Y:\\")

        thread {
            Thread.sleep(1000)

            val file = root.resolve("file.txt")
            val sibling = file.resolveSibling("file (copy).txt")

            val text = "abcdefghij".repeat(1024 * 1024 * 10)

            logger.info("Writing file")
            val writeTime = measureTime {
                file.writeText(text)
            }
            logger.info("Wrote file")
            logger.warn { "Write took ${writeTime.toString(DurationUnit.MILLISECONDS, 3)}" }

            logger.info("Copying file")
            val copyTime = measureTime {
                file.copyTo(sibling)
            }
            logger.info("Copied file")
            logger.warn { "Copy took ${copyTime.toString(DurationUnit.MILLISECONDS, 3)}" }

            val (fileText, fileReadDuration) = measureTimedValue { file.readBytes() }
            val (siblingText, siblingReadDuration) = measureTimedValue { sibling.readBytes() }

            logger.warn { "Read file took ${fileReadDuration.toString(DurationUnit.MILLISECONDS, 3)}" }
            logger.warn { "Read sibling took ${siblingReadDuration.toString(DurationUnit.MILLISECONDS, 3)}" }

            logger.info("file.readText() == text = ${fileText.decodeToString() == text}")
            logger.info("file.readText() == sibling.readText() = ${fileText.contentEquals(siblingText)}")

            exitProcess(0)
        }

        try {
            memFS.mount(root, true, false)
        } finally {
            memFS.umount()
        }
    }
}