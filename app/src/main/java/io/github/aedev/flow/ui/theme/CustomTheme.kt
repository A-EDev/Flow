package io.github.aedev.flow.ui.theme

import androidx.compose.runtime.Immutable

/** The three styles every theme has. */
enum class ThemeVariant {
    LIGHT,
    DARK,
    AMOLED,
}

/**
 * A user's own theme: a name and all thirteen roles for each style, the same shape as Flow
 * Desktop's `CustomThemeDefinition`, so it can be exported from one app and imported in the other.
 */
@Immutable
data class CustomTheme(
    val id: String,
    val name: String,
    val light: PaletteColors,
    val dark: PaletteColors,
    val amoled: PaletteColors,
) {
    fun colorsFor(variant: ThemeVariant): PaletteColors =
        when (variant) {
            ThemeVariant.LIGHT -> light
            ThemeVariant.DARK -> dark
            ThemeVariant.AMOLED -> amoled
        }

    fun withColors(
        variant: ThemeVariant,
        colors: PaletteColors,
    ): CustomTheme =
        when (variant) {
            ThemeVariant.LIGHT -> copy(light = colors)
            ThemeVariant.DARK -> copy(dark = colors)
            ThemeVariant.AMOLED -> copy(amoled = colors)
        }

    companion object {
        /** Flow Desktop keeps at most this many, and so does Android, so a full set moves both ways. */
        const val MAX_COUNT = 24
        const val MAX_NAME_LENGTH = 48
        const val ID_PREFIX = "custom-"

        /** A new theme named [name], starting from every style of [base]. */
        fun from(
            id: String,
            name: String,
            base: PalettePair = FlowPalettes.default,
        ): CustomTheme =
            CustomTheme(
                id = id,
                name = name.trim().take(MAX_NAME_LENGTH),
                light = base.colorsFor(ThemeVariant.LIGHT),
                dark = base.colorsFor(ThemeVariant.DARK),
                amoled = base.colorsFor(ThemeVariant.AMOLED),
            )
    }
}
