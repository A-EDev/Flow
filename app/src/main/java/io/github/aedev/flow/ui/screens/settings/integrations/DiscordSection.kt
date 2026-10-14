package io.github.aedev.flow.ui.screens.settings.integrations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.discord.DiscordConnectionState
import io.github.aedev.flow.discord.DiscordSettingsState
import io.github.aedev.flow.discord.DiscordSettingsSummary
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.notice
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow
import io.github.aedev.flow.ui.screens.settings.index.IntegrationsIndex

internal fun SettingsListScope.discordSection(
    state: DiscordSettingsState,
    onEnabledChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onUnlink: () -> Unit,
    onRetry: () -> Unit,
) {
    group(
        key = "integrations.discord.group",
        header = R.string.discord_presence_title,
        footer = R.string.discord_presence_description,
    ) {
        row(IntegrationsIndex.discord.key) { shape ->
            FlowSwitchRow(
                title = stringResource(IntegrationsIndex.discord.title),
                supportingText = stringResource(R.string.discord_presence_enable_description),
                checked = state.isEnabled,
                enabled = state.canEnable,
                onCheckedChange = onEnabledChange,
                leadingPainter = painterResource(R.drawable.ic_discord),
                shape = shape,
            )
        }
        row(IntegrationsIndex.discordAccount.key) { shape ->
            DiscordAccountRow(state = state, shape = shape, onConnect = onConnect, onUnlink = onUnlink)
        }
        if (state.summary == DiscordSettingsSummary.ERROR && state.isAvailable) {
            row("integrations.discord.retry") { shape ->
                FlowNavRow(
                    title = stringResource(R.string.discord_presence_retry),
                    supportingText = state.errorMessage,
                    leadingIcon = Icons.Outlined.Refresh,
                    showChevron = false,
                    onClick = onRetry,
                    shape = shape,
                )
            }
        }
    }
    notice(
        key = "integrations.discord.privacy",
        title = { stringResource(R.string.discord_presence_privacy_title) },
        text = { stringResource(R.string.discord_presence_privacy_body) },
        icon = Icons.Outlined.Security,
    )
    notice(
        key = "integrations.discord.risk",
        title = { stringResource(R.string.discord_presence_privacy_section) },
        text = { stringResource(R.string.discord_presence_gateway_warning) },
        icon = Icons.Outlined.WarningAmber,
        isWarning = true,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscordAccountRow(
    state: DiscordSettingsState,
    shape: Shape,
    onConnect: () -> Unit,
    onUnlink: () -> Unit,
) {
    val pending =
        state.connectionState == DiscordConnectionState.LINKING ||
            state.connectionState == DiscordConnectionState.CONNECTING
    SegmentedListItem(
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
        supportingContent = { Text(discordStatusText(state)) },
        trailingContent = {
            when {
                pending -> {
                    FlowLoadingIndicator()
                }

                state.accountName != null -> {
                    TextButton(onClick = onUnlink) { Text(stringResource(R.string.discord_presence_unlink_action)) }
                }

                state.isAvailable -> {
                    TextButton(onClick = onConnect, enabled = state.isEnabled) {
                        Text(stringResource(R.string.discord_presence_connect_action))
                    }
                }
            }
        },
    ) {
        Text(stringResource(R.string.discord_presence_account))
    }
}

@Composable
private fun discordStatusText(state: DiscordSettingsState): String =
    when (state.connectionState) {
        DiscordConnectionState.LINKING -> stringResource(R.string.discord_presence_linking)
        DiscordConnectionState.CONNECTING -> stringResource(R.string.discord_presence_connecting)
        else -> discordSummaryText(state)
    }

@Composable
internal fun discordSummaryText(state: DiscordSettingsState): String =
    when (state.summary) {
        DiscordSettingsSummary.OFF -> {
            stringResource(R.string.discord_presence_off)
        }

        DiscordSettingsSummary.NOT_CONNECTED -> {
            stringResource(R.string.discord_presence_not_connected)
        }

        DiscordSettingsSummary.READY -> {
            state.accountName?.let { stringResource(R.string.discord_presence_ready_as, it) }
                ?: stringResource(R.string.discord_presence_ready)
        }

        DiscordSettingsSummary.CONNECTED -> {
            state.accountName?.let { stringResource(R.string.discord_presence_connected_as, it) }
                ?: stringResource(R.string.discord_presence_connected)
        }

        DiscordSettingsSummary.UNAVAILABLE -> {
            stringResource(R.string.discord_presence_unavailable)
        }

        DiscordSettingsSummary.ERROR -> {
            stringResource(R.string.discord_presence_connection_error)
        }
    }
