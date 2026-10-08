package io.github.aedev.flow.data.local

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.FlowPalettes
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import org.junit.Test

class CustomThemeCodecTest {
    private val theme = CustomTheme.from("custom-test", "Ocean", FlowPalettes.forMode(ThemeMode.TIDE))

    /** A theme as Flow Desktop's `createCustomTheme` stores it: Flow Default cloned, derived steps as `color-mix`. */
    private val desktopTheme =
        """
        {
          "id": "custom-1719000000000",
          "name": "From desktop",
          "custom": true,
          "variants": {
            "light": ${desktopVariant("#ffffff", "#f3f3f3", "#111111", "58", "7", "11")},
            "dark": ${desktopVariant("#0f0f0f", "#1d1d1d", "#f4f4f4", "72", "9", "14")},
            "amoled": {"primary":"#ff0000","onPrimary":"#ffffff","secondary":"#aaaaaa","background":"#000000","surface":"#080808",
              "surfaceContainerLow":"#050505","surfaceContainer":"#0c0c0c","surfaceContainerHigh":"#141414","surfaceContainerHighest":"#1c1c1c",
              "outline":"#343434","onSurface":"#f4f4f4","onSurfaceVariant":"#b8b8b8","error":"#ef5350"}
          }
        }
        """.trimIndent()

    private fun desktopVariant(
        background: String,
        surface: String,
        text: String,
        low: String,
        high: String,
        highest: String,
    ) = """{"primary":"#ff0000","onPrimary":"#ffffff","secondary":"#606060","background":"$background","surface":"$surface",
        "surfaceContainerLow":"color-mix(in srgb, $surface $low%, $background)","surfaceContainer":"$surface",
        "surfaceContainerHigh":"color-mix(in srgb, $text $high%, $surface)","surfaceContainerHighest":"color-mix(in srgb, $text $highest%, $surface)",
        "outline":"#d7d7d7","onSurface":"$text","onSurfaceVariant":"#5f5f5f","error":"#d32f2f"}"""

    @Test
    fun `a theme survives export and import`() {
        val once = CustomThemeCodec.encodeOne(theme)
        val back = CustomThemeCodec.decode(once).single()
        assertThat(back.id).isEqualTo(theme.id)
        assertThat(back.name).isEqualTo(theme.name)
        assertThat(CustomThemeCodec.encodeOne(back)).isEqualTo(once)
    }

    @Test
    fun `exports use desktop's shape and plain hex`() {
        val text = CustomThemeCodec.encodeOne(theme)
        assertThat(text).contains("\"custom\": true")
        assertThat(text).contains("\"variants\"")
        listOf("light", "dark", "amoled").forEach { assertThat(text).contains("\"$it\"") }
        assertThat(Regex("\"(#[^\"]*)\"").findAll(text).map { it.groupValues[1] }.all { Regex("#[0-9a-f]{6}").matches(it) }).isTrue()
    }

    @Test
    fun `a desktop theme with color-mix steps imports with the mixes computed`() {
        val imported = CustomThemeCodec.decode(desktopTheme).single()
        assertThat(imported.name).isEqualTo("From desktop")
        // color-mix(in srgb, #111111 7%, #f3f3f3) = 0x11 * 0.07 + 0xf3 * 0.93 = 227 = 0xe3
        assertThat(imported.light.surfaceContainerHigh.toThemeColor()).isEqualTo("#e3e3e3")
        assertThat(imported.amoled.background).isEqualTo(Color.Black)
    }

    @Test
    fun `a list imports every valid theme and drops the invalid ones, as desktop does`() {
        val valid = CustomThemeCodec.encodeOne(theme)
        val wrongPrefix = valid.replace("custom-test", "mine")
        val notCustom = valid.replace("\"custom\": true", "\"custom\": false")
        val longName = valid.replace("\"Ocean\"", "\"" + "x".repeat(CustomTheme.MAX_NAME_LENGTH + 1) + "\"")
        val missingVariant = valid.replace("\"amoled\"", "\"other\"")
        val decoded = CustomThemeCodec.decode("[$valid, $wrongPrefix, $notCustom, $longName, $missingVariant]")
        assertThat(decoded.map { it.id }).containsExactly("custom-test")
    }

    @Test
    fun `at most the desktop limit is kept`() {
        val many = (1..CustomTheme.MAX_COUNT + 5).map { theme.copy(id = "custom-$it") }
        assertThat(CustomThemeCodec.decode(CustomThemeCodec.encodeList(many))).hasSize(CustomTheme.MAX_COUNT)
    }

    @Test
    fun `garbage imports nothing`() {
        assertThat(CustomThemeCodec.decode("{ not json")).isEmpty()
        assertThat(CustomThemeCodec.decode("42")).isEmpty()
        assertThat(CustomThemeCodec.decode(null)).isEmpty()
    }

    @Test
    fun `colour parsing accepts desktop's forms and nothing else`() {
        assertThat(parseThemeColor("#FF0000")).isEqualTo(Color.Red)
        assertThat(parseThemeColor("color-mix(in srgb, #000000, #ffffff 25%)")?.toThemeColor()).isEqualTo("#404040")
        assertThat(parseThemeColor("red")).isNull()
        assertThat(parseThemeColor("#fff")).isNull()
        assertThat(parseThemeColor("#ff000080")).isNull()
        assertThat(parseThemeColor("transparent")).isNull()
    }

    @Test
    fun `a translucent colour is written as desktop's color-mix with transparent`() {
        assertThat(Color(0xD91D212B).toThemeColor()).isEqualTo("color-mix(in srgb, #1d212b 85.1%, transparent)")
        assertThat(Color(0x801D212B).toThemeColor()).isEqualTo("color-mix(in srgb, #1d212b 50.2%, transparent)")
        assertThat(Color(0x001D212B).toThemeColor()).isEqualTo("color-mix(in srgb, #1d212b 0.1%, transparent)")
        assertThat(Color(0xFF1D212B).toThemeColor()).isEqualTo("#1d212b")
    }

    @Test
    fun `desktop's transparent mix reads as the colour at that opacity`() {
        val half = parseThemeColor("color-mix(in srgb, #ff0000 50%, transparent)")
        assertThat(half?.copy(alpha = 1f)).isEqualTo(Color.Red)
        assertThat(half?.alpha).isWithin(0.002f).of(0.5f)
        assertThat(parseThemeColor("color-mix(in srgb, transparent, #ff0000 25%)")?.alpha).isWithin(0.002f).of(0.25f)
    }

    @Test
    fun `every opacity step survives a round trip with its hue`() {
        (0..255).forEach { alpha ->
            val color = Color((alpha.toLong() shl 24) or 0x3366CCL)
            val back = parseThemeColor(color.toThemeColor())
            assertThat(back?.toArgb()).isEqualTo(color.toArgb())
        }
    }

    @Test
    fun `a theme with translucent roles survives export and import`() {
        val dark =
            theme.dark.copy(
                surfaceContainer = theme.dark.surfaceContainer.copy(alpha = 0.85f),
                secondary = theme.dark.secondary.copy(alpha = 0.7f),
            )
        val translucent = theme.withColors(ThemeVariant.DARK, dark)
        val once = CustomThemeCodec.encodeOne(translucent)
        val back = CustomThemeCodec.decode(once).single()
        assertThat(back.dark.surfaceContainer.toArgb()).isEqualTo(dark.surfaceContainer.toArgb())
        assertThat(back.dark.secondary.toArgb()).isEqualTo(dark.secondary.toArgb())
        assertThat(CustomThemeCodec.encodeOne(back)).isEqualTo(once)
    }

    @Test
    fun `an older palette keeps its opacity once saved in the new format`() {
        val stored = mapOf(ThemeVariant.DARK to mapOf("SURFACE" to 0x99202020L))
        val migrated = legacyCustomTheme(stored, "custom-android-legacy", "My theme")
        val back = CustomThemeCodec.decode(CustomThemeCodec.encodeOne(migrated)).single()
        assertThat(back.dark.surface.toArgb()).isEqualTo(0x99202020L.toInt())
    }

    @Test
    fun `the per-style palette older versions stored becomes one theme`() {
        val raw =
            """{"light":{"values":{"PRIMARY":4278255360,"ON_PRIMARY":4294967295}},""" +
                """"dark":{"values":{"PRIMARY":4278190335,"OUTLINE_VARIANT":4282664004,"RETIRED_ROLE":1}},"amoled":{"values":{}}}"""
        val stored = decodeLegacyCustomPalettes(raw, legacyCsv = null)!!
        val migrated = legacyCustomTheme(stored, "custom-android-legacy", "My theme")
        assertThat(migrated.light.primary).isEqualTo(Color(0xFF00FF00))
        assertThat(migrated.dark.primary).isEqualTo(Color(0xFF0000FF))
        assertThat(migrated.dark.outline).isEqualTo(Color(0xFF444444))
        assertThat(migrated.amoled).isEqualTo(FlowPalettes.default.colorsFor(ThemeVariant.AMOLED))
        assertThat(CustomThemeCodec.decode(CustomThemeCodec.encodeOne(migrated))).hasSize(1)
    }

    @Test
    fun `the first comma separated custom palette still migrates onto the dark style`() {
        val values = (1..16).map { 0xFF000000 + it }
        val stored = decodeLegacyCustomPalettes(raw = null, legacyCsv = values.joinToString(","))!!
        val migrated = legacyCustomTheme(stored, "custom-android-legacy", "My theme")
        assertThat(migrated.dark.primary).isEqualTo(Color(0xFF000001))
        assertThat(migrated.dark.surface).isEqualTo(Color(0xFF000009))
    }

    @Test
    fun `nothing stored means no migrated theme`() {
        assertThat(decodeLegacyCustomPalettes(raw = null, legacyCsv = null)).isNull()
        assertThat(decodeLegacyCustomPalettes(raw = "{ not json", legacyCsv = null)).isNull()
    }
}
