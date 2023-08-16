package io.github.freya022.mediathor.dl.utils

import okhttp3.Dispatcher
import java.util.concurrent.ExecutorService

fun ExecutorService.toDispatcher() = Dispatcher(this)