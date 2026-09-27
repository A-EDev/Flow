package io.github.aedev.flow.widget.onrepeat

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnRepeatGridTest {
    @Test
    fun aSquareShowsFourCoversWithoutAHeader() {
        val grid = onRepeatGrid(DpSize(170.dp, 150.dp))
        assertFalse(grid.withHeader)
        assertEquals(2, grid.columns)
    }

    @Test
    fun aShortWideWidgetShowsOneRowOfFour() {
        val grid = onRepeatGrid(DpSize(340.dp, 110.dp))
        assertFalse(grid.withHeader)
        assertEquals(4, grid.columns)
        assertTrue(grid.coverSize <= 86.dp)
    }

    @Test
    fun aTallWidgetGetsTheHeaderAndColumnsByWidth() {
        val grid = onRepeatGrid(DpSize(340.dp, 260.dp))
        assertTrue(grid.withHeader)
        assertEquals(3, grid.columns)
    }
}
