package io.github.aedev.flow.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekbarPaddingPolicyTest {
    @Test
    fun `portrait mode defaults to full width`() {
        assertEquals(
            SeekbarPaddingMode.FULL_WIDTH,
            resolvePortraitSeekbarPaddingMode(storedMode = null)
        )
    }

    @Test
    fun `legacy portrait default maps to spaced`() {
        assertEquals(
            SeekbarPaddingMode.SPACED,
            resolvePortraitSeekbarPaddingMode(SeekbarPaddingMode.DEFAULT.name)
        )
    }

    @Test
    fun `full width always resolves to zero`() {
        assertEquals(
            0,
            resolveSeekbarHorizontalPaddingDp(
                mode = SeekbarPaddingMode.FULL_WIDTH,
                customPaddingDp = 40,
                defaultPaddingDp = 16,
                maxPaddingDp = 64
            )
        )
    }

    @Test
    fun `spaced mode uses orientation default`() {
        assertEquals(
            16,
            resolveSeekbarHorizontalPaddingDp(
                mode = SeekbarPaddingMode.SPACED,
                customPaddingDp = 40,
                defaultPaddingDp = 16,
                maxPaddingDp = 64
            )
        )
    }

    @Test
    fun `custom mode clamps padding to orientation maximum`() {
        assertEquals(
            64,
            resolveSeekbarHorizontalPaddingDp(
                mode = SeekbarPaddingMode.CUSTOM,
                customPaddingDp = 120,
                defaultPaddingDp = 16,
                maxPaddingDp = 64
            )
        )
    }
}
