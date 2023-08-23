package io.github.freya022.mediathor.record

import io.github.freya022.mediathor.record.memfs.WinFspMemFS
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.logger.slf4jLogger

object Main {
    val appModule = module {
        single(createdAtStart = true) {
            MemoryFileSystem('O')
        }
        singleOf(MemoryFileSystem::memFS)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val koin = startKoin {
            slf4jLogger()

            modules(appModule)
        }.koin

        val memFS: WinFspMemFS = koin.get()

//        thread {
//            Thread.sleep(1000)
//
//            Path("C:\\Users\\freya02\\Videos\\Replay 2023-08-11 18-22-53.mkv").copyTo(memFS.mountPointPath.resolve("first.mkv"))
//            Path("C:\\Users\\freya02\\Videos\\Replay 2023-08-11 18-23-04.mkv").copyTo(memFS.mountPointPath.resolve("second.mkv"))
//            Path("C:\\Users\\freya02\\Videos\\Replay 2023-08-12 16-49-27.mkv").copyTo(memFS.mountPointPath.resolve("unrelated.mkv"))
//        }
    }
}