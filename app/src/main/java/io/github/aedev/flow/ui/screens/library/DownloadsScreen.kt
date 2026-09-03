package io.github.aedev.flow.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.library.ActiveDownloadRow
import io.github.aedev.flow.ui.components.library.DownloadsEmptyState
import io.github.aedev.flow.ui.components.library.DownloadsSectionHeader
import io.github.aedev.flow.ui.components.library.MusicDownloadRow
import io.github.aedev.flow.ui.components.library.VideoDownloadRow
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.MediaKindSelector

private val ListContentPadding = PaddingValues(vertical = 8.dp)
private val ListItemSpacing = 2.dp

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
    val context = LocalContext.current

    val permissionsToRequest =
        remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            if (results.values.any { it }) viewModel.rescan()
        }

    LaunchedEffect(Unit) {
        val anyMissing =
            permissionsToRequest.any { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            }
        if (anyMissing) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            viewModel.rescan()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.downloads_title),
                onBack = onBackClick,
                actions = {
                    if (uiState.incompleteDownloadCount > 0) {
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
                            onHomeClick = onHomeClick,
                        )
                    }

                    MediaKind.Music -> {
                        MusicDownloadsList(
                            tracks = uiState.downloadedMusic,
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
        AlertDialog(
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
        AlertDialog(
            onDismissRequest = { showRemoveIncompleteDialog = false },
            title = { Text(stringResource(R.string.remove_incomplete_downloads)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.remove_incomplete_downloads_message,
                        uiState.incompleteDownloadCount,
                        uiState.incompleteDownloadCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveIncompleteDialog = false
                        viewModel.removeIncompleteDownloads()
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

private data class PendingDeletion(
    val id: String,
    val title: String,
    val kind: MediaKind,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideosDownloadsList(
    videos: List<DownloadedVideo>,
    incompleteDownloads: List<DownloadWithItems>,
    progressMap: Map<String, Float>,
    mergingVideoIds: Set<String>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDeleteClick: (String, String) -> Unit,
    onPauseClick: (String) -> Unit,
    onResumeClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (videos.isEmpty() && incompleteDownloads.isEmpty()) {
            DownloadsEmptyState(kind = MediaKind.Videos, onHomeClick = onHomeClick)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
            ) {
                if (incompleteDownloads.isNotEmpty()) {
                    item(key = "section_active", contentType = "section") {
                        DownloadsSectionHeader(
                            text = stringResource(R.string.section_incomplete_downloads),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(
                        items = incompleteDownloads,
                        key = { "active_${it.download.videoId}" },
                        contentType = { "active" },
                    ) { download ->
                        ActiveDownloadRow(
                            download = download,
                            progressMap = progressMap,
                            isMerging = download.download.videoId in mergingVideoIds,
                            onPauseClick = { onPauseClick(download.download.videoId) },
                            onResumeClick = { onResumeClick(download.download.videoId) },
                            onDeleteClick = {
                                onDeleteClick(download.download.videoId, download.download.title)
                            },
                            modifier =
                                Modifier.animateItem(
                                    fadeInSpec = tween(300, easing = EaseOutCubic),
                                    fadeOutSpec = tween(200, easing = EaseInCubic),
                                    placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                                ),
                        )
                    }
                    if (videos.isNotEmpty()) {
                        item(key = "section_completed", contentType = "section") {
                            DownloadsSectionHeader(
                                text = stringResource(R.string.section_completed),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = videos,
                    key = { _, video -> video.video.id },
                    contentType = { _, _ -> "video" },
                ) { index, video ->
                    VideoDownloadRow(
                        video = video,
                        onClick = { onVideoClick(videos, index) },
                        onDeleteClick = { onDeleteClick(video.video.id, video.video.title) },
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = tween(300, easing = EaseOutCubic),
                                fadeOutSpec = tween(200, easing = EaseInCubic),
                                placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                            ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicDownloadsList(
    tracks: List<DownloadedTrack>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    onDeleteClick: (String, String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (tracks.isEmpty()) {
            DownloadsEmptyState(kind = MediaKind.Music, onHomeClick = onHomeClick)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.track.videoId },
                    contentType = { _, _ -> "track" },
                ) { index, downloadedTrack ->
                    MusicDownloadRow(
                        downloadedTrack = downloadedTrack,
                        onClick = { onMusicClick(tracks, index) },
                        onDeleteClick = {
                            onDeleteClick(downloadedTrack.track.videoId, downloadedTrack.track.title)
                        },
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = tween(300, easing = EaseOutCubic),
                                fadeOutSpec = tween(200, easing = EaseInCubic),
                                placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                            ),
                    )
                }
            }
        }
    }
}
