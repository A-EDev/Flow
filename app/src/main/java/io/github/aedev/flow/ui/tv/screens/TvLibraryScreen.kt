package io.github.aedev.flow.ui.tv.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.tv.components.TvFilterChip
import io.github.aedev.flow.ui.tv.components.TvMediaGrid
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvPlaylistCard
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.ui.tv.toTvVideo
import io.github.aedev.flow.ui.tv.tvWatchProgress

private enum class TvLibrarySection(@StringRes val titleRes: Int) {
    HISTORY(R.string.tv_library_history),
    LIKES(R.string.tv_library_likes),
    WATCH_LATER(R.string.tv_library_watch_later),
    PLAYLISTS(R.string.tv_library_playlists),
    DOWNLOADS(R.string.tv_library_downloads),
    LOCAL_MEDIA(R.string.tv_library_local),
}

/**
 * Library hub: section chips over full-height grids. Downloads and Local Media
 * navigate to their own screens; playlists open the playlist detail route.
 */
@Composable
fun TvLibraryScreen(
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenLocalMedia: () -> Unit = {},
) {
    val context = LocalContext.current
    val historyRepository = remember { ViewHistory.getInstance(context.applicationContext) }
    val likedRepository = remember { LikedVideosRepository.getInstance(context.applicationContext) }
    val playlistRepository = remember { PlaylistRepository(context.applicationContext) }
    val history by historyRepository.getVideoHistoryFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val liked by likedRepository.getAllLikedVideos()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val watchLater by playlistRepository.getVideoOnlyWatchLaterFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by playlistRepository.getAllPlaylistsFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSection by rememberSaveable { mutableStateOf(TvLibrarySection.HISTORY) }
    val dimens = LocalTvDimens.current

    val visiblePlaylists = playlists.filterNot {
        it.id == PlaylistRepository.WATCH_LATER_ID || it.id == PlaylistRepository.SAVED_SHORTS_ID
    }

    TvScreenScaffold(
        title = stringResource(R.string.library),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvRowFocus(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = dimens.overscanHorizontal),
            ) {
                items(TvLibrarySection.entries, key = TvLibrarySection::name) { section ->
                    TvFilterChip(
                        label = stringResource(section.titleRes),
                        selected = selectedSection == section,
                        onClick = {
                            when (section) {
                                TvLibrarySection.DOWNLOADS -> onOpenDownloads()
                                TvLibrarySection.LOCAL_MEDIA -> onOpenLocalMedia()
                                else -> selectedSection = section
                            }
                        },
                    )
                }
            }

            when (selectedSection) {
                TvLibrarySection.PLAYLISTS ->
                    if (visiblePlaylists.isEmpty()) {
                        TvMessageState(
                            title = stringResource(R.string.tv_library_empty),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = dimens.overscanHorizontal),
                        )
                    } else {
                        TvMediaGrid(
                            items = visiblePlaylists,
                            key = { it.id },
                        ) { info ->
                            TvPlaylistCard(
                                playlist = Playlist(
                                    id = info.id,
                                    name = info.name,
                                    thumbnailUrl = info.thumbnailUrl,
                                    videoCount = info.videoCount,
                                    description = info.description,
                                ),
                                onClick = { onOpenPlaylist(info.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                TvLibrarySection.HISTORY ->
                    TvLibraryVideoGrid(
                        videos = history.map { entry -> entry.toTvVideo() to entry.tvWatchProgress() },
                        onVideoClick = onVideoClick,
                    )

                else -> {
                    val videos = when (selectedSection) {
                        TvLibrarySection.LIKES -> liked.map(LikedVideoInfo::toTvVideo)
                        TvLibrarySection.WATCH_LATER -> watchLater
                        else -> emptyList()
                    }
                    TvLibraryVideoGrid(
                        videos = videos.map { it to null },
                        onVideoClick = onVideoClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvLibraryVideoGrid(
    videos: List<Pair<Video, Float?>>,
    onVideoClick: (Video) -> Unit,
) {
    if (videos.isEmpty()) {
        TvMessageState(
            title = stringResource(R.string.tv_library_empty),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LocalTvDimens.current.overscanHorizontal),
        )
        return
    }
    TvMediaGrid(
        items = videos,
        key = { it.first.id },
    ) { (video, progress) ->
        TvVideoCard(
            video = video,
            onClick = { onVideoClick(video) },
            watchProgress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
