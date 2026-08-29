package io.github.aedev.flow.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowDimensionsTest {
    @Test
    fun `content rhythm uses larger section gaps than item gaps`() {
        assertTrue(Dimensions.ItemSpacing < Dimensions.SectionSpacing)
        assertEquals(16f, Dimensions.ContentPaddingHorizontal.value, 0.001f)
    }

    @Test
    fun `media surfaces use rounded but distinct corner scales`() {
        assertEquals(12f, Dimensions.ThumbnailCornerRadius.value, 0.001f)
        assertEquals(20f, Dimensions.CardCornerRadius.value, 0.001f)
        assertTrue(Dimensions.CardCornerRadius > Dimensions.ThumbnailCornerRadius)
    }
}
