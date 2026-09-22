package io.github.aedev.flow.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.shorts.ShortsContentFilter
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.ui.components.library.LIBRARY_SHELF_ITEM_LIMIT
import io.github.aedev.flow.ui.components.library.LibraryMediaItem
import io.github.aedev.flow.ui.components.library.toLibraryMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import io.github.aedev.flow.data.music.DownloadManager as MusicDownloadManager

internal data class LibraryCounts(
    val history: Int,
    val playlists: Int,
    val watchLater: Int,
    val likes: Int,
    val downloadedVideos: Int,
    val downloadedTracks: Int,
    val savedShorts: Int,
) {
    val isEmpty: Boolean
        get() =
            history == 0 &&
                playlists == 0 &&
                watchLater == 0 &&
                likes == 0 &&
                downloadedVideos == 0 &&
                downloadedTracks == 0 &&
                savedShorts == 0
}

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
        playerPreferences: PlayerPreferences,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L)

        private fun <T> Flow<T>.shared(): StateFlow<T?> {
            val upstream: Flow<T?> = this
            return upstream
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, sharing, null)
        }

        private val allLikes = likedVideosRepository.getAllLikedVideos().shared()
        private val allVideoPlaylists = playlistRepository.getAllPlaylistsFlow().shared()
        private val allMusicPlaylists = playlistRepository.getMusicPlaylistsFlow().shared()
        private val allWatchLater = playlistRepository.getVideoOnlyWatchLaterFlow().shared()
        private val allSavedShorts = playlistRepository.getVideoOnlySavedShortsFlow().shared()

        private val allDownloads =
            combine(
                videoDownloadManager.downloadedVideos,
                musicDownloadManager.downloadedTracks,
            ) { videos, tracks -> videos to tracks }.shared()

        internal val history =
            viewHistory
                .getRecentLibraryHistory(LIBRARY_SHELF_ITEM_LIMIT)
                .map { entries -> entries.map { it.toLibraryMediaItem() } }
                .shared()

        internal val likes =
            allLikes
                .map { liked ->
                    liked?.take(LIBRARY_SHELF_ITEM_LIMIT)?.map { it.toLibraryMediaItem() }
                }.shared()

        internal val playlists = allVideoPlaylists.map { it?.take(LIBRARY_SHELF_ITEM_LIMIT) }.shared()

        internal val musicPlaylists = allMusicPlaylists.map { it?.take(LIBRARY_SHELF_ITEM_LIMIT) }.shared()

        internal val watchLater = allWatchLater.map { it?.take(LIBRARY_SHELF_ITEM_LIMIT) }.shared()

        internal val savedShorts = allSavedShorts.map { it?.take(LIBRARY_SHELF_ITEM_LIMIT) }.shared()

        internal val downloads =
            allDownloads
                .map { downloaded ->
                    downloaded?.let { (videos, tracks) ->
                        buildList<LibraryMediaItem> {
                            videos.forEach { add(LibraryMediaItem.DownloadedVideoItem(it)) }
                            tracks.forEach { add(LibraryMediaItem.DownloadedMusicItem(it)) }
                        }.sortedByDescending { item -> item.downloadedAt }
                            .take(LIBRARY_SHELF_ITEM_LIMIT)
                    }
                }.shared()

        internal val shortsEnabled =
            shortsContentFilter.enabled
                .stateIn(viewModelScope, sharing, true)

        internal val shelfPreviewsEnabled =
            playerPreferences.libraryShelfPreviewsEnabled
                .distinctUntilChanged()
                .stateIn(viewModelScope, sharing, true)

        /**
         * Counts come off the same shared sources the shelves read, so the compact layout costs one
         * extra `COUNT(*)` on watch history and nothing else.
         */
        internal val counts: StateFlow<LibraryCounts?> =
            combine(
                viewHistory.getLibraryHistoryCount(),
                combine(allVideoPlaylists, allMusicPlaylists) { video, music ->
                    if (video == null || music == null) null else video.size + music.size
                },
                combine(allWatchLater, allSavedShorts) { later, shorts ->
                    if (later == null || shorts == null) null else later.size to shorts.size
                },
                allLikes,
                allDownloads,
            ) { historyCount, playlistCount, saved, liked, downloaded ->
                if (playlistCount == null || saved == null || liked == null || downloaded == null) {
                    null
                } else {
                    LibraryCounts(
                        history = historyCount,
                        playlists = playlistCount,
                        watchLater = saved.first,
                        likes = liked.size,
                        downloadedVideos = downloaded.first.size,
                        downloadedTracks = downloaded.second.size,
                        savedShorts = saved.second,
                    )
                }
            }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, sharing, null)

        internal val isLibraryEmpty =
            counts
                .map { it != null && it.isEmpty }
                .distinctUntilChanged()
                .stateIn(viewModelScope, sharing, false)
    }

private val LibraryMediaItem.downloadedAt: Long
    get() =
        when (this) {
            is LibraryMediaItem.DownloadedVideoItem -> download.downloadedAt
            is LibraryMediaItem.DownloadedMusicItem -> download.downloadedAt
            else -> 0L
        }
