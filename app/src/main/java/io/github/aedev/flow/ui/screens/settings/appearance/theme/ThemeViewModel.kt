package io.github.aedev.flow.ui.screens.settings.appearance.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.ThemeCatalog
import io.github.aedev.flow.ui.theme.ThemeCatalogEntry
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.theme.resolveFlowColorScheme
import io.github.aedev.flow.ui.theme.toColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The three colours a theme card shows, taken from the theme's resolved scheme. */
@Immutable
data class ThemeSwatch(
    val tile: Color,
    val primary: Color,
    val secondary: Color,
    val neutral: Color,
)

internal fun ColorScheme.toSwatch() =
    ThemeSwatch(tile = background, primary = primary, secondary = secondary, neutral = surfaceContainerHighest)

/** The key a swatch is stored under: a palette's mode name, or a custom theme's id. */
internal fun swatchKey(mode: ThemeMode): String = mode.name

internal fun swatchKey(theme: CustomTheme): String = theme.id

@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val variant: ThemeVariant = ThemeVariant.DARK,
    val customThemes: List<CustomTheme> = emptyList(),
    val activeCustomId: String? = null,
    val systemLightMode: ThemeMode = ThemeMode.DARK,
    val systemDarkMode: ThemeMode = ThemeMode.DARK,
    val systemDarkVariant: ThemeVariant = ThemeVariant.DARK,
) {
    val followsSystem: Boolean get() = mode == ThemeMode.SYSTEM
}

/** The theme picker. Writes go straight to the same store the app theme reads. */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataManager: LocalDataManager,
    ) : SettingsViewModel() {
        val settings: StateFlow<ThemeSettings> =
            combine(
                combine(dataManager.themeMode, dataManager.themeVariant, ::Pair),
                combine(dataManager.customThemes, dataManager.activeCustomTheme, ::Pair),
                combine(dataManager.systemLightThemeMode, dataManager.systemDarkThemeMode, ::Pair),
                dataManager.systemDarkThemeVariant,
            ) { (mode, variant), (custom, active), (light, dark), darkVariant ->
                ThemeSettings(mode, variant, custom, active?.id, light, dark, darkVariant)
            }.asState(ThemeSettings())

        /** Palettes on offer: Material You needs Android 12's wallpaper colours. */
        val palettes: List<ThemeCatalogEntry> =
            ThemeCatalog.palettes.filter { it.mode != ThemeMode.MATERIAL_YOU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

        /** Every theme's swatch in every style, so switching style or slot never waits on a recompute. */
        val swatches: StateFlow<Map<ThemeVariant, Map<String, ThemeSwatch>>> =
            dataManager.customThemes
                .map { custom ->
                    ThemeVariant.entries.associateWith { variant ->
                        palettes.associate { swatchKey(it.mode) to paletteSwatch(it.mode, variant) } +
                            custom.associate { swatchKey(it) to it.colorsFor(variant).toColorScheme(variant).toSwatch() }
                    }
                }.flowOn(Dispatchers.Default)
                .asState(emptyMap())

        private fun paletteSwatch(
            mode: ThemeMode,
            variant: ThemeVariant,
        ): ThemeSwatch =
            resolveFlowColorScheme(
                context = context,
                isSystemDark = variant != ThemeVariant.LIGHT,
                themeMode = mode,
                themeVariant = variant,
                customTheme = null,
                systemLightThemeMode = mode,
                systemDarkThemeMode = mode,
                systemDarkThemeVariant = variant,
            ).toSwatch()

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

        fun useCustomTheme(id: String) = write { dataManager.useCustomTheme(id, settings.value.variant) }

        fun setSystemLightTheme(mode: ThemeMode) = write { dataManager.setSystemLightThemeMode(mode) }

        fun setSystemDarkTheme(mode: ThemeMode) = write { dataManager.setSystemDarkThemeMode(mode) }

        /**
         * Puts custom theme [id] in a Follow-system slot. Both slots share one active custom theme, so
         * picking a custom theme for one also changes it for the other if that slot is custom too.
         */
        fun setSystemSlotCustom(
            lightSlot: Boolean,
            id: String,
        ) = write {
            dataManager.setActiveCustomTheme(id)
            if (lightSlot) dataManager.setSystemLightThemeMode(ThemeMode.CUSTOM) else dataManager.setSystemDarkThemeMode(ThemeMode.CUSTOM)
        }

        fun setSystemDarkVariant(variant: ThemeVariant) = write { dataManager.setSystemDarkThemeVariant(variant) }
    }
