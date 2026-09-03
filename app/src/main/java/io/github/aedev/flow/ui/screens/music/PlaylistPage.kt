package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.components.ReorderHandle
import io.github.aedev.flow.ui.components.ThumbnailWatchProgress
import io.github.aedev.flow.ui.components.music.common.MusicFeedProgress
import io.github.aedev.flow.ui.components.music.common.MusicSegmentedGap
import io.github.aedev.flow.ui.components.music.common.isTrackPlaying
import io.github.aedev.flow.ui.components.music.common.musicSegmentShape
import io.github.aedev.flow.ui.components.music.common.rememberMusicCollectionColorScheme
import io.github.aedev.flow.ui.components.music.detail.PlaylistFooter
import io.github.aedev.flow.ui.components.music.detail.PlaylistHeader
import io.github.aedev.flow.ui.components.music.detail.PlaylistSearchBar
import io.github.aedev.flow.ui.components.music.detail.PlaylistTopBar
import io.github.aedev.flow.ui.components.music.item.MusicItemDensity
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.sheet.MusicMergeIntoPlaylistDialog
import io.github.aedev.flow.ui.components.music.sheet.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.rememberReorderableLazyListState
import io.github.aedev.flow.ui.theme.Dimensions
import kotlinx.coroutines.delay

private const val ITEMS_BEFORE_TRACKS = 2

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

    LaunchedEffect(displayTracks) {
        orderedDisplayTracks = displayTracks
    }

    val reorderState =
        rememberReorderableLazyListState(
            listState = scrollState,
            itemIndexOffset = ITEMS_BEFORE_TRACKS,
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

    val reachedBottom by remember {
        derivedStateOf {
            val last = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()
            last?.index != 0 && last?.index == scrollState.layoutInfo.totalItemsCount - 1
        }
    }
    LaunchedEffect(reachedBottom) {
        if (reachedBottom && playlistDetails.continuation != null) onLoadMore()
    }

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
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 }
    }

    fun closeSearch() {
        showSearchPanel = false
        searchQuery = ""
        playlistsViewModel.clearTrackSearch()
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun playAll() {
        if (orderedDisplayTracks.isNotEmpty()) {
            onTrackClick(orderedDisplayTracks.first(), orderedDisplayTracks)
        }
    }

    val mergeAction: (() -> Unit)? = if (isUserPlaylist) null else ({ showMergeDialog = true })
    val pageScheme = rememberMusicCollectionColorScheme(playlistDetails.thumbnailUrl)

    MaterialTheme(colorScheme = pageScheme) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                PlaylistTopBar(
                    showTitle = showCollapsedTopBarTitle,
                    title = playlistDetails.title,
                    onBackClick = onBackClick,
                    onPlayClick = ::playAll,
                    onShareClick = onShareClick,
                    showSearchToggle = isUserPlaylist,
                    searchActive = showSearchPanel,
                    onSearchToggle = { if (showSearchPanel) closeSearch() else showSearchPanel = true },
                    isSaved = isSaved,
                    onSaveToggle = onSaveToggle.takeIf { !isUserPlaylist },
                    onMergeClick = mergeAction,
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
                verticalArrangement = Arrangement.spacedBy(MusicSegmentedGap),
            ) {
                item(key = "header") {
                    PlaylistHeader(
                        playlistDetails = playlistDetails,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        onPlayClick = ::playAll,
                        onShuffleClick = {
                            if (orderedDisplayTracks.isNotEmpty()) {
                                val shuffled = orderedDisplayTracks.shuffled()
                                onTrackClick(shuffled.first(), shuffled)
                            }
                        },
                        onDownloadClick = {
                            if (!isDownloading) playlistsViewModel.downloadPlaylistTracks(playlistDetails)
                        },
                        onShareClick = onShareClick,
                        onArtistClick = onArtistClick,
                        isSaved = isSaved,
                        onSaveToggle = onSaveToggle.takeIf { !isUserPlaylist },
                        onMergeClick = mergeAction,
                    )
                }

                if (isUserPlaylist) {
                    item(key = "search_bar") {
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
                            onToggleSearch = { if (showSearchPanel) closeSearch() else showSearchPanel = true },
                        )
                    }
                }

                if (showSearchPanel && isUserPlaylist) {
                    if (isSearchingTracks) {
                        item(key = "search_loading") { MusicFeedProgress() }
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
                                            imageVector = Icons.Rounded.CheckCircle,
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
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        item(key = "search_empty") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_no_songs_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    val trackCount = orderedDisplayTracks.size
                    itemsIndexed(orderedDisplayTracks, key = { index, t -> "${t.videoId}_$index" }) { index, track ->
                        val isPlaying = isTrackPlaying(track.videoId)
                        MusicTrackItem(
                            track = track,
                            onClick = { onTrackClick(track, orderedDisplayTracks) },
                            modifier =
                                Modifier
                                    .padding(horizontal = Dimensions.ContentPaddingHorizontal)
                                    .then(if (isUserPlaylist) reorderState.itemModifier(index) else Modifier),
                            density = MusicItemDensity.Compact,
                            index = index + 1,
                            shape = musicSegmentShape(index = index, count = trackCount, selected = isPlaying),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            isPlaying = isPlaying,
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
                                                imageVector = Icons.Rounded.Delete,
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
                    item(key = "footer") {
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
