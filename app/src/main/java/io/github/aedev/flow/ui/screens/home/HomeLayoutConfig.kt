package io.github.aedev.flow.ui.screens.home

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.ui.components.FeedGridLayout
import io.github.aedev.flow.ui.components.rememberFeedGridLayout

internal data class HomeLayoutConfig(
    val cells: GridCells,
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
    val shortsShelfAfterIndex: Int,
)

@Composable
internal fun rememberHomeLayoutConfig(
    maxWidth: Dp,
    columnPreference: HomeFeedColumns = HomeFeedColumns.AUTO,
): HomeLayoutConfig {
    val base = rememberFeedGridLayout(maxWidth)
    return remember(base, columnPreference) {
        resolveHomeLayoutConfig(base, columnPreference)
    }
}

internal fun resolveHomeLayoutConfig(
    base: FeedGridLayout,
    columnPreference: HomeFeedColumns,
): HomeLayoutConfig {
    val fixedColumns = columnPreference.fixedCount
    val columns = fixedColumns ?: base.columns
    return HomeLayoutConfig(
        cells = if (fixedColumns == null) base.cells else GridCells.Fixed(fixedColumns),
        columns = columns,
        contentPadding = base.contentPadding,
        cardSpacing = base.cardSpacing,
        // The shelf spans every column, so it has to start on a fresh row or the row above it
        // renders with holes in it.
        shortsShelfAfterIndex = columns,
    )
}
