package io.github.aedev.flow.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the one grid decision Home, Subscriptions, Categories and Search share: a compact window is
 * a single edge-to-edge column, and every wider window is filled by the platform's own adaptive
 * derivation rather than by a table of widths.
 */
class FeedGridLayoutTest {
    private fun columnsAt(width: Dp) = feedGridLayoutFor(width).columns

    @Test
    fun `a compact window is one edge to edge column`() {
        listOf(0.dp, 360.dp, 411.dp, 480.dp, 599.dp).forEach { width ->
            val layout = feedGridLayoutFor(width)

            assertThat(layout.columns).isEqualTo(1)
            assertThat(layout.cells).isEqualTo(GridCells.Fixed(1))
            assertThat(layout.contentPadding).isEqualTo(0.dp)
        }
    }

    @Test
    fun `a medium window gains a second column and gutters`() {
        val layout = feedGridLayoutFor(600.dp)

        assertThat(layout.columns).isEqualTo(2)
        assertThat(layout.contentPadding).isEqualTo(16.dp)
    }

    @Test
    fun `wide windows let the platform derive the count`() {
        listOf(600.dp, 840.dp, 1200.dp).forEach { width ->
            assertThat(feedGridLayoutFor(width).cells).isInstanceOf(GridCells.Adaptive::class.java)
        }
    }

    @Test
    fun `the counts the width tables produced survive at their own breakpoints`() {
        assertThat(columnsAt(700.dp)).isEqualTo(2)
        assertThat(columnsAt(900.dp)).isEqualTo(3)
        assertThat(columnsAt(1200.dp)).isEqualTo(4)
    }

    @Test
    fun `a wider window never renders fewer columns`() {
        var previous = 0
        var width = 200
        while (width <= 2400) {
            val columns = columnsAt(width.dp)
            assertThat(columns).isAtLeast(previous)
            previous = columns
            width++
        }
    }

    @Test
    fun `a very wide window keeps filling with cards instead of stretching four of them`() {
        assertThat(columnsAt(1600.dp)).isEqualTo(5)
        assertThat(columnsAt(2000.dp)).isEqualTo(7)
    }

    @Test
    fun `the large window band keeps its wider gutters`() {
        val layout = feedGridLayoutFor(1200.dp)

        assertThat(layout.contentPadding).isEqualTo(24.dp)
        assertThat(layout.cardSpacing).isEqualTo(16.dp)
    }
}
