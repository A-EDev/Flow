package io.github.aedev.flow.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.music.DownloadManager as MusicDownloadManager
import io.github.aedev.flow.data.video.VideoDownloadManager
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    playlistRepository: PlaylistRepository,
    videoDownloadManager: VideoDownloadManager,
    musicDownloadManager: MusicDownloadManager
) : ViewModel() {

    private val likedVideosRepository = LikedVideosRepository.getInstance(context)
    private val viewHistory = ViewHistory.getInstance(context)
    private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L)

    internal val history = viewHistory.getRecentLibraryHistory(LIBRARY_SHELF_ITEM_LIMIT)
        .map { history ->
            history.asSequence()
                .map { it.toLibraryMediaItem() }
                .toList()
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, sharing, emptyList())

    internal val likes = likedVideosRepository.getAllLikedVideos()
        .map { likes ->
            likes.take(LIBRARY_SHELF_ITEM_LIMIT).map { it.toLibraryMediaItem() }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, sharing, emptyList())

    internal val playlists = playlistRepository.getAllPlaylistsFlow()
        .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, sharing, emptyList())

    internal val musicPlaylists = playlistRepository.getMusicPlaylistsFlow()
        .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, sharing, emptyList())

    internal val watchLater = playlistRepository.getVideoOnlyWatchLaterFlow()
        .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, sharing, emptyList())

    internal val savedShorts = playlistRepository.getVideoOnlySavedShortsFlow()
        .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, sharing, emptyList())

    internal val downloads = combine(
        videoDownloadManager.downloadedVideos,
        musicDownloadManager.downloadedTracks
    ) { videos, tracks ->
        buildList<LibraryMediaItem> {
            videos.forEach { add(LibraryMediaItem.DownloadedVideoItem(it)) }
            tracks.forEach { add(LibraryMediaItem.DownloadedMusicItem(it)) }
        }.sortedByDescending { item ->
            when (item) {
                is LibraryMediaItem.DownloadedVideoItem -> item.download.downloadedAt
                is LibraryMediaItem.DownloadedMusicItem -> item.download.downloadedAt
                else -> 0L
            }
        }.take(LIBRARY_SHELF_ITEM_LIMIT)
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, sharing, emptyList())
}
