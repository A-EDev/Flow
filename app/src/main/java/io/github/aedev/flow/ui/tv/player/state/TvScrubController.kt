package io.github.aedev.flow.ui.tv.player.state

/**
 * D-pad scrubbing with hold-to-accelerate. Android's key-repeat drives cadence —
 * the step ladder is a pure function of the event's repeatCount, so no timers.
 * Scrubbing is preview-then-commit: playback continues until [commit] performs
 * exactly one seek.
 */
class TvScrubController {

    data class ScrubState(
        val isScrubbing: Boolean = false,
        val targetMs: Long = 0L,
        val stepMs: Long = 0L,
    )

    var current: ScrubState = ScrubState()
        private set

    /**
     * Starts or advances a scrub. [direction] is -1 (back) or +1 (forward);
     * [repeatCount] comes straight from the native key event.
     */
    fun beginOrStep(
        direction: Int,
        repeatCount: Int,
        currentPositionMs: Long,
        durationMs: Long,
    ): ScrubState {
        val step = stepSizeFor(repeatCount)
        val base = if (current.isScrubbing) current.targetMs else currentPositionMs
        val target = (base + direction * step).coerceIn(0L, durationMs.coerceAtLeast(0L))
        current = ScrubState(isScrubbing = true, targetMs = target, stepMs = step)
        return current
    }

    /** Returns the position to seek to (or null if no scrub was active) and resets. */
    fun commit(): Long? {
        val target = current.takeIf { it.isScrubbing }?.targetMs
        current = ScrubState()
        return target
    }

    fun cancel() {
        current = ScrubState()
    }

    companion object {
        /** Acceleration ladder: 10s → 30s → 60s → 120s as the key repeats. */
        fun stepSizeFor(repeatCount: Int): Long = when {
            repeatCount >= 16 -> 120_000L
            repeatCount >= 8 -> 60_000L
            repeatCount >= 3 -> 30_000L
            else -> 10_000L
        }
    }
}
