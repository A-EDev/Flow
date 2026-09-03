package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.PlaylistCardLayout
import io.github.aedev.flow.ui.screens.playlists.PlaylistInfo
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun LibraryMediaShelfRoute(
    title: String,
    itemsFlow: StateFlow<List<LibraryMediaItem>?>,
    sourceName: String,
    onTitleClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onDownloadedVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDownloadedMusicClick: (List<DownloadedTrack>, Int) -> Unit,
) {
    val items by itemsFlow.collectAsStateWithLifecycle()
    when {
        items == null -> {
            LibraryShelfPlaceholder(title = title)
        }

        items.isNullOrEmpty() -> {
            Unit
        }

        else -> {
            LibraryMediaShelf(
                title = title,
                items = items.orEmpty(),
                sourceName = sourceName,
                onTitleClick = onTitleClick,
                onVideoClick = onVideoClick,
                onMusicClick = onMusicClick,
                onDownloadedVideoClick = onDownloadedVideoClick,
                onDownloadedMusicClick = onDownloadedMusicClick,
            )
        }
    }
}

@Composable
internal fun LibraryPlaylistsShelf(
    title: String,
    videoPlaylistsFlow: StateFlow<List<PlaylistInfo>?>,
    musicPlaylistsFlow: StateFlow<List<PlaylistInfo>?>,
    onTitleClick: () -> Unit,
    onVideoPlaylistClick: (String) -> Unit,
    onMusicPlaylistClick: (String) -> Unit,
) {
    val videoPlaylists by videoPlaylistsFlow.collectAsStateWithLifecycle()
    val musicPlaylists by musicPlaylistsFlow.collectAsStateWithLifecycle()

    if (videoPlaylists == null && musicPlaylists == null) {
        LibraryShelfPlaceholder(title = title)
        return
    }
    if (videoPlaylists.isNullOrEmpty() && musicPlaylists.isNullOrEmpty()) return

    LibraryShelf(title = title, onTitleClick = onTitleClick) {
        items(
            items = videoPlaylists.orEmpty(),
            key = { "video-${it.id}" },
            contentType = { "video-playlist" },
        ) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = { onVideoPlaylistClick(playlist.id) },
                layout = PlaylistCardLayout.SHELF,
                modifier = Modifier.width(LibraryShelfCardWidth),
            )
        }
        items(
            items = musicPlaylists.orEmpty(),
            key = { "music-${it.id}" },
            contentType = { "music-playlist" },
        ) { playlist ->
            LibraryAlbumCard(
                title = playlist.name,
                subtitle = stringResource(R.string.tracks_count_template, playlist.videoCount),
                thumbnailUrl = playlist.thumbnailUrl,
                onClick = { onMusicPlaylistClick(playlist.id) },
            )
        }
    }
}

@Composable
internal fun LibraryVideoShelf(
    title: String,
    videosFlow: StateFlow<List<Video>?>,
    onTitleClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
) {
    val videos by videosFlow.collectAsStateWithLifecycle()
    when {
        videos == null -> {
            LibraryShelfPlaceholder(title = title)
        }

        videos.isNullOrEmpty() -> {
            Unit
        }

        else -> {
            LibraryShelf(title = title, onTitleClick = onTitleClick) {
                items(
                    items = videos.orEmpty(),
                    key = Video::id,
                    contentType = { "video" },
                ) { video ->
                    LibraryVideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun LibraryShortsShelfRoute(
    title: String,
    shortsFlow: StateFlow<List<Video>?>,
    onTitleClick: () -> Unit,
    onShortClick: (Video) -> Unit,
) {
    val shorts by shortsFlow.collectAsStateWithLifecycle()
    when {
        shorts == null -> {
            LibraryShelfPlaceholder(title = title)
        }

        shorts.isNullOrEmpty() -> {
            Unit
        }

        else -> {
            LibraryShortsShelf(
                title = title,
                shorts = shorts.orEmpty(),
                onTitleClick = onTitleClick,
                onShortClick = onShortClick,
            )
        }
    }
}
