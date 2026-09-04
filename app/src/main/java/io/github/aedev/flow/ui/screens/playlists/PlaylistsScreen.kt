package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.library.MusicPlaylistLibraryCard
import io.github.aedev.flow.ui.components.library.PlaylistCreationFabMenu
import io.github.aedev.flow.ui.components.library.PlaylistCreationTarget
import io.github.aedev.flow.ui.components.library.PlaylistLibraryFilterRow
import io.github.aedev.flow.ui.components.library.PlaylistOwnershipFilter
import io.github.aedev.flow.ui.components.shared.CollectionEditDialog
import io.github.aedev.flow.ui.components.shared.DeleteCollectionDialog
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.screens.music.MusicPlaylistsViewModel

private val GridCellMinWidth = 160.dp
private val GridSpacing = 16.dp
private val GridContentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp)
private val FabMenuPadding = 16.dp

@Composable
fun PlaylistsScreen(
    onBackClick: () -> Unit,
    onVideoPlaylistClick: (PlaylistInfo) -> Unit,
    onMusicPlaylistClick: (PlaylistInfo) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
    musicViewModel: MusicPlaylistsViewModel = hiltViewModel(),
) {
    val videoState by viewModel.uiState.collectAsStateWithLifecycle()
    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    var contentKind by rememberSaveable { mutableStateOf(MediaKind.Videos) }
    var ownershipFilter by rememberSaveable { mutableStateOf(PlaylistOwnershipFilter.All) }
    var creationTarget by remember { mutableStateOf<PlaylistCreationTarget?>(null) }
    var videoToDelete by remember { mutableStateOf<PlaylistInfo?>(null) }
    var musicToRename by remember { mutableStateOf<PlaylistInfo?>(null) }
    var musicToDelete by remember { mutableStateOf<PlaylistInfo?>(null) }

    val visibleVideoPlaylists =
        remember(videoState.playlists, videoState.savedPlaylists, ownershipFilter) {
            ownershipFilter.select(videoState.playlists, videoState.savedPlaylists)
        }
    val visibleMusicPlaylists =
        remember(musicState.playlists, musicState.savedPlaylists, ownershipFilter) {
            ownershipFilter.select(musicState.playlists, musicState.savedPlaylists)
        }
    val ownedMusicPlaylistIds =
        remember(musicState.playlists) {
            musicState.playlists.mapTo(HashSet(), PlaylistInfo::id)
        }
    val isLoading =
        when (contentKind) {
            MediaKind.Videos -> videoState.isLoading
            MediaKind.Music -> musicState.isLoading
        }

    LaunchedEffect(contentKind) {
        if (contentKind == MediaKind.Music) {
            musicViewModel.enrichMusicPlaylistStubs()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.library_playlists_label),
                onBack = onBackClick,
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlaylistLibraryFilterRow(
                    selectedKind = contentKind,
                    onKindSelected = { contentKind = it },
                    selectedOwnership = ownershipFilter,
                    onOwnershipSelected = { ownershipFilter = it },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(GridCellMinWidth),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = GridContentPadding,
                            verticalArrangement = Arrangement.spacedBy(GridSpacing),
                            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                        ) {
                            when (contentKind) {
                                MediaKind.Videos -> {
                                    if (visibleVideoPlaylists.isEmpty()) {
                                        item(
                                            key = "empty-video-playlists",
                                            span = { GridItemSpan(maxLineSpan) },
                                            contentType = "empty",
                                        ) {
                                            FlowEmptyState(
                                                title = stringResource(R.string.no_playlists_found),
                                                icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                                            )
                                        }
                                    } else {
                                        items(
                                            items = visibleVideoPlaylists,
                                            key = { "video-${it.id}" },
                                            contentType = { "video-playlist" },
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) { playlist ->
                                            PlaylistCard(
                                                playlist = playlist,
                                                onClick = { onVideoPlaylistClick(playlist) },
                                                onDeleteClick = { videoToDelete = playlist },
                                            )
                                        }
                                    }
                                }

                                MediaKind.Music -> {
                                    if (visibleMusicPlaylists.isEmpty()) {
                                        item(
                                            key = "empty-music-playlists",
                                            span = { GridItemSpan(maxLineSpan) },
                                            contentType = "empty",
                                        ) {
                                            FlowEmptyState(
                                                title = stringResource(R.string.empty_music_playlists),
                                                icon = Icons.Default.MusicNote,
                                            )
                                        }
                                    } else {
                                        items(
                                            items = visibleMusicPlaylists,
                                            key = { "music-${it.id}" },
                                            contentType = { "music-playlist" },
                                        ) { playlist ->
                                            val isOwned = playlist.id in ownedMusicPlaylistIds
                                            MusicPlaylistLibraryCard(
                                                playlist = playlist,
                                                onClick = { onMusicPlaylistClick(playlist) },
                                                onDownload =
                                                    if (isOwned) {
                                                        { musicViewModel.downloadPlaylist(playlist) }
                                                    } else {
                                                        null
                                                    },
                                                onRename =
                                                    if (isOwned) {
                                                        { musicToRename = playlist }
                                                    } else {
                                                        null
                                                    },
                                                onDelete = { musicToDelete = playlist },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PlaylistCreationFabMenu(
                onCreateVideo = { creationTarget = PlaylistCreationTarget.Video },
                onCreateMusic = { creationTarget = PlaylistCreationTarget.Music },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(FabMenuPadding),
            )
        }
    }

    creationTarget?.let { target ->
        val isVideo = target == PlaylistCreationTarget.Video
        CollectionEditDialog(
            title =
                stringResource(
                    if (isVideo) R.string.create_new_playlist else R.string.new_playlist_button,
                ),
            confirmLabel = stringResource(R.string.create),
            icon = if (isVideo) Icons.Default.VideoLibrary else Icons.Default.MusicNote,
            onDismiss = { creationTarget = null },
            onConfirm = { name, description ->
                if (isVideo) {
                    viewModel.createPlaylist(name, description)
                } else {
                    musicViewModel.createPlaylist(name, description)
                }
                creationTarget = null
            },
        )
    }

    videoToDelete?.let { playlist ->
        DeleteCollectionDialog(
            collectionName = playlist.name,
            onDismiss = { videoToDelete = null },
            onConfirm = {
                viewModel.deletePlaylist(playlist.id)
                videoToDelete = null
            },
        )
    }

    musicToDelete?.let { playlist ->
        DeleteCollectionDialog(
            collectionName = playlist.name,
            onDismiss = { musicToDelete = null },
            onConfirm = {
                musicViewModel.deletePlaylist(playlist.id)
                musicToDelete = null
            },
        )
    }

    musicToRename?.let { playlist ->
        CollectionEditDialog(
            title = stringResource(R.string.rename_playlist_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = playlist.name,
            showDescription = false,
            onDismiss = { musicToRename = null },
            onConfirm = { name, _ ->
                musicViewModel.renamePlaylist(playlist.id, name)
                musicToRename = null
            },
        )
    }
}
