package io.github.aedev.flow.ui.screens.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.library.MusicDownloadsList
import io.github.aedev.flow.ui.components.library.VideosDownloadsList
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.MediaKindSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onVideoClick: (videos: List<DownloadedVideo>, startIndex: Int) -> Unit,
    onMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedKind by remember { mutableStateOf(MediaKind.Videos) }
    var showRemoveIncompleteDialog by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<PendingDeletion?>(null) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.downloads_title),
                onBack = onBackClick,
                actions = {
                    val incompleteCount =
                        if (selectedKind ==
                            MediaKind.Videos
                        ) {
                            uiState.incompleteVideoDownloads.size
                        } else {
                            uiState.incompleteMusicDownloads.size
                        }
                    if (incompleteCount > 0) {
                        IconButton(onClick = { showRemoveIncompleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.remove_incomplete_downloads),
                            )
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            MediaKindSelector(
                options = MediaKind.entries,
                selected = selectedKind,
                onSelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedKind = it
                },
                label = { stringResource(it.labelRes) },
                icon = { it.icon },
            )

            Crossfade(
                targetState = selectedKind,
                animationSpec = tween(250, easing = EaseOutCubic),
                label = "downloads_kind_crossfade",
                modifier =
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
            ) { kind ->
                when (kind) {
                    MediaKind.Videos -> {
                        VideosDownloadsList(
                            videos = uiState.downloadedVideos,
                            incompleteDownloads = uiState.incompleteVideoDownloads,
                            progressMap = uiState.downloadProgressMap,
                            mergingVideoIds = uiState.mergingVideoIds,
                            isRefreshing = uiState.isScanning,
                            onRefresh = { viewModel.rescan() },
                            onVideoClick = onVideoClick,
                            onDeleteClick = { id, title ->
                                pendingDeletion = PendingDeletion(id, title, MediaKind.Videos)
                            },
                            onPauseClick = { viewModel.pauseVideoDownload(it) },
                            onResumeClick = { viewModel.resumeVideoDownload(it) },
                            onRetryClick = { viewModel.retryVideoDownload(it) },
                            onHomeClick = onHomeClick,
                        )
                    }

                    MediaKind.Music -> {
                        MusicDownloadsList(
                            tracks = uiState.downloadedMusic,
                            incompleteDownloads = uiState.incompleteMusicDownloads,
                            progressMap = uiState.downloadProgressMap,
                            onPauseClick = { viewModel.pauseVideoDownload(it) },
                            onResumeClick = { viewModel.resumeVideoDownload(it) },
                            onRetryClick = { viewModel.retryVideoDownload(it) },
                            onCancelClick = { id, title -> pendingDeletion = PendingDeletion(id, title, MediaKind.Videos) },
                            isRefreshing = uiState.isScanning,
                            onRefresh = { viewModel.rescan() },
                            onMusicClick = onMusicClick,
                            onDeleteClick = { id, title ->
                                pendingDeletion = PendingDeletion(id, title, MediaKind.Music)
                            },
                            onHomeClick = onHomeClick,
                        )
                    }
                }
            }
        }
    }

    pendingDeletion?.let { deletion ->
        FlowAlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.delete_download_dialog_title)) },
            text = { Text(stringResource(R.string.delete_download_dialog_text, deletion.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        when (deletion.kind) {
                            MediaKind.Videos -> viewModel.deleteVideoDownload(deletion.id)
                            MediaKind.Music -> viewModel.deleteMusicDownload(deletion.id)
                        }
                        pendingDeletion = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showRemoveIncompleteDialog) {
        FlowAlertDialog(
            onDismissRequest = { showRemoveIncompleteDialog = false },
            title = { Text(stringResource(R.string.remove_incomplete_downloads)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.remove_incomplete_downloads_message,
                        incompleteCount(uiState, selectedKind),
                        incompleteCount(uiState, selectedKind),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveIncompleteDialog = false
                        viewModel.removeIncompleteDownloads(audioOnly = selectedKind == MediaKind.Music)
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveIncompleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun incompleteCount(
    state: DownloadsUiState,
    kind: MediaKind,
): Int = if (kind == MediaKind.Videos) state.incompleteVideoDownloads.size else state.incompleteMusicDownloads.size

private data class PendingDeletion(
    val id: String,
    val title: String,
    val kind: MediaKind,
)
