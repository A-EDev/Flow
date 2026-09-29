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

/**
 * The items whose grid row cannot be filled.
 *
 * A row only ever holds items from one contiguous run between the full-width rows a hero card or a
 * strip occupies, so a run whose length is not a multiple of [columns] leaves its last row short.
 * One card beside two gaps reads as a mistake, so those trailing items take a full-width row each
 * instead.
 *
 * [includeLastRun] is false while more pages may still arrive: the tail of the list would otherwise
 * reflow every time a page lands.
 */
fun partialRowIndices(
    spansOwnRow: List<Boolean>,
    columns: Int,
    includeLastRun: Boolean = true,
): Set<Int> {
    if (columns <= 1) return emptySet()
    val partial = mutableSetOf<Int>()
    var runStart = -1

    fun closeRun(endExclusive: Int) {
        if (runStart < 0) return
        val remainder = (endExclusive - runStart) % columns
        (endExclusive - remainder until endExclusive).forEach(partial::add)
        runStart = -1
    }

    spansOwnRow.forEachIndexed { index, spansRow ->
        when {
            spansRow -> closeRun(index)
            runStart < 0 -> runStart = index
        }
    }
    if (includeLastRun) closeRun(spansOwnRow.size)
    return partial
}

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
