package io.github.aedev.flow.data.video.downloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * How many downloads may transfer at once. The limit follows a setting that can change while work is
 * queued: raising it lets waiting downloads start, lowering it only holds back new ones.
 * Until a limit is set, every caller waits.
 */
internal class DownloadSlots(
    initialLimit: Int? = null,
) {
    private data class State(
        val limit: Int?,
        val running: Int,
    ) {
        val hasRoom: Boolean get() = limit != null && running < limit
    }

    private val state = MutableStateFlow(State(initialLimit?.coerceAtLeast(1), running = 0))

    val running: Int get() = state.value.running

    fun setLimit(limit: Int) {
        state.update { it.copy(limit = limit.coerceAtLeast(1)) }
    }

    suspend fun <T> withSlot(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            state.update { it.copy(running = it.running - 1) }
        }
    }

    private suspend fun acquire() {
        while (true) {
            val current = state.value
            if (current.hasRoom) {
                if (state.compareAndSet(current, current.copy(running = current.running + 1))) return
            } else {
                state.first { it.hasRoom }
            }
        }
    }
}
