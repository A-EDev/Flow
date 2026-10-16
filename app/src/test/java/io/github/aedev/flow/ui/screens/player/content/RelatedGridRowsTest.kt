package io.github.aedev.flow.ui.screens.player.content

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.ui.components.feedGridLayoutFor
import org.junit.Test

class RelatedGridRowsTest {
    @Test
    fun `full rows chunk by the column count`() {
        assertThat(relatedGridRows(6, 3)).containsExactly(listOf(0, 1, 2), listOf(3, 4, 5)).inOrder()
    }

    @Test
    fun `the items a row cannot fill take a row each`() {
        assertThat(relatedGridRows(5, 3)).containsExactly(listOf(0, 1, 2), listOf(3), listOf(4)).inOrder()
        assertThat(relatedGridRows(3, 2)).containsExactly(listOf(0, 1), listOf(2)).inOrder()
    }

    @Test
    fun `one column is one row per item`() {
        assertThat(relatedGridRows(3, 1)).containsExactly(listOf(0), listOf(1), listOf(2)).inOrder()
    }

    @Test
    fun `no items make no rows`() {
        assertThat(relatedGridRows(0, 3)).isEmpty()
    }

    @Test
    fun `a phone keeps the plain list until cards per row asks for more than one`() {
        val auto = feedGridLayoutFor(360.dp)
        val two = feedGridLayoutFor(360.dp, HomeFeedColumns.TWO)
        val three = feedGridLayoutFor(360.dp, HomeFeedColumns.THREE)

        assertThat(relatedListUsesGrid(false, PlayerRelatedCardStyle.FULL_WIDTH, auto)).isFalse()
        assertThat(relatedListUsesGrid(false, PlayerRelatedCardStyle.FULL_WIDTH, two)).isTrue()
        assertThat(relatedListUsesGrid(false, PlayerRelatedCardStyle.FULL_WIDTH, three)).isTrue()
    }

    @Test
    fun `the compact related style stays one row per video on a phone`() {
        val two = feedGridLayoutFor(360.dp, HomeFeedColumns.TWO)

        assertThat(relatedListUsesGrid(false, PlayerRelatedCardStyle.COMPACT, two)).isFalse()
    }

    @Test
    fun `the medium layout always lays out as a grid`() {
        assertThat(relatedListUsesGrid(true, PlayerRelatedCardStyle.COMPACT, feedGridLayoutFor(745.dp))).isTrue()
        assertThat(relatedListUsesGrid(true, PlayerRelatedCardStyle.FULL_WIDTH, feedGridLayoutFor(745.dp))).isTrue()
    }
}
