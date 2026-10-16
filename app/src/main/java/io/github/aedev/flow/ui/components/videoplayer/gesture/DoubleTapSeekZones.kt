package io.github.aedev.flow.ui.components.videoplayer.gesture

/** Where a tap landed across the player's width. [EDGE] is the strip the system back gesture owns. */
internal enum class TapZone { EDGE, BACK, CENTER, FORWARD }

/** How long the seek read-out stays up after a seek. A tap on the same side inside it adds to that seek. */
internal const val DOUBLE_TAP_SEEK_WINDOW_MS = 800L

/** A side fraction of zero puts the whole width, edges aside, in [TapZone.CENTER]. */
internal fun tapZoneOf(
    x: Float,
    width: Float,
    sideFraction: Float,
    edgePx: Float = 0f,
): TapZone =
    when {
        width <= 0f -> TapZone.CENTER
        x < edgePx || x > width - edgePx -> TapZone.EDGE
        sideFraction <= 0f -> TapZone.CENTER
        x < width * sideFraction -> TapZone.BACK
        x > width * (1f - sideFraction) -> TapZone.FORWARD
        else -> TapZone.CENTER
    }

/** The edge strip taps leave to the back gesture: none when the zones reach the edges. */
internal fun tapEdgePx(
    reachesEdges: Boolean,
    backGestureEdgePx: Float,
): Float = if (reachesEdges) 0f else backGestureEdgePx
