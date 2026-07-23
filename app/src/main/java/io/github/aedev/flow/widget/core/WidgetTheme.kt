package io.github.aedev.flow.widget.core

import android.content.Context
import androidx.glance.color.ColorProviders
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.theme.CustomThemePalettes
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.theme.resolveFlowColorScheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Everything that determines the app's active palette — used to detect theme changes. */
data class WidgetThemeSignature(
    val themeMode: ThemeMode,
    val themeVariant: ThemeVariant,
    val customThemePalettes: CustomThemePalettes,
    val systemLightThemeMode: ThemeMode,
    val systemDarkThemeMode: ThemeMode,
    val systemDarkThemeVariant: ThemeVariant,
)

fun widgetThemeSignatureFlow(context: Context): Flow<WidgetThemeSignature> {
    val dataManager = LocalDataManager(context.applicationContext)
    return combine(
        combine(
            dataManager.themeMode,
            dataManager.themeVariant,
            dataManager.customThemePalettes,
        ) { mode, variant, palettes -> Triple(mode, variant, palettes) },
        combine(
            dataManager.systemLightThemeMode,
            dataManager.systemDarkThemeMode,
            dataManager.systemDarkThemeVariant,
        ) { light, dark, darkVariant -> Triple(light, dark, darkVariant) },
    ) { (mode, variant, palettes), (light, dark, darkVariant) ->
        WidgetThemeSignature(mode, variant, palettes, light, dark, darkVariant)
    }.distinctUntilChanged()
}

/**
 * The app's active color scheme as Glance color providers, resolved through the same
 * [resolveFlowColorScheme] the in-app theme uses — widgets always match the app.
 */
fun widgetColorsFlow(context: Context): Flow<ColorProviders> {
    val appContext = context.applicationContext
    return widgetThemeSignatureFlow(appContext).map { signature ->
        androidx.glance.material3.ColorProviders(
            light = resolveFlowColorScheme(
                context = appContext,
                isSystemDark = false,
                themeMode = signature.themeMode,
                themeVariant = signature.themeVariant,
                customThemePalettes = signature.customThemePalettes,
                systemLightThemeMode = signature.systemLightThemeMode,
                systemDarkThemeMode = signature.systemDarkThemeMode,
                systemDarkThemeVariant = signature.systemDarkThemeVariant,
            ),
            dark = resolveFlowColorScheme(
                context = appContext,
                isSystemDark = true,
                themeMode = signature.themeMode,
                themeVariant = signature.themeVariant,
                customThemePalettes = signature.customThemePalettes,
                systemLightThemeMode = signature.systemLightThemeMode,
                systemDarkThemeMode = signature.systemDarkThemeMode,
                systemDarkThemeVariant = signature.systemDarkThemeVariant,
            ),
        )
    }
}
