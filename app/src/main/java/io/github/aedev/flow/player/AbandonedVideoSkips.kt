package io.github.aedev.flow.player

private const val MAX_CONSECUTIVE_SKIPS = 2

/**
 * Whether a queue may move past a video whose streams YouTube kept refusing (#1008). A run of
 * refusals in a row means the network or the session is at fault, not the videos, so after
 * [limit] skips without anything playing in between the queue stops and says so instead of
 * burning through every video's retries.
 */
internal class AbandonedVideoSkips(
    private val limit: Int = MAX_CONSECUTIVE_SKIPS,
) {
    private var skipsInARow = 0

    fun trySkip(hasNextInQueue: Boolean): Boolean {
        if (!hasNextInQueue || skipsInARow >= limit) return false
        skipsInARow++
        return true
    }

    fun onPlaybackStarted() {
        skipsInARow = 0
    }
}
