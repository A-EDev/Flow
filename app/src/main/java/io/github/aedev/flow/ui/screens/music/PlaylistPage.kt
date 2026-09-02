package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.components.ReorderHandle
import io.github.aedev.flow.ui.components.ThumbnailWatchProgress
import io.github.aedev.flow.ui.components.music.detail.PlaylistCenteredHeader
import io.github.aedev.flow.ui.components.music.detail.PlaylistFooter
import io.github.aedev.flow.ui.components.music.detail.PlaylistSearchBar
import io.github.aedev.flow.ui.components.music.detail.PlaylistTopBar
import io.github.aedev.flow.ui.components.music.item.MusicItemDensity
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.sheet.MusicMergeIntoPlaylistDialog
import io.github.aedev.flow.ui.components.music.sheet.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import io.github.aedev.flow.ui.components.rememberReorderableLazyListState
import io.github.aedev.flow.ui.screens.playlists.PlaylistInfo
import io.github.aedev.flow.ui.theme.musicScrim
import io.github.aedev.flow.ui.theme.musicScrimContent
import io.github.aedev.flow.utils.formatDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPage(
    playlistDetails: PlaylistDetails,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onArtistClick: (String) -> Unit,
    onDownloadClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    isUserPlaylist: Boolean = false,
    isSaved: Boolean = false,
    onSaveToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    playlistsViewModel: MusicPlaylistsViewModel = hiltViewModel(),
) {
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Download
    val downloadProgress by playlistsViewModel.playlistDownloadProgress.collectAsState()
    val isDownloading by playlistsViewModel.isDownloadingPlaylist.collectAsState()

    val searchResults by playlistsViewModel.trackSearchResults.collectAsState()
    val isSearchingTracks by playlistsViewModel.isSearchingTracks.collectAsState()
    val addedTrackIds by playlistsViewModel.addedTrackIds.collectAsState()
    val locallyAddedTracks by playlistsViewModel.locallyAddedTracks.collectAsState()
    val deletedTrackIds = remember { mutableStateOf(emptySet<String>()) }
    val displayTracks =
        remember(playlistDetails.tracks, locallyAddedTracks, deletedTrackIds.value) {
            val existing = playlistDetails.tracks.map { it.videoId }.toHashSet()
            val all = playlistDetails.tracks + locallyAddedTracks.filter { it.videoId !in existing }
            all.filter { it.videoId !in deletedTrackIds.value }
        }
    var orderedDisplayTracks by remember { mutableStateOf(displayTracks) }
    var heroTitleBottomPx by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var topBarBottomPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(displayTracks) {
        orderedDisplayTracks = displayTracks
    }

    val reorderState =
        rememberReorderableLazyListState(
            listState = scrollState,
            itemIndexOffset = 3,
            onMove = { from, to ->
                orderedDisplayTracks =
                    orderedDisplayTracks.toMutableList().apply {
                        add(to, removeAt(from))
                    }
            },
            onDragStopped = {
                if (isUserPlaylist) {
                    playlistsViewModel.reorderTracksInPlaylist(
                        playlistDetails.id,
                        orderedDisplayTracks.map { it.videoId },
                    )
                }
            },
        )

    var showSearchPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    var showMergeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(350L)
            playlistsViewModel.searchTracks(searchQuery)
        } else {
            playlistsViewModel.clearTrackSearch()
        }
    }

    LaunchedEffect(showSearchPanel) {
        if (showSearchPanel) {
            delay(100L)
            searchFocusRequester.requestFocus()
            scrollState.animateScrollToItem(1)
        }
    }

    // Infinite scroll
    val reachedBottom by remember {
        derivedStateOf {
            val last = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()
            last?.index != 0 && last?.index == scrollState.layoutInfo.totalItemsCount - 1
        }
    }
    LaunchedEffect(reachedBottom) {
        if (reachedBottom && playlistDetails.continuation != null) onLoadMore()
    }

    // Bottom sheet
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }

    if (showBottomSheet && selectedTrack != null) {
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onViewArtist = {
                if (selectedTrack!!.channelId.isNotEmpty()) onArtistClick(selectedTrack!!.channelId)
            },
            onViewAlbum = {},
            onShare = {
                val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, selectedTrack!!.title)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(
                                R.string.share_message_template,
                                selectedTrack!!.title,
                                selectedTrack!!.artist,
                                selectedTrack!!.videoId,
                            ),
                        )
                    }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_song)))
            },
        )
    }

    val showCollapsedTopBarTitle by remember {
        derivedStateOf {
            heroTitleBottomPx <= topBarBottomPx
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Ambient blurred background (same as music player) ──────────────
        AsyncImage(
            model = playlistDetails.thumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .blur(120.dp),
            alpha = 0.65f,
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    musicScrim(0.45f),
                                    musicScrim(0.3f),
                                    musicScrim(0.65f),
                                    musicScrim(0.92f),
                                    MaterialTheme.colorScheme.background,
                                ),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 480.dp)
                    .background(MaterialTheme.colorScheme.background),
        )

        // ── Main content ──────────────────────────────────────────────────
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                PlaylistTopBar(
                    showTitle = showCollapsedTopBarTitle,
                    title = playlistDetails.title,
                    onBackClick = onBackClick,
                    showSearchToggle = isUserPlaylist,
                    searchActive = showSearchPanel,
                    onSearchToggle = {
                        showSearchPanel = !showSearchPanel
                        if (!showSearchPanel) {
                            searchQuery = ""
                            playlistsViewModel.clearTrackSearch()
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    },
                    showSaveButton = !isUserPlaylist,
                    isSaved = isSaved,
                    onSaveToggle = onSaveToggle,
                    showMergeButton = !isUserPlaylist,
                    onMergeClick = { showMergeDialog = true },
                    onBottomPositioned = { topBarBottomPx = it },
                )
            },
        ) { paddingValues ->
            LazyColumn(
                state = scrollState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .imePadding(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                // ── HEADER: centered poster + metadata + actions ───────────
                item {
                    PlaylistCenteredHeader(
                        playlistDetails = playlistDetails,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        onPlayClick = {
                            if (orderedDisplayTracks.isNotEmpty()) {
                                onTrackClick(orderedDisplayTracks.first(), orderedDisplayTracks)
                            }
                        },
                        onShuffleClick = {
                            if (orderedDisplayTracks.isNotEmpty()) {
                                val shuffled = orderedDisplayTracks.shuffled()
                                onTrackClick(shuffled.first(), shuffled)
                            }
                        },
                        onDownloadClick = {
                            if (!isDownloading) playlistsViewModel.downloadPlaylistTracks(playlistDetails)
                        },
                        onArtistClick = onArtistClick,
                        onTitleBottomPositioned = { heroTitleBottomPx = it },
                    )
                }

                // ── SEARCH BAR (user playlists only, inline in list) ──────
                if (isUserPlaylist) {
                    item {
                        PlaylistSearchBar(
                            query = searchQuery,
                            onQueryChange = {
                                searchQuery = it
                                if (!showSearchPanel && it.isNotBlank()) showSearchPanel = true
                            },
                            onSearch = { keyboardController?.hide() },
                            onClear = {
                                searchQuery = ""
                                playlistsViewModel.clearTrackSearch()
                            },
                            focusRequester = searchFocusRequester,
                            searchActive = showSearchPanel,
                            onActivate = { showSearchPanel = true },
                            onToggleSearch = {
                                showSearchPanel = !showSearchPanel
                                if (!showSearchPanel) {
                                    searchQuery = ""
                                    playlistsViewModel.clearTrackSearch()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            },
                        )
                    }
                }

                // ── SEARCH RESULTS (when search is active) ─────────────────
                if (showSearchPanel && isUserPlaylist) {
                    if (isSearchingTracks) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else if (searchResults.isNotEmpty()) {
                        itemsIndexed(
                            searchResults,
                            key = { index, track -> "${track.videoId}_$index" },
                        ) { _, track ->
                            val isAdded = addedTrackIds.contains(track.videoId)
                            MusicTrackItem(
                                track = track,
                                onClick = { onTrackClick(track, listOf(track)) },
                                showMenu = false,
                                trailingContent = {
                                    if (isAdded) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = stringResource(R.string.ui_added),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                playlistsViewModel.addTrackToPlaylist(playlistDetails.id, track)
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AddCircle,
                                                contentDescription = stringResource(R.string.add_to_playlist),
                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_no_songs_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                } else {
                    // ── TRACK LIST ─────────────────────────────────────────
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = musicScrimContent(0.08f),
                        )
                    }
                    itemsIndexed(orderedDisplayTracks, key = { index, t -> "${t.videoId}_$index" }) { index, track ->
                        MusicTrackItem(
                            track = track,
                            onClick = { onTrackClick(track, orderedDisplayTracks) },
                            modifier = if (isUserPlaylist) reorderState.itemModifier(index) else Modifier,
                            density = MusicItemDensity.Compact,
                            index = index + 1,
                            leadingContent =
                                if (isUserPlaylist) {
                                    {
                                        ReorderHandle(
                                            modifier = reorderState.handleModifier(index),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    null
                                },
                            thumbnailOverlay = {
                                ThumbnailWatchProgress(
                                    videoId = track.videoId,
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .height(3.dp),
                                )
                            },
                            trailingContent =
                                if (isUserPlaylist) {
                                    {
                                        IconButton(
                                            onClick = {
                                                deletedTrackIds.value = deletedTrackIds.value + track.videoId
                                                playlistsViewModel.removeTrackFromPlaylist(playlistDetails.id, track.videoId)
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.ui_delete_from_playlist),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                            onMenuClick = {
                                selectedTrack = track
                                showBottomSheet = true
                            },
                        )
                    }
                    item {
                        PlaylistFooter(
                            trackCount = playlistDetails.trackCount,
                            durationText = playlistDetails.durationText,
                            isLoadingMore = playlistDetails.continuation != null,
                        )
                    }
                }
            }
        }
    }

    if (showMergeDialog) {
        MusicMergeIntoPlaylistDialog(
            tracks = playlistDetails.tracks,
            playlistsViewModel = playlistsViewModel,
            onDismiss = { showMergeDialog = false },
        )
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

// ─── Centered Playlist Header ─────────────────────────────────────────────────

// ─── Inline Search Bar ───────────────────────────────────────

// ─── Footer ───────────────────────────────────────────────────────────────────

// ─── Helpers ──────────────────────────────────────────────────────────────────

// ─── Merge Into Playlist Dialog ─────────────────────────────────────────────────────
