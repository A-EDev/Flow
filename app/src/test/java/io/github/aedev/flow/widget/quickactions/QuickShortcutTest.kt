package io.github.aedev.flow.widget.quickactions

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickShortcutTest {
    @Test
    fun nothingStoredMeansTheDefaultSet() {
        assertEquals(QuickShortcut.DEFAULT, QuickShortcut.decode(null))
    }

    @Test
    fun storedChoicesRoundTripAndDropUnknownKeys() {
        val chosen = listOf(QuickShortcut.SHORTS, QuickShortcut.LIBRARY)
        assertEquals(chosen, QuickShortcut.decode(QuickShortcut.encode(chosen)))
        assertEquals(listOf(QuickShortcut.MUSIC), QuickShortcut.decode("gone,music,music"))
    }

    @Test
    fun atMostFourAreKept() {
        assertEquals(QuickShortcut.MAX, QuickShortcut.decode(QuickShortcut.entries.joinToString(",") { it.key }).size)
    }

    @Test
    fun theRowHoldsAsManyShortcutsAsItsWidthAllows() {
        assertEquals(0, shortcutsThatFit(150.dp))
        assertEquals(1, shortcutsThatFit(185.dp))
        assertEquals(3, shortcutsThatFit(300.dp))
        assertEquals(QuickShortcut.MAX, shortcutsThatFit(600.dp))
    }
}
