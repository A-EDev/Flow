package io.github.aedev.flow.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.sync.SyncState
import io.github.aedev.flow.sync.protocol.SyncCollection
import io.github.aedev.flow.sync.protocol.SyncRole
import io.github.aedev.flow.ui.screens.sync.QrCodeImage
import io.github.aedev.flow.ui.screens.sync.SyncViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.focus.tvInitialFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
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

private enum class TvSyncStep { CHOOSER, SEND_SELECT }

/**
 * D-pad sync flow. The TV has no camera, so it always plays host and shows the
 * pairing QR (sized for a phone camera — never full-bleed) for the phone to
 * scan, whether sending or receiving.
 */
@Composable
fun TvSyncScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(TvSyncStep.CHOOSER) }
    var selected by remember { mutableStateOf(COLLECTION_KEYS.toSet()) }
    val dimens = LocalTvDimens.current

    // BACK steps out of the flow (cancelling any live session) before the
    // shell pops the route.
    val inFlow = step != TvSyncStep.CHOOSER || state !is SyncState.Idle
    BackHandler(enabled = inFlow) {
        viewModel.cancel()
        viewModel.reset()
        step = TvSyncStep.CHOOSER
    }

    TvScreenScaffold(
        title = stringResource(R.string.sync_devices_title),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.overscanHorizontal)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                is SyncState.Idle -> when (step) {
                    TvSyncStep.CHOOSER -> TvSyncChooser(
                        onSend = { step = TvSyncStep.SEND_SELECT },
                        onReceive = { viewModel.host(SyncRole.RECEIVER, COLLECTION_KEYS) },
                    )
                    TvSyncStep.SEND_SELECT -> TvSyncSelect(
                        selected = selected,
                        onToggle = { key ->
                            selected = if (key in selected) selected - key else selected + key
                        },
                        onContinue = { viewModel.host(SyncRole.SENDER, selected.toList()) },
                    )
                }
                is SyncState.Preparing -> TvSyncBusy(stringResource(R.string.sync_preparing))
                is SyncState.Connecting -> TvSyncBusy(stringResource(R.string.sync_connecting))
                is SyncState.ShowingQr -> TvSyncQr(s)
                is SyncState.AwaitingSas -> TvSyncSas(
                    sas = s.sas,
                    onConfirm = viewModel::confirmSas,
                )
                is SyncState.AwaitingConsent -> TvSyncConsent(
                    collections = s.summary.collections,
                    onDecision = viewModel::confirmConsent,
                )
                is SyncState.Transferring -> {
                    Text(
                        text = stringResource(R.string.sync_transferring, collectionLabel(s.collection)),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val progress = if (s.total > 0) s.done.toFloat() / s.total else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.sync_progress_fraction, s.done, s.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SyncState.Done -> {
                    Text(
                        text = stringResource(R.string.sync_complete),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.sync_synced_with, s.peerName),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        s.stats.forEach { (collection, st) ->
                            Text(
                                text = stringResource(
                                    R.string.sync_done_collection_stats,
                                    collectionLabel(collection), st.added, st.updated, st.skipped,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TvButton(
                        text = stringResource(R.string.sync_done_button),
                        onClick = {
                            viewModel.reset()
                            step = TvSyncStep.CHOOSER
                            onNavigateBack()
                        },
                    )
                }
                is SyncState.Failed -> {
                    Text(
                        text = stringResource(R.string.sync_failed_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TvButton(
                        text = stringResource(R.string.sync_try_again),
                        onClick = {
                            viewModel.reset()
                            step = TvSyncStep.CHOOSER
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSyncChooser(
    onSend: () -> Unit,
    onReceive: () -> Unit,
) {
    Text(
        text = stringResource(R.string.sync_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 720.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton(
            text = stringResource(R.string.sync_send_to_device),
            onClick = onSend,
            modifier = Modifier.tvInitialFocus(),
        )
        TvButton(
            text = stringResource(R.string.sync_receive_from_device),
            onClick = onReceive,
        )
    }
    Text(
        text = stringResource(R.string.sync_qr_network_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TvSyncSelect(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Text(
        text = stringResource(R.string.sync_choose_what_to_send),
        style = MaterialTheme.typography.titleLarge,
    )
    Column(
        modifier = Modifier.widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        COLLECTION_KEYS.forEach { key ->
            TvSelectionRow(
                label = collectionLabel(key),
                selected = key in selected,
                onClick = { onToggle(key) },
            )
        }
    }
    Text(
        text = stringResource(R.string.sync_safety_backup_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (selected.isNotEmpty()) {
        TvButton(
            text = stringResource(R.string.sync_continue),
            onClick = onContinue,
        )
    }
}

@Composable
private fun TvSyncQr(s: SyncState.ShowingQr) {
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(s.expiresAtEpochSeconds) {
        while (true) {
            remaining = (s.expiresAtEpochSeconds - System.currentTimeMillis() / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvInitialFocus()
            .focusable(),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (s.sending) {
                    stringResource(R.string.sync_qr_scan_on_target_sending)
                } else {
                    stringResource(R.string.sync_qr_scan_on_target_receiving)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.sync_confirmation_code, s.sas),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(R.string.sync_expires_in, remaining),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.sync_qr_network_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Phone-camera sized: a fixed compact code on a white quiet zone —
        // full-bleed QRs overflow the TV and can't be scanned.
        Surface(color = Color.White, shape = MaterialTheme.shapes.large) {
            QrCodeImage(
                text = s.qrText,
                modifier = Modifier
                    .padding(20.dp)
                    .size(280.dp),
            )
        }
    }
}

@Composable
private fun TvSyncSas(sas: String, onConfirm: (Boolean) -> Unit) {
    Text(
        text = stringResource(R.string.sync_sas_title),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        text = sas,
        style = MaterialTheme.typography.displaySmall,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        text = stringResource(R.string.sync_sas_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 720.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton(
            text = stringResource(R.string.sync_sas_match),
            onClick = { onConfirm(true) },
        )
        TvButton(
            text = stringResource(R.string.sync_sas_differ),
            onClick = { onConfirm(false) },
        )
    }
}

@Composable
private fun TvSyncConsent(collections: List<String>, onDecision: (Boolean) -> Unit) {
    Text(
        text = stringResource(R.string.sync_consent_title),
        style = MaterialTheme.typography.titleLarge,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        collections.forEach {
            Text(
                text = stringResource(R.string.sync_bullet_item, collectionLabel(it)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    Text(
        text = stringResource(R.string.sync_consent_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 720.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton(
            text = stringResource(R.string.sync_merge),
            onClick = { onDecision(true) },
        )
        TvButton(
            text = stringResource(R.string.sync_decline),
            onClick = { onDecision(false) },
        )
    }
}

@Composable
private fun TvSyncBusy(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 24.dp),
    ) {
        CircularProgressIndicator()
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
