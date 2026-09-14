package io.github.aedev.flow.ui.components.search

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.ui.components.feedCardsFormGrid
import io.github.aedev.flow.ui.components.feedGridLayoutFor
import org.junit.Test

/**
 * A search result is a full-width 16:9 card only when the row it sits in is the phone's own width.
 * Anywhere else — a tablet, a foldable, a one-column preference on a wide window — a lone card that
 * wide is a thumbnail the size of the screen, so it takes the thumbnail-left row instead.
 */
class SearchResultLayoutTest {
    private fun thumbnailRows(
        widthDp: Int,
        itemCount: Int,
        isGridMode: Boolean = false,
        columns: HomeFeedColumns = HomeFeedColumns.AUTO,
    ): Boolean {
        val layout = feedGridLayoutFor(widthDp.dp, columns)
        val gridCards = feedCardsFormGrid(layout.columns, itemCount)
        return isGridMode || (!gridCards && !layout.isCompact)
    }

    @Test
    fun `a phone keeps the full-width card`() {
        assertThat(thumbnailRows(widthDp = 411, itemCount = 20)).isFalse()
    }

    @Test
    fun `a tablet row of several cards keeps the full-width card in each cell`() {
        assertThat(thumbnailRows(widthDp = 1200, itemCount = 20)).isFalse()
    }

    @Test
    fun `a lone card on a tablet takes the thumbnail row`() {
        assertThat(thumbnailRows(widthDp = 1200, itemCount = 1)).isTrue()
    }

    @Test
    fun `one pinned column on a tablet takes the thumbnail row`() {
        assertThat(thumbnailRows(widthDp = 1200, itemCount = 20, columns = HomeFeedColumns.ONE)).isTrue()
    }

    @Test
    fun `the grid toggle always takes the thumbnail row`() {
        assertThat(thumbnailRows(widthDp = 411, itemCount = 20, isGridMode = true)).isTrue()
        assertThat(thumbnailRows(widthDp = 1200, itemCount = 20, isGridMode = true)).isTrue()
    }

    @Test
    fun `a strip is one card per row, so it follows the window rather than the item count`() {
        assertThat(feedGridLayoutFor(411.dp).isCompact).isTrue()
        assertThat(feedGridLayoutFor(700.dp).isCompact).isFalse()
        assertThat(feedGridLayoutFor(1200.dp).isCompact).isFalse()
    }
}
