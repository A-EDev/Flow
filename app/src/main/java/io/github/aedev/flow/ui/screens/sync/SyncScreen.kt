package io.github.aedev.flow.ui.screens.sync

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.sync.SyncState
import io.github.aedev.flow.sync.protocol.SyncRole

/** Where the user is in the pre-session setup. Once a session starts, [SyncState] drives the UI. */
private enum class Step { CHOOSER, SEND_SELECT, SEND_TRANSPORT, SEND_SCAN, RECEIVE_TRANSPORT, RECEIVE_SCAN }

/** The step to return to, or null when there is nothing left to back out of but the screen itself. */
private fun Step.previous(): Step? =
    when (this) {
        Step.CHOOSER -> null
        Step.SEND_SELECT -> Step.CHOOSER
        Step.SEND_TRANSPORT -> Step.SEND_SELECT
        Step.SEND_SCAN -> Step.SEND_TRANSPORT
        Step.RECEIVE_TRANSPORT -> Step.CHOOSER
        Step.RECEIVE_SCAN -> Step.RECEIVE_TRANSPORT
    }

@Composable
private fun Step.title(): String =
    when (this) {
        Step.CHOOSER -> stringResource(R.string.sync_devices_title)
        Step.SEND_SELECT -> stringResource(R.string.sync_choose_what_to_send)
        Step.SEND_TRANSPORT, Step.RECEIVE_TRANSPORT -> stringResource(R.string.sync_step_title_pair)
        Step.SEND_SCAN, Step.RECEIVE_SCAN -> stringResource(R.string.sync_step_title_scan)
    }

/** Identifies the visible step for the cross-fade, without the volatile parts of the state. */
private fun SyncState.screenKey(step: Step): String =
    when (this) {
        is SyncState.Idle -> "idle:${step.name}"
        is SyncState.Preparing -> "preparing"
        is SyncState.Connecting -> "connecting"
        is SyncState.ShowingQr -> "qr"
        is SyncState.AwaitingSas -> "sas"
        is SyncState.AwaitingConsent -> "consent"
        is SyncState.Transferring -> "transferring"
        is SyncState.Done -> "done"
        is SyncState.Failed -> "failed"
    }

@Composable
private fun SyncState.title(step: Step): String =
    when (this) {
        is SyncState.Idle -> step.title()
        is SyncState.Preparing, is SyncState.Connecting -> stringResource(R.string.sync_devices_title)
        is SyncState.ShowingQr -> stringResource(R.string.sync_step_title_code)
        is SyncState.AwaitingSas -> stringResource(R.string.sync_step_title_verify)
        is SyncState.AwaitingConsent -> stringResource(R.string.sync_step_title_merge)
        is SyncState.Transferring -> stringResource(R.string.sync_step_title_syncing)
        is SyncState.Done -> stringResource(R.string.sync_step_title_syncing)
        is SyncState.Failed -> stringResource(R.string.sync_failed_title)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(Step.CHOOSER) }
    var selected by remember { mutableStateOf(COLLECTION_KEYS.toSet()) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun restart() {
        viewModel.reset()
        step = Step.CHOOSER
    }

    // Back steps through the flow rather than abandoning it: from any live session it cancels back
    // to the chooser, and only the chooser itself leaves the screen.
    fun goBack() {
        when (state) {
            is SyncState.Idle -> {
                step.previous()?.let { step = it } ?: onNavigateBack()
            }

            is SyncState.Done -> {
                restart()
                onNavigateBack()
            }

            is SyncState.Failed -> {
                restart()
            }

            else -> {
                viewModel.cancel()
                step = Step.CHOOSER
            }
        }
    }

    BackHandler(onBack = ::goBack)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The app shell's Scaffold already pads for WindowInsets.systemBars, so both this Scaffold
        // and the bar itself must consume nothing — otherwise the status-bar inset is applied twice
        // and leaves an empty band above the title.
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(state.title(step)) },
                navigationIcon = {
                    IconButton(onClick = ::goBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Keyed on which step is showing, not on the state itself: `Transferring` changes on
            // every progress tick and would otherwise cross-fade the screen a few times a second.
            AnimatedContent(
                targetState = state.screenKey(step),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "syncStep",
                contentAlignment = Alignment.TopCenter,
            ) { _ ->
                Column(
                    // Capped so the flow stays a readable column on tablets and unfolded devices
                    // instead of stretching every card the full width of the display.
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SyncStepContent(
                        state = state,
                        step = step,
                        selected = selected,
                        onStepChange = { step = it },
                        onSelectedChange = { selected = it },
                        onHost = { role -> viewModel.host(role, selected.toList()) },
                        onJoin = { role, qr -> viewModel.join(role, qr, selected.toList()) },
                        onCancel = {
                            viewModel.cancel()
                            step = Step.CHOOSER
                        },
                        onConfirmSas = viewModel::confirmSas,
                        onConfirmConsent = viewModel::confirmConsent,
                        onFinish = {
                            restart()
                            onNavigateBack()
                        },
                        onRetry = ::restart,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStepContent(
    state: SyncState,
    step: Step,
    selected: Set<String>,
    onStepChange: (Step) -> Unit,
    onSelectedChange: (Set<String>) -> Unit,
    onHost: (SyncRole) -> Unit,
    onJoin: (SyncRole, String) -> Unit,
    onCancel: () -> Unit,
    onConfirmSas: (Boolean) -> Unit,
    onConfirmConsent: (Boolean) -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is SyncState.Idle -> {
            SyncSetupStep(
                step = step,
                selected = selected,
                onStepChange = onStepChange,
                onSelectedChange = onSelectedChange,
                onHost = onHost,
                onJoin = onJoin,
            )
        }

        is SyncState.Preparing -> {
            SyncBusyContent(stringResource(R.string.sync_preparing))
        }

        is SyncState.Connecting -> {
            SyncBusyContent(stringResource(R.string.sync_connecting))
        }

        is SyncState.ShowingQr -> {
            SyncQrContent(state, onCancel = onCancel)
        }

        is SyncState.AwaitingSas -> {
            SyncSasContent(state.sas, onConfirm = onConfirmSas)
        }

        is SyncState.AwaitingConsent -> {
            SyncConsentContent(
                collections = state.summary.collections,
                onDecision = onConfirmConsent,
            )
        }

        is SyncState.Transferring -> {
            SyncTransferContent(state)
        }

        is SyncState.Done -> {
            SyncDoneContent(state, onDone = onFinish)
        }

        is SyncState.Failed -> {
            SyncFailedContent(state.message, onRetry = onRetry)
        }
    }
}

@Composable
private fun SyncSetupStep(
    step: Step,
    selected: Set<String>,
    onStepChange: (Step) -> Unit,
    onSelectedChange: (Set<String>) -> Unit,
    onHost: (SyncRole) -> Unit,
    onJoin: (SyncRole, String) -> Unit,
) {
    when (step) {
        Step.CHOOSER -> {
            SyncChooserContent(
                onSend = { onStepChange(Step.SEND_SELECT) },
                onReceive = { onStepChange(Step.RECEIVE_TRANSPORT) },
            )
        }

        Step.SEND_SELECT -> {
            SyncSelectContent(
                selected = selected,
                onSelectedChange = onSelectedChange,
                onContinue = { onStepChange(Step.SEND_TRANSPORT) },
            )
        }

        Step.SEND_TRANSPORT -> {
            SyncTransportContent(
                showQrLabel = stringResource(R.string.sync_show_qr_here),
                showQrHint = stringResource(R.string.sync_send_show_qr_hint),
                scanLabel = stringResource(R.string.sync_scan_other_qr),
                scanHint = stringResource(R.string.sync_send_scan_hint),
                onShowQr = { onHost(SyncRole.SENDER) },
                onScan = { onStepChange(Step.SEND_SCAN) },
            )
        }

        Step.SEND_SCAN -> {
            SyncScanContent(
                prompt = stringResource(R.string.sync_scan_prompt_receive_code),
                onScanned = { onJoin(SyncRole.SENDER, it) },
            )
        }

        Step.RECEIVE_TRANSPORT -> {
            SyncTransportContent(
                showQrLabel = stringResource(R.string.sync_scan_other_qr),
                showQrHint = stringResource(R.string.sync_receive_scan_hint),
                scanLabel = stringResource(R.string.sync_show_qr_here),
                scanHint = stringResource(R.string.sync_receive_show_qr_hint),
                onShowQr = { onStepChange(Step.RECEIVE_SCAN) },
                onScan = { onHost(SyncRole.RECEIVER) },
            )
        }

        Step.RECEIVE_SCAN -> {
            SyncScanContent(
                prompt = stringResource(R.string.sync_scan_prompt_send_code),
                onScanned = { onJoin(SyncRole.RECEIVER, it) },
            )
        }
    }
}
