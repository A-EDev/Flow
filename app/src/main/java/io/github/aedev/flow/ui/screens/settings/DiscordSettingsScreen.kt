package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.discord.DiscordConnectionState
import io.github.aedev.flow.discord.DiscordPresenceRuntime
import io.github.aedev.flow.discord.DiscordSettingsState
import io.github.aedev.flow.discord.DiscordSettingsSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettingsScreen(onNavigateBack: () -> Unit) {
    val state by DiscordPresenceRuntime.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discord_presence_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.discord_presence_enable),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = statusText(state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.isEnabled,
                        enabled = state.canEnable,
                        onCheckedChange = { enabled ->
                            scope.launch { DiscordPresenceRuntime.setEnabled(enabled) }
                        },
                    )
                }
            }

            state.accountName?.let { accountName ->
                Text(
                    text = stringResource(R.string.discord_presence_connected_account, accountName),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            state.errorMessage?.takeIf {
                state.summary == DiscordSettingsSummary.ERROR || !state.isAvailable
            }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.isAvailable) {
                if (state.accountName == null) {
                    Button(
                        onClick = { scope.launch { DiscordPresenceRuntime.connectAccount() } },
                        enabled = state.connectionState !in setOf(
                            DiscordConnectionState.LINKING,
                            DiscordConnectionState.CONNECTING,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.discord_presence_connect))
                    }
                } else {
                    OutlinedButton(
                        onClick = { scope.launch { DiscordPresenceRuntime.unlink() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.LinkOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.discord_presence_disconnect))
                    }
                }

                if (state.summary == DiscordSettingsSummary.ERROR) {
                    OutlinedButton(
                        onClick = { scope.launch { DiscordPresenceRuntime.retry() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.discord_presence_retry))
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, contentDescription = null)
                        Text(
                            text = stringResource(R.string.discord_presence_privacy_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.discord_presence_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.discord_presence_gateway_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun discordSettingsSummaryText(state: DiscordSettingsState): String = when (state.summary) {
    DiscordSettingsSummary.OFF -> stringResource(R.string.discord_presence_off)
    DiscordSettingsSummary.NOT_CONNECTED -> stringResource(R.string.discord_presence_not_connected)
    DiscordSettingsSummary.CONNECTED -> state.accountName?.let { account ->
        stringResource(R.string.discord_presence_connected_as, account)
    } ?: stringResource(R.string.discord_presence_connected)
    DiscordSettingsSummary.UNAVAILABLE -> stringResource(R.string.discord_presence_unavailable)
    DiscordSettingsSummary.ERROR -> stringResource(R.string.discord_presence_connection_error)
}

@Composable
private fun statusText(state: DiscordSettingsState): String = when (state.connectionState) {
    DiscordConnectionState.LINKING -> stringResource(R.string.discord_presence_linking)
    DiscordConnectionState.CONNECTING -> stringResource(R.string.discord_presence_connecting)
    else -> discordSettingsSummaryText(state)
}
