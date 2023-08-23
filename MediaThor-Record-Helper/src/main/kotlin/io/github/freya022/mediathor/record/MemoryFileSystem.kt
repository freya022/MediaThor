package io.github.freya022.mediathor.record

import com.github.jnrwinfspteam.jnrwinfsp.api.MountException
import com.github.jnrwinfspteam.jnrwinfsp.api.MountOptions
import com.github.jnrwinfspteam.jnrwinfsp.api.Mountable
import com.github.jnrwinfspteam.jnrwinfsp.service.OnStart
import com.github.jnrwinfspteam.jnrwinfsp.service.OnStop
import com.github.jnrwinfspteam.jnrwinfsp.service.ServiceRunner
import io.github.freya022.mediathor.record.memfs.WinFspMemFS
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.Path

private const val GB = 1024 * 1024 * 1024

class MemoryFileSystem(root: Char) {
    val memFS = WinFspMemFS("OBS Volatile Staging", 1 * GB, 4L * GB)

    init {
        runBlocking {
            mountLocalDriveAsService("OBS Volatile FS", memFS, Path(root.toString()))
        }
    }
}

suspend fun mountLocalDriveAsService(
    serviceName: String,
    mountable: Mountable,
    mountPoint: Path,
    options: MountOptions = MountOptions()
) = suspendCoroutine { continuation ->
    val onStart = OnStart { _, _ ->
        try {
            mountable.mountLocalDrive(mountPoint, options)
            continuation.resume(Unit)
            0
        } catch (e: MountException) {
            continuation.resumeWithException(e)
            e.ntStatus
        }
    }
    val onStop = OnStop { _ ->
        mountable.unmountLocalDrive()
        0
    }

    thread(name = "'$serviceName' service thread") {
        // Blocking
        ServiceRunner.runAsService(serviceName, onStart, onStop)
    }
}