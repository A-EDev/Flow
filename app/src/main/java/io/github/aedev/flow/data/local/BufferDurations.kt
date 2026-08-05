package io.github.aedev.flow.data.local

/**
 * Buffer durations for Media3's `DefaultLoadControl`, already reconciled with each other.
 *
 * `setBufferDurationsMs` asserts `min >= playback`, `min >= rebuffer` and `max >= min`, and throws while the
 * player is built rather than falling back. The values are user-editable and are capped again per device heap
 * at read time, so a combination saved as valid can be invalid when it is used — that is how #788 and #876
 * became launch crashes that repeated on every start, the offending value living on in DataStore.
 */
data class BufferDurations(
    val minMs: Int,
    val maxMs: Int,
    val playbackMs: Int,
    val rebufferMs: Int,
) {
    companion object {
        /** Smallest min buffer that still absorbs a short network stall. */
        const val MIN_FLOOR_MS = 2_500

        /** Smallest amount of media that must be buffered before playback starts. */
        const val PLAYBACK_FLOOR_MS = 250

        /** Smallest amount of media that must be buffered before playback resumes after a rebuffer. */
        const val REBUFFER_FLOOR_MS = 750

        /** Headroom the max buffer keeps above the min buffer so the loader always has room to load ahead. */
        const val MAX_HEADROOM_MS = 5_000

        /**
         * Reconciles stored buffer preferences with the caps the device heap can afford.
         *
         * Total by construction: every bound is widened before it is applied, so no combination of stored
         * values — corrupt, negative, or contradicting each other — and no cap, however small, can throw or
         * produce durations `DefaultLoadControl` rejects.
         *
         * @param maxSafeMinMs device budget for the min buffer; a stored min above it is capped, not honoured.
         * @param maxSafeMaxMs device budget for the max buffer.
         */
        fun sanitize(
            minMs: Int,
            maxMs: Int,
            playbackMs: Int,
            rebufferMs: Int,
            maxSafeMinMs: Int,
            maxSafeMaxMs: Int,
        ): BufferDurations {
            // Capping the ceiling keeps the `+ MAX_HEADROOM_MS` below from overflowing on an absurd budget.
            val minCeiling = maxOf(maxSafeMinMs, MIN_FLOOR_MS).coerceAtMost(Int.MAX_VALUE - MAX_HEADROOM_MS)
            // A resume threshold has to fit inside the min buffer, so it raises the min instead of being
            // dropped: the user asked to resume with that much buffered.
            val resolvedMin = maxOf(minMs, rebufferMs).coerceIn(MIN_FLOOR_MS, minCeiling)
            val maxFloor = resolvedMin + MAX_HEADROOM_MS
            val resolvedMax = maxOf(maxMs, maxFloor).coerceAtMost(maxOf(maxSafeMaxMs, maxFloor))

            return BufferDurations(
                minMs = resolvedMin,
                maxMs = resolvedMax,
                playbackMs = playbackMs.coerceIn(PLAYBACK_FLOOR_MS, maxOf(resolvedMin, PLAYBACK_FLOOR_MS)),
                rebufferMs = rebufferMs.coerceIn(REBUFFER_FLOOR_MS, maxOf(resolvedMin, REBUFFER_FLOOR_MS)),
            )
        }
    }
}
