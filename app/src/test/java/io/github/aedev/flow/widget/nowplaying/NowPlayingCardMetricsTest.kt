package io.github.aedev.flow.widget.nowplaying

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingCardMetricsTest {
    /** Everything in one row: inset, artwork, its gap, the controls, inset. */
    private fun rowWidth(metrics: CardMetrics): Dp {
        val art = if (metrics.art > 0.dp) metrics.art + ArtGap else 0.dp
        return metrics.inset * 2 + art + metrics.controls.width
    }

    @Test
    fun theControlsFitEveryPhoneCardWidth() {
        listOf(250, 280, 300, 320, 340, 360, 400, 520).forEach { width ->
            listOf(110, 140, 176).forEach { height ->
                val size = DpSize(width.dp, height.dp)
                assertTrue("controls overflow at $size", rowWidth(cardMetrics(size)) <= size.width)
            }
        }
    }

    @Test
    fun aTypicalPhoneCardKeepsFullSizeControlsAndShrinksTheArt() {
        val metrics = cardMetrics(DpSize(340.dp, 176.dp))
        assertEquals(124.dp, metrics.art)
        assertEquals(56.dp, metrics.controls.play)
        assertTrue(metrics.showProgress)
    }

    @Test
    fun aWideCardLetsTheArtFillItsHeight() {
        assertEquals(152.dp, cardMetrics(DpSize(520.dp, 176.dp)).art)
    }

    @Test
    fun aNarrowCardStepsTheControlsDownBeforeDroppingTheArt() {
        val metrics = cardMetrics(DpSize(260.dp, 176.dp))
        assertTrue(metrics.art >= 64.dp)
        assertTrue(metrics.controls.play < 56.dp)
    }

    @Test
    fun aShortCardDropsTheProgressRow() {
        assertFalse(cardMetrics(DpSize(340.dp, 110.dp)).showProgress)
    }
}
