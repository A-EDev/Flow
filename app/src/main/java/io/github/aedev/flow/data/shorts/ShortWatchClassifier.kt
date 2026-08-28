/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.data.recommendation.InteractionType

/** Classified watch outcome for a Short. */
data class ShortWatchSignal(
    val position: Long,
    val safeDuration: Long,
    val percent: Float,
    val interaction: InteractionType,
)

/** Pure watch-signal classification for Shorts, extracted from the ViewModel for testability. */
object ShortWatchClassifier {
    // Quick flicks below this count as skips, not watches.
    const val MIN_SHORT_WATCH_MS = 2_000L

    // Swiping away below this fraction (and before ABANDON_NEUTRAL_MS) reads as rejection.
    const val ABANDON_SKIP_FRACTION = 0.30f

    // Watching past this fraction before swiping away still counts as a watch.
    const val ABANDON_WATCH_FRACTION = 0.60f

    // Past this absolute position an early swipe is neutral, not a rejection.
    const val ABANDON_NEUTRAL_MS = 15_000L

    fun classify(
        positionMs: Long,
        durationMs: Long,
        videoDurationSec: Int,
    ): ShortWatchSignal {
        val safeDuration =
            when {
                durationMs > 0L -> durationMs
                videoDurationSec > 0 -> videoDurationSec * 1000L
                else -> positionMs.coerceAtLeast(1_000L)
            }
        val position = positionMs.coerceIn(0L, safeDuration)
        val percent = (position.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        val interaction =
            if (position < MIN_SHORT_WATCH_MS) {
                InteractionType.SKIPPED
            } else {
                InteractionType.WATCHED
            }
        return ShortWatchSignal(position, safeDuration, percent, interaction)
    }

    /**
     * Classifies a short the user swiped away from BEFORE the terminal watch fired.
     * Early abandonment is the clearest negative signal Shorts produce; a swipe
     * after substantial watching is still positive; the middle band is neutral
     * (returns null — no engine signal).
     */
    fun classifyAbandon(
        positionMs: Long,
        durationMs: Long,
        videoDurationSec: Int,
    ): ShortWatchSignal? {
        val base = classify(positionMs, durationMs, videoDurationSec)
        return when {
            base.percent >= ABANDON_WATCH_FRACTION -> {
                base.copy(interaction = InteractionType.WATCHED)
            }

            base.percent < ABANDON_SKIP_FRACTION && base.position < ABANDON_NEUTRAL_MS -> {
                base.copy(interaction = InteractionType.SKIPPED)
            }

            else -> {
                null
            }
        }
    }
}
