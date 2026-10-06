package io.github.aedev.flow.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PendingPlaybackRequest(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun replace(action: suspend () -> Unit) {
        cancel()
        job = scope.launch { action() }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
