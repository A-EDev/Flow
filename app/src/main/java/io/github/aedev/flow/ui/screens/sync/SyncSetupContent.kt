package io.github.aedev.flow.ui.screens.sync

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.aedev.flow.R

/**
 * The pre-session steps: pick a direction, pick what travels, pick how the two devices pair, and
 * (when this device scans) the camera step.
 */

@Composable
internal fun SyncChooserContent(
    onSend: () -> Unit,
    onReceive: () -> Unit,
) {
    SyncStepHeader(
        icon = Icons.Outlined.Wifi,
        body = stringResource(R.string.sync_intro),
    )
    SyncOptionCard(
        icon = Icons.Outlined.Upload,
        title = stringResource(R.string.sync_send_to_device),
        body = stringResource(R.string.sync_send_option_body),
        onClick = onSend,
    )
    SyncOptionCard(
        icon = Icons.Outlined.Download,
        title = stringResource(R.string.sync_receive_from_device),
        body = stringResource(R.string.sync_receive_option_body),
        onClick = onReceive,
    )
}

@Composable
internal fun SyncSelectContent(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    onContinue: () -> Unit,
) {
    val allSelected = selected.size == COLLECTION_KEYS.size
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.sync_selected_count,
                    selected.size,
                    selected.size,
                    COLLECTION_KEYS.size,
                ),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { onSelectedChange(if (allSelected) emptySet() else COLLECTION_KEYS.toSet()) }) {
            Text(
                stringResource(if (allSelected) R.string.sync_select_none else R.string.sync_select_all),
            )
        }
    }

    SyncCard {
        Column(Modifier.fillMaxWidth()) {
            COLLECTION_KEYS.forEachIndexed { index, key ->
                val checked = key in selected
                ListItem(
                    // One toggle target for the whole row: the checkbox is decorative, the row owns
                    // the semantics, so a screen reader announces label + state once.
                    modifier =
                        Modifier.toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { isChecked ->
                                onSelectedChange(if (isChecked) selected + key else selected - key)
                            },
                        ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(collectionIcon(key), contentDescription = null) },
                    headlineContent = { Text(collectionLabel(key)) },
                    supportingContent = collectionDescription(key)?.let { body -> { Text(body) } },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                )
                if (index != COLLECTION_KEYS.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }

    SyncInfoRow(
        icon = Icons.Outlined.Shield,
        text = stringResource(R.string.sync_safety_backup_note),
    )
    SyncActionRow(
        confirmLabel = stringResource(R.string.sync_continue),
        onConfirm = onContinue,
        confirmEnabled = selected.isNotEmpty(),
    )
}

@Composable
internal fun SyncTransportContent(
    showQrLabel: String,
    showQrHint: String,
    scanLabel: String,
    scanHint: String,
    onShowQr: () -> Unit,
    onScan: () -> Unit,
) {
    SyncOptionCard(
        icon = Icons.Outlined.QrCode2,
        title = showQrLabel,
        body = showQrHint,
        onClick = onShowQr,
    )
    SyncOptionCard(
        icon = Icons.Outlined.QrCodeScanner,
        title = scanLabel,
        body = scanHint,
        onClick = onScan,
    )
}

@Composable
internal fun SyncScanContent(
    prompt: String,
    onScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        SyncStepHeader(
            icon = Icons.Outlined.QrCodeScanner,
            title = prompt,
            body = stringResource(R.string.sync_scanning_hint),
        )
        QrScannerView(
            onQrScanned = onScanned,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraLarge),
        )
    } else {
        SyncStepHeader(
            icon = Icons.Outlined.CameraAlt,
            title = stringResource(R.string.sync_grant_camera),
            body = stringResource(R.string.sync_camera_permission_rationale),
        )
        SyncActionRow(
            confirmLabel = stringResource(R.string.sync_grant_camera),
            onConfirm = { launcher.launch(Manifest.permission.CAMERA) },
        )
    }
}
