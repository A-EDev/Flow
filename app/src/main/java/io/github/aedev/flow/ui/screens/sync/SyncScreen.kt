package io.github.aedev.flow.ui.screens.sync

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.sync.SyncState
import io.github.aedev.flow.sync.protocol.SyncCollection
import io.github.aedev.flow.sync.protocol.SyncRole
import kotlinx.coroutines.delay

private val COLLECTION_KEYS = listOf(
    SyncCollection.PLAYLISTS,
    SyncCollection.WATCH_HISTORY,
    SyncCollection.LIKES,
    SyncCollection.SUBSCRIPTIONS,
    SyncCollection.SETTINGS,
    SyncCollection.FLOW_NEURO_BRAIN,
)

@Composable
private fun collectionLabel(key: String): String = when (key) {
    SyncCollection.PLAYLISTS -> stringResource(R.string.sync_collection_playlists)
    SyncCollection.WATCH_HISTORY -> stringResource(R.string.sync_collection_watch_history)
    SyncCollection.LIKES -> stringResource(R.string.sync_collection_likes)
    SyncCollection.SUBSCRIPTIONS -> stringResource(R.string.sync_collection_subscriptions)
    SyncCollection.SETTINGS -> stringResource(R.string.sync_collection_settings)
    SyncCollection.FLOW_NEURO_BRAIN -> stringResource(R.string.sync_collection_recommendation_profile)
    else -> key
}

private enum class Step { CHOOSER, SEND_SELECT, SEND_TRANSPORT, SEND_SCAN, RECEIVE_TRANSPORT, RECEIVE_SCAN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(Step.CHOOSER) }
    val selected = remember { mutableStateOf(COLLECTION_KEYS.toMutableSet()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.cancel(); onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.sync_devices_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                is SyncState.Idle -> IdleContent(
                    step = step,
                    onStepChange = { step = it },
                    selected = selected.value,
                    onSelectedChange = { selected.value = it },
                    onHost = { role -> viewModel.host(role, selected.value.toList()) },
                    onJoin = { role, qr -> viewModel.join(role, qr, selected.value.toList()) },
                )
                is SyncState.Preparing -> Busy(stringResource(R.string.sync_preparing))
                is SyncState.Connecting -> Busy(stringResource(R.string.sync_connecting))
                is SyncState.ShowingQr -> QrContent(s)
                is SyncState.AwaitingSas -> SasContent(s.sas, onConfirm = { viewModel.confirmSas(it) })
                is SyncState.AwaitingConsent -> ConsentContent(
                    collections = s.summary.collections,
                    onDecision = { viewModel.confirmConsent(it) },
                )
                is SyncState.Transferring -> TransferContent(s)
                is SyncState.Done -> DoneContent(s) {
                    viewModel.reset(); step = Step.CHOOSER; onNavigateBack()
                }
                is SyncState.Failed -> FailedContent(s.message) {
                    viewModel.reset(); step = Step.CHOOSER
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    step: Step,
    onStepChange: (Step) -> Unit,
    selected: Set<String>,
    onSelectedChange: (MutableSet<String>) -> Unit,
    onHost: (SyncRole) -> Unit,
    onJoin: (SyncRole, String) -> Unit,
) {
    when (step) {
        Step.CHOOSER -> {
            Text(
                stringResource(R.string.sync_intro),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onStepChange(Step.SEND_SELECT) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sync_send_to_device))
            }
            OutlinedButton(onClick = { onStepChange(Step.RECEIVE_TRANSPORT) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sync_receive_from_device))
            }
        }
        Step.SEND_SELECT -> SelectSendContent(
            selected = selected,
            onSelectedChange = onSelectedChange,
            onContinue = { onStepChange(Step.SEND_TRANSPORT) },
        )
        Step.SEND_TRANSPORT -> TransportChooser(
            title = stringResource(R.string.sync_pairing_title),
            showQrLabel = stringResource(R.string.sync_show_qr_here),
            showQrHint = stringResource(R.string.sync_send_show_qr_hint),
            scanLabel = stringResource(R.string.sync_scan_other_qr),
            scanHint = stringResource(R.string.sync_send_scan_hint),
            onShowQr = { onHost(SyncRole.SENDER) },
            onScan = { onStepChange(Step.SEND_SCAN) },
        )
        Step.SEND_SCAN -> ScanContent(
            prompt = stringResource(R.string.sync_scan_prompt_receive_code),
            onScanned = { onJoin(SyncRole.SENDER, it) },
        )
        Step.RECEIVE_TRANSPORT -> TransportChooser(
            title = stringResource(R.string.sync_pairing_title),
            showQrLabel = stringResource(R.string.sync_scan_other_qr),
            showQrHint = stringResource(R.string.sync_receive_scan_hint),
            scanLabel = stringResource(R.string.sync_show_qr_here),
            scanHint = stringResource(R.string.sync_receive_show_qr_hint),
            onShowQr = { onStepChange(Step.RECEIVE_SCAN) },
            onScan = { onHost(SyncRole.RECEIVER) },
        )
        Step.RECEIVE_SCAN -> ScanContent(
            prompt = stringResource(R.string.sync_scan_prompt_send_code),
            onScanned = { onJoin(SyncRole.RECEIVER, it) },
        )
    }
}

@Composable
private fun TransportChooser(
    title: String,
    showQrLabel: String,
    showQrHint: String,
    scanLabel: String,
    scanHint: String,
    onShowQr: () -> Unit,
    onScan: () -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Button(onClick = onShowQr, modifier = Modifier.fillMaxWidth()) { Text(showQrLabel) }
    Text(showQrHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(scanLabel) }
    Text(scanHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun SelectSendContent(
    selected: Set<String>,
    onSelectedChange: (MutableSet<String>) -> Unit,
    onContinue: () -> Unit,
) {
    Text(stringResource(R.string.sync_choose_what_to_send), style = MaterialTheme.typography.titleMedium)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            COLLECTION_KEYS.forEach { key ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selected.contains(key),
                        onCheckedChange = {
                            val next = selected.toMutableSet()
                            if (it) next.add(key) else next.remove(key)
                            onSelectedChange(next)
                        },
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(collectionLabel(key), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    Text(
        stringResource(R.string.sync_safety_backup_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onContinue,
        enabled = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sync_continue))
    }
}

@Composable
private fun ScanContent(prompt: String, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }
    if (hasPermission) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        QrScannerView(
            onQrScanned = onScanned,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
    } else {
        Text(
            stringResource(R.string.sync_camera_permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
            Text(stringResource(R.string.sync_grant_camera))
        }
    }
}

@Composable
private fun QrContent(s: SyncState.ShowingQr) {
    var remaining by remember { mutableStateOf(0L) }
    LaunchedEffect(s.expiresAtEpochSeconds) {
        while (true) {
            remaining = (s.expiresAtEpochSeconds - System.currentTimeMillis() / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    Text(
        if (s.sending) stringResource(R.string.sync_qr_scan_on_target_sending) else stringResource(R.string.sync_qr_scan_on_target_receiving),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    QrCodeImage(text = s.qrText, modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp))
    Text(stringResource(R.string.sync_confirmation_code, s.sas), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
    Text(stringResource(R.string.sync_expires_in, remaining), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        stringResource(R.string.sync_qr_network_note),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SasContent(sas: String, onConfirm: (Boolean) -> Unit) {
    Text(stringResource(R.string.sync_sas_title), style = MaterialTheme.typography.titleMedium)
    Text(sas, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
    Text(
        stringResource(R.string.sync_sas_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onConfirm(false) }) { Text(stringResource(R.string.sync_sas_differ)) }
        Button(onClick = { onConfirm(true) }) { Text(stringResource(R.string.sync_sas_match)) }
    }
}

@Composable
private fun ConsentContent(collections: List<String>, onDecision: (Boolean) -> Unit) {
    Text(stringResource(R.string.sync_consent_title), style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            collections.forEach { Text(stringResource(R.string.sync_bullet_item, collectionLabel(it))) }
        }
    }
    Text(
        stringResource(R.string.sync_consent_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onDecision(false) }) { Text(stringResource(R.string.sync_decline)) }
        Button(onClick = { onDecision(true) }) { Text(stringResource(R.string.sync_merge)) }
    }
}

@Composable
private fun TransferContent(s: SyncState.Transferring) {
    Text(stringResource(R.string.sync_transferring, collectionLabel(s.collection)), style = MaterialTheme.typography.titleMedium)
    val progress = if (s.total > 0) s.done.toFloat() / s.total else 0f
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.sync_progress_fraction, s.done, s.total), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DoneContent(s: SyncState.Done, onDone: () -> Unit) {
    Text(stringResource(R.string.sync_complete), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.sync_synced_with, s.peerName), style = MaterialTheme.typography.bodyMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s.stats.forEach { (collection, st) ->
                Text(stringResource(R.string.sync_done_collection_stats, collectionLabel(collection), st.added, st.updated, st.skipped))
            }
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_done_button)) }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit) {
    Text(stringResource(R.string.sync_failed_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
    Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_try_again)) }
}

@Composable
private fun Busy(label: String) {
    Spacer(Modifier.height(40.dp))
    CircularProgressIndicator()
    Text(label, style = MaterialTheme.typography.bodyMedium)
}
