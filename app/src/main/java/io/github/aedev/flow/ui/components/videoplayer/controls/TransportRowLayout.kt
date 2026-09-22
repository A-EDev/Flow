package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How the transport row fits itself to the player it is drawn over. */
internal data class TransportRowLayout(
    val spacing: Dp,
    val scale: Float,
)

/** How far apart the buttons sit on a player with room to spare. */
private val MaxSpacing = 48.dp

/** How close they are allowed to get before the row shrinks instead. */
private val MinSpacing = 12.dp

/** A row that still overflows at [MinSpacing] shrinks, but only this far. */
private const val MIN_SCALE = 0.7f

/**
 * Spacing and scale that keep every transport button on screen.
 *
 * The row used to space its buttons at a fixed 48dp whatever it was drawn into. Five buttons then
 * came to more than a portrait player is wide, and since a Row lays out from its start and overflows
 * past its end, the result was a row pinned to the left edge with the next button off the right one.
 *
 * Spacing closes up first because it costs nothing, and only a row that will not fit even closed up
 * is scaled down — a smaller play button is a worse answer than a tighter row.
 */
internal fun transportRowLayout(
    availableWidth: Dp,
    contentWidth: Dp,
    gaps: Int,
): TransportRowLayout {
    if (availableWidth <= 0.dp || contentWidth <= 0.dp) return TransportRowLayout(MaxSpacing, 1f)
    val spacing =
        if (gaps > 0) ((availableWidth - contentWidth) / gaps).coerceIn(MinSpacing, MaxSpacing) else 0.dp
    val needed = contentWidth + spacing * gaps
    val scale = if (needed > availableWidth) (availableWidth / needed).coerceIn(MIN_SCALE, 1f) else 1f
    return TransportRowLayout(spacing = spacing, scale = scale)
}
