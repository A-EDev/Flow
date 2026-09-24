package io.github.aedev.flow.ui.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val UPDATE_WAIT_MS = 15_000L

/**
 * Keeps each launch to one prompt. The update page goes first; the donation prompt waits for the
 * launch update check and stands down for this launch if the update page opened.
 */
@Singleton
class LaunchPrompts
    @Inject
    constructor() {
        private val updateSettled = MutableStateFlow(false)

        @Volatile
        private var updateShown = false

        fun updateCheckFinished(shownUpdate: Boolean) {
            updateShown = updateShown || shownUpdate
            updateSettled.value = true
        }

        /** Waits for the launch update check (at most [waitMs]), then says whether a donation prompt may show. */
        suspend fun donationMayShow(waitMs: Long = UPDATE_WAIT_MS): Boolean {
            withTimeoutOrNull(waitMs) { updateSettled.first { it } }
            return !updateShown
        }
    }
