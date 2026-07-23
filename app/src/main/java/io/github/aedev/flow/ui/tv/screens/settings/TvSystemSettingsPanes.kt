package io.github.aedev.flow.ui.tv.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.AppUiModePreferences
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.platform.AppUiMode
import io.github.aedev.flow.player.DeepFlowManager
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.components.TvToggleRow
import kotlinx.coroutines.launch

@Composable
fun TvFlowEngineSettingsPane(
    playerPreferences: PlayerPreferences,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deepFlowActive by playerPreferences.deepFlowActive.collectAsStateWithLifecycle(initialValue = false)
    val saveToHistory by playerPreferences.deepFlowSaveToHistory.collectAsStateWithLifecycle(initialValue = true)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvToggleRow(
            label = stringResource(R.string.deep_flow_mode_title),
            supportingText = stringResource(R.string.deep_flow_mode_subtitle),
            checked = deepFlowActive,
            onCheckedChange = { enabled ->
                scope.launch { DeepFlowManager.setEnabled(context.applicationContext, enabled) }
            },
        )
        TvToggleRow(
            label = stringResource(R.string.deep_flow_save_history_title),
            supportingText = stringResource(R.string.deep_flow_save_history_subtitle),
            checked = saveToHistory,
            onCheckedChange = { enabled ->
                scope.launch { playerPreferences.setDeepFlowSaveToHistory(enabled) }
            },
        )
    }
}

@Composable
fun TvInterfaceSettingsPane(
    modePreferences: AppUiModePreferences,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val selectedMode by modePreferences.mode.collectAsStateWithLifecycle(initialValue = AppUiMode.AUTOMATIC)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppUiMode.entries.forEach { mode ->
            val label = when (mode) {
                AppUiMode.AUTOMATIC -> stringResource(R.string.interface_mode_automatic)
                AppUiMode.MOBILE -> stringResource(R.string.interface_mode_mobile)
                AppUiMode.TV -> stringResource(R.string.interface_mode_tv)
            }
            val summary = when (mode) {
                AppUiMode.AUTOMATIC -> stringResource(R.string.interface_mode_automatic_summary)
                AppUiMode.MOBILE -> stringResource(R.string.interface_mode_mobile_summary)
                AppUiMode.TV -> stringResource(R.string.interface_mode_tv_summary)
            }
            TvSelectionRow(
                label = label,
                supportingText = summary,
                selected = selectedMode == mode,
                onClick = {
                    if (mode != selectedMode) {
                        scope.launch {
                            // Release focus before the whole UI tree is swapped out.
                            focusManager.clearFocus(force = true)
                            withFrameNanos { }
                            modePreferences.setMode(mode)
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun TvAboutSettingsPane(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.tv_about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
