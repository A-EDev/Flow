package io.github.aedev.flow.widget.recent

import kotlin.math.ceil

/** The share watched, or null when there is nothing to show: no duration, not started, or finished. */
internal fun watchedFraction(
    positionMs: Long,
    durationMs: Long,
): Float? {
    if (durationMs <= 0L || positionMs <= 0L) return null
    val fraction = positionMs.toFloat() / durationMs
    return fraction.takeIf { it < FINISHED_FRACTION }?.coerceIn(0f, 1f)
}

/** Whole minutes left, rounded up so a video with seconds to go never reads "0 min left". */
internal fun minutesLeft(
    positionMs: Long,
    durationMs: Long,
): Int? = watchedFraction(positionMs, durationMs)?.let { ceil((durationMs - positionMs) / 60_000.0).toInt().coerceAtLeast(1) }

/** The same line the app uses for a finished video: past this share it counts as watched. */
private const val FINISHED_FRACTION = 0.95f
