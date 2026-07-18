package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.screens.music.MusicTrack

@Composable
fun LibraryScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToMusicPlaylists: () -> Unit,
    onNavigateToLikedVideos: () -> Unit,
    onNavigateToWatchLater: () -> Unit,
    onNavigateToSavedShorts: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLocalMedia: () -> Unit,
    onManageData: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onMusicPlaylistClick: (String) -> Unit,
    onDownloadedVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDownloadedMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    onSavedShortClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val historyTitle = stringResource(R.string.library_history_label)
    val playlistsTitle = stringResource(R.string.library_playlists_label)
    val musicPlaylistsTitle = stringResource(R.string.library_music_playlists_label)
    val likesTitle = stringResource(R.string.library_liked_videos_label)
    val downloadsTitle = stringResource(R.string.library_downloads_label)
    val watchLaterTitle = stringResource(R.string.library_watch_later_label)
    val savedShortsTitle = stringResource(R.string.library_saved_shorts_label)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { LibraryTopBar() }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "history", contentType = "media-shelf") {
                LibraryMediaShelfRoute(
                    title = historyTitle,
                    itemsFlow = viewModel.history,
                    sourceName = historyTitle,
                    onTitleClick = onNavigateToHistory,
                    onVideoClick = onVideoClick,
                    onMusicClick = onMusicClick,
                    onDownloadedVideoClick = onDownloadedVideoClick,
                    onDownloadedMusicClick = onDownloadedMusicClick
                )
            }

            item(key = "playlists", contentType = "playlist-shelf") {
                LibraryPlaylistsShelf(
                    title = playlistsTitle,
                    playlistsFlow = viewModel.playlists,
                    onTitleClick = onNavigateToPlaylists,
                    onPlaylistClick = onPlaylistClick
                )
            }

            item(key = "music-playlists", contentType = "album-shelf") {
                LibraryMusicPlaylistsShelf(
                    title = musicPlaylistsTitle,
                    playlistsFlow = viewModel.musicPlaylists,
                    onTitleClick = onNavigateToMusicPlaylists,
                    onPlaylistClick = onMusicPlaylistClick
                )
            }

            item(key = "watch-later", contentType = "video-shelf") {
                LibraryVideoShelf(
                    title = watchLaterTitle,
                    videosFlow = viewModel.watchLater,
                    onTitleClick = onNavigateToWatchLater,
                    onVideoClick = onVideoClick
                )
            }

            item(key = "likes", contentType = "media-shelf") {
                LibraryMediaShelfRoute(
                    title = likesTitle,
                    itemsFlow = viewModel.likes,
                    sourceName = likesTitle,
                    onTitleClick = onNavigateToLikedVideos,
                    onVideoClick = onVideoClick,
                    onMusicClick = onMusicClick,
                    onDownloadedVideoClick = onDownloadedVideoClick,
                    onDownloadedMusicClick = onDownloadedMusicClick
                )
            }

            item(key = "downloads", contentType = "media-shelf") {
                LibraryMediaShelfRoute(
                    title = downloadsTitle,
                    itemsFlow = viewModel.downloads,
                    sourceName = downloadsTitle,
                    onTitleClick = onNavigateToDownloads,
                    onVideoClick = onVideoClick,
                    onMusicClick = onMusicClick,
                    onDownloadedVideoClick = onDownloadedVideoClick,
                    onDownloadedMusicClick = onDownloadedMusicClick
                )
            }

            item(key = "saved-shorts", contentType = "shorts-shelf") {
                LibraryShortsShelfRoute(
                    title = savedShortsTitle,
                    shortsFlow = viewModel.savedShorts,
                    onTitleClick = onNavigateToSavedShorts,
                    onShortClick = onSavedShortClick
                )
            }

            item(key = "settings-data", contentType = "navigation-section") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.library_settings_data_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    LibraryNavigationRow(
                        icon = Icons.Outlined.PermMedia,
                        title = stringResource(R.string.library_local_media_label),
                        subtitle = stringResource(R.string.library_local_media_subtitle),
                        onClick = onNavigateToLocalMedia
                    )
                    LibraryNavigationRow(
                        icon = Icons.Outlined.Storage,
                        title = stringResource(R.string.library_manage_data_label),
                        subtitle = stringResource(R.string.library_manage_data_subtitle),
                        onClick = onManageData
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryTopBar() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
