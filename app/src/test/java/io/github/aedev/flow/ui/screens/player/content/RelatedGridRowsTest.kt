package io.github.aedev.flow.ui.screens.player.content

import com.google.common.truth.Truth.assertThat
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
}
