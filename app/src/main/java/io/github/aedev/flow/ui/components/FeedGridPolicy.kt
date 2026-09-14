package io.github.aedev.flow.ui.components

/**
 * Past three cards a row of 16:9 thumbnails reads as a strip of stamps; YouTube's tablet layout stops
 * there too. A pinned preference from settings still overrides this.
 */
internal const val FEED_MAX_AUTO_COLUMNS = 3

private const val SHELF_PREVIEW_ROWS_COMPACT = 4
private const val SHELF_PREVIEW_ROWS_GRID = 2

/**
 * Whether a set of cards forms a grid at all. Shared by the channel tabs and search. One lone grid card on a wide window is a thumbnail
 * blown up to a third of the screen, so a single item always takes the list variant instead.
 */
internal fun feedCardsFormGrid(
    columns: Int,
    itemCount: Int,
): Boolean = columns > 1 && itemCount > 1

/** A phone shelf previews four rows; a grid previews two full rows so the expander sits on a seam. */
internal fun feedShelfPreviewCount(
    columns: Int,
    itemCount: Int,
): Int =
    if (feedCardsFormGrid(columns, itemCount)) {
        columns * SHELF_PREVIEW_ROWS_GRID
    } else {
        SHELF_PREVIEW_ROWS_COMPACT
    }
