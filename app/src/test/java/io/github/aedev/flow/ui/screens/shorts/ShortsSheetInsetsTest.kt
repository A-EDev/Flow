package io.github.aedev.flow.ui.screens.shorts

import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortsSheetInsetsTest {
    private fun portraitState() =
        ShortsSheetInsetState(TestScope()).apply {
            containerHeightPx = 2000f
            shrinkEnabled = true
        }

    @Test
    fun `a sheet reserves exactly what it covers`() {
        assertEquals(400f, shortsSheetReservedPx(sheetHeightPx = 400f, containerHeightPx = 2000f), 0f)
    }

    @Test
    fun `an oversized sheet cannot squeeze the reel past the cap`() {
        val reserved = shortsSheetReservedPx(sheetHeightPx = 1900f, containerHeightPx = 2000f)
        assertEquals(2000f * SHORTS_SHEET_HEIGHT_FRACTION, reserved, 0f)
    }

    @Test
    fun `nothing is reserved before the screen has been measured`() {
        assertEquals(0f, shortsSheetReservedPx(sheetHeightPx = 400f, containerHeightPx = 0f), 0f)
    }

    @Test
    fun `a sheet dragged below its collapsed height reserves nothing`() {
        assertEquals(0f, shortsSheetReservedPx(sheetHeightPx = -12f, containerHeightPx = 2000f), 0f)
    }

    @Test
    fun `landscape keeps the reel at full height however tall the sheet is`() {
        val state =
            ShortsSheetInsetState(TestScope()).apply {
                containerHeightPx = 1000f
                shrinkEnabled = false
            }

        state.follow(900f)

        assertEquals(0f, state.reservedPx, 0f)
    }

    @Test
    fun `following a sheet tracks its height and clamps it`() {
        val state = portraitState()

        state.follow(300f)
        assertEquals(300f, state.reservedPx, 0f)

        state.follow(1900f)
        assertEquals(2000f * SHORTS_SHEET_HEIGHT_FRACTION, state.reservedPx, 0f)
    }

    @Test
    fun `a sheet is capped to the share of the screen the reel does not need`() {
        val state = portraitState()

        assertEquals(2000f * SHORTS_SHEET_HEIGHT_FRACTION, state.sheetMaxHeightPx, 0f)

        state.shrinkEnabled = false
        assertEquals(2000f * SHORTS_SHEET_LANDSCAPE_HEIGHT_FRACTION, state.sheetMaxHeightPx, 0f)
    }
}
