package io.github.aedev.flow.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.sponsordetection.SponsorModelConfig
import io.github.aedev.flow.data.sponsordetection.SponsorModelState
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow

@Composable
internal fun SponsorModelSettingsSection(
    enabled: Boolean,
    modelState: SponsorModelState,
    onEnabledChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.sponsor_model_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowSwitchRow(
            title = stringResource(R.string.sponsor_model_toggle_title),
            supportingText = stringResource(R.string.sponsor_model_toggle_subtitle),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            shape = MaterialTheme.shapes.medium,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (modelState) {
                is SponsorModelState.Installed -> {
                    Text(
                        text =
                            stringResource(
                                R.string.sponsor_model_status_installed,
                                Formatter.formatFileSize(context, modelState.sizeBytes),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sponsor_model_delete))
                    }
                }

                is SponsorModelState.Downloading -> {
                    val fraction =
                        if (modelState.totalBytes > 0L) {
                            (modelState.downloadedBytes.toFloat() / modelState.totalBytes).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.sponsor_model_downloading, (fraction * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SponsorModelState.NotInstalled -> {
                    ModelStatusRow(
                        statusText =
                            stringResource(
                                R.string.sponsor_model_status_missing,
                                Formatter.formatFileSize(context, SponsorModelConfig.TOTAL_BYTES),
                            ),
                        actionText = stringResource(R.string.sponsor_model_download),
                        onAction = onDownload,
                    )
                }

                SponsorModelState.Failed -> {
                    ModelStatusRow(
                        statusText = stringResource(R.string.sponsor_model_failed),
                        actionText = stringResource(R.string.sponsor_model_retry),
                        onAction = onDownload,
                        isError = true,
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.sponsor_model_delete_title)) },
            text = { Text(stringResource(R.string.sponsor_model_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

@Composable
private fun ModelStatusRow(
    statusText: String,
    actionText: String,
    onAction: () -> Unit,
    isError: Boolean = false,
) {
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onAction) {
        Icon(Icons.Outlined.CloudDownload, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(actionText)
    }
}
