package io.github.aedev.flow.player

/** Podcasts, audiobooks and long mixes pick up where they stopped; songs always start over. */
object MusicResumePolicy {
    const val MIN_DURATION_MS = 10 * 60 * 1000L

    fun isLongTrack(durationMs: Long): Boolean = durationMs >= MIN_DURATION_MS

    fun startPosition(
        savedPosition: Long,
        durationMs: Long,
    ): Long =
        PlaybackResumePolicy.resolveStartPosition(
            savedPosition = savedPosition,
            durationMs = durationMs,
            resumeAllowed = isLongTrack(durationMs),
        )
}
