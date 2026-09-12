package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.theme.CustomColorRole
import io.github.aedev.flow.ui.theme.CustomThemeColors
import io.github.aedev.flow.ui.theme.CustomThemePalettes
import io.github.aedev.flow.ui.theme.ThemeVariant
import org.junit.Test

class CustomThemePalettePersistenceTest {
    @Test
    fun `stored palettes round trip through the codec`() {
        val palettes =
            CustomThemePalettes()
                .withPalette(
                    ThemeVariant.DARK,
                    CustomThemeColors.default(ThemeVariant.DARK).withColor(CustomColorRole.PRIMARY, 0xFF00FF00),
                )

        val restored = decodeCustomThemePalettes(raw = encodeCustomThemePalettes(palettes), legacyRaw = null)

        assertThat(restored).isEqualTo(palettes)
    }

    @Test
    fun `a palette written by the previous gson reader decodes onto role keys`() {
        val raw =
            """{"light":{"values":{"PRIMARY":4278255360,"ON_PRIMARY":4294967295}},""" +
                """"dark":{"values":{"PRIMARY":4278190335}},"amoled":{"values":{}}}"""

        val restored = decodeCustomThemePalettes(raw = raw, legacyRaw = null)

        assertThat(restored.light.values.keys).containsExactly(CustomColorRole.PRIMARY, CustomColorRole.ON_PRIMARY)
        assertThat(restored.light.colorOf(CustomColorRole.PRIMARY)).isEqualTo(0xFF00FF00)
        assertThat(restored.dark.colorOf(CustomColorRole.PRIMARY)).isEqualTo(0xFF0000FF)
    }

    @Test
    fun `a role the app no longer knows drops out instead of losing the palette`() {
        val raw = """{"dark":{"values":{"PRIMARY":4278190335,"RETIRED_ROLE":1}}}"""

        val restored = decodeCustomThemePalettes(raw = raw, legacyRaw = null)

        assertThat(restored.dark.values.keys).containsExactly(CustomColorRole.PRIMARY)
    }

    @Test
    fun `legacy comma separated colours still migrate onto the dark palette`() {
        val legacy = (1L..16L).joinToString(",")

        val restored = decodeCustomThemePalettes(raw = null, legacyRaw = legacy)

        assertThat(restored.dark.colorOf(CustomColorRole.PRIMARY)).isEqualTo(1L)
        assertThat(restored.dark.colorOf(CustomColorRole.SCRIM)).isEqualTo(16L)
    }

    @Test
    fun `unreadable json falls back to the defaults`() {
        val restored = decodeCustomThemePalettes(raw = "{ not json", legacyRaw = null)

        assertThat(restored).isEqualTo(CustomThemePalettes())
    }
}
