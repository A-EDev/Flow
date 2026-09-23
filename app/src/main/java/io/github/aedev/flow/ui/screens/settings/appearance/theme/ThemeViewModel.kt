package io.github.aedev.flow.ui.screens.settings.appearance.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import io.github.aedev.flow.ui.theme.CustomThemeColors
import io.github.aedev.flow.ui.theme.CustomThemePalettes
import io.github.aedev.flow.ui.theme.ThemeCatalog
import io.github.aedev.flow.ui.theme.ThemeCatalogEntry
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.theme.resolveFlowColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The three colours a theme card shows, taken from the palette's resolved scheme. */
@Immutable
data class ThemeSwatch(
    val tile: Color,
    val primary: Color,
    val secondary: Color,
    val neutral: Color,
)

@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val variant: ThemeVariant = ThemeVariant.DARK,
    val palettes: CustomThemePalettes = CustomThemePalettes(),
    val systemLightMode: ThemeMode = ThemeMode.DARK,
    val systemDarkMode: ThemeMode = ThemeMode.DARK,
    val systemDarkVariant: ThemeVariant = ThemeVariant.DARK,
) {
    val followsSystem: Boolean get() = mode == ThemeMode.SYSTEM
}

/** The theme picker and the custom theme editor. Writes go straight to the same store the app theme reads. */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataManager: LocalDataManager,
    ) : SettingsViewModel() {
        val settings: StateFlow<ThemeSettings> =
            combine(
                dataManager.themeMode,
                dataManager.themeVariant,
                dataManager.customThemePalettes,
                combine(dataManager.systemLightThemeMode, dataManager.systemDarkThemeMode, ::Pair),
                dataManager.systemDarkThemeVariant,
            ) { mode, variant, palettes, (light, dark), darkVariant ->
                ThemeSettings(mode, variant, palettes, light, dark, darkVariant)
            }.asState(ThemeSettings())

        /** Palettes on offer: Material You needs Android 12's wallpaper colours. */
        val palettes: List<ThemeCatalogEntry> =
            ThemeCatalog.palettes.filter { it.mode != ThemeMode.MATERIAL_YOU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

        /** Every palette's swatch for every style, so switching style or slot never waits on a recompute. */
        val swatches: StateFlow<Map<ThemeVariant, Map<ThemeMode, ThemeSwatch>>> =
            dataManager.customThemePalettes
                .map { custom ->
                    ThemeVariant.entries.associateWith { variant ->
                        palettes.associate { entry -> entry.mode to swatchFor(entry.mode, variant, custom) }
                    }
                }.flowOn(Dispatchers.Default)
                .asState(emptyMap())

        private fun swatchFor(
            mode: ThemeMode,
            variant: ThemeVariant,
            custom: CustomThemePalettes,
        ): ThemeSwatch {
            val scheme =
                resolveFlowColorScheme(
                    context = context,
                    isSystemDark = variant != ThemeVariant.LIGHT,
                    themeMode = mode,
                    themeVariant = variant,
                    customThemePalettes = custom,
                    systemLightThemeMode = mode,
                    systemDarkThemeMode = mode,
                    systemDarkThemeVariant = variant,
                )
            return ThemeSwatch(
                tile = scheme.background,
                primary = scheme.primary,
                secondary = scheme.secondary,
                neutral = scheme.surfaceContainerHighest,
            )
        }

        fun setFollowSystem(
            enabled: Boolean,
            isSystemDark: Boolean,
        ) = write {
            val current = settings.value
            if (enabled) {
                dataManager.setThemeMode(ThemeMode.SYSTEM)
            } else {
                dataManager.setThemeMode(if (isSystemDark) current.systemDarkMode else current.systemLightMode)
                dataManager.setThemeVariant(if (isSystemDark) current.systemDarkVariant else ThemeVariant.LIGHT)
            }
        }

        fun setTheme(mode: ThemeMode) = write { dataManager.setThemeMode(mode) }

        fun setVariant(variant: ThemeVariant) = write { dataManager.setThemeVariant(variant) }

        fun setSystemLightTheme(mode: ThemeMode) = write { dataManager.setSystemLightThemeMode(mode) }

        fun setSystemDarkTheme(mode: ThemeMode) = write { dataManager.setSystemDarkThemeMode(mode) }

        fun setSystemDarkVariant(variant: ThemeVariant) = write { dataManager.setSystemDarkThemeVariant(variant) }

        fun saveCustomPalette(
            variant: ThemeVariant,
            colors: CustomThemeColors,
        ) = write { dataManager.setCustomThemePalettes(settings.value.palettes.withPalette(variant, colors)) }

        fun useCustomTheme(variant: ThemeVariant) =
            write {
                dataManager.setThemeMode(ThemeMode.CUSTOM)
                dataManager.setThemeVariant(variant)
            }
    }
