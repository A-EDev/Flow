package io.github.aedev.flow.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `every retired palette lands on a palette that still exists`() {
        assertEquals(13, ThemeMode.retiredNames.size)
        ThemeMode.retiredNames.forEach { name ->
            val mode = ThemeMode.fromStored(name)
            assertNotNull(name, mode)
            assertTrue(name, ThemeCatalog.palettes.any { it.mode == mode })
        }
    }

    @Test
    fun `retired light and black palettes keep their style`() {
        assertEquals(ThemeVariant.LIGHT, ThemeMode.storedDefaultVariant("ROSE_LIGHT"))
        assertEquals(ThemeMode.STRAWBERRY_DAIQUIRI, ThemeMode.fromStored("ROSE_LIGHT"))
        assertEquals(ThemeVariant.AMOLED, ThemeMode.storedDefaultVariant("MIDNIGHT_BLACK"))
        assertEquals(ThemeMode.CAFFEINE, ThemeMode.fromStored("ESPRESSO"))
    }

    @Test
    fun `current names read back as themselves and unknown names read as nothing`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.name)) }
        assertNull(ThemeMode.fromStored("NOT_A_THEME"))
        assertNull(ThemeMode.fromStored(null))
    }
}
