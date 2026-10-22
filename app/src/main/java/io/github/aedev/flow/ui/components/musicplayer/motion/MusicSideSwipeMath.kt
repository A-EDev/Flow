package io.github.aedev.flow.ui.components.musicplayer.motion

import kotlin.math.roundToInt

/** The same split as the fullscreen video gestures, so the columns sit where they do there. */
private const val SIDE_FRACTION = 0.33f

/** Matches the video volume swipe: one swipe across [VOLUME_SENSITIVITY] of the height spans the range. */
private const val VOLUME_SENSITIVITY = 1.5f

/** Which job a vertical swipe on the open music player does, decided where the finger lands. */
internal enum class MusicSwipeZone {
    SPEED,
    CENTER,
    VOLUME,
}

/**
 * The zone for a touch at ([x], [y]) on a player [width] wide. The top bar band and the system
 * gesture edges stay [MusicSwipeZone.CENTER], so pulling the player down from the top always
 * collapses it and a back swipe never turns into a level change.
 */
internal fun musicSwipeZoneAt(
    x: Float,
    y: Float,
    width: Float,
    topBandPx: Float,
    leftEdgePx: Float,
    rightEdgePx: Float,
): MusicSwipeZone =
    when {
        width <= 0f || y < topBandPx || x < leftEdgePx || x > width - rightEdgePx -> MusicSwipeZone.CENTER
        x < width * SIDE_FRACTION -> MusicSwipeZone.SPEED
        x > width * (1f - SIDE_FRACTION) -> MusicSwipeZone.VOLUME
        else -> MusicSwipeZone.CENTER
    }

internal object MusicVolumeSwipe {
    /** The volume, 0 to 1, after a swipe from [startLevel] that has travelled [travelUpPx] upwards. */
    fun levelFor(
        startLevel: Float,
        travelUpPx: Float,
        heightPx: Float,
    ): Float {
        if (heightPx <= 0f) return startLevel
        return (startLevel + travelUpPx / heightPx * VOLUME_SENSITIVITY).coerceIn(0f, 1f)
    }

    /**
     * The stream step for [level]. Rounded rather than floored, so a swipe has to travel half a
     * step either way before the volume moves, instead of dropping a step at the first pixel down.
     */
    fun stepFor(
        level: Float,
        maxStep: Int,
    ): Int = (level * maxStep).roundToInt().coerceIn(0, maxStep.coerceAtLeast(0))
}
