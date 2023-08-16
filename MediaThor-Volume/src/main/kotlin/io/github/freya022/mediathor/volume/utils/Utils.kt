package io.github.freya022.mediathor.volume.utils

import io.github.freya022.mediathor.http.CachedHttpClient
import io.github.freya022.mediathor.volume.Data

val sharedClient = CachedHttpClient(Data.cacheFolder)