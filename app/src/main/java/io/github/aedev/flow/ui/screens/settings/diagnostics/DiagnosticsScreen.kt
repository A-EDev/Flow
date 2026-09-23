package io.github.aedev.flow.ui.screens.settings.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.info
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.FlowConnectedToggleGroup
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.screens.settings.index.DiagnosticsIndex
import io.github.aedev.flow.utils.copyPlainText
import kotlinx.coroutines.launch

private enum class DiagnosticsTab { SESSION, CRASHES }

private enum class DiagnosticsDialog { CLEAR_CRASHES, RESET_SESSION }

private val StateHeight = 200.dp

/** The device, a shareable report, the session's warnings and errors, and saved crash reports. */
@Composable
internal fun DiagnosticsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val crashes by viewModel.crashes.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(DiagnosticsTab.SESSION) }
    var dialog by rememberSaveable { mutableStateOf<DiagnosticsDialog?>(null) }

    val device = viewModel.device
    val androidValue = stringResource(R.string.settings_diagnostics_android_value, device.androidRelease, device.sdk)
    val versionValue = device.versionName?.let { "$it (${device.versionCode})" } ?: stringResource(R.string.unknown)
    val copiedMessage = stringResource(R.string.diagnostics_copied)
    val subject = stringResource(R.string.diagnostics_report_subject)
    val shareTitle = stringResource(R.string.diagnostics_share)
    val tabs =
        listOf(
            FlowToggleOption(DiagnosticsTab.SESSION, stringResource(R.string.diagnostics_tab_session), Icons.Outlined.BugReport),
            FlowToggleOption(DiagnosticsTab.CRASHES, stringResource(R.string.diagnostics_tab_crashes), Icons.Outlined.Warning),
        )

    SettingsPage(
        title = stringResource(R.string.diagnostics_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
    ) {
        group(key = "diagnostics.device", header = R.string.settings_section_this_device) {
            info(DiagnosticsIndex.model, "${device.manufacturer} ${device.model}", Icons.Outlined.PhoneAndroid)
            info(DiagnosticsIndex.android, androidValue, Icons.Outlined.Android)
            info(DiagnosticsIndex.appVersion, versionValue, Icons.Outlined.Info)
        }
        group(key = "diagnostics.report", header = R.string.settings_section_report) {
            nav(DiagnosticsIndex.copy, icon = Icons.Outlined.ContentCopy, showChevron = false, onClick = {
                scope.launch {
                    clipboard.copyPlainText(subject, viewModel.report())
                    snackbarHostState.showSnackbar(copiedMessage)
                }
            })
            nav(DiagnosticsIndex.share, icon = Icons.Outlined.Share, showChevron = false, onClick = {
                scope.launch {
                    val send =
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, subject)
                            .putExtra(Intent.EXTRA_TEXT, viewModel.report())
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }
            })
            nav(
                DiagnosticsIndex.resetSession,
                icon = Icons.Outlined.Restore,
                showChevron = false,
                onClick = { dialog = DiagnosticsDialog.RESET_SESSION },
            )
        }
        item("diagnostics.tabs") {
            FlowConnectedToggleGroup(options = tabs, selected = tab, onSelected = { tab = it })
        }
        when (tab) {
            DiagnosticsTab.SESSION -> {
                logSection("diagnostics.session", session, R.string.diagnostics_no_session_logs)
            }

            DiagnosticsTab.CRASHES -> {
                if (crashes is LogState.Lines) {
                    group(key = "diagnostics.crash_actions") {
                        nav(
                            DiagnosticsIndex.clearCrashes,
                            icon = Icons.Outlined.DeleteForever,
                            showChevron = false,
                            onClick = { dialog = DiagnosticsDialog.CLEAR_CRASHES },
                        )
                    }
                }
                logSection("diagnostics.crashes", crashes, R.string.diagnostics_no_crashes)
            }
        }
    }

    when (dialog) {
        DiagnosticsDialog.CLEAR_CRASHES -> {
            DiagnosticsConfirmDialog(
                title = stringResource(R.string.diagnostics_clear_confirm_title),
                body = stringResource(R.string.diagnostics_clear_confirm_body),
                confirm = stringResource(R.string.clear),
                destructive = true,
                onConfirm = viewModel::clearCrashes,
                onDismiss = { dialog = null },
            )
        }

        DiagnosticsDialog.RESET_SESSION -> {
            val doneMessage = stringResource(R.string.diagnostics_reset_session_done)
            DiagnosticsConfirmDialog(
                title = stringResource(R.string.diagnostics_reset_session_confirm_title),
                body = stringResource(R.string.diagnostics_reset_session_confirm_body),
                confirm = stringResource(R.string.reset),
                destructive = false,
                onConfirm = {
                    scope.launch {
                        viewModel.resetSession()
                        snackbarHostState.showSnackbar(doneMessage)
                    }
                },
                onDismiss = { dialog = null },
            )
        }

        null -> {
            Unit
        }
    }
}

private fun SettingsListScope.logSection(
    key: String,
    state: LogState,
    emptyMessage: Int,
) {
    when (state) {
        LogState.Loading -> {
            item("$key.loading") {
                Box(Modifier.fillMaxWidth().height(StateHeight), contentAlignment = Alignment.Center) { FlowLoadingIndicator() }
            }
        }

        LogState.Empty -> {
            item("$key.empty") {
                Box(Modifier.fillMaxWidth().height(StateHeight)) {
                    FlowEmptyState(title = stringResource(emptyMessage), icon = Icons.Outlined.CheckCircle)
                }
            }
        }

        is LogState.Lines -> {
            group(key = key) {
                state.chunks.forEachIndexed { index, chunk ->
                    row("$key.$index") { shape -> LogChunk(chunk, shape) }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
