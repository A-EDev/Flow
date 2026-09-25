package io.github.aedev.flow.ui.components.musicplayer.sheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** A phone's mini player spans the window; anything wider gets one a little wider than a phone's. */
internal val MiniPlayerMaxWidth = 480.dp
internal val MiniPlayerCompactMargin = 12.dp
internal val MiniPlayerLargeMargin = 16.dp

/** Where the collapsed mini player sits across the window, in px from the start edge. */
@Immutable
internal data class MiniPlayerBounds(
    val start: Float,
    val width: Float,
)

/**
 * Compact windows get the full width minus [compactMarginPx] on each side. Wider windows get at
 * most [maxWidthPx], centred over the content area, which begins after the navigation rail
 * ([startInsetPx]), and never closer than [largeMarginPx] to its edges.
 */
internal fun miniPlayerBounds(
    containerWidthPx: Float,
    startInsetPx: Float,
    isCompactWidth: Boolean,
    compactMarginPx: Float,
    largeMarginPx: Float,
    maxWidthPx: Float,
): MiniPlayerBounds {
    if (isCompactWidth) {
        return MiniPlayerBounds(
            start = compactMarginPx,
            width = (containerWidthPx - compactMarginPx * 2).coerceAtLeast(0f),
        )
    }
    val contentWidth = (containerWidthPx - startInsetPx).coerceAtLeast(0f)
    val width = minOf(maxWidthPx, contentWidth - largeMarginPx * 2).coerceAtLeast(0f)
    return MiniPlayerBounds(
        start = startInsetPx + (contentWidth - width) / 2f,
        width = width,
    )
}
