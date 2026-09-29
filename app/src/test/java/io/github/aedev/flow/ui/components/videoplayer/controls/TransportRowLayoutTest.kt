package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val PlayPauseSize = 62.dp
private val SkipSize = 48.dp

private fun contentWidth(buttons: Int) = PlayPauseSize + SkipSize * (buttons - 1)

private fun layoutFor(
    available: Dp,
    buttons: Int,
) = transportRowLayout(available, contentWidth(buttons), buttons - 1)

private fun drawnWidth(
    layout: TransportRowLayout,
    buttons: Int,
): Float = ((contentWidth(buttons) + layout.spacing * (buttons - 1)) * layout.scale).value

/**
 * Three buttons is play plus previous/next; five adds the frame-step arrows. A portrait player on a
 * phone gives the row a little over 400dp, which is where the five-button row used to run off the
 * end (#664 follow-up).
 */
class TransportRowLayoutTest {
    @Test
    fun `a row with room to spare keeps its full spacing`() {
        val layout = layoutFor(available = 700.dp, buttons = 3)

        assertThat(layout.spacing).isEqualTo(48.dp)
        assertThat(layout.scale).isEqualTo(1f)
    }

    @Test
    fun `the frame-step row closes up rather than running off a portrait player`() {
        val available = 390.dp
        val layout = layoutFor(available, buttons = 5)

        assertThat(layout.spacing.value).isLessThan(48f)
        assertThat(layout.scale).isEqualTo(1f)
        assertThat(drawnWidth(layout, 5)).isAtMost(available.value + 0.01f)
    }

    @Test
    fun `adding the frame-step arrows does not change a row that still fits`() {
        val wide = layoutFor(available = 900.dp, buttons = 5)

        assertThat(wide.spacing).isEqualTo(48.dp)
        assertThat(wide.scale).isEqualTo(1f)
    }

    @Test
    fun `a row that cannot fit even closed up is scaled down`() {
        val available = 260.dp
        val layout = layoutFor(available, buttons = 5)

        assertThat(layout.spacing).isEqualTo(12.dp)
        assertThat(layout.scale).isLessThan(1f)
        assertThat(drawnWidth(layout, 5)).isAtMost(available.value + 0.01f)
    }

    @Test
    fun `the row is never shrunk past legibility`() {
        val layout = layoutFor(available = 40.dp, buttons = 5)

        assertThat(layout.scale).isEqualTo(0.7f)
    }

    @Test
    fun `every button count fits the width it is given`() {
        for (buttons in 1..5) {
            for (available in listOf(300, 360, 390, 420, 520, 640, 900)) {
                val layout = layoutFor(available.dp, buttons)

                assertThat(drawnWidth(layout, buttons)).isAtMost(available + 0.01f)
                assertThat(layout.spacing.value).isAtLeast(if (buttons > 1) 12f else 0f)
            }
        }
    }

    @Test
    fun `a row measured before its player has a width is left alone`() {
        val layout = transportRowLayout(availableWidth = 0.dp, contentWidth = contentWidth(5), gaps = 4)

        assertThat(layout.spacing).isEqualTo(48.dp)
        assertThat(layout.scale).isEqualTo(1f)
    }

    @Test
    fun `an unbounded row takes its full spacing`() {
        val layout = transportRowLayout(availableWidth = Dp.Infinity, contentWidth = contentWidth(5), gaps = 4)

        assertThat(layout.spacing).isEqualTo(48.dp)
        assertThat(layout.scale).isEqualTo(1f)
    }
}
