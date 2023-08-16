package io.github.freya022.mediathor.dl.utils

import io.github.freya022.mediathor.dl.Data
import io.github.freya022.mediathor.http.CachedHttpClient

val sharedClient = CachedHttpClient(Data.cacheFolder)