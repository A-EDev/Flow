package io.github.aedev.flow.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
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
import io.github.aedev.flow.ui.components.library.ActiveDownloadActions
import io.github.aedev.flow.ui.components.library.DownloadsStorageCard
import io.github.aedev.flow.ui.components.library.LibraryKindHeader
import io.github.aedev.flow.ui.components.library.LibrarySelection
import io.github.aedev.flow.ui.components.library.MusicDownloadsList
import io.github.aedev.flow.ui.components.library.VideosDownloadsList
import io.github.aedev.flow.ui.components.library.libraryHeaderIsOneRow
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.FlowSelectionAction
import io.github.aedev.flow.ui.components.shared.FlowSelectionToolbar
import io.github.aedev.flow.ui.components.shared.FlowSortChip
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.dismissKeyboardOnPress
import io.github.aedev.flow.ui.components.shared.flowGridColumns

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
    var selectedKind by rememberSaveable { mutableStateOf(MediaKind.Videos) }
    var pendingDeletion by remember { mutableStateOf<PendingDeletion?>(null) }
    var removeIncompleteOf by remember { mutableStateOf<MediaKind?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val exitSelection = {
        selectionMode = false
        selectedIds = emptySet()
    }
    val shownIds =
        if (selectedKind ==
            MediaKind.Videos
        ) {
            uiState.downloadedVideos.map { it.video.id }
        } else {
            uiState.downloadedMusic.map { it.track.videoId }
        }

    BackHandler(enabled = selectionMode) { exitSelection() }

    val selection =
        LibrarySelection(selectionMode, selectedIds) { id ->
            selectedIds =
                if (id in selectedIds) selectedIds - id else selectedIds + id
        }
    val activeActions =
        ActiveDownloadActions(
            onPause = viewModel::pauseVideoDownload,
            onResume = viewModel::resumeVideoDownload,
            onRetry = viewModel::retryVideoDownload,
            onCancel = { id, title -> pendingDeletion = PendingDeletion(setOf(id), title) },
            onCancelAll = { removeIncompleteOf = selectedKind },
        )
    val sortChip: @Composable () -> Unit = {
        FlowSortChip(
            options = DownloadSort.entries,
            selected = uiState.sort,
            default = DownloadSort.NEWEST,
            label = { stringResource(it.labelRes) },
            onSelected = viewModel::setSort,
        )
    }
    val sortInHeader = libraryHeaderIsOneRow()
    val listHeader: @Composable () -> Unit = {
        Column {
            DownloadsStorageCard(uiState.storage.videoBytes, uiState.storage.musicBytes, uiState.storage.freeBytes)
            if (!sortInHeader) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    sortChip()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title =
                    if (selectionMode) {
                        pluralStringResource(R.plurals.selected_count_template, selectedIds.size, selectedIds.size)
                    } else {
                        stringResource(R.string.downloads_title)
                    },
                onBack = if (selectionMode) exitSelection else onBackClick,
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { selectedIds = if (selectedIds.size == shownIds.size) emptySet() else shownIds.toSet() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
                        }
                    } else if (shownIds.isNotEmpty()) {
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(Icons.Default.Checklist, contentDescription = stringResource(R.string.select_videos))
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                LibraryKindHeader(
                    query = uiState.query,
                    onQueryChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.downloads_search_hint),
                    selectedKind = selectedKind,
                    onKindSelected = {
                        exitSelection()
                        selectedKind = it
                    },
                    trailing = if (sortInHeader) sortChip else null,
                )
                val videoColumns = flowGridColumns(compact = 1, medium = 2, expanded = 3)
                val musicColumns = flowGridColumns(compact = 1, medium = 1, expanded = 2)
                Crossfade(
                    targetState = selectedKind,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "downloads_kind",
                    modifier = Modifier.weight(1f).dismissKeyboardOnPress { focusManager.clearFocus() },
                ) { kind ->
                    when (kind) {
                        MediaKind.Videos -> {
                            VideosDownloadsList(
                                header = listHeader,
                                videos = uiState.downloadedVideos,
                                totalCount = uiState.totalVideoCount,
                                incomplete = uiState.incompleteVideoDownloads,
                                progress = uiState.progress,
                                mergingIds = uiState.mergingVideoIds,
                                columns = videoColumns,
                                query = uiState.query,
                                selection = selection,
                                activeActions = activeActions,
                                isRefreshing = uiState.isScanning,
                                onRefresh = viewModel::rescan,
                                onVideoClick = onVideoClick,
                                onDelete = { id, title -> pendingDeletion = PendingDeletion(setOf(id), title) },
                                onHomeClick = onHomeClick,
                            )
                        }

                        MediaKind.Music -> {
                            MusicDownloadsList(
                                header = listHeader,
                                tracks = uiState.downloadedMusic,
                                totalCount = uiState.totalMusicCount,
                                incomplete = uiState.incompleteMusicDownloads,
                                progress = uiState.progress,
                                columns = musicColumns,
                                query = uiState.query,
                                selection = selection,
                                activeActions = activeActions,
                                isRefreshing = uiState.isScanning,
                                onRefresh = viewModel::rescan,
                                onMusicClick = onMusicClick,
                                onHomeClick = onHomeClick,
                            )
                        }
                    }
                }
            }
            FlowSelectionToolbar(
                visible = selectionMode && selectedIds.isNotEmpty(),
                summary = pluralStringResource(R.plurals.selected_count_template, selectedIds.size, selectedIds.size),
                actions =
                    listOf(
                        FlowSelectionAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) {
                            pendingDeletion = PendingDeletion(selectedIds, title = null)
                        },
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    pendingDeletion?.let { deletion ->
        DeleteDownloadsDialog(
            deletion = deletion,
            onDismiss = { pendingDeletion = null },
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.deleteDownloads(deletion.ids)
                pendingDeletion = null
                exitSelection()
            },
        )
    }

    removeIncompleteOf?.let { kind ->
        val count = if (kind == MediaKind.Videos) uiState.incompleteVideoDownloads.size else uiState.incompleteMusicDownloads.size
        FlowAlertDialog(
            onDismissRequest = { removeIncompleteOf = null },
            title = { Text(stringResource(R.string.remove_incomplete_downloads)) },
            text = { Text(pluralStringResource(R.plurals.remove_incomplete_downloads_message, count, count)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeIncompleteDownloads(audioOnly = kind == MediaKind.Music)
                        removeIncompleteOf = null
                    },
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { removeIncompleteOf = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DeleteDownloadsDialog(
    deletion: PendingDeletion,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_download_dialog_title)) },
        text = {
            Text(
                if (deletion.title != null) {
                    stringResource(R.string.delete_download_dialog_text, deletion.title)
                } else {
                    pluralStringResource(R.plurals.delete_downloads_dialog_text, deletion.ids.size, deletion.ids.size)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What a confirmed delete removes: one download named by [title], or a selection. */
private data class PendingDeletion(
    val ids: Set<String>,
    val title: String?,
)
