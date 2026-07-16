package io.github.aedev.flow.player

object PlaybackResumePolicy {
    fun resolveStartPosition(
        savedPosition: Long,
        durationMs: Long,
        resumeAllowed: Boolean
    ): Long {
        if (!resumeAllowed || savedPosition <= 500L) return 0L
        return savedPosition.takeUnless {
            shouldRestartCompletedPlayback(savedPosition, durationMs)
        } ?: 0L
    }

    fun shouldRestartCompletedPlayback(savedPosition: Long, durationMs: Long): Boolean {
        if (savedPosition <= 0L) return false
        if (durationMs > 0L) {
            val remainingMs = durationMs - savedPosition
            return remainingMs <= 1_500L || savedPosition >= (durationMs * 0.98f).toLong()
        }
        return savedPosition > 4 * 60 * 60 * 1000L
    }
}
