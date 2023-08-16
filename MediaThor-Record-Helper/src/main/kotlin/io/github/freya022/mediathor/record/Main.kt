package io.github.freya022.mediathor.record

import com.github.jnrwinfspteam.jnrwinfsp.api.MountOptions
import com.github.jnrwinfspteam.jnrwinfsp.service.ServiceRunner
import mu.two.KotlinLogging
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.writeText
import kotlin.time.DurationUnit
import kotlin.time.measureTime

object Main {
    private val logger = KotlinLogging.logger { }

    @JvmStatic
    fun main(args: Array<String>) {
        val memFS = WinFspMemFS(false)

        thread {
            Thread.sleep(1000)

            val root = Path(memFS.mountPoint)
            val file = root.resolve("file.txt")

            logger.info("Writing file")
            val writeTime = measureTime {
                file.writeText("a".repeat(1024 * 1024 * 20))
            }
            logger.info("Wrote file")
            logger.warn { "Write took ${writeTime.toString(DurationUnit.MILLISECONDS, 3)}" }

            logger.info("Copying file")
            val copyTime = measureTime {
                file.copyTo(file.resolveSibling("file (copy).txt"))
            }
            logger.info("Copied file")
            logger.warn { "Copy took ${copyTime.toString(DurationUnit.MILLISECONDS, 3)}" }

        }

        ServiceRunner.mountLocalDriveAsService(
            "WinFpsMemFS",
            memFS,
            null,
            MountOptions()
//                .setDebug(false)
//                .setCase(MountOptions.CaseOption.CASE_SENSITIVE)
//                .setSectorSize(512)
//                .setSectorsPerAllocationUnit(1)
//                .setForceBuiltinAdminOwnerAndGroup(true)
        )
    }
}