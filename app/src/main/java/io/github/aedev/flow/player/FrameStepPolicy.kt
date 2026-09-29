package io.github.aedev.flow.player

/**
 * Where a single-frame step lands.
 *
 * Kept separate from the player so the arithmetic — which frame rate to trust, how far one frame
 * is, and where the clamps sit — can be exercised without one.
 */
object FrameStepPolicy {
    /** Used when the format reports no usable rate; most YouTube uploads are 30fps. */
    const val FALLBACK_FRAME_RATE = 30f

    const val MIN_FRAME_RATE = 1f
    const val MAX_FRAME_RATE = 240f

    fun frameDurationMs(frameRate: Float?): Long {
        val rate =
            frameRate
                ?.takeIf { it.isFinite() && it >= MIN_FRAME_RATE && it <= MAX_FRAME_RATE }
                ?: FALLBACK_FRAME_RATE
        return (1_000f / rate).toLong().coerceAtLeast(1L)
    }

    /**
     * @return the position to seek to, or null when the step would not move the playhead — at
     *   either end, or on a stream with no known duration to clamp against.
     */
    fun stepTarget(
        positionMs: Long,
        durationMs: Long,
        frameRate: Float?,
        forward: Boolean,
    ): Long? {
        if (durationMs <= 0L) return null
        val delta = frameDurationMs(frameRate)
        val target = (if (forward) positionMs + delta else positionMs - delta).coerceIn(0L, durationMs)
        return target.takeIf { it != positionMs }
    }
}
