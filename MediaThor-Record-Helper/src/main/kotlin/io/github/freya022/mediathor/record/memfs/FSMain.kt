package io.github.freya022.mediathor.record.memfs

import com.github.jnrwinfspteam.jnrwinfsp.api.MountOptions
import com.github.jnrwinfspteam.jnrwinfsp.service.ServiceRunner
import kotlin.io.path.Path

object FSMain {
    private const val KB = 1024
    private const val MB = KB * KB
    private const val GB = KB * KB * KB

    val root = Path("O:")

    @JvmStatic
    fun main(args: Array<String>) {
        val memFS = WinFspMemFS("OBS Volatile Staging", 1 * GB, 4L * GB)

        ServiceRunner.mountLocalDriveAsService(
            "OBS Volatile FS",
            memFS,
            root,
            MountOptions()
//                .setDebug(false)
//                .setCase(MountOptions.CaseOption.CASE_SENSITIVE)
//                .setSectorSize(512)
//                .setSectorsPerAllocationUnit(1)
//                .setForceBuiltinAdminOwnerAndGroup(true)
        )
    }
}