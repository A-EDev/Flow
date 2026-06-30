package io.github.aedev.flow.ui.screens.sync

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.sync.SyncState
import io.github.aedev.flow.sync.protocol.SyncCollection
import io.github.aedev.flow.sync.protocol.SyncRole
import kotlinx.coroutines.delay

private val COLLECTION_LABELS = linkedMapOf(
    SyncCollection.PLAYLISTS to "Playlists",
    SyncCollection.WATCH_HISTORY to "Watch history",
    SyncCollection.LIKES to "Likes",
    SyncCollection.SUBSCRIPTIONS to "Subscriptions",
    SyncCollection.SETTINGS to "Settings",
    SyncCollection.FLOW_NEURO_BRAIN to "Recommendation profile",
)

/**
 * Role and transport are independent (see SyncManager): the user first picks Send/Receive, then
 * picks whether *this* device shows the QR or scans the other device's. Showing a QR lets a
 * camera-less peer (e.g. desktop) take the other role by scanning.
 */
private enum class Step { CHOOSER, SEND_SELECT, SEND_TRANSPORT, SEND_SCAN, RECEIVE_TRANSPORT, RECEIVE_SCAN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(Step.CHOOSER) }
    val selected = remember { mutableStateOf(COLLECTION_LABELS.keys.toMutableSet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync devices") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.cancel(); onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
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
                is SyncState.Preparing -> Busy("Preparing…")
                is SyncState.Connecting -> Busy("Connecting…")
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
                "Sync your library, history, likes, settings and recommendation profile with another device over your local Wi-Fi. Nothing leaves your network.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onStepChange(Step.SEND_SELECT) }, modifier = Modifier.fillMaxWidth()) {
                Text("Send to a device")
            }
            OutlinedButton(onClick = { onStepChange(Step.RECEIVE_TRANSPORT) }, modifier = Modifier.fillMaxWidth()) {
                Text("Receive from a device")
            }
        }
        Step.SEND_SELECT -> SelectSendContent(
            selected = selected,
            onSelectedChange = onSelectedChange,
            onContinue = { onStepChange(Step.SEND_TRANSPORT) },
        )
        Step.SEND_TRANSPORT -> TransportChooser(
            title = "How should the devices pair?",
            showQrLabel = "Show a QR code here",
            showQrHint = "The other device scans it. Use this if the other device has a camera.",
            scanLabel = "Scan the other device's QR",
            scanHint = "Use this to send to a device with no camera (e.g. desktop) that is showing a receive code.",
            onShowQr = { onHost(SyncRole.SENDER) },
            onScan = { onStepChange(Step.SEND_SCAN) },
        )
        Step.SEND_SCAN -> ScanContent(
            prompt = "Point the camera at the other device's receive code",
            onScanned = { onJoin(SyncRole.SENDER, it) },
        )
        Step.RECEIVE_TRANSPORT -> TransportChooser(
            title = "How should the devices pair?",
            showQrLabel = "Scan the other device's QR",
            showQrHint = "Use this if this device has a camera and the other is showing a send code.",
            scanLabel = "Show a QR code here",
            scanHint = "The sender scans it. Use this if this device has no camera or you'd rather show a code.",
            // Note: for Receive the primary (first) button is the scanner, the secondary is show-QR.
            onShowQr = { onStepChange(Step.RECEIVE_SCAN) },
            onScan = { onHost(SyncRole.RECEIVER) },
        )
        Step.RECEIVE_SCAN -> ScanContent(
            prompt = "Point the camera at the other device's send code",
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
    Text("Choose what to send", style = MaterialTheme.typography.titleMedium)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            COLLECTION_LABELS.forEach { (key, label) ->
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
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    Text(
        "A safety backup is taken automatically on the receiving device before anything is merged.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onContinue,
        enabled = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Continue")
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
            "Camera permission is needed to scan the QR code.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Grant camera access") }
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
        if (s.sending) "Scan this on the device you're sending to" else "Scan this on the device you're receiving from",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    QrCodeImage(text = s.qrText, modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp))
    Text("Confirmation code: ${s.sas}", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
    Text("Expires in ${remaining}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        "Both devices must be on the same Wi-Fi (or one device's hotspot). You may need to allow Flow through a firewall.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SasContent(sas: String, onConfirm: (Boolean) -> Unit) {
    Text("Do the codes match?", style = MaterialTheme.typography.titleMedium)
    Text(sas, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
    Text(
        "Confirm this 6-digit code is identical on both devices before continuing.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onConfirm(false) }) { Text("They differ") }
        Button(onClick = { onConfirm(true) }) { Text("They match") }
    }
}

@Composable
private fun ConsentContent(collections: List<String>, onDecision: (Boolean) -> Unit) {
    Text("Merge incoming data?", style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            collections.forEach { Text("• ${COLLECTION_LABELS[it] ?: it}") }
        }
    }
    Text(
        "A safety backup will be taken before merging, and the merge is conflict-free — nothing is overwritten or double-counted.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onDecision(false) }) { Text("Decline") }
        Button(onClick = { onDecision(true) }) { Text("Merge") }
    }
}

@Composable
private fun TransferContent(s: SyncState.Transferring) {
    Text("Syncing ${COLLECTION_LABELS[s.collection] ?: s.collection}…", style = MaterialTheme.typography.titleMedium)
    val progress = if (s.total > 0) s.done.toFloat() / s.total else 0f
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    Text("${s.done} / ${s.total}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DoneContent(s: SyncState.Done, onDone: () -> Unit) {
    Text("Sync complete 🎉", style = MaterialTheme.typography.headlineSmall)
    Text("Synced with ${s.peerName}", style = MaterialTheme.typography.bodyMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s.stats.forEach { (collection, st) ->
                Text("${COLLECTION_LABELS[collection] ?: collection}: +${st.added} added, ${st.updated} updated, ${st.skipped} skipped")
            }
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit) {
    Text("Sync failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
    Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
}

@Composable
private fun Busy(label: String) {
    Spacer(Modifier.height(40.dp))
    CircularProgressIndicator()
    Text(label, style = MaterialTheme.typography.bodyMedium)
}
