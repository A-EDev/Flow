package io.github.aedev.flow.ui.components.search

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.ui.components.FEED_MAX_AUTO_COLUMNS
import io.github.aedev.flow.ui.components.feedGridLayoutFor
import io.github.aedev.flow.ui.components.partialRowIndices
import org.junit.Test

/**
 * Mirrors the two decisions `SearchResults` makes per item: how many columns the grid carries, and
 * which cards take the thumbnail-left row because their own row is theirs alone.
 */
class SearchResultLayoutTest {
    private fun columns(
        widthDp: Int,
        listMode: Boolean = false,
        preference: HomeFeedColumns = HomeFeedColumns.AUTO,
    ): Int = if (listMode) 1 else feedGridLayoutFor(widthDp.dp, preference, FEED_MAX_AUTO_COLUMNS).columns

    private fun listCards(
        widthDp: Int,
        itemCount: Int,
        listMode: Boolean = false,
        preference: HomeFeedColumns = HomeFeedColumns.AUTO,
    ): Set<Int> {
        val layout = feedGridLayoutFor(widthDp.dp, preference, FEED_MAX_AUTO_COLUMNS)
        val columns = columns(widthDp, listMode, preference)
        val partial = partialRowIndices(List(itemCount) { false }, columns)
        return (0 until itemCount)
            .filter { listMode || it in partial || (columns == 1 && !layout.isCompact) }
            .toSet()
    }

    @Test
    fun `a wide window carries at most three cards a row`() {
        assertThat(columns(widthDp = 1600)).isAtMost(FEED_MAX_AUTO_COLUMNS)
        assertThat(columns(widthDp = 1200)).isEqualTo(FEED_MAX_AUTO_COLUMNS)
    }

    @Test
    fun `list mode is one full-width column at every size`() {
        assertThat(columns(widthDp = 411, listMode = true)).isEqualTo(1)
        assertThat(columns(widthDp = 1200, listMode = true)).isEqualTo(1)
        assertThat(listCards(widthDp = 1200, itemCount = 7, listMode = true)).hasSize(7)
    }

    @Test
    fun `a phone keeps the full-width card in grid mode`() {
        assertThat(columns(widthDp = 411)).isEqualTo(1)
        assertThat(listCards(widthDp = 411, itemCount = 7)).isEmpty()
    }

    @Test
    fun `a row a tablet cannot fill takes the thumbnail-left card`() {
        assertThat(listCards(widthDp = 1200, itemCount = 6)).isEmpty()
        assertThat(listCards(widthDp = 1200, itemCount = 7)).containsExactly(6)
        assertThat(listCards(widthDp = 1200, itemCount = 8)).containsExactly(6, 7)
    }

    @Test
    fun `one pinned column on a tablet takes the thumbnail-left card`() {
        assertThat(listCards(widthDp = 1200, itemCount = 5, preference = HomeFeedColumns.ONE)).hasSize(5)
    }

    @Test
    fun `a lone result on a tablet takes its own row`() {
        assertThat(listCards(widthDp = 1200, itemCount = 1)).containsExactly(0)
    }

    @Test
    fun `the creator strip sizes its cards from the window`() {
        val phone = stripCardWidth(387.dp)
        val tablet = stripCardWidth(1128.dp)

        assertThat(phone.value).isLessThan(tablet.value)
        assertThat(phone.value).isAtLeast(260f)
        assertThat(tablet.value).isAtMost(380f)
    }

    @Test
    fun `a thumbnail-left row on a tablet is as wide as one grid column`() {
        val layout = feedGridLayoutFor(1200.dp, HomeFeedColumns.AUTO, FEED_MAX_AUTO_COLUMNS)
        val spanned = layout.cardWidth * layout.columns + layout.cardSpacing * (layout.columns - 1)

        assertThat(spanned.value).isWithin(TOLERANCE).of((1200.dp - layout.contentPadding * 2).value)
    }

    @Test
    fun `a phone card is the whole content width`() {
        val layout = feedGridLayoutFor(411.dp)

        assertThat(layout.columns).isEqualTo(1)
        assertThat(layout.cardWidth.value).isWithin(TOLERANCE).of((411.dp - layout.contentPadding * 2).value)
    }

    @Test
    fun `the creator strip never shows a card the window cannot hold`() {
        assertThat(stripCardWidth(320.dp).value).isAtMost(320f)
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
