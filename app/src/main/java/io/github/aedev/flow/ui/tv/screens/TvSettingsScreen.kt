package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.AppUiModePreferences
import io.github.aedev.flow.platform.AppUiMode
import io.github.aedev.flow.ui.tv.components.TvFocusableCard
import kotlinx.coroutines.launch

@Composable
fun TvSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { AppUiModePreferences(context.applicationContext) }
    val selected by preferences.mode.collectAsStateWithLifecycle(initialValue = AppUiMode.AUTOMATIC)
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.interface_mode_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        AppUiMode.entries.forEach { mode ->
            val title = when (mode) {
                AppUiMode.AUTOMATIC -> stringResource(R.string.interface_mode_automatic)
                AppUiMode.MOBILE -> stringResource(R.string.interface_mode_mobile)
                AppUiMode.TV -> stringResource(R.string.interface_mode_tv)
            }
            val summary = when (mode) {
                AppUiMode.AUTOMATIC -> stringResource(R.string.interface_mode_automatic_summary)
                AppUiMode.MOBILE -> stringResource(R.string.interface_mode_mobile_summary)
                AppUiMode.TV -> stringResource(R.string.interface_mode_tv_summary)
            }
            TvFocusableCard(
                onClick = { scope.launch { preferences.setMode(mode) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RadioButton(
                        selected = selected == mode,
                        onClick = null,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
