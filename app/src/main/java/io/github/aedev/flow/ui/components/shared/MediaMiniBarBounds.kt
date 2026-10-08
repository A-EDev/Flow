package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The size and placement of the bottom mini bars: the music player's and the background video's. */
object MediaMiniBarDefaults {
    val Height: Dp = 64.dp
    val BottomSpacer: Dp = 8.dp
    val CornerRadius: Dp = 32.dp

    /** A phone's mini bar spans the window; anything wider gets one a little wider than a phone's. */
    val MaxWidth: Dp = 480.dp
    val CompactMargin: Dp = 12.dp
    val LargeMargin: Dp = 16.dp
}

/** Where a collapsed mini bar sits across the window, in px from the start edge. */
@Immutable
data class MediaMiniBarBounds(
    val start: Float,
    val width: Float,
)

/**
 * Compact windows get the full width minus [compactMarginPx] on each side. Wider windows get at
 * most [maxWidthPx], centred over the content area, which begins after the navigation rail
 * ([startInsetPx]), and never closer than [largeMarginPx] to its edges.
 */
fun mediaMiniBarBounds(
    containerWidthPx: Float,
    startInsetPx: Float,
    isCompactWidth: Boolean,
    compactMarginPx: Float,
    largeMarginPx: Float,
    maxWidthPx: Float,
): MediaMiniBarBounds {
    if (isCompactWidth) {
        return MediaMiniBarBounds(
            start = compactMarginPx,
            width = (containerWidthPx - compactMarginPx * 2).coerceAtLeast(0f),
        )
    }
    val contentWidth = (containerWidthPx - startInsetPx).coerceAtLeast(0f)
    val width = minOf(maxWidthPx, contentWidth - largeMarginPx * 2).coerceAtLeast(0f)
    return MediaMiniBarBounds(
        start = startInsetPx + (contentWidth - width) / 2f,
        width = width,
    )
}
