package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.OnTabReselected
import io.github.aedev.flow.ui.components.layout.navigation.FlowTab
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

private val ListContentPadding = PaddingValues(vertical = 12.dp)
private val ShelfSpacing = 24.dp

@Composable
fun LibraryScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
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
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val shortsEnabled by viewModel.shortsEnabled.collectAsStateWithLifecycle()
    val shelfPreviewsEnabled by viewModel.shelfPreviewsEnabled.collectAsStateWithLifecycle()
    val isLibraryEmpty by viewModel.isLibraryEmpty.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    OnTabReselected(FlowTab.Library.route) { listState.animateScrollToItem(0) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { FlowTopBar(title = stringResource(R.string.library)) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(ShelfSpacing),
        ) {
            if (shelfPreviewsEnabled && isLibraryEmpty) {
                item(key = "library-empty", contentType = "empty") {
                    FlowEmptyState(
                        title = stringResource(R.string.library_empty_title),
                        subtitle = stringResource(R.string.library_empty_body),
                        icon = Icons.Outlined.VideoLibrary,
                    )
                }
            } else if (shelfPreviewsEnabled) {
                libraryShelves(
                    viewModel = viewModel,
                    shortsEnabled = shortsEnabled,
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToPlaylists = onNavigateToPlaylists,
                    onNavigateToLikedVideos = onNavigateToLikedVideos,
                    onNavigateToWatchLater = onNavigateToWatchLater,
                    onNavigateToSavedShorts = onNavigateToSavedShorts,
                    onNavigateToDownloads = onNavigateToDownloads,
                    onVideoClick = onVideoClick,
                    onMusicClick = onMusicClick,
                    onPlaylistClick = onPlaylistClick,
                    onMusicPlaylistClick = onMusicPlaylistClick,
                    onDownloadedVideoClick = onDownloadedVideoClick,
                    onDownloadedMusicClick = onDownloadedMusicClick,
                    onSavedShortClick = onSavedShortClick,
                )
            } else {
                item(key = "sections", contentType = "navigation-section") {
                    val counts by viewModel.counts.collectAsStateWithLifecycle()
                    LibrarySectionList(
                        counts = counts,
                        shortsEnabled = shortsEnabled,
                        onNavigateToHistory = onNavigateToHistory,
                        onNavigateToPlaylists = onNavigateToPlaylists,
                        onNavigateToLikedVideos = onNavigateToLikedVideos,
                        onNavigateToWatchLater = onNavigateToWatchLater,
                        onNavigateToSavedShorts = onNavigateToSavedShorts,
                        onNavigateToDownloads = onNavigateToDownloads,
                    )
                }
            }

            item(key = "settings-data", contentType = "navigation-section") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LibrarySectionHeader(stringResource(R.string.library_settings_data_header))
                    LibrarySectionRow(
                        section = LibrarySection.LOCAL_MEDIA,
                        counts = null,
                        onClick = onNavigateToLocalMedia,
                    )
                    LibrarySectionRow(
                        section = LibrarySection.SETTINGS,
                        counts = null,
                        onClick = onManageData,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun LazyListScope.libraryShelves(
    viewModel: LibraryViewModel,
    shortsEnabled: Boolean,
    onNavigateToHistory: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLikedVideos: () -> Unit,
    onNavigateToWatchLater: () -> Unit,
    onNavigateToSavedShorts: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onMusicPlaylistClick: (String) -> Unit,
    onDownloadedVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDownloadedMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    onSavedShortClick: (Video) -> Unit,
) {
    item(key = "history", contentType = "media-shelf") {
        LibraryMediaShelfRoute(
            section = LibrarySection.HISTORY,
            itemsFlow = viewModel.history,
            onTitleClick = onNavigateToHistory,
            onVideoClick = onVideoClick,
            onMusicClick = onMusicClick,
            onDownloadedVideoClick = onDownloadedVideoClick,
            onDownloadedMusicClick = onDownloadedMusicClick,
        )
    }

    item(key = "playlists", contentType = "playlist-shelf") {
        LibraryPlaylistsShelf(
            section = LibrarySection.PLAYLISTS,
            videoPlaylistsFlow = viewModel.playlists,
            musicPlaylistsFlow = viewModel.musicPlaylists,
            onTitleClick = onNavigateToPlaylists,
            onVideoPlaylistClick = onPlaylistClick,
            onMusicPlaylistClick = onMusicPlaylistClick,
        )
    }

    item(key = "watch-later", contentType = "video-shelf") {
        LibraryVideoShelf(
            section = LibrarySection.WATCH_LATER,
            videosFlow = viewModel.watchLater,
            onTitleClick = onNavigateToWatchLater,
            onVideoClick = onVideoClick,
        )
    }

    item(key = "likes", contentType = "media-shelf") {
        LibraryMediaShelfRoute(
            section = LibrarySection.LIKES,
            itemsFlow = viewModel.likes,
            onTitleClick = onNavigateToLikedVideos,
            onVideoClick = onVideoClick,
            onMusicClick = onMusicClick,
            onDownloadedVideoClick = onDownloadedVideoClick,
            onDownloadedMusicClick = onDownloadedMusicClick,
        )
    }

    item(key = "downloads", contentType = "media-shelf") {
        LibraryMediaShelfRoute(
            section = LibrarySection.DOWNLOADS,
            itemsFlow = viewModel.downloads,
            onTitleClick = onNavigateToDownloads,
            onVideoClick = onVideoClick,
            onMusicClick = onMusicClick,
            onDownloadedVideoClick = onDownloadedVideoClick,
            onDownloadedMusicClick = onDownloadedMusicClick,
        )
    }

    if (shortsEnabled) {
        item(key = "saved-shorts", contentType = "shorts-shelf") {
            LibraryShortsShelfRoute(
                section = LibrarySection.SAVED_SHORTS,
                shortsFlow = viewModel.savedShorts,
                onTitleClick = onNavigateToSavedShorts,
                onShortClick = onSavedShortClick,
            )
        }
    }
}
