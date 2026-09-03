package io.github.aedev.flow.ui.screens.playlists

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.library.PlaylistDetailTopBar
import io.github.aedev.flow.ui.components.library.PlaylistHeader
import io.github.aedev.flow.ui.components.library.PlaylistSortButton
import io.github.aedev.flow.ui.components.library.PlaylistSortOrder
import io.github.aedev.flow.ui.components.library.PlaylistSortSheet
import io.github.aedev.flow.ui.components.library.PlaylistVideoRow
import io.github.aedev.flow.ui.components.shared.CollectionEditDialog
import io.github.aedev.flow.ui.components.shared.CollectionTarget
import io.github.aedev.flow.ui.components.shared.DeleteCollectionDialog
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.MergeIntoCollectionSheet
import io.github.aedev.flow.ui.components.shared.rememberReorderableLazyListState

private val ListBottomPadding: Dp = 16.dp

@Composable
fun PlaylistDetailScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onPlayPlaylist: (List<Video>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: ((String) -> Unit)? = null,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sortedVideos by viewModel.sortedVideos.collectAsStateWithLifecycle()
    val isDownloadingPlaylist by viewModel.isDownloadingPlaylist.collectAsStateWithLifecycle()
    val playlistDownloadProgress by viewModel.playlistDownloadProgress.collectAsStateWithLifecycle()
    val currentDownloadingTitle by viewModel.currentDownloadingTitle.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val mergeTargets by viewModel.userCreatedPlaylists.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showMergeSheet by remember { mutableStateOf(false) }
    var showDownloadAllDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showRemoveSelectedDialog by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var displayVideos by remember { mutableStateOf(sortedVideos) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val isUserCreatedPlaylist = uiState.isLocalPlaylist && !uiState.isSaved
    val canReorder = isUserCreatedPlaylist && sortOrder == PlaylistSortOrder.MANUAL
    val canModify = uiState.isLocalPlaylist
    val exitSelection = {
        selectionMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(sortedVideos) {
        displayVideos = sortedVideos
        selectedIds = selectedIds.intersect(sortedVideos.mapTo(HashSet()) { it.id })
        if (sortedVideos.isEmpty()) exitSelection()
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message.resolve(context))
        }
    }

    val reorderState =
        rememberReorderableLazyListState(
            listState = listState,
            itemIndexOffset = 1,
            onMove = { from, to ->
                if (canReorder) {
                    displayVideos =
                        displayVideos.toMutableList().apply {
                            add(to, removeAt(from))
                        }
                }
            },
            onDragStopped = {
                if (canReorder) {
                    viewModel.reorderVideos(displayVideos.map { it.id })
                }
            },
        )

    BackHandler(enabled = selectionMode) { exitSelection() }

    val showCollapsedTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            PlaylistDetailTopBar(
                title = uiState.playlistName,
                showTitle = showCollapsedTitle || selectionMode,
                inSelectionMode = selectionMode,
                selectedCount = selectedIds.size,
                allSelected = selectedIds.size == displayVideos.size && displayVideos.isNotEmpty(),
                canSelect = canModify && displayVideos.isNotEmpty(),
                isUserCreatedPlaylist = isUserCreatedPlaylist,
                isWatchLater = uiState.isWatchLater,
                isSaved = uiState.isSaved,
                isPrivate = uiState.isPrivate,
                showOptionsMenu = showOptionsMenu,
                onNavigateBack = onNavigateBack,
                onEnterSelection = { selectionMode = true },
                onClearSelection = exitSelection,
                onSelectAll = {
                    selectedIds =
                        if (selectedIds.size == displayVideos.size) {
                            emptySet()
                        } else {
                            displayVideos.mapTo(HashSet()) { it.id }
                        }
                },
                onDeleteSelected = { showRemoveSelectedDialog = true },
                onMergeClick = { showMergeSheet = true },
                onSaveToggle = {
                    if (uiState.isSaved) viewModel.unsaveFromLibrary() else viewModel.saveToLibrary()
                },
                onOptionsClick = { showOptionsMenu = true },
                onOptionsDismiss = { showOptionsMenu = false },
                onEditClick = {
                    showOptionsMenu = false
                    showEditDialog = true
                },
                onDeletePlaylistClick = {
                    showOptionsMenu = false
                    showDeleteDialog = true
                },
                onTogglePrivacy = {
                    showOptionsMenu = false
                    viewModel.togglePrivacy()
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                FlowErrorState(
                    error = uiState.errorMessage.orEmpty(),
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(paddingValues),
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = ListBottomPadding),
                ) {
                    item(key = "playlist-header", contentType = "header") {
                        Column {
                            PlaylistHeader(
                                name = uiState.playlistName,
                                description = uiState.description,
                                videoCount = uiState.videos.size,
                                thumbnailUrl = displayVideos.firstOrNull()?.thumbnailUrl ?: uiState.thumbnailUrl,
                                isPrivate = uiState.isPrivate,
                                onPlayAll = {
                                    if (displayVideos.isNotEmpty()) onPlayPlaylist(displayVideos, 0)
                                },
                                onShuffle = {
                                    val shuffled = displayVideos.shuffled()
                                    if (shuffled.isNotEmpty()) onPlayPlaylist(shuffled, 0)
                                },
                                onDownloadAll = { showDownloadAllDialog = true },
                                isDownloading = isDownloadingPlaylist,
                                downloadProgress = playlistDownloadProgress,
                                currentDownloadingTitle = currentDownloadingTitle,
                            )
                            PlaylistSortButton(
                                sortOrder = sortOrder,
                                onClick = { showSortSheet = true },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (displayVideos.isEmpty()) {
                        item(key = "playlist-empty", contentType = "empty") {
                            FlowEmptyState(
                                title =
                                    stringResource(
                                        if (uiState.isWatchLater) {
                                            R.string.no_videos_saved
                                        } else {
                                            R.string.playlist_empty_title
                                        },
                                    ),
                                subtitle =
                                    stringResource(
                                        if (uiState.isWatchLater) {
                                            R.string.no_videos_saved_body
                                        } else {
                                            R.string.playlist_empty_desc
                                        },
                                    ),
                                icon =
                                    if (uiState.isWatchLater) {
                                        Icons.Default.WatchLater
                                    } else {
                                        Icons.AutoMirrored.Filled.PlaylistPlay
                                    },
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = displayVideos,
                            key = { _, video -> video.id },
                            contentType = { _, _ -> "playlist-video" },
                        ) { index, video ->
                            val isSelected = video.id in selectedIds
                            PlaylistVideoRow(
                                modifier =
                                    if (canReorder) {
                                        Modifier
                                    } else {
                                        Modifier.animateItem(
                                            fadeInSpec = tween(300, easing = EaseOutCubic),
                                            fadeOutSpec = tween(200, easing = EaseInCubic),
                                            placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                                        )
                                    },
                                video = video,
                                position = index + 1,
                                isSelected = isSelected,
                                inSelectionMode = selectionMode,
                                canModify = canModify,
                                reorderModifier = if (canReorder) reorderState.itemModifier(index) else Modifier,
                                dragHandleModifier =
                                    if (canReorder && !selectionMode) {
                                        reorderState.handleModifier(index)
                                    } else {
                                        Modifier
                                    },
                                showDragHandle = canReorder,
                                showAddedDate = isUserCreatedPlaylist,
                                isWatchLater = uiState.isWatchLater,
                                onChannelClick = onChannelClick,
                                onRemove = { viewModel.removeVideo(video.id) },
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds =
                                            if (isSelected) selectedIds - video.id else selectedIds + video.id
                                    } else {
                                        onPlayPlaylist(displayVideos, index)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && uiState.playlistName.isNotEmpty()) {
        CollectionEditDialog(
            title = stringResource(R.string.edit_playlist_action),
            confirmLabel = stringResource(R.string.action_save),
            initialName = uiState.playlistName,
            initialDescription = uiState.description,
            icon = Icons.Default.Edit,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, description, _ ->
                viewModel.updatePlaylist(name, description)
                showEditDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        DeleteCollectionDialog(
            collectionName = uiState.playlistName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deletePlaylist()
                showDeleteDialog = false
                onNavigateBack()
            },
        )
    }

    if (showMergeSheet) {
        MergeIntoCollectionSheet(
            targets =
                remember(mergeTargets) {
                    mergeTargets.map {
                        CollectionTarget(
                            id = it.id,
                            name = it.name,
                            thumbnailUrl = it.thumbnailUrl,
                            itemCount = it.videoCount,
                        )
                    }
                },
            placeholder = Icons.AutoMirrored.Filled.PlaylistPlay,
            itemCountLabel = { pluralStringResource(R.plurals.songs_count_template, it, it) },
            onSelect = { viewModel.mergeIntoPlaylist(it.id) },
            onDismiss = { showMergeSheet = false },
        )
    }

    if (showDownloadAllDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadAllDialog = false },
            icon = { Icon(Icons.Default.Download, contentDescription = null) },
            title = { Text(stringResource(R.string.download_all)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.download_all_confirmation,
                        uiState.videos.size,
                        uiState.videos.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.downloadPlaylist()
                        showDownloadAllDialog = false
                    },
                ) {
                    Text(stringResource(R.string.download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showRemoveSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveSelectedDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.remove_selected_videos_title,
                        selectedIds.size,
                        selectedIds.size,
                    ),
                )
            },
            text = {
                Text(
                    if (uiState.isWatchLater) {
                        stringResource(R.string.remove_selected_watch_later_text)
                    } else {
                        stringResource(R.string.remove_selected_playlist_text)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeVideos(selectedIds)
                        selectedIds = emptySet()
                        showRemoveSelectedDialog = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSortSheet) {
        PlaylistSortSheet(
            selected = sortOrder,
            onSelected = {
                viewModel.setSortOrder(it)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false },
        )
    }
}

private fun PlaylistUiMessage.resolve(context: Context): String =
    when {
        pluralRes != 0 -> context.resources.getQuantityString(pluralRes, count, *args.toTypedArray())
        args.isEmpty() -> context.getString(stringRes)
        else -> context.getString(stringRes, *args.toTypedArray())
    }
