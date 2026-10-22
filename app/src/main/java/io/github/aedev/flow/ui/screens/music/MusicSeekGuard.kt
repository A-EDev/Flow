package io.github.aedev.flow.ui.screens.music

import android.os.SystemClock
import kotlin.math.abs

private const val SEEK_POSITION_CONFIRM_TOLERANCE_MS = 1_000L
private const val SEEK_POSITION_MIN_HOLD_MS = 250L
private const val SEEK_POSITION_GUARD_MS = 1_500L

/** Holds the seek bar on a requested position until the player reports it, so it never jumps back. */
internal class MusicSeekGuard(
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private var pendingPosition: Long? = null
    private var startedAtMs: Long = 0L

    fun seekRequested(target: Long) {
        pendingPosition = target
        startedAtMs = now()
    }

    /** The position to show for a player report, or null while a seek is still in flight. */
    fun accept(position: Long): Long? {
        val pending = pendingPosition ?: return position
        val elapsedMs = now() - startedAtMs
        if (abs(position - pending) <= SEEK_POSITION_CONFIRM_TOLERANCE_MS) {
            if (elapsedMs >= SEEK_POSITION_MIN_HOLD_MS) pendingPosition = null
            return position
        }
        if (elapsedMs >= SEEK_POSITION_GUARD_MS) {
            pendingPosition = null
            return position
        }
        return null
    }
}
