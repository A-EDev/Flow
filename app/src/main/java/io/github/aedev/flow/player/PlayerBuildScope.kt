package io.github.aedev.flow.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The coroutines that belong to one built ExoPlayer, such as its preference collectors. They never
 * end on their own, so launching them on the manager's lifelong scope left every released player's
 * set running beside the next one's (#1148).
 *
 * Children are supervised, so one failing collector does not take its siblings down, and closing
 * this scope never cancels [parent].
 */
internal class PlayerBuildScope(
    private val parent: CoroutineScope,
) {
    private var current: CoroutineScope? = null

    val isOpen: Boolean
        get() = current != null

    /** A fresh scope for the player being built, after ending the previous build's. */
    fun open(): CoroutineScope {
        close()
        return CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
            .also { current = it }
    }

    fun close() {
        current?.cancel()
        current = null
    }
}
