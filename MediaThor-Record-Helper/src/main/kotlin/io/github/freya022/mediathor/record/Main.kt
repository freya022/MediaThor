package io.github.freya022.mediathor.record

import com.github.jnrwinfspteam.jnrwinfsp.service.ServiceRunner
import io.github.freya022.mediathor.record.memfs.FSMain
import io.github.freya022.mediathor.record.memfs.WinFspMemFS
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.copyTo

object Main {
    private const val GB = 1024 * 1024 * 1024

    @JvmStatic
    fun main(args: Array<String>) {
        val memFS = WinFspMemFS("OBS Volatile Staging", 1 * GB, 4L * GB)

        RecordWatcher(memFS)

        thread {
            Thread.sleep(1000)

            Path("C:\\Users\\freya02\\Videos\\Replay 2023-08-11 18-22-53.mkv").copyTo(memFS.mountPointPath.resolve("first.mkv"))
            Path("C:\\Users\\freya02\\Videos\\Replay 2023-08-11 18-23-04.mkv").copyTo(memFS.mountPointPath.resolve("second.mkv"))
            Path("C:\\Users\\freya02\\Videos\\Replay 2023-08-12 16-49-27.mkv").copyTo(memFS.mountPointPath.resolve("unrelated.mkv"))
        }

        ServiceRunner.mountLocalDriveAsService("OBS Volatile FS", memFS, FSMain.root)
    }
}