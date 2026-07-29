package io.github.aedev.flow.player.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun Flow<EnhancedPlayerState>.queuePresence(): Flow<Boolean> =
    map { state -> state.queueTitle != null }
        .distinctUntilChanged()
