package io.github.aedev.flow.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.sync.SyncState
import io.github.aedev.flow.sync.protocol.ApplyStats
import io.github.aedev.flow.sync.qr.QrCodec
import kotlinx.coroutines.delay

/** The steps that run once a session is live: pairing code, verification, merge consent, outcome. */

@Composable
internal fun SyncQrContent(
    s: SyncState.ShowingQr,
    onCancel: () -> Unit,
) {
    var remaining by remember { mutableLongStateOf(QrCodec.DEFAULT_TTL_SECONDS) }
    LaunchedEffect(s.expiresAtEpochSeconds) {
        while (true) {
            remaining = (s.expiresAtEpochSeconds - System.currentTimeMillis() / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }

    Text(
        text =
            if (s.sending) {
                stringResource(R.string.sync_qr_scan_on_target_sending)
            } else {
                stringResource(R.string.sync_qr_scan_on_target_receiving)
            },
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )

    // The code stays dark-on-light so any camera can read it, and sits on a tonal plate so it reads
    // as a deliberate element in both themes. Rounding only ever clips the quiet zone, never the
    // finder patterns.
    SyncCard {
        QrCodeImage(
            text = s.qrText,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .aspectRatio(1f),
        )
    }

    SyncCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SasReadout(s.sas)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.sync_expires_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.sync_expires_seconds, remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { (remaining.toFloat() / QrCodec.DEFAULT_TTL_SECONDS).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    SyncInfoRow(icon = Icons.Outlined.Lan, text = stringResource(R.string.sync_qr_advertised_address, s.address))
    SyncInfoRow(icon = Icons.Outlined.Wifi, text = stringResource(R.string.sync_qr_network_note))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.sync_waiting_for_peer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sync_cancel_session))
    }
}

/** The 6-digit short authentication string, spaced so two people can read it aloud reliably. */
@Composable
private fun SasReadout(
    sas: String,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        contentColor =
            if (emphasized) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = sas,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            style =
                if (emphasized) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
            fontFamily = FontFamily.Monospace,
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SyncSasContent(
    sas: String,
    onConfirm: (Boolean) -> Unit,
) {
    SyncStepHeader(
        icon = Icons.Outlined.Password,
        title = stringResource(R.string.sync_sas_title),
        body = stringResource(R.string.sync_sas_body),
    )
    SasReadout(sas, emphasized = true)
    SyncActionRow(
        confirmLabel = stringResource(R.string.sync_sas_match),
        onConfirm = { onConfirm(true) },
        dismissLabel = stringResource(R.string.sync_sas_differ),
        onDismiss = { onConfirm(false) },
    )
}

@Composable
internal fun SyncConsentContent(
    collections: List<String>,
    onDecision: (Boolean) -> Unit,
) {
    SyncStepHeader(
        icon = Icons.Outlined.MergeType,
        title = stringResource(R.string.sync_consent_title),
    )
    SyncCard {
        Column(Modifier.fillMaxWidth()) {
            collections.forEach { key ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(collectionIcon(key), contentDescription = null) },
                    headlineContent = { Text(collectionLabel(key)) },
                )
            }
        }
    }
    SyncInfoRow(icon = Icons.Outlined.Shield, text = stringResource(R.string.sync_consent_note))
    SyncActionRow(
        confirmLabel = stringResource(R.string.sync_merge),
        onConfirm = { onDecision(true) },
        dismissLabel = stringResource(R.string.sync_decline),
        onDismiss = { onDecision(false) },
    )
}

@Composable
internal fun SyncTransferContent(s: SyncState.Transferring) {
    SyncStepHeader(
        icon = Icons.Outlined.Sync,
        title = stringResource(R.string.sync_transferring, collectionLabel(s.collection)),
    )
    SyncCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.total > 0) {
                LinearProgressIndicator(
                    progress = { (s.done.toFloat() / s.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = stringResource(R.string.sync_progress_fraction, s.done, s.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SyncDoneContent(
    s: SyncState.Done,
    onDone: () -> Unit,
) {
    SyncStepHeader(
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(R.string.sync_complete),
        body = stringResource(R.string.sync_synced_with, s.peerName),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    if (s.stats.isNotEmpty()) {
        SyncCard {
            Column(Modifier.fillMaxWidth()) {
                s.stats.forEach { (collection, stats) ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(collectionIcon(collection), contentDescription = null) },
                        headlineContent = { Text(collectionLabel(collection)) },
                        supportingContent = { Text(statsSummary(stats)) },
                    )
                }
            }
        }
    }
    SyncActionRow(confirmLabel = stringResource(R.string.sync_done_button), onConfirm = onDone)
}

@Composable
private fun statsSummary(stats: ApplyStats): String =
    stringResource(R.string.sync_done_stats_summary, stats.added, stats.updated, stats.skipped)

@Composable
internal fun SyncFailedContent(
    message: String,
    onRetry: () -> Unit,
) {
    SyncStepHeader(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.sync_failed_title),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
    SyncCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    SyncActionRow(confirmLabel = stringResource(R.string.sync_try_again), onConfirm = onRetry)
}

@Composable
internal fun SyncBusyContent(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
