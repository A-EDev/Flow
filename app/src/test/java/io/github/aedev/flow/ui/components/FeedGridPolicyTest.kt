package io.github.aedev.flow.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeedGridPolicyTest {
    @Test
    fun `a phone column never forms a grid`() {
        assertThat(feedCardsFormGrid(columns = 1, itemCount = 12)).isFalse()
    }

    @Test
    fun `a lone card on a wide window falls back to the list variant`() {
        assertThat(feedCardsFormGrid(columns = 3, itemCount = 1)).isFalse()
        assertThat(feedCardsFormGrid(columns = 3, itemCount = 2)).isTrue()
    }

    @Test
    fun `a phone shelf previews four rows`() {
        assertThat(feedShelfPreviewCount(columns = 1, itemCount = 20)).isEqualTo(4)
    }

    @Test
    fun `a grid shelf previews two full rows`() {
        assertThat(feedShelfPreviewCount(columns = 3, itemCount = 20)).isEqualTo(6)
        assertThat(feedShelfPreviewCount(columns = 2, itemCount = 20)).isEqualTo(4)
    }

    @Test
    fun `a lone item on a wide window keeps the list preview`() {
        assertThat(feedShelfPreviewCount(columns = 3, itemCount = 1)).isEqualTo(4)
    }

    @Test
    fun `a full run fills every row`() {
        assertThat(partialRowIndices(spansOwnRow = List(6) { false }, columns = 3)).isEmpty()
    }

    @Test
    fun `the tail a grid cannot fill takes its own rows`() {
        assertThat(partialRowIndices(spansOwnRow = List(7) { false }, columns = 3)).containsExactly(6)
        assertThat(partialRowIndices(spansOwnRow = List(8) { false }, columns = 3)).containsExactly(6, 7)
    }

    @Test
    fun `a full-width item ends the run it interrupts`() {
        // hero, two videos, a strip, four videos
        val spans = listOf(true, false, false, true, false, false, false, false)

        assertThat(partialRowIndices(spans, columns = 3)).containsExactly(1, 2, 7)
    }

    @Test
    fun `a single column never leaves a row short`() {
        assertThat(partialRowIndices(spansOwnRow = List(7) { false }, columns = 1)).isEmpty()
    }

    @Test
    fun `the trailing run is left alone while more pages may land`() {
        val spans = listOf(false, false, true, false, false)

        assertThat(partialRowIndices(spans, columns = 3, includeLastRun = false)).containsExactly(0, 1)
        assertThat(partialRowIndices(spans, columns = 3, includeLastRun = true)).containsExactly(0, 1, 3, 4)
    }

    @Test
    fun `a run of exactly one card takes its own row`() {
        assertThat(partialRowIndices(spansOwnRow = listOf(false, true, false), columns = 3))
            .containsExactly(0, 2)
    }
}
