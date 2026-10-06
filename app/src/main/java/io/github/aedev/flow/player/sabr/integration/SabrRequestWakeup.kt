package io.github.aedev.flow.player.sabr.integration

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal class SabrRequestWakeup {
    private val playbackRequested = MutableStateFlow(true)

    fun setPlaybackRequested(requested: Boolean) {
        playbackRequested.value = requested
    }

    suspend fun awaitNext(
        activeIntervalMs: Long,
        heartbeatWaitMs: Long,
    ) {
        if (playbackRequested.value) {
            delay(activeIntervalMs)
        } else {
            withTimeoutOrNull(heartbeatWaitMs.coerceAtLeast(1)) {
                playbackRequested.first { it }
            }
        }
    }
}
