package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    var showRiskConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discord_presence_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "description") {
                Text(
                    text = stringResource(R.string.discord_presence_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item(key = "connection") {
                Column {
                    SectionHeader(stringResource(R.string.discord_presence_connection_section))
                    DiscordSettingsGroup {
                        SettingsSwitchItem(
                            icon = Icons.Outlined.Share,
                            title = stringResource(R.string.discord_presence_enable),
                            subtitle = stringResource(R.string.discord_presence_enable_description),
                            checked = state.isEnabled,
                            enabled = state.canEnable,
                            onCheckedChange = { enabled ->
                                scope.launch { DiscordPresenceRuntime.setEnabled(enabled) }
                            },
                        )

                        HorizontalDivider(Modifier.padding(start = 56.dp))

                        DiscordAccountRow(
                            state = state,
                            onConnect = { showRiskConfirmation = true },
                            onUnlink = { scope.launch { DiscordPresenceRuntime.unlink() } },
                        )

                        state.errorMessage?.takeIf {
                            state.summary == DiscordSettingsSummary.ERROR || !state.isAvailable
                        }?.let { error ->
                            HorizontalDivider(Modifier.padding(start = 56.dp))
                            DiscordErrorRow(error)
                        }
                    }
                }
            }

            if (state.summary == DiscordSettingsSummary.ERROR && state.isAvailable) {
                item(key = "retry") {
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

            item(key = "privacy") {
                Column {
                    SectionHeader(stringResource(R.string.discord_presence_privacy_section))
                    DiscordSettingsGroup {
                        DiscordInformationRow(
                            icon = Icons.Outlined.Security,
                            title = stringResource(R.string.discord_presence_privacy_title),
                            body = stringResource(R.string.discord_presence_privacy_body),
                        )
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                        DiscordInformationRow(
                            icon = Icons.Outlined.WarningAmber,
                            title = stringResource(R.string.discord_presence_risk_dialog_title),
                            body = stringResource(R.string.discord_presence_gateway_warning),
                            isWarning = true,
                        )
                    }
                }
            }
        }
    }

    if (showRiskConfirmation) {
        AlertDialog(
            onDismissRequest = { showRiskConfirmation = false },
            title = { Text(stringResource(R.string.discord_presence_risk_dialog_title)) },
            text = { Text(stringResource(R.string.discord_presence_risk_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRiskConfirmation = false
                        scope.launch { DiscordPresenceRuntime.connectAccount() }
                    },
                ) {
                    Text(stringResource(R.string.discord_presence_risk_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRiskConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DiscordSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(content = content)
    }
}

@Composable
private fun DiscordAccountRow(
    state: DiscordSettingsState,
    onConnect: () -> Unit,
    onUnlink: () -> Unit,
) {
    val connectionPending = state.connectionState == DiscordConnectionState.LINKING ||
        state.connectionState == DiscordConnectionState.CONNECTING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.discord_presence_account),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            connectionPending -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )

            state.accountName != null -> TextButton(onClick = onUnlink) {
                Text(stringResource(R.string.discord_presence_unlink_action))
            }

            state.isAvailable -> TextButton(
                onClick = onConnect,
                enabled = state.isEnabled,
            ) {
                Text(stringResource(R.string.discord_presence_connect_action))
            }
        }
    }
}

@Composable
private fun DiscordErrorRow(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DiscordInformationRow(
    icon: ImageVector,
    title: String,
    body: String,
    isWarning: Boolean = false,
) {
    val accentColor = if (isWarning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isWarning) accentColor else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun discordSettingsSummaryText(state: DiscordSettingsState): String = when (state.summary) {
    DiscordSettingsSummary.OFF -> stringResource(R.string.discord_presence_off)
    DiscordSettingsSummary.NOT_CONNECTED -> stringResource(R.string.discord_presence_not_connected)
    DiscordSettingsSummary.READY -> state.accountName?.let { account ->
        stringResource(R.string.discord_presence_ready_as, account)
    } ?: stringResource(R.string.discord_presence_ready)
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
