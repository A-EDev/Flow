package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.CompactVideoCardThumbnailWidth
import io.github.aedev.flow.ui.components.FeedGridLayout
import io.github.aedev.flow.ui.components.partialRowIndices

/**
 * The per-index decisions a vertical feed grid makes once its [FeedGridLayout] and its item list are
 * known — which rows the grid cannot fill, which cards therefore take the thumbnail-left shape, how
 * wide that thumbnail is, and what gutter the grid runs at.
 *
 * Four rules, shared by every feed surface:
 *
 * 1. List mode is always one full-width column, at every screen size.
 * 2. A compact window is one column of edge-to-edge full-width cards — a phone has no room for a
 *    thumbnail-left row.
 * 3. A wide window pinned to one column uses thumbnail-left cards instead; a full-width card there
 *    is a 16:9 image the size of the screen.
 * 4. A row the grid cannot fill takes thumbnail-left cards, one per row, and their thumbnails are
 *    exactly one grid column wide so they align with the grid above.
 */
@Immutable
data class FeedGridPlan(
    val cells: GridCells,
    val columns: Int,
    val gutter: Dp,
    val contentPadding: PaddingValues,
    val listThumbnailWidth: Dp,
    val partialRows: Set<Int>,
    private val listMode: Boolean,
    private val isCompact: Boolean,
) {
    fun isListCard(index: Int): Boolean = listMode || index in partialRows || (columns == 1 && !isCompact)

    fun span(
        index: Int,
        spansOwnRow: Boolean,
        maxLineSpan: Int,
    ): GridItemSpan = if (spansOwnRow || index in partialRows) GridItemSpan(maxLineSpan) else GridItemSpan(1)
}

/**
 * [includeLastRun] is false while more pages may still arrive: the tail of the list would otherwise
 * reflow every time a page lands.
 */
fun feedGridPlanFor(
    layout: FeedGridLayout,
    listMode: Boolean,
    itemCount: Int,
    spansOwnRow: (Int) -> Boolean,
    includeLastRun: Boolean,
    topPadding: Dp = FeedGridTopPadding,
    bottomPadding: Dp = FeedGridBottomPadding,
): FeedGridPlan {
    val columns = if (listMode) 1 else layout.columns
    return FeedGridPlan(
        cells = if (listMode) GridCells.Fixed(1) else layout.cells,
        columns = columns,
        gutter = if (columns == 1) 0.dp else layout.cardSpacing,
        contentPadding =
            PaddingValues(
                start = layout.contentPadding,
                end = layout.contentPadding,
                top = topPadding,
                bottom = bottomPadding,
            ),
        listThumbnailWidth = if (layout.isCompact) CompactVideoCardThumbnailWidth else layout.cardWidth,
        partialRows =
            partialRowIndices(
                spansOwnRow = (0 until itemCount).map(spansOwnRow),
                columns = columns,
                includeLastRun = includeLastRun,
            ),
        listMode = listMode,
        isCompact = layout.isCompact,
    )
}

@Composable
fun rememberFeedGridPlan(
    layout: FeedGridLayout,
    listMode: Boolean,
    itemCount: Int,
    spansOwnRow: (Int) -> Boolean,
    includeLastRun: Boolean,
    itemsKey: Any? = null,
    topPadding: Dp = FeedGridTopPadding,
    bottomPadding: Dp = FeedGridBottomPadding,
): FeedGridPlan =
    remember(layout, listMode, itemCount, includeLastRun, itemsKey, topPadding, bottomPadding) {
        feedGridPlanFor(layout, listMode, itemCount, spansOwnRow, includeLastRun, topPadding, bottomPadding)
    }

private val FeedGridTopPadding = 8.dp

/** Clears the mini player, which floats over the bottom of every feed. */
private val FeedGridBottomPadding = 90.dp
