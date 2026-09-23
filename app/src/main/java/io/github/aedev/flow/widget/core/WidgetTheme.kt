package io.github.aedev.flow.widget.core

import android.content.Context
import androidx.glance.color.ColorProviders
import io.github.aedev.flow.data.local.CustomThemeCodec
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.theme.CustomTheme
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
    val customTheme: CustomTheme?,
    val systemLightThemeMode: ThemeMode,
    val systemDarkThemeMode: ThemeMode,
    val systemDarkThemeVariant: ThemeVariant,
) {
    /**
     * A form of this signature that survives the process, so a launch can tell "the theme is the
     * same as last time" from "this is the first value I have seen".
     *
     * Built by hand rather than from `hashCode()`, which is identity-based for the enums in it and so
     * stable within one process but meaningless across two. The custom theme is written in its
     * exported form, which spells every colour out.
     */
    fun persistedForm(): String =
        buildString {
            append(themeMode.name).append('|')
            append(themeVariant.name).append('|')
            append(systemLightThemeMode.name).append('|')
            append(systemDarkThemeMode.name).append('|')
            append(systemDarkThemeVariant.name)
            append('|')
            customTheme?.let { append(CustomThemeCodec.encodeOne(it)) }
        }
}

fun widgetThemeSignatureFlow(context: Context): Flow<WidgetThemeSignature> {
    val dataManager = LocalDataManager(context.applicationContext)
    return combine(
        combine(
            dataManager.themeMode,
            dataManager.themeVariant,
            dataManager.activeCustomTheme,
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
            light =
                resolveFlowColorScheme(
                    context = appContext,
                    isSystemDark = false,
                    themeMode = signature.themeMode,
                    themeVariant = signature.themeVariant,
                    customTheme = signature.customTheme,
                    systemLightThemeMode = signature.systemLightThemeMode,
                    systemDarkThemeMode = signature.systemDarkThemeMode,
                    systemDarkThemeVariant = signature.systemDarkThemeVariant,
                ),
            dark =
                resolveFlowColorScheme(
                    context = appContext,
                    isSystemDark = true,
                    themeMode = signature.themeMode,
                    themeVariant = signature.themeVariant,
                    customTheme = signature.customTheme,
                    systemLightThemeMode = signature.systemLightThemeMode,
                    systemDarkThemeMode = signature.systemDarkThemeMode,
                    systemDarkThemeVariant = signature.systemDarkThemeVariant,
                ),
        )
    }
}
