package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaletteColorsTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `every palette in the picker has a seed and every seed is in the picker`() {
        val catalogued = ThemeCatalog.palettes.map { it.mode }.filterNot { it == ThemeMode.MATERIAL_YOU }
        assertThat(catalogued).hasSize(26)
        assertThat(catalogued.map { FlowPalettes.forMode(it).id }.toSet()).hasSize(26)
    }

    @Test
    fun `surface steps follow Flow Desktop's expansion`() {
        val light = FlowPalettes.default.colorsFor(ThemeVariant.LIGHT)
        assertThat(light.surfaceContainer).isEqualTo(light.surface)
        assertThat(light.surfaceContainerHigh).isEqualTo(mixColors(light.onSurface, light.surface, 0.07f))
        assertThat(light.surfaceContainerHighest).isEqualTo(mixColors(light.onSurface, light.surface, 0.11f))
        assertThat(light.surfaceContainerLow).isEqualTo(mixColors(light.surface, light.background, 0.58f))

        val dark = FlowPalettes.default.colorsFor(ThemeVariant.DARK)
        assertThat(dark.surfaceContainerHigh).isEqualTo(mixColors(dark.onSurface, dark.surface, 0.09f))
        assertThat(dark.surfaceContainerLow).isEqualTo(mixColors(dark.surface, dark.background, 0.72f))
    }

    @Test
    fun `amoled keeps the dark accents on desktop's black ladder`() {
        val dark = FlowPalettes.default.colorsFor(ThemeVariant.DARK)
        val amoled = FlowPalettes.default.colorsFor(ThemeVariant.AMOLED)
        assertThat(amoled.primary).isEqualTo(dark.primary)
        assertThat(amoled.onSurface).isEqualTo(dark.onSurface)
        assertThat(amoled.background).isEqualTo(Color.Black)
        assertThat(amoled.surface).isEqualTo(Color(0xFF080808))
        assertThat(amoled.surfaceContainerHighest).isEqualTo(Color(0xFF1C1C1C))
    }

    @Test
    fun `monochrome dark and amoled are told apart`() {
        val dark = FlowPalettes.forMode(ThemeMode.MONOCHROME).colorsFor(ThemeVariant.DARK)
        val amoled = FlowPalettes.forMode(ThemeMode.MONOCHROME).colorsFor(ThemeVariant.AMOLED)
        assertThat(dark.background).isNotEqualTo(amoled.background)
        assertThat(dark.surface).isNotEqualTo(amoled.surface)
        assertThat(dark.surfaceContainerHigh).isNotEqualTo(amoled.surfaceContainerHigh)
    }

    @Test
    fun `rows stand apart from the page in every palette and style`() {
        ThemeCatalog.palettes.filterNot { it.mode == ThemeMode.MATERIAL_YOU }.forEach { entry ->
            ThemeVariant.entries.forEach { variant ->
                val colors = FlowPalettes.forMode(entry.mode).colorsFor(variant)
                assertThat(colors.surfaceContainerHigh).isNotEqualTo(colors.background)
            }
        }
    }

    @Test
    fun `the palettes Android keeps meet contrast minimums in both styles`() {
        listOf(
            ThemeMode.OCEAN_BLUE,
            ThemeMode.GUNMETAL,
            ThemeMode.COSMIC_VOID,
            ThemeMode.CYBERPUNK,
            ThemeMode.ROYAL_GOLD,
            ThemeMode.CREAM_LIGHT,
        ).forEach { mode ->
            listOf(ThemeVariant.LIGHT, ThemeVariant.DARK).forEach { variant ->
                val c = FlowPalettes.forMode(mode).colorsFor(variant)
                assertThat(contrast(c.primary, c.background)).isAtLeast(3f)
                assertThat(contrast(c.onPrimary, c.primary)).isAtLeast(4.5f)
                assertThat(contrast(c.onSurface, c.surface)).isAtLeast(4.5f)
                assertThat(contrast(c.onSurfaceVariant, c.surface)).isAtLeast(4.5f)
            }
        }
    }

    @Test
    fun `the Material scheme carries the thirteen roles through unchanged`() {
        val colors = FlowPalettes.forMode(ThemeMode.CATPPUCCIN).colorsFor(ThemeVariant.DARK)
        val scheme = colors.toColorScheme(ThemeVariant.DARK)
        assertThat(scheme.primary).isEqualTo(colors.primary)
        assertThat(scheme.background).isEqualTo(colors.background)
        assertThat(scheme.surfaceContainerHigh).isEqualTo(colors.surfaceContainerHigh)
        assertThat(scheme.outlineVariant).isEqualTo(colors.outline)
        assertThat(scheme.onSurfaceVariant).isEqualTo(colors.onSurfaceVariant)
    }
}
