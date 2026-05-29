package io.github.aedev.flow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.utils.DateContextMode
import io.github.aedev.flow.utils.DateDisplayMode
import io.github.aedev.flow.utils.DateDisplaySettings
import io.github.aedev.flow.utils.DateFormatStyle

@Composable
fun rememberDateDisplaySettings(): DateDisplaySettings {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val globalMode by prefs.dateDisplayMode.collectAsState(initial = DateDisplayMode.RELATIVE)
    val formatStyle by prefs.dateFormatStyle.collectAsState(initial = DateFormatStyle.SYSTEM)
    val listsMode by prefs.dateModeLists.collectAsState(initial = DateContextMode.DEFAULT)
    val watchMode by prefs.dateModeWatch.collectAsState(initial = DateContextMode.DEFAULT)
    val descriptionMode by prefs.dateModeDescription.collectAsState(initial = DateContextMode.DEFAULT)
    return DateDisplaySettings(globalMode, formatStyle, listsMode, watchMode, descriptionMode)
}
