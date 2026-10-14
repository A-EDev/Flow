package io.github.aedev.flow.ui.components.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.feedGridLayoutFor
import io.github.aedev.flow.ui.components.partialRowIndices
import io.github.aedev.flow.ui.components.shared.MediaRowDefaults
import io.github.aedev.flow.ui.components.shared.MediaThumbnailDefaults
import io.github.aedev.flow.ui.components.shared.card.VideoCardDefaults

/**
 * A library grid sized on the space it actually has, with the same column count and margins as the
 * feeds. Library cards fill their cell and carry no inset, so the grid pads the full margin and
 * spaces the cards itself.
 */
internal data class LibraryGridLayout(
    val columns: Int,
    val padding: Dp,
    val spacing: Dp,
    /** A row's thumbnail when the row stands in for a card, so it spans about one column. */
    val rowThumbnailWidth: Dp,
    val isCompact: Boolean,
) {
    val isGrid: Boolean get() = columns > 1

    /** A wide grid gives the cards its last row cannot fill a row each; a phone keeps its grid as is. */
    fun partialRows(itemCount: Int): Set<Int> = if (isCompact) emptySet() else partialRowIndices(List(itemCount) { false }, columns)
}

/** [compactColumns] is how many cards a phone shows when the screen draws cards at all. */
internal fun libraryGridLayoutFor(
    maxWidth: Dp,
    compactColumns: Int = 1,
): LibraryGridLayout {
    val feed = feedGridLayoutFor(maxWidth)
    if (feed.isCompact) {
        return LibraryGridLayout(
            columns = compactColumns,
            padding = if (compactColumns > 1) CompactGridPadding else 0.dp,
            spacing = CompactGridSpacing,
            rowThumbnailWidth = MediaThumbnailDefaults.VideoWidth,
            isCompact = true,
        )
    }
    val padding = feed.contentPadding + VideoCardDefaults.Inset
    val spacing = feed.cardSpacing
    val cardWidth = (maxWidth - padding * 2 - spacing * (feed.columns - 1)) / feed.columns
    return LibraryGridLayout(
        columns = feed.columns,
        padding = padding,
        spacing = spacing,
        rowThumbnailWidth = cardWidth - MediaRowDefaults.HorizontalPadding,
        isCompact = false,
    )
}

/** A one-column library list's thumbnail: fixed on a phone, one feed column's thumbnail wider. */
internal fun libraryListThumbnailWidth(maxWidth: Dp): Dp {
    val feed = feedGridLayoutFor(maxWidth)
    return if (feed.isCompact) MediaThumbnailDefaults.VideoWidth else feed.thumbnailWidth
}

private val CompactGridPadding = 16.dp
private val CompactGridSpacing = 12.dp
