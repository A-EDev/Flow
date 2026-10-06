package io.github.aedev.flow.ui.components.videoplayer.gesture

/** Where a tap landed across the player's width. */
internal enum class TapZone { BACK, CENTER, FORWARD }

/** Share of the player width each seek zone covers, shared by the hit test and the seek ripple. */
internal const val DEFAULT_SEEK_ZONE_FRACTION = 1f / 3f

/** A side fraction of zero puts the whole width in [TapZone.CENTER]. */
internal fun tapZoneOf(
    x: Float,
    width: Float,
    sideFraction: Float,
): TapZone =
    when {
        width <= 0f || sideFraction <= 0f -> TapZone.CENTER
        x < width * sideFraction -> TapZone.BACK
        x > width * (1f - sideFraction) -> TapZone.FORWARD
        else -> TapZone.CENTER
    }
