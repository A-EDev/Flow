package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.utils.DateDisplaySettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Static because the five date preferences change only when the user edits a setting: reads cost
 * nothing, and the rare write invalidates the subtree wholesale instead of being tracked per
 * reader. The default matches [DateDisplaySettings]'s own defaults, so a tree rendered outside the
 * provider still formats dates the way an unconfigured install does.
 */
val LocalDateDisplaySettings = staticCompositionLocalOf { DateDisplaySettings() }

/**
 * Installs the shared date settings.
 *
 * Each of the five preferences used to be collected per call site, so an expanded player with
 * twenty-six related cards opened well over a hundred DataStore collectors and re-mapped the same
 * preference file in each of them on every write.
 */
@Composable
fun ProvideDateDisplaySettings(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settingsFlow =
        remember(context) {
            val preferences = PlayerPreferences(context)
            combine(
                preferences.dateDisplayMode,
                preferences.dateFormatStyle,
                preferences.dateModeLists,
                preferences.dateModeWatch,
                preferences.dateModeDescription,
            ) { globalMode, formatStyle, lists, watch, description ->
                DateDisplaySettings(globalMode, formatStyle, lists, watch, description)
            }.distinctUntilChanged()
        }
    val settings by settingsFlow.collectAsStateWithLifecycle(DateDisplaySettings())

    CompositionLocalProvider(LocalDateDisplaySettings provides settings, content = content)
}

@Composable
fun rememberDateDisplaySettings(): DateDisplaySettings = LocalDateDisplaySettings.current
