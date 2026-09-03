package io.github.aedev.flow.ui.screens.playlists

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.VideoQuickActionsBottomSheet
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.CollectionEditDialog
import io.github.aedev.flow.ui.components.shared.CollectionTarget
import io.github.aedev.flow.ui.components.shared.DeleteCollectionDialog
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaRowAction
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.MergeIntoCollectionSheet
import io.github.aedev.flow.ui.components.shared.ReorderHandle
import io.github.aedev.flow.ui.components.shared.rememberDateDisplaySettings
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState
import io.github.aedev.flow.ui.components.shared.rememberReorderableLazyListState
import io.github.aedev.flow.utils.DateContext
import io.github.aedev.flow.utils.formatPremiereDate
import io.github.aedev.flow.utils.formatViewCount
import io.github.aedev.flow.utils.formatYouTubeRelativeTime

private val HeaderPadding: Dp = 16.dp
private val HeaderArtworkWidthFraction = 0.95f
private val ActionButtonSize: Dp = 48.dp
private val ActionIconSize: Dp = 24.dp
private val ListBottomPadding: Dp = 16.dp
private val PositionColumnWidth: Dp = 20.dp
private val SortSheetRowPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)
private val SortSheetBottomPadding: Dp = 24.dp
private const val ARTWORK_PLACEHOLDER_ALPHA = 0.5f

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

@Composable
private fun PlaylistDetailTopBar(
    title: String,
    showTitle: Boolean,
    inSelectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    canSelect: Boolean,
    isUserCreatedPlaylist: Boolean,
    isWatchLater: Boolean,
    isSaved: Boolean,
    isPrivate: Boolean,
    showOptionsMenu: Boolean,
    onNavigateBack: () -> Unit,
    onEnterSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMergeClick: () -> Unit,
    onSaveToggle: () -> Unit,
    onOptionsClick: () -> Unit,
    onOptionsDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
    onTogglePrivacy: () -> Unit,
) {
    FlowTopBar(
        title =
            when {
                inSelectionMode -> pluralStringResource(R.plurals.selected_count_template, selectedCount, selectedCount)
                showTitle -> title
                else -> ""
            },
        onBack = if (inSelectionMode) onClearSelection else onNavigateBack,
        actions = {
            if (inSelectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = if (allSelected) Icons.Outlined.CheckBox else Icons.Default.SelectAll,
                        contentDescription =
                            if (allSelected) {
                                stringResource(R.string.deselect_all)
                            } else {
                                stringResource(R.string.select_all)
                            },
                    )
                }
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_selected),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                if (canSelect) {
                    IconButton(onClick = onEnterSelection) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = stringResource(R.string.select_videos),
                        )
                    }
                }
                if (!isUserCreatedPlaylist) {
                    IconButton(onClick = onMergeClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(R.string.add_all_to_playlist),
                        )
                    }
                    if (!isWatchLater) {
                        IconButton(onClick = onSaveToggle) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription =
                                    if (isSaved) {
                                        stringResource(R.string.ui_remove_from_library)
                                    } else {
                                        stringResource(R.string.ui_save_to_library)
                                    },
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                    }
                }
                if (isUserCreatedPlaylist && !isWatchLater) {
                    Box {
                        IconButton(onClick = onOptionsClick) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = onOptionsDismiss,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_playlist_action)) },
                                onClick = onEditClick,
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_playlist_action)) },
                                onClick = onDeletePlaylistClick,
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isPrivate) {
                                            stringResource(R.string.make_public_action)
                                        } else {
                                            stringResource(R.string.make_private_action)
                                        },
                                    )
                                },
                                onClick = onTogglePrivacy,
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistHeader(
    name: String,
    description: String,
    videoCount: Int,
    thumbnailUrl: String,
    isPrivate: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onDownloadAll: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Float,
    currentDownloadingTitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(HeaderPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(HeaderArtworkWidthFraction)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint =
                        MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = ARTWORK_PLACEHOLDER_ALPHA),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text =
                    pluralStringResource(
                        if (isPrivate) {
                            R.plurals.playlist_metadata_private_template
                        } else {
                            R.plurals.playlist_metadata_public_template
                        },
                        videoCount,
                        videoCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier =
                        Modifier
                            .height(ActionButtonSize)
                            .weight(1f),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ActionIconSize),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.play_all),
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    onClick = onShuffle,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(ActionButtonSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                            modifier = Modifier.size(ActionIconSize),
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(ActionButtonSize),
                ) {
                    Surface(
                        onClick = { if (!isDownloading) onDownloadAll() },
                        shape = CircleShape,
                        color =
                            if (isDownloading) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        modifier = Modifier.size(ActionButtonSize),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector =
                                    if (isDownloading) {
                                        Icons.Default.Downloading
                                    } else {
                                        Icons.Default.ArrowDownward
                                    },
                                contentDescription =
                                    if (isDownloading) {
                                        stringResource(R.string.ui_downloading_playlist)
                                    } else {
                                        stringResource(R.string.download_all)
                                    },
                                tint =
                                    if (isDownloading) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        LocalContentColor.current
                                    },
                                modifier = Modifier.size(ActionIconSize),
                            )
                        }
                    }
                    if (isDownloading && downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(ActionButtonSize),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isDownloading && currentDownloadingTitle != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = stringResource(R.string.playlist_downloading_template, currentDownloadingTitle.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaylistSortButton(
    sortOrder: PlaylistSortOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(sortOrder.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = stringResource(R.string.playlist_sort_options),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSortSheet(
    selected: PlaylistSortOrder,
    onSelected: (PlaylistSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = SortSheetBottomPadding),
        ) {
            PlaylistSortOrder.entries.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(SortSheetRowPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(
                        imageVector = if (option == selected) Icons.Default.Check else Icons.Default.Sort,
                        contentDescription = null,
                        tint =
                            if (option == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistVideoRow(
    video: Video,
    position: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    canModify: Boolean,
    reorderModifier: Modifier,
    dragHandleModifier: Modifier,
    showDragHandle: Boolean,
    showAddedDate: Boolean,
    isWatchLater: Boolean,
    onChannelClick: ((String) -> Unit)?,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    var showQuickActions by remember { mutableStateOf(false) }

    MediaRow(
        title = video.title,
        modifier = modifier.then(reorderModifier),
        subtitle = video.channelName,
        supporting = video.playlistMetadataLine(showAddedDate),
        supportingColor =
            if (video.viewCount < 0L && !showAddedDate) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        selected = isSelected,
        onClick = onClick,
        onLongClick =
            if (!inSelectionMode) {
                { showQuickActions = true }
            } else {
                null
            },
        leading = {
            when {
                inSelectionMode -> {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                    )
                }

                showDragHandle -> {
                    ReorderHandle(modifier = dragHandleModifier)
                }

                else -> {
                    Text(
                        text = position.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(PositionColumnWidth),
                    )
                }
            }
        },
        trailing = {
            if (!inSelectionMode) {
                MediaRowAction(
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    onClick = { showQuickActions = true },
                )
            }
        },
    ) {
        MediaThumbnail(
            videoId = video.id,
            thumbnailUrl = video.thumbnailUrl,
            durationSeconds = video.duration,
            showWatchProgress = true,
        )
    }

    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = onChannelClick,
            onRemoveFromCollection = if (canModify) onRemove else null,
            removeFromCollectionLabel =
                if (canModify) {
                    stringResource(
                        if (isWatchLater) {
                            R.string.remove_from_watch_later
                        } else {
                            R.string.remove_from_playlist_action
                        },
                    )
                } else {
                    null
                },
            onDismiss = { showQuickActions = false },
        )
    }
}

@Composable
private fun Video.playlistMetadataLine(showAddedDate: Boolean): String {
    val addedAt = addedAtInPlaylist
    if (showAddedDate && addedAt != null) {
        return stringResource(R.string.playlist_video_added_template, formatYouTubeRelativeTime(addedAt))
    }
    if (viewCount < 0L) {
        return formatPremiereDate(uploadDate)?.let { stringResource(R.string.premiere_date_prefix, it) }
            ?: stringResource(R.string.premiere_soon)
    }
    val uploaded = rememberDateDisplaySettings().format(uploadDate, DateContext.LISTS, timestamp)
    return stringResource(
        R.string.video_metadata_short_template,
        stringResource(R.string.views_template, formatViewCount(viewCount)),
        uploaded,
    )
}

private fun PlaylistUiMessage.resolve(context: Context): String =
    when {
        pluralRes != 0 -> context.resources.getQuantityString(pluralRes, count, *args.toTypedArray())
        args.isEmpty() -> context.getString(stringRes)
        else -> context.getString(stringRes, *args.toTypedArray())
    }
