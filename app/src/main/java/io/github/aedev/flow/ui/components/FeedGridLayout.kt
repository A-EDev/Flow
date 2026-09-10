package io.github.aedev.flow.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import io.github.aedev.flow.ui.theme.Dimensions

/**
 * Narrowest a video card may be before the grid drops a column. Picked so the counts the
 * per-screen width tables produced at 700 dp, 900 dp and 1200 dp survive.
 */
private val FeedCardMinWidth = 260.dp

private class FeedGridSpacing(
    val contentPadding: Dp,
    val cardSpacing: Dp,
)

private val CompactWindowSpacing = FeedGridSpacing(contentPadding = 0.dp, cardSpacing = Dimensions.ItemSpacing)
private val WideWindowSpacing = FeedGridSpacing(contentPadding = 16.dp, cardSpacing = Dimensions.ItemSpacing)
private val LargeWindowSpacing = FeedGridSpacing(contentPadding = 24.dp, cardSpacing = 16.dp)

/**
 * The single layout decision behind every vertical video grid — Home, Subscriptions, Categories and
 * Search all render the same cards, so they all have to size them the same way.
 *
 * A compact window is one column of edge-to-edge cards, as Material 3 asks of a single-pane layout.
 * Wider windows hand the count to [GridCells.Adaptive], which fills them with as many cards of at
 * least [FeedCardMinWidth] as fit instead of matching the width against a table. [columns] mirrors
 * that derivation for the callers that must know the count up front: a full-span shelf has to start
 * on a fresh row, and a one-column grid drops its gutters.
 */
data class FeedGridLayout(
    val cells: GridCells,
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
)

fun feedGridLayoutFor(maxWidth: Dp): FeedGridLayout {
    val isCompact = maxWidth < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp
    val spacing =
        when {
            maxWidth >= WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND.dp -> LargeWindowSpacing
            isCompact -> CompactWindowSpacing
            else -> WideWindowSpacing
        }
    val availableWidth = maxWidth - spacing.contentPadding * 2
    return FeedGridLayout(
        cells = if (isCompact) GridCells.Fixed(1) else GridCells.Adaptive(FeedCardMinWidth),
        columns = if (isCompact) 1 else adaptiveColumnsFor(availableWidth, spacing.cardSpacing),
        contentPadding = spacing.contentPadding,
        cardSpacing = spacing.cardSpacing,
    )
}

/** The count [GridCells.Adaptive] derives for [FeedCardMinWidth]: as many whole cards as fit. */
private fun adaptiveColumnsFor(
    availableWidth: Dp,
    cardSpacing: Dp,
): Int = ((availableWidth + cardSpacing) / (FeedCardMinWidth + cardSpacing)).toInt().coerceAtLeast(1)

@Composable
fun rememberFeedGridLayout(maxWidth: Dp): FeedGridLayout = remember(maxWidth) { feedGridLayoutFor(maxWidth) }
