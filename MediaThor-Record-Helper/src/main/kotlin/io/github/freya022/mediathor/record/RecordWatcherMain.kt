package io.github.freya022.mediathor.record

import io.github.freya022.mediathor.record.obs.OBS
import io.github.freya022.mediathor.record.watcher.RecordWatcher
import io.github.freya022.mediathor.record.watcher.RecordWatcherImpl
import javafx.application.Application
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.logger.slf4jLogger

object RecordWatcherMain {
    val appModule = module {
        single(createdAtStart = true) {
            MemoryFileSystem('O')
        }
        singleOf(MemoryFileSystem::memFS)
        single<RecordWatcher>(createdAtStart = true) { RecordWatcherImpl() }
        single(createdAtStart = true) {
            OBS("127.0.0.1", Config.config.obsPort, Config.config.obsPassword)
        }
        singleOf(OBS::replayBuffer)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        startKoin {
            slf4jLogger()

            modules(appModule)
        }

        Application.launch(App::class.java)
    }
}