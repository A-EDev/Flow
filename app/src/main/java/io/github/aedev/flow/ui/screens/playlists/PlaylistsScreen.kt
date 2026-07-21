package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.screens.music.MusicPlaylistsViewModel

@Composable
fun PlaylistsScreen(
    onBackClick: () -> Unit,
    onVideoPlaylistClick: (PlaylistInfo) -> Unit,
    onMusicPlaylistClick: (PlaylistInfo) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
    musicViewModel: MusicPlaylistsViewModel = hiltViewModel()
) {
    val videoState by viewModel.uiState.collectAsStateWithLifecycle()
    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    var contentFilter by rememberSaveable { mutableStateOf(PlaylistContentFilter.Videos) }
    var ownershipFilter by rememberSaveable { mutableStateOf(PlaylistOwnershipFilter.All) }
    var creationTarget by remember { mutableStateOf<PlaylistCreationTarget?>(null) }
    var videoToDelete by remember { mutableStateOf<PlaylistInfo?>(null) }
    var musicToRename by remember { mutableStateOf<PlaylistInfo?>(null) }
    var musicToDelete by remember { mutableStateOf<PlaylistInfo?>(null) }

    val visibleVideoPlaylists = remember(
        videoState.playlists,
        videoState.savedPlaylists,
        ownershipFilter
    ) {
        ownershipFilter.select(videoState.playlists, videoState.savedPlaylists)
    }
    val visibleMusicPlaylists = remember(
        musicState.playlists,
        musicState.savedPlaylists,
        ownershipFilter
    ) {
        ownershipFilter.select(musicState.playlists, musicState.savedPlaylists)
    }
    val ownedMusicPlaylistIds = remember(musicState.playlists) {
        musicState.playlists.mapTo(HashSet(), PlaylistInfo::id)
    }
    val isLoading = when (contentFilter) {
        PlaylistContentFilter.Videos -> videoState.isLoading
        PlaylistContentFilter.Music -> musicState.isLoading
    }

    LaunchedEffect(contentFilter) {
        if (contentFilter == PlaylistContentFilter.Music) {
            musicViewModel.enrichMusicPlaylistStubs()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            PlaylistLibraryTopBar(
                title = stringResource(R.string.library_playlists_label),
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            PlaylistCreationFabMenu(
                onCreateVideo = { creationTarget = PlaylistCreationTarget.Video },
                onCreateMusic = { creationTarget = PlaylistCreationTarget.Music }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            PlaylistLibraryFilterRow(
                selectedContent = contentFilter,
                onContentSelected = { contentFilter = it },
                selectedOwnership = ownershipFilter,
                onOwnershipSelected = { ownershipFilter = it }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (contentFilter) {
                            PlaylistContentFilter.Videos -> {
                                if (visibleVideoPlaylists.isEmpty()) {
                                    item(
                                        key = "empty-video-playlists",
                                        span = { GridItemSpan(maxLineSpan) },
                                        contentType = "empty"
                                    ) {
                                        EmptyPlaylistLibraryState(isMusic = false)
                                    }
                                } else {
                                    items(
                                        items = visibleVideoPlaylists,
                                        key = { "video-${it.id}" },
                                        contentType = { "video-playlist" },
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) { playlist ->
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = { onVideoPlaylistClick(playlist) },
                                            onDeleteClick = { videoToDelete = playlist }
                                        )
                                    }
                                }
                            }

                            PlaylistContentFilter.Music -> {
                                if (visibleMusicPlaylists.isEmpty()) {
                                    item(
                                        key = "empty-music-playlists",
                                        span = { GridItemSpan(maxLineSpan) },
                                        contentType = "empty"
                                    ) {
                                        EmptyPlaylistLibraryState(isMusic = true)
                                    }
                                } else {
                                    items(
                                        items = visibleMusicPlaylists,
                                        key = { "music-${it.id}" },
                                        contentType = { "music-playlist" }
                                    ) { playlist ->
                                        val isOwned = playlist.id in ownedMusicPlaylistIds
                                        MusicPlaylistLibraryCard(
                                            playlist = playlist,
                                            onClick = { onMusicPlaylistClick(playlist) },
                                            onDownload = if (isOwned) {
                                                { musicViewModel.downloadPlaylist(playlist) }
                                            } else {
                                                null
                                            },
                                            onRename = if (isOwned) {
                                                { musicToRename = playlist }
                                            } else {
                                                null
                                            },
                                            onDelete = { musicToDelete = playlist }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    PlaylistCreationDialogHost(
        target = creationTarget,
        onDismiss = { creationTarget = null },
        onCreateVideo = { name, description, isPrivate ->
            viewModel.createPlaylist(name, description, isPrivate)
        },
        onCreateMusic = { name, description ->
            musicViewModel.createPlaylist(name, description, isPrivate = true)
        }
    )

    PlaylistManagementDialogHost(
        videoToDelete = videoToDelete,
        musicToRename = musicToRename,
        musicToDelete = musicToDelete,
        onDismissVideoDelete = { videoToDelete = null },
        onConfirmVideoDelete = {
            viewModel.deletePlaylist(it.id)
            videoToDelete = null
        },
        onDismissMusicRename = { musicToRename = null },
        onConfirmMusicRename = { playlist, newName ->
            musicViewModel.renamePlaylist(playlist.id, newName)
            musicToRename = null
        },
        onDismissMusicDelete = { musicToDelete = null },
        onConfirmMusicDelete = {
            musicViewModel.deletePlaylist(it.id)
            musicToDelete = null
        }
    )
}

@Composable
private fun PlaylistLibraryTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmptyPlaylistLibraryState(isMusic: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = if (isMusic) Icons.Default.MusicNote else Icons.AutoMirrored.Outlined.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
        Text(
            text = stringResource(
                if (isMusic) R.string.empty_music_playlists else R.string.no_playlists_found
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
