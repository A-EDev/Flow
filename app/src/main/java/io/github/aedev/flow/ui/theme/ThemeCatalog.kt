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
    /**
     * Every built-in palette in the picker, in display order: Flow Desktop's catalogue with Material You
     * after Flow Default, then the palettes only Android has so far. System Default and custom themes
     * are not palettes and are offered separately.
     */
    val palettes: List<ThemeCatalogEntry> =
        listOf(
            ThemeCatalogEntry(ThemeMode.DARK, R.string.theme_name_classic_dark, R.string.theme_desc_classic_dark),
            ThemeCatalogEntry(ThemeMode.MATERIAL_YOU, R.string.theme_name_material_you, R.string.theme_desc_material_you),
            ThemeCatalogEntry(ThemeMode.MONOCHROME, R.string.theme_name_monochrome, R.string.theme_desc_monochrome),
            ThemeCatalogEntry(ThemeMode.CATPPUCCIN, R.string.theme_name_catppuccin, R.string.theme_desc_catppuccin),
            ThemeCatalogEntry(ThemeMode.GREEN_APPLE, R.string.theme_name_green_apple, R.string.theme_desc_green_apple),
            ThemeCatalogEntry(ThemeMode.LAVENDER_MIST, R.string.theme_name_lavender, R.string.theme_desc_lavender),
            ThemeCatalogEntry(ThemeMode.NORDIC_HORIZON, R.string.theme_name_nordic, R.string.theme_desc_nordic),
            ThemeCatalogEntry(ThemeMode.TAKO, R.string.theme_name_tako, R.string.theme_desc_tako),
            ThemeCatalogEntry(ThemeMode.YIN_YANG, R.string.theme_name_yin_yang, R.string.theme_desc_yin_yang),
            ThemeCatalogEntry(
                ThemeMode.STRAWBERRY_DAIQUIRI,
                R.string.theme_name_strawberry_daiquiri,
                R.string.theme_desc_strawberry_daiquiri,
            ),
            ThemeCatalogEntry(ThemeMode.KANAGAWA, R.string.theme_name_kanagawa, R.string.theme_desc_kanagawa),
            ThemeCatalogEntry(ThemeMode.TOKYO_NIGHT, R.string.theme_name_tokyo_night, R.string.theme_desc_tokyo_night),
            ThemeCatalogEntry(ThemeMode.ROSE_PINE, R.string.theme_name_rose_pine, R.string.theme_desc_rose_pine),
            ThemeCatalogEntry(ThemeMode.EVERFOREST, R.string.theme_name_everforest, R.string.theme_desc_everforest),
            ThemeCatalogEntry(ThemeMode.GRUVBOX, R.string.theme_name_gruvbox, R.string.theme_desc_gruvbox),
            ThemeCatalogEntry(ThemeMode.DRACULA, R.string.theme_name_dracula, R.string.theme_desc_dracula),
            ThemeCatalogEntry(ThemeMode.SOLARIZED, R.string.theme_name_solarized, R.string.theme_desc_solarized),
            ThemeCatalogEntry(ThemeMode.TIDE, R.string.theme_name_tide, R.string.theme_desc_tide),
            ThemeCatalogEntry(ThemeMode.SAGE, R.string.theme_name_sage, R.string.theme_desc_sage),
            ThemeCatalogEntry(ThemeMode.CAFFEINE, R.string.theme_name_caffeine, R.string.theme_desc_caffeine),
            ThemeCatalogEntry(ThemeMode.CLAUDE, R.string.theme_name_claude, R.string.theme_desc_claude),
            ThemeCatalogEntry(ThemeMode.OCEAN_BLUE, R.string.theme_name_deep_ocean, R.string.theme_desc_deep_ocean),
            ThemeCatalogEntry(ThemeMode.GUNMETAL, R.string.theme_name_gunmetal, R.string.theme_desc_gunmetal),
            ThemeCatalogEntry(ThemeMode.COSMIC_VOID, R.string.theme_name_cosmic_void, R.string.theme_desc_cosmic_void),
            ThemeCatalogEntry(ThemeMode.CYBERPUNK, R.string.theme_name_cyberpunk, R.string.theme_desc_cyberpunk),
            ThemeCatalogEntry(ThemeMode.ROYAL_GOLD, R.string.theme_name_royal_gold, R.string.theme_desc_royal_gold),
            ThemeCatalogEntry(ThemeMode.CREAM_LIGHT, R.string.theme_name_cream_paper, R.string.theme_desc_cream_paper),
        )

    @StringRes
    fun nameRes(mode: ThemeMode): Int =
        when (mode) {
            ThemeMode.SYSTEM -> R.string.theme_name_system_default
            ThemeMode.LIGHT -> R.string.theme_name_pure_light
            ThemeMode.OLED -> R.string.theme_name_true_black
            ThemeMode.CUSTOM -> R.string.theme_name_custom
            else -> palettes.first { it.mode == mode }.nameRes
        }
}
