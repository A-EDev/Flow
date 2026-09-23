package io.github.aedev.flow.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.aedev.flow.R

/** A palette the user can pick, with its display name and one-line description. */
@Immutable
data class ThemeCatalogEntry(
    val mode: ThemeMode,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
)

/**
 * The one place a [ThemeMode] gets its name. The phone theme picker, the settings summaries and the
 * TV appearance pane all read it, so a palette is never called two different things.
 */
object ThemeCatalog {
    /** Every palette offered in the picker, in display order. System Default is not a palette. */
    val palettes: List<ThemeCatalogEntry> =
        listOf(
            ThemeCatalogEntry(ThemeMode.DARK, R.string.theme_name_classic_dark, R.string.theme_desc_classic_dark),
            ThemeCatalogEntry(ThemeMode.MATERIAL_YOU, R.string.theme_name_material_you, R.string.theme_desc_material_you),
            ThemeCatalogEntry(ThemeMode.MONOCHROME, R.string.theme_name_monochrome, R.string.theme_desc_monochrome),
            ThemeCatalogEntry(ThemeMode.MIDNIGHT_BLACK, R.string.theme_name_midnight, R.string.theme_desc_midnight),
            ThemeCatalogEntry(ThemeMode.OCEAN_BLUE, R.string.theme_name_deep_ocean, R.string.theme_desc_deep_ocean),
            ThemeCatalogEntry(ThemeMode.FOREST_GREEN, R.string.theme_name_forest, R.string.theme_desc_forest),
            ThemeCatalogEntry(ThemeMode.LAVENDER_MIST, R.string.theme_name_lavender, R.string.theme_desc_lavender),
            ThemeCatalogEntry(ThemeMode.SUNSET_ORANGE, R.string.theme_name_sunset, R.string.theme_desc_sunset),
            ThemeCatalogEntry(ThemeMode.PURPLE_NEBULA, R.string.theme_name_nebula, R.string.theme_desc_nebula),
            ThemeCatalogEntry(ThemeMode.ROSE_GOLD, R.string.theme_name_rose_gold, R.string.theme_desc_rose_gold),
            ThemeCatalogEntry(ThemeMode.ARCTIC_ICE, R.string.theme_name_arctic, R.string.theme_desc_arctic),
            ThemeCatalogEntry(ThemeMode.MINTY_FRESH, R.string.theme_name_mint_night, R.string.theme_desc_mint_night),
            ThemeCatalogEntry(ThemeMode.CRIMSON_RED, R.string.theme_name_crimson, R.string.theme_desc_crimson),
            ThemeCatalogEntry(ThemeMode.COSMIC_VOID, R.string.theme_name_cosmic_void, R.string.theme_desc_cosmic_void),
            ThemeCatalogEntry(ThemeMode.SOLAR_FLARE, R.string.theme_name_solar_flare, R.string.theme_desc_solar_flare),
            ThemeCatalogEntry(ThemeMode.CYBERPUNK, R.string.theme_name_cyberpunk, R.string.theme_desc_cyberpunk),
            ThemeCatalogEntry(ThemeMode.ROYAL_GOLD, R.string.theme_name_royal_gold, R.string.theme_desc_royal_gold),
            ThemeCatalogEntry(ThemeMode.NORDIC_HORIZON, R.string.theme_name_nordic, R.string.theme_desc_nordic),
            ThemeCatalogEntry(ThemeMode.ESPRESSO, R.string.theme_name_espresso, R.string.theme_desc_espresso),
            ThemeCatalogEntry(ThemeMode.GUNMETAL, R.string.theme_name_gunmetal, R.string.theme_desc_gunmetal),
            ThemeCatalogEntry(ThemeMode.MINT_LIGHT, R.string.theme_name_mint_fresh, R.string.theme_desc_mint_fresh),
            ThemeCatalogEntry(ThemeMode.ROSE_LIGHT, R.string.theme_name_rose_petal, R.string.theme_desc_rose_petal),
            ThemeCatalogEntry(ThemeMode.SKY_LIGHT, R.string.theme_name_sky_blue, R.string.theme_desc_sky_blue),
            ThemeCatalogEntry(ThemeMode.CREAM_LIGHT, R.string.theme_name_cream_paper, R.string.theme_desc_cream_paper),
            ThemeCatalogEntry(ThemeMode.CUSTOM, R.string.theme_name_custom, R.string.theme_desc_custom),
        )

    @StringRes
    fun nameRes(mode: ThemeMode): Int =
        when (mode) {
            ThemeMode.SYSTEM -> R.string.theme_name_system_default
            ThemeMode.LIGHT -> R.string.theme_name_pure_light
            ThemeMode.OLED -> R.string.theme_name_true_black
            else -> palettes.first { it.mode == mode }.nameRes
        }
}
