package io.github.aedev.flow.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.sponsordetection.SponsorJournalStats
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow

@Composable
internal fun SponsorTrainingSettingsSection(
    consentEnabled: Boolean,
    stats: SponsorJournalStats,
    onConsentChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.sponsor_training_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowSwitchRow(
            title = stringResource(R.string.sponsor_training_consent_title),
            supportingText = stringResource(R.string.sponsor_training_consent_subtitle),
            checked = consentEnabled,
            onCheckedChange = onConsentChange,
            shape = MaterialTheme.shapes.medium,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.sponsor_training_stats,
                        stats.evaluationCount,
                        stats.feedbackCount,
                        Formatter.formatFileSize(context, stats.sizeBytes),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.isFull) {
                Text(
                    text = stringResource(R.string.sponsor_training_storage_full),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = stats.evaluationCount + stats.feedbackCount > 0,
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sponsor_training_export))
                }
                TextButton(
                    onClick = onClear,
                    enabled = stats.evaluationCount + stats.feedbackCount > 0,
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sponsor_training_clear))
                }
            }
        }
    }
}

@Composable
internal fun SponsorTrainingConsentDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sponsor_training_consent_dialog_title)) },
        text = { Text(stringResource(R.string.sponsor_training_consent_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.sponsor_training_consent_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

@Composable
internal fun SponsorTrainingClearDialog(
    afterExport: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sponsor_training_clear_title)) },
        text = {
            Text(
                stringResource(
                    if (afterExport) {
                        R.string.sponsor_training_clear_after_export
                    } else {
                        R.string.sponsor_training_clear_body
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}
