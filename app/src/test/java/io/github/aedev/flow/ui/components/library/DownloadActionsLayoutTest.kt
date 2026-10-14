package io.github.aedev.flow.ui.components.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadActionsLayoutTest {
    private fun below(
        width: Int,
        fontScale: Float = 1f,
        actions: Int = 2,
    ) = downloadActionsBelowText(width.dp, fontScale, actions, actionWidth = 48.dp)

    @Test
    fun `phone widths put the buttons under the title`() {
        assertTrue(below(320))
        assertTrue(below(360))
        assertTrue(below(411))
    }

    @Test
    fun `a wide row keeps the buttons beside the title`() {
        assertFalse(below(600))
        assertFalse(below(840))
    }

    @Test
    fun `a large font moves the buttons under the title sooner`() {
        assertFalse(below(500))
        assertTrue(below(500, fontScale = 1.5f))
    }

    @Test
    fun `a single button needs less room than two`() {
        assertTrue(below(440, actions = 2))
        assertFalse(below(440, actions = 1))
    }
}
