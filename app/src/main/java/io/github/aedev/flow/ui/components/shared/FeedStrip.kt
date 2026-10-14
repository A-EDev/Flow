package io.github.aedev.flow.ui.components.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontal strip cards are sized from the width they scroll across rather than pinned, so a phone
 * shows one and a peek of the next while a tablet shows three of the same shape.
 *
 * A strip that is pinned on a phone by design passes [compactWidth]; it is kept unless the strip
 * could no longer show a peek of the next card.
 */
fun feedStripCardWidth(
    availableWidth: Dp,
    compactWidth: Dp = Dp.Unspecified,
): Dp {
    if (availableWidth < CompactStripWidth && compactWidth != Dp.Unspecified) {
        return compactWidth.coerceAtMost(availableWidth - StripMinimumPeek).coerceAtLeast(StripCardFloor)
    }
    val divisor = if (availableWidth < CompactStripWidth) COMPACT_STRIP_DIVISOR else WIDE_STRIP_DIVISOR
    return (availableWidth / divisor).coerceIn(StripCardMinWidth, StripCardMaxWidth)
}

private const val COMPACT_STRIP_DIVISOR = 1.3f
private const val WIDE_STRIP_DIVISOR = 2.6f
private val CompactStripWidth = 600.dp
private val StripCardMinWidth = 260.dp
private val StripCardMaxWidth = 380.dp
private val StripMinimumPeek = 60.dp
private val StripCardFloor = 160.dp
