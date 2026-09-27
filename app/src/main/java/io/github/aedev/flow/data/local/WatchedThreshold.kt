package io.github.aedev.flow.data.local

import io.github.aedev.flow.data.local.dao.WatchProgress

/**
 * How much of a video counts as watched. [isWatched] is the only definition: every watched filter,
 * Shorts included, reads history rows and applies it here rather than restating it in SQL (#979).
 *
 * [ALMOST_FINISHED] means the last minute of a long video, and at least 90 % of a short one, so a
 * one-minute Short is not watched the moment it starts.
 */
enum class WatchedThreshold(
    val minPercent: Float,
    val maxRemainingMs: Long,
) {
    PERCENT_90(90f, Long.MAX_VALUE),
    PERCENT_95(95f, Long.MAX_VALUE),
    PERCENT_99(99f, Long.MAX_VALUE),
    ALMOST_FINISHED(90f, 60_000L),
    ;

    fun isWatched(
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        if (positionMs <= 0L || durationMs <= 0L) return false
        val percent = positionMs.toFloat() / durationMs.toFloat() * 100f
        return percent >= minPercent && durationMs - positionMs <= maxRemainingMs
    }
}

internal fun List<WatchProgress>.watchedIds(threshold: WatchedThreshold): Set<String> =
    filter { threshold.isWatched(it.position, it.duration) }.mapTo(HashSet()) { it.videoId }
