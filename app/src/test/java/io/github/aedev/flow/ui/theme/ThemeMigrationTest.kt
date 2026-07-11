package io.github.aedev.flow.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeMigrationTest {
    @Test
    fun `legacy classic modes map to one family and preserve their variant`() {
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.canonicalFamily())
        assertEquals(ThemeVariant.LIGHT, ThemeMode.LIGHT.defaultVariant())

        assertEquals(ThemeMode.DARK, ThemeMode.DARK.canonicalFamily())
        assertEquals(ThemeVariant.DARK, ThemeMode.DARK.defaultVariant())

        assertEquals(ThemeMode.DARK, ThemeMode.OLED.canonicalFamily())
        assertEquals(ThemeVariant.AMOLED, ThemeMode.OLED.defaultVariant())
    }

    @Test
    fun `non-classic theme families remain unchanged`() {
        ThemeMode.entries
            .filterNot { it == ThemeMode.LIGHT || it == ThemeMode.DARK || it == ThemeMode.OLED }
            .forEach { mode -> assertEquals(mode, mode.canonicalFamily()) }
    }
}
