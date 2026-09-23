package io.github.aedev.flow.ui.screens.settings.taste

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.backup.BackupOperation
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.SettingsTarget
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import java.time.LocalDate

private val LoadingHeight = 240.dp

private enum class ResetKind { VIDEO, MUSIC }

/** How Flow sees the viewer, with the levers that change it. */
@Composable
internal fun TasteScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    onNavigate: (SettingsTarget) -> Unit,
    onOpenRecap: () -> Unit,
    viewModel: TasteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmReset by rememberSaveable { mutableStateOf<ResetKind?>(null) }
    val resetDone = stringResource(R.string.taste_reset_done)
    var resetMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(operation) {
        val message =
            when (val current = operation) {
                is BackupOperation.Succeeded -> current.message
                is BackupOperation.Failed -> current.message
                else -> return@LaunchedEffect
            }
        viewModel.dismissOperation()
        viewModel.reload()
        snackbarHostState.showSnackbar(message)
    }
    LaunchedEffect(resetMessage) {
        resetMessage?.let {
            snackbarHostState.showSnackbar(it)
            resetMessage = null
        }
    }

    val exportVideo =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri -> uri?.let(viewModel::exportVideoProfile) }
    val exportMusic =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri -> uri?.let(viewModel::exportMusicProfile) }
    val importVideo =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importVideoProfile) }
    val importMusic =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importMusicProfile) }
    val hiddenLabel = hiddenCountLabel(state.hidden.count)
    val noQueriesLabel = stringResource(R.string.diagnostics_engine_queries_none)

    val actions =
        remember(viewModel) {
            TasteActions(
                onTopicPreferred = viewModel::setTopicPreferred,
                onBlockTopic = viewModel::blockTopic,
                onBlockChannel = viewModel::blockChannel,
                onOpenHidden = { onNavigate(SettingsTarget(SettingsDestination.HIDDEN_CONTENT)) },
                onOpenRecap = onOpenRecap,
                onExportVideo = { exportVideo.launch("flow_video_profile_${LocalDate.now()}.json") },
                onImportVideo = { importVideo.launch(arrayOf(JSON)) },
                onResetVideo = { confirmReset = ResetKind.VIDEO },
                onExportMusic = { exportMusic.launch("flow_music_profile_${LocalDate.now()}.json") },
                onImportMusic = { importMusic.launch(arrayOf(JSON)) },
                onResetMusic = { confirmReset = ResetKind.MUSIC },
            )
        }

    SettingsPage(
        title = stringResource(R.string.taste_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
    ) {
        if (state.loading) {
            item("taste.loading") { FlowLoadingIndicator(Modifier.fillMaxWidth().height(LoadingHeight)) }
        } else {
            tasteContent(state, hiddenLabel, noQueriesLabel, actions)
        }
    }

    confirmReset?.let { kind ->
        val video = kind == ResetKind.VIDEO
        val title = if (video) R.string.reset_profile_dialog_title else R.string.taste_reset_music_title
        val body = if (video) R.string.taste_reset_video_body else R.string.taste_reset_music_body
        FlowAlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (video) viewModel.resetVideoProfile() else viewModel.resetMusicProfile()
                        confirmReset = null
                        resetMessage = resetDone
                    },
                ) { Text(stringResource(R.string.reset_profile_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val JSON = "application/json"
