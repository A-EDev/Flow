package io.github.aedev.flow.ui.screens.music.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.music.common.rememberMusicCollectionColorScheme
import io.github.aedev.flow.ui.components.music.sheet.LocalMusicMenus
import io.github.aedev.flow.ui.components.music.sheet.toCollectionActionItem
import io.github.aedev.flow.ui.components.shared.CollectionTarget
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowSidePanes
import io.github.aedev.flow.ui.components.shared.MergeIntoCollectionSheet
import io.github.aedev.flow.ui.components.shared.quickactions.sharedQuickActionsViewModel
import io.github.aedev.flow.ui.components.shared.rememberFlowPaneState
import io.github.aedev.flow.ui.components.shared.rememberReorderableLazyListState
import io.github.aedev.flow.ui.screens.music.MusicPlaylistsViewModel

// The same header column as the video playlist page, so both read as one design.
private val HeaderPaneWidth = 360.dp
private const val PAGE_AHEAD_ITEMS = 4

private enum class CollectionSheet { ADD_SONGS, ADD_ALL }

/** An album or playlist page: loading, the page itself, or an error with Retry and a way back. */
@Composable
fun MusicCollectionScreen(
    onBackClick: () -> Unit,
    onTrackClick: (track: MusicTrack, queue: List<MusicTrack>, sourceName: String) -> Unit,
    onArtistClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    viewModel: MusicCollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quickActions = sharedQuickActionsViewModel()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { quickActions.announce(it.resolve(context), it.undo) }
    }
    val details = state.details
    when {
        details != null -> {
            CollectionContent(state, details, viewModel, onBackClick, onTrackClick, onArtistClick, onCollectionClick)
        }

        state.isLoading -> {
            FlowLoadingIndicator()
        }

        else -> {
            Scaffold(
                topBar = { FlowTopBar(title = "", onBack = onBackClick) },
                contentWindowInsets = WindowInsets(0.dp),
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                FlowErrorState(
                    error = stringResource(R.string.error_failed_to_load_playlist),
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun CollectionContent(
    state: MusicCollectionUiState,
    details: PlaylistDetails,
    viewModel: MusicCollectionViewModel,
    onBackClick: () -> Unit,
    onTrackClick: (track: MusicTrack, queue: List<MusicTrack>, sourceName: String) -> Unit,
    onArtistClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
) {
    val downloads: MusicPlaylistsViewModel = hiltViewModel()
    val isDownloading by downloads.isDownloadingPlaylist.collectAsStateWithLifecycle()
    val downloadProgress by downloads.playlistDownloadProgress.collectAsStateWithLifecycle()
    val musicMenus = LocalMusicMenus.current
    var sheet by rememberSaveable { mutableStateOf<CollectionSheet?>(null) }
    val listState = rememberLazyListState()
    val panes = rememberFlowPaneState()
    val twoPane = panes.showsSidePane

    var displayTracks by remember { mutableStateOf(details.tracks.withStableKeys()) }
    val reorderState =
        rememberReorderableLazyListState(
            listState = listState,
            itemIndexOffset = if (twoPane) 0 else 1,
            onMove = { from, to -> displayTracks = displayTracks.toMutableList().apply { add(to, removeAt(from)) } },
            onDragStopped = { viewModel.reorder(displayTracks.map { it.second.videoId }) },
        )
    // Held while a drag is in progress, so an update from the database can't replace the list mid-drag.
    LaunchedEffect(details.tracks, reorderState.isDragging) {
        if (!reorderState.isDragging) displayTracks = details.tracks.withStableKeys()
    }
    val tracks = remember(displayTracks) { displayTracks.map { it.second } }

    val nearEnd by remember {
        derivedStateOf {
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - PAGE_AHEAD_ITEMS
        }
    }
    LaunchedEffect(nearEnd, details.tracks.size, state.isLoadingMore) {
        if (nearEnd && details.continuation != null && !state.isLoadingMore && !state.moreFailed) viewModel.loadMore()
    }

    val play: (Int, List<MusicTrack>) -> Unit = { index, queue -> queue.getOrNull(index)?.let { onTrackClick(it, queue, details.title) } }
    val headerState = rememberCollectionHeaderState(state, tracks, downloadProgress.takeIf { isDownloading })
    val headerActions =
        CollectionHeaderActions(
            onPlay = { play(0, tracks) },
            onShuffle = { play(0, tracks.shuffled()) },
            onSaveToggle = viewModel::toggleSaved,
            onDownload = { downloads.downloadPlaylistTracks(details.copy(tracks = tracks)) },
            onShare = {},
            onAuthorClick = onArtistClick,
            menu =
                buildList {
                    if (state.isOwn) {
                        add(
                            CollectionMenuItem(
                                stringResource(R.string.ui_add_songs),
                                Icons.Rounded.Add,
                            ) { sheet = CollectionSheet.ADD_SONGS },
                        )
                    } else {
                        add(
                            CollectionMenuItem(stringResource(R.string.add_all_to_playlist), Icons.AutoMirrored.Rounded.PlaylistAdd) {
                                sheet = CollectionSheet.ADD_ALL
                            },
                        )
                    }
                },
        )
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    MaterialTheme(colorScheme = rememberMusicCollectionColorScheme(headerState.artworkUrl)) {
        Scaffold(
            topBar = { FlowTopBar(title = if (showTitle && !twoPane) details.title else "", onBack = onBackClick) },
            contentWindowInsets = WindowInsets(0.dp),
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val list: @Composable (header: (@Composable () -> Unit)?) -> Unit = { header ->
                    MusicCollectionList(
                        tracks = displayTracks,
                        mode = CollectionListMode(kind = state.kind, canReorder = state.isOwn),
                        footer =
                            CollectionFooter(
                                summary = songsSummary(tracks.size, details.durationText),
                                isLoadingMore = state.isLoadingMore,
                                moreFailed = state.moreFailed,
                                onRetryMore = viewModel::loadMore,
                            ),
                        otherVersions = details.otherVersions,
                        listState = listState,
                        reorderState = reorderState,
                        onTrackClick = { index -> play(index, tracks) },
                        onTrackMenu = musicMenus::openSong,
                        onRemove = { viewModel.removeTracks(setOf(it.videoId)) },
                        onCollectionClick = { onCollectionClick(it.id) },
                        onCollectionMenu = { musicMenus.openCollection(it.toCollectionActionItem(isAlbum = true)) },
                        header = header,
                    )
                }
                FlowSidePanes(
                    panes = panes,
                    sidePaneWidth = HeaderPaneWidth,
                    sidePane = { MusicCollectionHeaderPane(headerState, headerActions) },
                    mainPane = {
                        if (twoPane) {
                            Column { list(null) }
                        } else {
                            list { MusicCollectionHero(headerState, headerActions, sortChip = {}) }
                        }
                    },
                )
            }
        }

        when (sheet) {
            CollectionSheet.ADD_SONGS -> {
                val search by viewModel.songSearch.state.collectAsStateWithLifecycle()
                MusicAddSongsSheet(
                    search = search,
                    inPlaylist = remember(details.tracks) { details.tracks.mapTo(HashSet()) { it.videoId } },
                    onQueryChange = viewModel.songSearch::search,
                    onAdd = viewModel::addTrack,
                    onPreview = { play(0, listOf(it)) },
                    onDismiss = {
                        sheet = null
                        viewModel.songSearch.clear()
                    },
                )
            }

            CollectionSheet.ADD_ALL -> {
                val targets by viewModel.mergeTargets.collectAsStateWithLifecycle()
                MergeIntoCollectionSheet(
                    targets =
                        remember(targets) {
                            targets.map {
                                CollectionTarget(
                                    id = it.id,
                                    name = it.name,
                                    thumbnailUrl = it.thumbnailUrl,
                                    itemCount = it.videoCount,
                                )
                            }
                        },
                    placeholder = Icons.Rounded.MusicNote,
                    itemCountLabel = { pluralStringResource(R.plurals.songs_count_template, it, it) },
                    onSelect = { target -> targets.firstOrNull { it.id == target.id }?.let(viewModel::addAllTo) },
                    onDismiss = { sheet = null },
                )
            }

            null -> {
                Unit
            }
        }
    }
}

/** Pairs each song with a key that survives reordering: its id, told apart for duplicates. */
private fun List<MusicTrack>.withStableKeys(): List<Pair<String, MusicTrack>> {
    val seen = HashMap<String, Int>()
    return map { track ->
        val occurrence = (seen[track.videoId] ?: 0) + 1
        seen[track.videoId] = occurrence
        "${track.videoId}#$occurrence" to track
    }
}
