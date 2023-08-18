package io.github.freya022.mediathor.record

import com.github.jnrwinfspteam.jnrwinfsp.service.ServiceRunner
import io.github.freya022.mediathor.record.memfs.FSMain
import io.github.freya022.mediathor.record.memfs.WinFspMemFS

object Main {
    private const val GB = 1024 * 1024 * 1024

    @JvmStatic
    fun main(args: Array<String>) {
        val memFS = WinFspMemFS("OBS Volatile Staging", 1 * GB, 4L * GB)

        RecordWatcher(memFS)

        ServiceRunner.mountLocalDriveAsService("OBS Volatile FS", memFS, FSMain.root)
    }
}