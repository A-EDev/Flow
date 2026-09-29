package io.github.aedev.flow.ui.screens.settings.integrations

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ThumbDownOffAlt
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.screens.settings.SponsorModelSettingsSection
import io.github.aedev.flow.ui.screens.settings.SponsorModelViewModel
import io.github.aedev.flow.ui.screens.settings.SponsorTrainingClearDialog
import io.github.aedev.flow.ui.screens.settings.SponsorTrainingConsentDialog
import io.github.aedev.flow.ui.screens.settings.SponsorTrainingSettingsSection
import io.github.aedev.flow.ui.screens.settings.index.IntegrationsIndex
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The outside services Flow can talk to: SponsorBlock, DeArrow, Return YouTube Dislike and, in
 * builds that include it, Discord Rich Presence. Each service's first row is its own switch, and
 * the rows that depend on it wait for it.
 */
@Composable
internal fun IntegrationsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: IntegrationsViewModel = hiltViewModel(),
    sponsorModelViewModel: SponsorModelViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sponsorBlock by viewModel.sponsorBlock.collectAsStateWithLifecycle()
    val submitButton by viewModel.submitButton.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val deArrow by viewModel.deArrow.collectAsStateWithLifecycle()
    val discordState by viewModel.discordState.collectAsStateWithLifecycle()
    val sponsorModelEnabled by sponsorModelViewModel.enabled.collectAsStateWithLifecycle()
    val sponsorModelState by sponsorModelViewModel.modelState.collectAsStateWithLifecycle()
    val trainingConsent by sponsorModelViewModel.trainingConsent.collectAsStateWithLifecycle()
    val trainingStats by sponsorModelViewModel.trainingStats.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<IntegrationsDialog?>(null) }
    var colourCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showTrainingConsentDialog by rememberSaveable { mutableStateOf(false) }
    var showClearTrainingDialog by rememberSaveable { mutableStateOf(false) }
    var clearAfterExport by rememberSaveable { mutableStateOf(false) }
    val exportSuccessMessage = stringResource(R.string.sponsor_training_export_success)
    val exportFailedMessage = stringResource(R.string.sponsor_training_export_failed)
    val clearSuccessMessage = stringResource(R.string.sponsor_training_clear_success)
    val exportTrainingLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val success =
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { sponsorModelViewModel.exportTrainingData(it) }
                            ?: error("Output stream unavailable")
                    }.isSuccess
                Toast
                    .makeText(
                        context,
                        if (success) exportSuccessMessage else exportFailedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                if (success) {
                    clearAfterExport = true
                    showClearTrainingDialog = true
                }
            }
        }

    LaunchedEffect(viewModel) {
        viewModel.discordFailures.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(sponsorModelViewModel) { sponsorModelViewModel.refreshTrainingStats() }

    SettingsPage(
        title = stringResource(R.string.settings_integrations_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
    ) {
        sponsorBlockSection(
            viewModel = viewModel,
            enabled = sponsorBlock,
            submitButton = submitButton,
            userId = userId,
            segments = segments,
            onEditUserId = { dialog = IntegrationsDialog.USER_ID },
            onPickColour = { colourCategory = it },
        )
        group(key = "integrations.sponsorblock.model") {
            row("integrations.sponsorblock.model.content") {
                SponsorModelSettingsSection(
                    enabled = sponsorModelEnabled,
                    modelState = sponsorModelState,
                    onEnabledChange = sponsorModelViewModel::setEnabled,
                    onDownload = sponsorModelViewModel::download,
                    onDelete = sponsorModelViewModel::delete,
                )
            }
        }
        group(key = "integrations.sponsorblock.training") {
            row("integrations.sponsorblock.training.content") {
                SponsorTrainingSettingsSection(
                    consentEnabled = trainingConsent,
                    stats = trainingStats,
                    onConsentChange = { enabled ->
                        if (enabled) showTrainingConsentDialog = true else sponsorModelViewModel.setTrainingConsent(false)
                    },
                    onExport = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        exportTrainingLauncher.launch("flow-sponsor-training-$stamp.jsonl")
                    },
                    onClear = {
                        clearAfterExport = false
                        showClearTrainingDialog = true
                    },
                )
            }
        }
        group(key = "integrations.dearrow.group", header = R.string.player_settings_dearrow) {
            switch(IntegrationsIndex.deArrow, viewModel.deArrow, viewModel::setDeArrow, icon = Icons.Outlined.AutoFixHigh)
            switch(IntegrationsIndex.deArrowBadge, viewModel.deArrowBadge, viewModel::setDeArrowBadge, enabled = deArrow)
        }
        group(key = "integrations.dislikes.group", header = R.string.player_settings_rytd_title) {
            switch(IntegrationsIndex.dislikes, viewModel.dislikes, viewModel::setDislikes, icon = Icons.Outlined.ThumbDownOffAlt)
        }
        if (BuildConfig.UPDATER_ENABLED) {
            discordSection(
                state = discordState,
                onEnabledChange = viewModel::setDiscordEnabled,
                onConnect = { dialog = IntegrationsDialog.DISCORD_RISK },
                onUnlink = viewModel::unlinkDiscord,
                onRetry = viewModel::retryDiscord,
            )
        }
    }

    IntegrationsDialogs(
        dialog = dialog,
        userId = userId,
        viewModel = viewModel,
        onDismiss = { dialog = null },
    )
    colourCategory?.let { category ->
        SegmentColourDialog(
            category = category,
            current = segments[category]?.colorArgb,
            onSelect = { viewModel.setSegmentColor(category, it) },
            onDismiss = { colourCategory = null },
        )
    }
    if (showTrainingConsentDialog) {
        SponsorTrainingConsentDialog(
            onDismiss = { showTrainingConsentDialog = false },
            onConfirm = {
                sponsorModelViewModel.setTrainingConsent(true)
                showTrainingConsentDialog = false
            },
        )
    }
    if (showClearTrainingDialog) {
        SponsorTrainingClearDialog(
            afterExport = clearAfterExport,
            onDismiss = { showClearTrainingDialog = false },
            onConfirm = {
                sponsorModelViewModel.clearTrainingData()
                Toast.makeText(context, clearSuccessMessage, Toast.LENGTH_SHORT).show()
                showClearTrainingDialog = false
            },
        )
    }
}
