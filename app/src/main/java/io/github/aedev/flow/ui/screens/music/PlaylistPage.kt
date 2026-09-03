package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.components.music.common.MusicAmbientBackdrop
import io.github.aedev.flow.ui.components.music.common.rememberMusicCollectionColorScheme
import io.github.aedev.flow.ui.components.music.detail.PlaylistFooter
import io.github.aedev.flow.ui.components.music.detail.PlaylistHeader
import io.github.aedev.flow.ui.components.music.detail.PlaylistSearchBar
import io.github.aedev.flow.ui.components.music.detail.PlaylistTopBar
import io.github.aedev.flow.ui.components.music.item.MusicItemDensity
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.sheet.MusicMergeIntoPlaylistDialog
import io.github.aedev.flow.ui.components.music.sheet.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.shared.FlowFeedProgress
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.ReorderHandle
import io.github.aedev.flow.ui.components.shared.ThumbnailWatchProgress
import io.github.aedev.flow.ui.components.shared.flowSegmentShape
import io.github.aedev.flow.ui.components.shared.rememberReorderableLazyListState
import io.github.aedev.flow.ui.theme.Dimensions
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    val haptics = LocalHapticFeedback.current

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
    var orderedTracks by remember { mutableStateOf(displayTracks.withStableKeys()) }
    val orderedDisplayTracks = remember(orderedTracks) { orderedTracks.map { it.second } }

    LaunchedEffect(displayTracks) {
        orderedTracks = displayTracks.withStableKeys()
    }

    var pendingReorder by remember { mutableStateOf(false) }
    val reorderableState =
        rememberReorderableLazyListState(scrollState) { from, to ->
            val fromIndex = from.index - ITEMS_BEFORE_TRACKS
            val toIndex = to.index - ITEMS_BEFORE_TRACKS
            if (fromIndex in orderedTracks.indices && toIndex in orderedTracks.indices && fromIndex != toIndex) {
                orderedTracks =
                    orderedTracks.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                pendingReorder = true
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && pendingReorder) {
            pendingReorder = false
            if (isUserPlaylist) {
                playlistsViewModel.reorderTracksInPlaylist(
                    playlistDetails.id,
                    orderedTracks.map { it.second.videoId },
                )
            }
        }
    }

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
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            MusicAmbientBackdrop(thumbnailUrl = playlistDetails.thumbnailUrl)
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
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
                    verticalArrangement = Arrangement.spacedBy(FlowSegmentedGap),
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
                            onArtistClick = onArtistClick,
                            isSaved = isSaved,
                            onSaveToggle = onSaveToggle.takeIf { !isUserPlaylist },
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
                            item(key = "search_loading") { FlowFeedProgress() }
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
                        val trackCount = orderedTracks.size
                        itemsIndexed(orderedTracks, key = { _, (key, _) -> key }) { index, (key, track) ->
                            ReorderableItem(
                                state = reorderableState,
                                key = key,
                                enabled = isUserPlaylist,
                            ) {
                                MusicTrackItem(
                                    track = track,
                                    onClick = { onTrackClick(track, orderedDisplayTracks) },
                                    modifier = Modifier.padding(horizontal = Dimensions.ContentPaddingHorizontal),
                                    density = MusicItemDensity.Compact,
                                    index = index + 1,
                                    shape = flowSegmentShape(index = index, count = trackCount),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    leadingContent =
                                        if (isUserPlaylist) {
                                            {
                                                ReorderHandle(
                                                    modifier =
                                                        Modifier.draggableHandle(
                                                            onDragStarted = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            onDragStopped = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                            },
                                                        ),
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
    }

    if (showMergeDialog) {
        MusicMergeIntoPlaylistDialog(
            tracks = playlistDetails.tracks,
            playlistsViewModel = playlistsViewModel,
            onDismiss = { showMergeDialog = false },
        )
    }
}

/**
 * Pairs each track with a key that survives reordering: the id, disambiguated for duplicates.
 */
private fun List<MusicTrack>.withStableKeys(): List<Pair<String, MusicTrack>> {
    val seen = HashMap<String, Int>()
    return map { track ->
        val occurrence = (seen[track.videoId] ?: 0) + 1
        seen[track.videoId] = occurrence
        "${track.videoId}#$occurrence" to track
    }
}
