package io.github.aedev.flow.widget.nowplaying

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingLayoutTest {
    @Test
    fun eachDeclaredSizePicksItsOwnLayout() {
        NowPlayingLayout.entries.forEach { assertEquals(it, NowPlayingLayout.forSize(it.size)) }
    }

    @Test
    fun realWidgetSizesPickTheLargestLayoutThatFits() {
        assertEquals(NowPlayingLayout.SMALL, NowPlayingLayout.forSize(DpSize(150.dp, 60.dp)))
        assertEquals(NowPlayingLayout.STRIP, NowPlayingLayout.forSize(DpSize(340.dp, 70.dp)))
        assertEquals(NowPlayingLayout.SQUARE, NowPlayingLayout.forSize(DpSize(170.dp, 170.dp)))
        assertEquals(NowPlayingLayout.CARD, NowPlayingLayout.forSize(DpSize(340.dp, 176.dp)))
        assertEquals(NowPlayingLayout.POSTER, NowPlayingLayout.forSize(DpSize(260.dp, 340.dp)))
        assertEquals(NowPlayingLayout.SMALL, NowPlayingLayout.forSize(DpSize(40.dp, 40.dp)))
        assertEquals(NowPlayingLayout.CARD, NowPlayingLayout.forSize(DpSize(660.dp, 240.dp)))
    }

    @Test
    fun artworkLoadsAtTheLargestLayoutDrawnAndNoBigger() {
        // The card's artwork fills its height, less the width its controls need.
        assertEquals(152f, NowPlayingLayout.artworkDpFor(listOf(DpSize(400.dp, 176.dp))), 0.01f)
        assertEquals(124f, NowPlayingLayout.artworkDpFor(listOf(DpSize(340.dp, 176.dp))), 0.01f)
        assertEquals(236f, NowPlayingLayout.artworkDpFor(listOf(DpSize(260.dp, 400.dp))), 0.01f)
        assertEquals(256f, NowPlayingLayout.artworkDpFor(listOf(DpSize(500.dp, 600.dp))), 0.01f)
        assertEquals(NowPlayingLayout.STRIP_ART_DP, NowPlayingLayout.artworkDpFor(listOf(DpSize(150.dp, 60.dp))), 0.01f)
    }
}
