package io.github.aedev.flow.ui.components.videoplayer.gesture

/** Where a tap landed across the player's width. */
internal enum class TapZone { BACK, CENTER, FORWARD }

/** How long the seek read-out stays up after a seek. A tap on the same side inside it adds to that seek. */
internal const val DOUBLE_TAP_SEEK_WINDOW_MS = 800L

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
