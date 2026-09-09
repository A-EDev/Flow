package io.github.aedev.flow.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pins the feed grid bands shared by Home and Subscriptions: columns, padding and spacing at every breakpoint. */
class FeedGridLayoutTest {
    private fun assertBand(
        widths: List<Dp>,
        expected: FeedGridLayout,
    ) {
        widths.forEach { width ->
            assertThat(feedGridLayoutFor(width)).isEqualTo(expected)
        }
    }

    @Test
    fun `narrow phones get one edge to edge column`() {
        assertBand(listOf(0.dp, 360.dp, 479.dp), FeedGridLayout(columns = 1, contentPadding = 0.dp, cardSpacing = 12.dp))
    }

    @Test
    fun `wide phones keep one column but gain padding`() {
        assertBand(listOf(480.dp, 599.dp, 699.dp), FeedGridLayout(columns = 1, contentPadding = 12.dp, cardSpacing = 14.dp))
    }

    @Test
    fun `two columns from 700dp`() {
        assertBand(listOf(700.dp, 899.dp), FeedGridLayout(columns = 2, contentPadding = 16.dp, cardSpacing = 12.dp))
    }

    @Test
    fun `three columns from 900dp`() {
        assertBand(listOf(900.dp, 1199.dp), FeedGridLayout(columns = 3, contentPadding = 20.dp, cardSpacing = 14.dp))
    }

    @Test
    fun `four columns from 1200dp`() {
        assertBand(listOf(1200.dp, 2000.dp), FeedGridLayout(columns = 4, contentPadding = 24.dp, cardSpacing = 16.dp))
    }

    @Test
    fun `every breakpoint is inclusive at its lower edge`() {
        listOf(480.dp, 700.dp, 900.dp, 1200.dp).forEach { edge ->
            assertThat(feedGridLayoutFor(edge)).isNotEqualTo(feedGridLayoutFor(edge - 1.dp))
        }
    }
}
