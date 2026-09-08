package io.github.aedev.flow.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.ui.components.FeedGridLayout
import io.github.aedev.flow.ui.components.rememberFeedGridLayout

/**
 * Width at which the feed stops being a single column on its own. Below it a second column leaves
 * each card too narrow to read; at or above it one card per row wastes most of the screen.
 */
private val TwoColumnMinWidth = 600.dp

internal data class HomeLayoutConfig(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
    val shortsShelfAfterIndex: Int,
    val shimmerColumns: Int,
)

@Composable
internal fun rememberHomeLayoutConfig(
    maxWidth: Dp,
    columnPreference: HomeFeedColumns = HomeFeedColumns.AUTO,
): HomeLayoutConfig {
    val base = rememberFeedGridLayout(maxWidth)
    return remember(base, maxWidth, columnPreference) {
        resolveHomeLayoutConfig(base, maxWidth, columnPreference)
    }
}

internal fun resolveHomeLayoutConfig(
    base: FeedGridLayout,
    maxWidth: Dp,
    columnPreference: HomeFeedColumns,
): HomeLayoutConfig {
    val autoColumns = if (base.columns == 1 && maxWidth >= TwoColumnMinWidth) 2 else base.columns
    val columns = columnPreference.fixedCount ?: autoColumns
    val shelfAfter =
        when {
            maxWidth < 480.dp -> 1
            maxWidth < 700.dp -> 2
            maxWidth < 900.dp -> 2
            maxWidth < 1200.dp -> 3
            else -> 4
        }
    return HomeLayoutConfig(
        columns = columns,
        contentPadding = base.contentPadding,
        cardSpacing = base.cardSpacing,
        // The shelf spans every column, so it has to start on a fresh row or the row above it
        // renders with holes in it.
        shortsShelfAfterIndex = shelfAfter.roundUpToMultipleOf(columns),
        shimmerColumns = columns,
    )
}

private fun Int.roundUpToMultipleOf(factor: Int): Int = if (factor <= 1) this else ((this + factor - 1) / factor) * factor
