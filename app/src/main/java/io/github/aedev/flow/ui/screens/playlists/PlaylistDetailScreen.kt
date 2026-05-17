package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.player.stream.AudioStreamSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore

import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.utils.formatPremiereDate

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import androidx.compose.foundation.lazy.items
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import io.github.aedev.flow.ui.components.ReorderHandle
import io.github.aedev.flow.ui.components.ThumbnailWatchProgress
import io.github.aedev.flow.ui.components.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onPlayPlaylist: (List<Video>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDownloadingPlaylist by viewModel.isDownloadingPlaylist.collectAsState()
    val playlistDownloadProgress by viewModel.playlistDownloadProgress.collectAsState()
    val currentDownloadingTitle by viewModel.currentDownloadingTitle.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showDownloadAllDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showRemoveSelectedDialog by remember { mutableStateOf(false) }
    val isUserCreatedPlaylist = uiState.isLocalPlaylist && !uiState.isSaved
    val canReorder = isUserCreatedPlaylist && sortOrder == PlaylistSortOrder.MANUAL
    val inSelectionMode = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()
    var displayVideos by remember { mutableStateOf(uiState.videos) }
    var heroTitleBottomPx by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var topBarBottomPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.videos, sortOrder) {
        displayVideos = uiState.videos.sortedForPlaylist(sortOrder)
        selectedIds = selectedIds.intersect(uiState.videos.map { it.id }.toSet())
    }

    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        itemIndexOffset = 1,
        onMove = { from, to ->
            if (canReorder) {
                displayVideos = displayVideos.toMutableList().apply {
                    add(to, removeAt(from))
                }
            }
        },
        onDragStopped = {
            if (canReorder) {
                viewModel.reorderVideos(displayVideos.map { it.id })
            }
        }
    )

    BackHandler(enabled = inSelectionMode) { selectedIds = emptySet() }

    Box(modifier = modifier.fillMaxSize()) {
        val heroThumbnail = displayVideos.firstOrNull()?.thumbnailUrl ?: uiState.thumbnailUrl
        val showCollapsedTopBarTitle by remember {
            derivedStateOf {
                heroTitleBottomPx <= topBarBottomPx
            }
        }

        if (heroThumbnail.isNotEmpty()) {
            AsyncImage(
                model = heroThumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .blur(90.dp),
                alpha = 0.55f,
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 420.dp)
                .background(MaterialTheme.colorScheme.background)
        )

        Scaffold(
            topBar = {
                PlaylistDetailTopBar(
                    title = uiState.playlistName,
                    showTitle = showCollapsedTopBarTitle,
                    inSelectionMode = inSelectionMode,
                    selectedCount = selectedIds.size,
                    allSelected = selectedIds.size == displayVideos.size && displayVideos.isNotEmpty(),
                    isUserCreatedPlaylist = isUserCreatedPlaylist,
                    isWatchLater = uiState.isWatchLater,
                    isSaved = uiState.isSaved,
                    showOptionsMenu = showOptionsMenu,
                    onNavigateBack = onNavigateBack,
                    onClearSelection = { selectedIds = emptySet() },
                    onSelectAll = {
                        selectedIds =
                            if (selectedIds.size == displayVideos.size) emptySet()
                            else displayVideos.map { it.id }.toSet()
                    },
                    onDeleteSelected = { showRemoveSelectedDialog = true },
                    onMergeClick = { showMergeDialog = true },
                    onSaveToggle = {
                        if (uiState.isSaved) viewModel.unsaveFromLibrary()
                        else viewModel.saveToLibrary()
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
                    isPrivate = uiState.isPrivate,
                    onBottomPositioned = { topBarBottomPx = it }
                )
            },
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Column {
                        PlaylistHeader(
                            name = uiState.playlistName,
                            description = uiState.description,
                            videoCount = uiState.videos.size,
                            thumbnailUrl = heroThumbnail,
                            isPrivate = uiState.isPrivate,
                            isWatchLater = uiState.isWatchLater,
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
                            onTitleBottomPositioned = { heroTitleBottomPx = it }
                        )
                        PlaylistSortButton(
                            sortOrder = sortOrder,
                            onClick = { showSortSheet = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                if (displayVideos.isEmpty()) {
                    item {
                        EmptyPlaylistState(
                            modifier = Modifier.padding(32.dp),
                            isWatchLater = uiState.isWatchLater
                        )
                    }
                } else {
                    itemsIndexed(
                        items = displayVideos,
                        key = { _, video -> video.id }
                    ) { index, video ->
                        val isSelected = video.id in selectedIds
                        PlaylistVideoItem(
                            video = video,
                            position = index + 1,
                            isSelected = isSelected,
                            inSelectionMode = inSelectionMode,
                            reorderModifier = if (canReorder) reorderState.itemModifier(index) else Modifier,
                            dragHandleModifier = if (canReorder && !inSelectionMode) reorderState.handleModifier(index) else Modifier,
                            showDragHandle = canReorder,
                            onVideoClick = {
                                if (inSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - video.id else selectedIds + video.id
                                } else {
                                    onPlayPlaylist(displayVideos, index)
                                }
                            },
                            onLongClick = {
                                if (!reorderState.isDragging) selectedIds = selectedIds + video.id
                            },
                            onRemove = { viewModel.removeVideo(video.id) },
                            isWatchLater = uiState.isWatchLater
                        )
                    }
                }
            }
        }
    }

    // Edit Dialog
    if (showEditDialog && uiState.playlistName.isNotEmpty()) {
        EditPlaylistDialog(
            name = uiState.playlistName,
            description = uiState.description,
            onDismiss = { showEditDialog = false },
            onSave = { name, description ->
                viewModel.updatePlaylist(name, description)
                showEditDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text(stringResource(R.string.delete_playlist_dialog_title)) },
            text = { Text(stringResource(R.string.delete_playlist_dialog_text, uiState.playlistName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist()
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showMergeDialog) {
        MergeIntoPlaylistDialog(
            viewModel = viewModel,
            onDismiss = { showMergeDialog = false }
        )
    }

    if (showDownloadAllDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadAllDialog = false },
            icon = { Icon(Icons.Default.Download, null) },
            title = { Text(stringResource(R.string.download_all)) },
            text = { Text(stringResource(R.string.download_all_confirmation, uiState.videos.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.downloadPlaylist()
                        showDownloadAllDialog = false
                    }
                ) {
                    Text(stringResource(R.string.download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRemoveSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveSelectedDialog = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(stringResource(R.string.remove_selected_videos_title, selectedIds.size))
            },
            text = {
                Text(
                    if (uiState.isWatchLater) stringResource(R.string.remove_selected_watch_later_text)
                    else stringResource(R.string.remove_selected_playlist_text)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeVideos(selectedIds)
                        selectedIds = emptySet()
                        showRemoveSelectedDialog = false
                    }
                ) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSortSheet) {
        PlaylistSortSheet(
            selected = sortOrder,
            onSelected = {
                viewModel.setSortOrder(it)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
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
    isUserCreatedPlaylist: Boolean,
    isWatchLater: Boolean,
    isSaved: Boolean,
    showOptionsMenu: Boolean,
    onNavigateBack: () -> Unit,
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
    isPrivate: Boolean,
    onBottomPositioned: (Int) -> Unit
) {
    val backgroundColor =
        if (inSelectionMode || showTitle) MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
        else Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onBottomPositioned(
                    (coordinates.positionInRoot().y + coordinates.size.height).toInt()
                )
            },
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = if (inSelectionMode) onClearSelection else onNavigateBack) {
                Icon(
                    imageVector = if (inSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                    contentDescription = if (inSelectionMode) "Cancel selection" else stringResource(R.string.btn_back)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (inSelectionMode || showTitle) {
                    Text(
                        text = if (inSelectionMode) stringResource(R.string.selected_count_template, selectedCount) else title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (inSelectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = if (allSelected) Icons.Outlined.CheckBox else Icons.Default.SelectAll,
                        contentDescription = if (allSelected) "Deselect all" else "Select all"
                    )
                }
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                if (!isUserCreatedPlaylist) {
                    IconButton(onClick = onMergeClick) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = stringResource(R.string.add_all_to_playlist)
                        )
                    }
                    if (!isWatchLater) {
                        IconButton(onClick = onSaveToggle) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Remove from library" else "Save to library",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                }
                if (isUserCreatedPlaylist && !isWatchLater) {
                    Box {
                        IconButton(onClick = onOptionsClick) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = onOptionsDismiss
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_playlist_action)) },
                                onClick = onEditClick,
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_playlist_action)) },
                                onClick = onDeletePlaylistClick,
                                leadingIcon = { Icon(Icons.Default.Delete, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isPrivate) stringResource(R.string.make_public_action) else stringResource(R.string.make_private_action)) },
                                onClick = onTogglePrivacy,
                                leadingIcon = {
                                    Icon(
                                        if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                        null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    name: String,
    description: String,
    videoCount: Int,
    thumbnailUrl: String,
    isPrivate: Boolean,
    isWatchLater: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onDownloadAll: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    currentDownloadingTitle: String? = null,
    onTitleBottomPositioned: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thumbnail - Hero style
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .aspectRatio(16f/9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    onTitleBottomPositioned(
                        (coordinates.positionInRoot().y + coordinates.size.height).toInt()
                    )
                },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Dynamic User Name Placeholder removed
            // Metadata Row
            Text(
                text = stringResource(
                    if (isPrivate) R.string.playlist_metadata_private_template else R.string.playlist_metadata_public_template,
                    videoCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.height(48.dp).weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play_all), fontWeight = FontWeight.Bold)
                }

                // Random (Dice) Shuffle Action
                Surface(
                    onClick = onShuffle,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle), modifier = Modifier.size(24.dp))
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        onClick = { if (!isDownloading) onDownloadAll() },
                        shape = CircleShape,
                        color = if (isDownloading)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDownloading) Icons.Default.Downloading else Icons.Default.ArrowDownward,
                                contentDescription = if (isDownloading) "Downloading playlist" else "Download all",
                                tint = if (isDownloading) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (isDownloading && downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isDownloading && currentDownloadingTitle != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Downloading: ${currentDownloadingTitle ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistSortButton(
    sortOrder: PlaylistSortOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(sortOrder.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.playlist_sort_options),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSortSheet(
    selected: PlaylistSortOrder,
    onSelected: (PlaylistSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            PlaylistSortOrder.values().forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(option) }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Icon(
                        imageVector = if (option == selected) Icons.Default.Check else Icons.Default.Sort,
                        contentDescription = null,
                        tint = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistVideoItem(
    video: Video,
    position: Int,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    reorderModifier: Modifier,
    dragHandleModifier: Modifier,
    showDragHandle: Boolean,
    onVideoClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    isWatchLater: Boolean,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val selectionBg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color.Transparent

    Row(
        modifier = modifier
            .then(reorderModifier)
            .fillMaxWidth()
            .background(selectionBg)
            .combinedClickable(
                onClick = onVideoClick,
                onLongClick = {
                    if (!inSelectionMode) onLongClick()
                }
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onVideoClick() },
                modifier = Modifier.size(24.dp)
            )
        } else if (showDragHandle) {
            ReorderHandle(
                modifier = dragHandleModifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        } else {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.width(20.dp)
            )
        }
        
        // Thumbnail
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Duration overlay
            video.duration?.let { duration ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            ThumbnailWatchProgress(
                videoId = video.id,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        // Video Info
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Column {
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val premiereDate = formatPremiereDate(video.uploadDate)
                val uploadDate = remember(video.uploadDate, video.timestamp) {
                    video.formattedPlaylistUploadDate()
                }
                Text(
                    text = if (video.viewCount < 0L)
                           premiereDate?.let { stringResource(R.string.premiere_date_prefix, it) } ?: stringResource(R.string.premiere_soon)
                           else stringResource(R.string.video_metadata_short_template, formatViewCount(video.viewCount), uploadDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (video.viewCount < 0L) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        if (!inSelectionMode) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isWatchLater) R.string.remove_from_watch_later
                                    else R.string.remove_from_playlist_action
                                )
                            )
                        },
                        onClick = {
                            onRemove()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistState(
    modifier: Modifier = Modifier,
    isWatchLater: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = if (isWatchLater) Icons.Default.WatchLater else Icons.Default.PlaylistPlay,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Text(
            text = stringResource(if (isWatchLater) R.string.no_videos_saved else R.string.playlist_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = stringResource(if (isWatchLater) R.string.no_videos_saved_body else R.string.playlist_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun EditPlaylistDialog(
    name: String,
    description: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var editedName by remember { mutableStateOf(name) }
    var editedDescription by remember { mutableStateOf(description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null) },
        title = { Text(stringResource(R.string.edit_playlist_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = editedDescription,
                    onValueChange = { editedDescription = it },
                    label = { Text(stringResource(R.string.playlist_description_optional)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editedName, editedDescription) },
                enabled = editedName.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// Helper Functions
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%d:%02d", minutes, secs)
    }
}

private fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1f%s views", count / 1_000_000_000.0, "B")
        count >= 1_000_000 -> String.format("%.1f%s views", count / 1_000_000.0, "M")
        count >= 1_000 -> String.format("%.1f%s views", count / 1_000.0, "K")
        else -> "$count views"
    }
}

enum class PlaylistSortOrder(val storageValue: String, val labelRes: Int) {
    MANUAL("manual", R.string.playlist_sort_manual),
    DATE_ADDED_NEWEST("date_added_newest", R.string.playlist_sort_date_added_newest),
    DATE_ADDED_OLDEST("date_added_oldest", R.string.playlist_sort_date_added_oldest),
    MOST_POPULAR("most_popular", R.string.playlist_sort_most_popular),
    DATE_PUBLISHED_NEWEST("date_published_newest", R.string.playlist_sort_date_published_newest),
    DATE_PUBLISHED_OLDEST("date_published_oldest", R.string.playlist_sort_date_published_oldest);

    companion object {
        fun fromStorageValue(value: String?): PlaylistSortOrder =
            values().firstOrNull { it.storageValue == value } ?: MANUAL
    }
}

private fun List<Video>.sortedForPlaylist(sortOrder: PlaylistSortOrder): List<Video> {
    return when (sortOrder) {
        PlaylistSortOrder.MANUAL,
        PlaylistSortOrder.DATE_ADDED_NEWEST -> this
        PlaylistSortOrder.DATE_ADDED_OLDEST -> asReversed()
        PlaylistSortOrder.MOST_POPULAR -> sortedByDescending { it.viewCount }
        PlaylistSortOrder.DATE_PUBLISHED_NEWEST -> sortedByDescending { it.effectivePlaylistUploadTimestamp() }
        PlaylistSortOrder.DATE_PUBLISHED_OLDEST -> sortedBy { it.effectivePlaylistUploadTimestamp() }
    }
}

private fun Video.formattedPlaylistUploadDate(now: Long = System.currentTimeMillis()): String {
    val timestamp = effectivePlaylistUploadTimestamp(now)
    return if (timestamp > 0L) formatRelativeTime(timestamp, now) else uploadDate
}

private fun Video.effectivePlaylistUploadTimestamp(now: Long = System.currentTimeMillis()): Long {
    val relativeDuration = parseRelativeDurationMillis(uploadDate)
    if (timestamp <= 0L) {
        return relativeDuration?.let { now - it } ?: 0L
    }
    if (relativeDuration == null) return timestamp

    val timestampAge = now - timestamp
    return if (timestampAge > relativeDuration + 30L * 60L * 1000L) {
        timestamp
    } else {
        now - relativeDuration
    }
}

private fun parseRelativeDurationMillis(text: String): Long? {
    val normalized = text.lowercase()
        .replace("streamed", "")
        .replace("premiered", "")
        .replace("ago", "")
        .trim()
    if (normalized.isBlank() || normalized == "unknown") return null
    if (normalized.contains("just now") || normalized == "today") return 0L
    if (normalized.contains("yesterday")) return 24L * 60L * 60L * 1000L

    val value = Regex("(\\d+)").find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: return null
    val unit = when {
        normalized.contains("second") || normalized.matches(Regex(".*\\d+\\s*s$")) -> 1_000L
        normalized.contains("minute") || normalized.matches(Regex(".*\\d+\\s*m$")) -> 60_000L
        normalized.contains("hour") || normalized.matches(Regex(".*\\d+\\s*h$")) -> 3_600_000L
        normalized.contains("day") || normalized.matches(Regex(".*\\d+\\s*d$")) -> 86_400_000L
        normalized.contains("week") || normalized.matches(Regex(".*\\d+\\s*w$")) -> 7L * 86_400_000L
        normalized.contains("month") || normalized.matches(Regex(".*\\d+\\s*mo$")) -> 30L * 86_400_000L
        normalized.contains("year") || normalized.matches(Regex(".*\\d+\\s*y$")) -> 365L * 86_400_000L
        else -> return null
    }
    return value * unit
}

private fun formatRelativeTime(timestamp: Long, now: Long): String {
    val diff = (now - timestamp).coerceAtLeast(0L)
    val seconds = diff / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    val weeks = days / 7L
    val months = days / 30L
    val years = days / 365L

    return when {
        years > 0 -> "${years}y ago"
        months > 0 -> "${months}mo ago"
        weeks > 0 -> "${weeks}w ago"
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        seconds > 10 -> "${seconds}s ago"
        else -> "Just now"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeIntoPlaylistDialog(
    viewModel: PlaylistDetailViewModel,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.userCreatedPlaylists.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.merge_playlist_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            HorizontalDivider()

            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(R.string.merge_playlist_no_playlists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = playlists,
                        key = { it.id }
                    ) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.mergeIntoPlaylist(playlist.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (playlist.thumbnailUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = playlist.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.Center),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.songs_count_template, playlist.videoCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ViewModel

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val youTubeRepository: io.github.aedev.flow.data.repository.YouTubeRepository,
    private val playerPreferences: PlayerPreferences,
    private val videoDao: VideoDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    data class UiState(
        val playlistName: String = "",
        val description: String = "",
        val isPrivate: Boolean = false,
        val videos: List<Video> = emptyList(),
        val thumbnailUrl: String = "",
        val isLocalPlaylist: Boolean = false,
        val isSaved: Boolean = false,
        val isWatchLater: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val sortOrder: StateFlow<PlaylistSortOrder> =
        playerPreferences.playlistSortOrder
            .map { PlaylistSortOrder.fromStorageValue(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlaylistSortOrder.MANUAL)

    // ── Playlist download state ──────────────────────────────────────────────
    private val _isDownloadingPlaylist = MutableStateFlow(false)
    val isDownloadingPlaylist: StateFlow<Boolean> = _isDownloadingPlaylist.asStateFlow()

    private val _playlistDownloadProgress = MutableStateFlow(0f)
    val playlistDownloadProgress: StateFlow<Float> = _playlistDownloadProgress.asStateFlow()

    private val _currentDownloadingTitle = MutableStateFlow<String?>(null)
    val currentDownloadingTitle: StateFlow<String?> = _currentDownloadingTitle.asStateFlow()

    init {
        loadPlaylist()
    }

    fun setSortOrder(order: PlaylistSortOrder) {
        viewModelScope.launch {
            playerPreferences.setPlaylistSortOrder(order.storageValue)
        }
    }

    /**
     * Download every video in this playlist sequentially via [FlowDownloadService].
     * Fetches stream info (NewPipe) for each video, picks the best 720p-compatible MP4
     * stream with a paired AAC audio track, then hands off to the background service.
     */
    fun downloadPlaylist() {
        if (_isDownloadingPlaylist.value) return

        viewModelScope.launch {
            val videos = _uiState.value.videos
            if (videos.isEmpty()) {
                Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
                return@launch
            }

            _isDownloadingPlaylist.value = true
            _playlistDownloadProgress.value = 0f
            Toast.makeText(context, "Downloading ${videos.size} videos…", Toast.LENGTH_SHORT).show()

            var successCount = 0
            var processedCount = 0
            val total = videos.size
            val preferredAudioLanguage = playerPreferences.preferredAudioLanguage.first()

            val semaphore = Semaphore(2)

            for (video in videos) {
                semaphore.withPermit {
                    _currentDownloadingTitle.value = video.title
                    try {
                        val streamInfo = withContext(Dispatchers.IO) {
                            youTubeRepository.getVideoStreamInfo(video.id)
                        }

                        if (streamInfo != null) {
                            // ── Select best video stream (prefer MP4, 720p cap for bandwidth) ──
                            val videoOnlyStreams = streamInfo.videoOnlyStreams
                                ?.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                                ?: emptyList()
                            val combinedStreams = streamInfo.videoStreams
                                ?.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                                ?: emptyList()
                            val allAudio = streamInfo.audioStreams ?: emptyList()

                            fun isMp4(s: org.schabi.newpipe.extractor.stream.VideoStream): Boolean {
                                val mime = (s.format?.mimeType ?: "").lowercase()
                                val name = (s.format?.name ?: "").lowercase()
                                return mime.contains("mp4") || name.contains("mp4") || name.contains("mpeg")
                            }

                            fun isAacAudio(a: org.schabi.newpipe.extractor.stream.AudioStream): Boolean {
                                val mime = (a.format?.mimeType ?: "").lowercase()
                                val name = (a.format?.name ?: "").lowercase()
                                return !name.contains("opus") && !name.contains("webm") &&
                                       !mime.contains("opus") && !mime.contains("webm")
                            }

                            // Prefer video-only MP4 ≤720p for offline storage efficiency
                            val bestVideoOnly = videoOnlyStreams
                                .filter { isMp4(it) && it.height <= 720 }
                                .maxByOrNull { it.height }
                                ?: videoOnlyStreams.filter { isMp4(it) }.maxByOrNull { it.height }

                            val bestCombined = combinedStreams.filter { isMp4(it) }
                                .maxByOrNull { it.height }

                            val selectedStream = bestVideoOnly ?: bestCombined
                                ?: (videoOnlyStreams + combinedStreams).maxByOrNull { it.height }

                            if (selectedStream != null) {
                                val videoUrl = selectedStream.content ?: selectedStream.url
                                val audioUrl = if (selectedStream in videoOnlyStreams) {
                                    val aac = AudioStreamSelector.selectPreferredAudioStream(
                                        streams = allAudio,
                                        preferredAudioLanguage = preferredAudioLanguage,
                                        compatibilityFilter = ::isAacAudio
                                    )
                                    aac?.content ?: aac?.url
                                } else null

                                // Detect codec so the service picks the correct container
                                // (VP9 → .webm, AV1 → .mkv, H264 → .mp4).
                                // Without this, VP9 content written into a .mp4 filename
                                // causes MediaMuxer to fail during audio/video merging.
                                val videoCodec = io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
                                    .codecKeyFromStream(selectedStream)

                                val qualityLabel = "${selectedStream.height}p"
                                val fullVideo = video.copy(
                                    thumbnailUrl = video.thumbnailUrl.ifBlank {
                                        streamInfo.thumbnails?.maxByOrNull { it.height }?.url ?: ""
                                    }
                                )

                                if (videoUrl != null) {
                                    withContext(Dispatchers.Main) {
                                        io.github.aedev.flow.data.video.downloader.FlowDownloadService.startDownload(
                                            context = context,
                                            video = fullVideo,
                                            url = videoUrl,
                                            quality = qualityLabel,
                                            audioUrl = audioUrl,
                                            videoCodec = videoCodec
                                        )
                                        successCount++
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PlaylistDetailVM", "Failed to queue download for ${video.title}", e)
                    }

                    processedCount++
                    _playlistDownloadProgress.value = processedCount.toFloat() / total
                    delay(400L)
                }
            }

            val msg = if (successCount > 0)
                "Queued $successCount/${total} downloads"
            else
                "Could not queue any downloads from this playlist"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

            _isDownloadingPlaylist.value = false
            _currentDownloadingTitle.value = null
            _playlistDownloadProgress.value = 0f
        }
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            if (playlistId == PlaylistRepository.WATCH_LATER_ID) {
                _uiState.update {
                    it.copy(
                        playlistName = context.getString(R.string.watch_later),
                        description = "",
                        isPrivate = true,
                        isLocalPlaylist = true,
                        isSaved = false,
                        isWatchLater = true
                    )
                }
                repository.getVideoOnlyWatchLaterFlow().collect { videos ->
                    _uiState.update {
                        it.copy(
                            videos = videos,
                            thumbnailUrl = videos.firstOrNull()?.thumbnailUrl.orEmpty()
                        )
                    }
                    val stubs = videos.filter { it.title.isEmpty() }.take(50)
                    if (stubs.isNotEmpty()) {
                        enrichMetadata(stubs)
                    }
                }
                return@launch
            }

            // Try Local first
            val localInfo = repository.getPlaylistInfo(playlistId)
            if (localInfo != null) {
                val isSaved = repository.isExternalPlaylistSaved(playlistId)
                _uiState.update { it.copy(
                    playlistName = localInfo.name,
                    description = localInfo.description,
                    isPrivate = localInfo.isPrivate,
                    thumbnailUrl = localInfo.thumbnailUrl,
                    isLocalPlaylist = true,
                    isSaved = isSaved,
                    isWatchLater = false
                )}
                repository.getPlaylistVideosFlow(playlistId).collect { videos ->
                    _uiState.update { it.copy(videos = videos) }
                    val stubs = videos.filter { it.title.isEmpty() }.take(50)
                    if (stubs.isNotEmpty()) {
                        enrichMetadata(stubs)
                    }
                }
            } else {
                // Try Remote (YouTube)
                try {
                    val details = youTubeRepository.getPlaylistDetails(playlistId)
                    if (details != null) {
                        _uiState.update { it.copy(
                            playlistName = details.name,
                            description = details.description ?: "",
                            isPrivate = false,
                            videos = details.videos,
                            thumbnailUrl = details.thumbnailUrl,
                            isLocalPlaylist = false,
                            isSaved = false,
                            isWatchLater = false
                        )}
                    } else {
                        // Fallback to Music Service if regular fails (e.g. music playlist)
                        val musicDetails = YouTubeMusicService.fetchPlaylistDetails(playlistId)
                        if (musicDetails != null) {
                            val videos = musicDetails.tracks.map { track ->
                                Video(
                                    id = track.videoId,
                                    title = track.title,
                                    channelName = track.artist,
                                    channelId = track.channelId,
                                    thumbnailUrl = track.thumbnailUrl,
                                    duration = track.duration,
                                    viewCount = track.views ?: 0,
                                    uploadDate = "",
                                    isMusic = true
                                )
                            }
                            _uiState.update { it.copy(
                                playlistName = musicDetails.title,
                                description = musicDetails.description ?: "",
                                isPrivate = false,
                                videos = videos,
                                thumbnailUrl = musicDetails.thumbnailUrl,
                                isLocalPlaylist = false,
                                isSaved = false,
                                isWatchLater = false
                            )}
                        }
                    }
                } catch (e: Exception) {
                    // Error handling could be added here
                }
            }
        }
    }

    fun saveToLibrary() {
        viewModelScope.launch {
            val state = _uiState.value
            repository.saveExternalVideoPlaylist(
                id = playlistId,
                name = state.playlistName,
                description = state.description,
                thumbnailUrl = state.thumbnailUrl.ifEmpty {
                    state.videos.firstOrNull()?.thumbnailUrl ?: ""
                }
            )
            state.videos.forEachIndexed { index, video ->
                repository.addVideoToPlaylist(playlistId, video)
            }
            _uiState.update { it.copy(isLocalPlaylist = true, isSaved = true) }
            android.widget.Toast.makeText(context, "Playlist saved to library", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun unsaveFromLibrary() {
        viewModelScope.launch {
            repository.unsaveExternalPlaylist(playlistId)
            _uiState.update { it.copy(isLocalPlaylist = false, isSaved = false) }
            android.widget.Toast.makeText(context, "Playlist removed from library", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun removeVideo(videoId: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, videoId)
        }
    }

    fun removeVideos(videoIds: Set<String>) {
        if (videoIds.isEmpty()) return
        viewModelScope.launch {
            videoIds.forEach { videoId ->
                repository.removeVideoFromPlaylist(playlistId, videoId)
            }
        }
    }

    fun reorderVideos(orderedVideoIds: List<String>) {
        viewModelScope.launch {
            repository.reorderVideosInPlaylist(playlistId, orderedVideoIds)
        }
    }

    fun updatePlaylist(name: String, description: String) {
        viewModelScope.launch {
            val currentInfo = repository.getPlaylistInfo(playlistId) ?: return@launch
            val videos = _uiState.value.videos
            repository.deletePlaylist(playlistId)
            repository.createPlaylist(playlistId, name, description, _uiState.value.isPrivate)
            // Re-add all videos
            videos.forEach { video ->
                repository.addVideoToPlaylist(playlistId, video)
            }
            _uiState.update { it.copy(
                playlistName = name,
                description = description
            )}
        }
    }

    fun togglePrivacy() {
        viewModelScope.launch {
            val newPrivacy = !_uiState.value.isPrivate
            updatePlaylist(_uiState.value.playlistName, _uiState.value.description)
            _uiState.update { it.copy(isPrivate = newPrivacy) }
        }
    }

    fun deletePlaylist() {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    val userCreatedPlaylists: StateFlow<List<PlaylistInfo>> =
        repository.getUserCreatedVideoPlaylistsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging.asStateFlow()

    fun mergeIntoPlaylist(targetPlaylistId: String) {
        viewModelScope.launch {
            _isMerging.value = true
            val videos = _uiState.value.videos
            try {
                repository.addVideosToPlaylist(targetPlaylistId, videos)
                val targetInfo = repository.getPlaylistInfo(targetPlaylistId)
                Toast.makeText(
                    context,
                    context.getString(R.string.merge_playlist_success, videos.size, targetInfo?.name ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("PlaylistDetailVM", "Failed to merge playlist", e)
                Toast.makeText(context, "Failed to merge playlist", Toast.LENGTH_SHORT).show()
            } finally {
                _isMerging.value = false
            }
        }
    }

    private val enrichSemaphore = Semaphore(1)

    private fun enrichMetadata(stubs: List<Video>) {
        if (!enrichSemaphore.tryAcquire()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stubs.chunked(5).forEach { chunk ->
                    chunk.forEach { stub ->
                        try {
                            val enriched = youTubeRepository.getVideo(stub.id) ?: return@forEach
                            val e = VideoEntity.fromDomain(enriched)
                            videoDao.insertVideoOrIgnore(e)
                            videoDao.updateVideoMetadata(
                                id = e.id,
                                title = e.title,
                                channelName = e.channelName,
                                channelId = e.channelId,
                                thumbnailUrl = e.thumbnailUrl,
                                duration = e.duration,
                                viewCount = e.viewCount,
                                uploadDate = e.uploadDate,
                                description = e.description,
                                channelThumbnailUrl = e.channelThumbnailUrl
                            )
                        } catch (_: Exception) {}
                    }
                    delay(300L)
                }
            } finally {
                enrichSemaphore.release()
            }
        }
    }
}
