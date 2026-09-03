package io.github.aedev.flow.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.shorts.ShortsContentFilter
import io.github.aedev.flow.data.video.VideoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import io.github.aedev.flow.data.music.DownloadManager as MusicDownloadManager

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        playlistRepository: PlaylistRepository,
        likedVideosRepository: LikedVideosRepository,
        viewHistory: ViewHistory,
        videoDownloadManager: VideoDownloadManager,
        musicDownloadManager: MusicDownloadManager,
        shortsContentFilter: ShortsContentFilter,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L)

        internal val history =
            viewHistory
                .getRecentLibraryHistory(LIBRARY_SHELF_ITEM_LIMIT)
                .map { history ->
                    history
                        .asSequence()
                        .map { it.toLibraryMediaItem() }
                        .toList()
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, sharing, null)

        internal val likes =
            likedVideosRepository
                .getAllLikedVideos()
                .map { likes ->
                    likes.take(LIBRARY_SHELF_ITEM_LIMIT).map { it.toLibraryMediaItem() }
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, sharing, null)

        internal val playlists =
            playlistRepository
                .getAllPlaylistsFlow()
                .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, sharing, null)

        internal val musicPlaylists =
            playlistRepository
                .getMusicPlaylistsFlow()
                .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, sharing, null)

        internal val watchLater =
            playlistRepository
                .getVideoOnlyWatchLaterFlow()
                .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, sharing, null)

        internal val shortsEnabled =
            shortsContentFilter.enabled
                .stateIn(viewModelScope, sharing, true)

        internal val savedShorts =
            playlistRepository
                .getVideoOnlySavedShortsFlow()
                .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, sharing, null)

        internal val downloads =
            combine(
                videoDownloadManager.downloadedVideos,
                musicDownloadManager.downloadedTracks,
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
            }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, sharing, null)

        internal val isLibraryEmpty =
            combine(
                history,
                likes,
                playlists,
                watchLater,
                downloads,
            ) { shelves -> shelves.all { it != null && it.isEmpty() } }
                .combine(musicPlaylists) { empty, music -> empty && music != null && music.isEmpty() }
                .combine(savedShorts) { empty, shorts -> empty && shorts != null && shorts.isEmpty() }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, sharing, false)
    }
