package io.github.freya022.mediathor.record.obs.data.events

import io.github.freya022.mediathor.record.obs.OBS
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

sealed class Event : KoinComponent {
    val obs: OBS by inject()
}