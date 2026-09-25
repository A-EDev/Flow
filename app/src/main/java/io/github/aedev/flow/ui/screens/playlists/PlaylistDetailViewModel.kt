package io.github.aedev.flow.ui.screens.playlists

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.WatchLaterCleanup
import io.github.aedev.flow.data.migration.WatchLaterMetadataMigrator
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.repository.RemotePlaylistPage
import io.github.aedev.flow.data.repository.YouTubePlaylistRepository
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.BackgroundDownloadQueuer
import io.github.aedev.flow.data.video.DownloadBatch
import io.github.aedev.flow.ui.components.library.PlaylistSortOrder
import io.github.aedev.flow.ui.components.library.sortedForPlaylist
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionUndo
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject

private const val ENRICHMENT_STUB_LIMIT = 50
private const val ENRICHMENT_CHUNK_SIZE = 5
private const val ENRICHMENT_CHUNK_DELAY_MS = 300L
private const val SHARING_TIMEOUT_MS = 5_000L

data class PlaylistUiMessage(
    @param:StringRes val stringRes: Int = 0,
    @param:PluralsRes val pluralRes: Int = 0,
    val count: Int = 0,
    val args: List<Any> = emptyList(),
    val undo: QuickActionUndo? = null,
)

data class PlaylistDetailUiState(
    val playlistName: String = "",
    val ownerName: String? = null,
    val description: String = "",
    val isPrivate: Boolean = false,
    val videos: List<Video> = emptyList(),
    val thumbnailUrl: String = "",
    val isLocalPlaylist: Boolean = false,
    val isSaved: Boolean = false,
    val isWatchLater: Boolean = false,
    val isLoading: Boolean = true,
    /** Later pages of a YouTube playlist are still arriving. */
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class PlaylistDetailViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: PlaylistRepository,
        private val youTubeRepository: YouTubeRepository,
        private val playlistRepository: YouTubePlaylistRepository,
        private val playerPreferences: PlayerPreferences,
        private val downloadQueuer: BackgroundDownloadQueuer,
        private val watchLaterMetadataMigrator: WatchLaterMetadataMigrator,
        private val watchLaterCleanup: WatchLaterCleanup,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val playlistId: String = checkNotNull(savedStateHandle["playlistId"])
        private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARING_TIMEOUT_MS)
        private val attemptedEnrichment = HashSet<String>()
        private val enrichSemaphore = Semaphore(1)

        private val _uiState = MutableStateFlow(PlaylistDetailUiState())
        val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

        private val _messages = Channel<PlaylistUiMessage>(Channel.BUFFERED)
        val messages: Flow<PlaylistUiMessage> = _messages.receiveAsFlow()

        val sortOrder: StateFlow<PlaylistSortOrder> =
            combine(
                playerPreferences.playlistSortOrder(playlistId),
                _uiState.map { it.isLocalPlaylist }.distinctUntilChanged(),
            ) { stored, isLocal ->
                PlaylistSortOrder
                    .fromStorageValue(stored)
                    .takeIf { it in PlaylistSortOrder.availableFor(isLocal) } ?: PlaylistSortOrder.MANUAL
            }.stateIn(viewModelScope, sharing, PlaylistSortOrder.MANUAL)

        val sortedVideos: StateFlow<List<Video>> =
            combine(_uiState.map { it.videos }, sortOrder) { videos, order ->
                videos.sortedForPlaylist(order)
            }.flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, sharing, emptyList())

        val userCreatedPlaylists: StateFlow<List<PlaylistInfo>> =
            repository
                .getUserCreatedVideoPlaylistsFlow()
                .stateIn(viewModelScope, sharing, emptyList())

        /** The running or just-finished "Download all" for this playlist. */
        val downloadBatch: StateFlow<DownloadBatch?> =
            downloadQueuer.batches
                .map { it[playlistId] }
                .stateIn(viewModelScope, sharing, null)

        init {
            loadPlaylist()
            viewModelScope.launch {
                downloadQueuer.batches
                    .map { it[playlistId] }
                    .filterNotNull()
                    .filter { it.isFinished }
                    .collect { batch ->
                        _messages.send(
                            if (batch.queued > 0) {
                                PlaylistUiMessage(stringRes = R.string.playlist_downloads_queued, args = listOf(batch.queued, batch.total))
                            } else {
                                PlaylistUiMessage(stringRes = R.string.playlist_download_queue_empty)
                            },
                        )
                        downloadQueuer.clearBatch(playlistId)
                    }
            }
        }

        fun setSortOrder(order: PlaylistSortOrder) {
            viewModelScope.launch {
                playerPreferences.setPlaylistSortOrder(playlistId, order.storageValue)
            }
        }

        fun retry() {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            loadPlaylist()
        }

        fun saveToLibrary() {
            viewModelScope.launch {
                val state = _uiState.value
                repository.saveExternalVideoPlaylist(
                    id = playlistId,
                    name = state.playlistName,
                    description = state.description,
                    thumbnailUrl = state.thumbnailUrl.ifEmpty { state.videos.firstOrNull()?.thumbnailUrl ?: "" },
                )
                repository.addVideosToPlaylist(playlistId, state.videos)
                _uiState.update { it.copy(isLocalPlaylist = true, isSaved = true) }
                _messages.send(PlaylistUiMessage(stringRes = R.string.ui_playlist_saved_to_library))
            }
        }

        fun unsaveFromLibrary() {
            viewModelScope.launch {
                repository.unsaveExternalPlaylist(playlistId)
                _uiState.update { it.copy(isLocalPlaylist = false, isSaved = false) }
                _messages.send(PlaylistUiMessage(stringRes = R.string.ui_playlist_removed_from_library))
            }
        }

        fun removeVideo(videoId: String) = removeVideos(setOf(videoId))

        fun removeVideos(videoIds: Set<String>) {
            if (videoIds.isEmpty()) return
            viewModelScope.launch {
                val removed = repository.takeVideosFromPlaylist(playlistId, videoIds)
                if (removed.isEmpty()) return@launch
                _messages.send(
                    PlaylistUiMessage(
                        pluralRes = R.plurals.playlist_videos_removed,
                        count = removed.size,
                        args = listOf(removed.size),
                        undo = QuickActionUndo.PlaylistRemoval(removed),
                    ),
                )
            }
        }

        fun reorderVideos(orderedVideoIds: List<String>) {
            viewModelScope.launch {
                repository.reorderVideosInPlaylist(playlistId, orderedVideoIds)
            }
        }

        fun updatePlaylist(
            name: String,
            description: String,
        ) {
            viewModelScope.launch {
                repository.updatePlaylistMetadata(playlistId, name, description, _uiState.value.isPrivate)
                _uiState.update { it.copy(playlistName = name, description = description) }
            }
        }

        fun deletePlaylist() {
            viewModelScope.launch {
                repository.deletePlaylist(playlistId)
            }
        }

        fun mergeIntoPlaylist(targetPlaylistId: String) {
            viewModelScope.launch {
                val videos = _uiState.value.videos
                try {
                    repository.addVideosToPlaylist(targetPlaylistId, videos)
                    val targetInfo = repository.getPlaylistInfo(targetPlaylistId)
                    _messages.send(
                        PlaylistUiMessage(
                            pluralRes = R.plurals.merge_playlist_success,
                            count = videos.size,
                            args = listOf(videos.size, targetInfo?.name ?: ""),
                        ),
                    )
                } catch (_: Exception) {
                    _messages.send(PlaylistUiMessage(stringRes = R.string.toast_failed_to_merge_playlist))
                }
            }
        }

        fun downloadPlaylist() {
            val videos = _uiState.value.videos
            if (videos.isEmpty()) {
                viewModelScope.launch { _messages.send(PlaylistUiMessage(stringRes = R.string.ui_playlist_empty)) }
                return
            }
            viewModelScope.launch {
                _messages.send(
                    PlaylistUiMessage(pluralRes = R.plurals.ui_downloading_videos, count = videos.size, args = listOf(videos.size)),
                )
            }
            downloadQueuer.queueAll(playlistId, videos)
        }

        private fun loadPlaylist() {
            viewModelScope.launch {
                if (playlistId == PlaylistRepository.WATCH_LATER_ID) {
                    loadWatchLater()
                    return@launch
                }

                val localInfo = repository.getPlaylistInfo(playlistId)
                if (localInfo != null) {
                    loadLocalPlaylist(localInfo)
                } else {
                    loadRemotePlaylist()
                }
            }
        }

        private suspend fun loadWatchLater() {
            _uiState.update {
                it.copy(
                    playlistName = context.getString(R.string.watch_later),
                    description = "",
                    isPrivate = true,
                    isLocalPlaylist = true,
                    isSaved = false,
                    isWatchLater = true,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            var migrationStarted = false
            repository.getVideoOnlyWatchLaterFlow().collect { videos ->
                _uiState.update {
                    it.copy(
                        videos = videos,
                        thumbnailUrl = videos.firstOrNull()?.thumbnailUrl.orEmpty(),
                    )
                }
                if (!migrationStarted && videos.isNotEmpty()) {
                    migrationStarted = true
                    viewModelScope.launch { watchLaterMetadataMigrator.migrate(videos) }
                    viewModelScope.launch { sweepWatched(videos) }
                }
                enrichStubs(videos)
            }
        }

        private suspend fun sweepWatched(videos: List<Video>) {
            val removed = watchLaterCleanup.sweep(videos)
            if (removed.isEmpty()) return
            _messages.send(
                PlaylistUiMessage(
                    pluralRes = R.plurals.watch_later_watched_removed,
                    count = removed.size,
                    args = listOf(removed.size),
                    undo = QuickActionUndo.PlaylistRemoval(removed),
                ),
            )
        }

        private suspend fun loadLocalPlaylist(localInfo: PlaylistInfo) {
            val isSaved = repository.isExternalPlaylistSaved(playlistId)
            _uiState.update {
                it.copy(
                    playlistName = localInfo.name,
                    description = localInfo.description,
                    isPrivate = localInfo.isPrivate,
                    thumbnailUrl = localInfo.thumbnailUrl,
                    isLocalPlaylist = true,
                    isSaved = isSaved,
                    isWatchLater = false,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            if (isSaved) {
                refreshSavedPlaylist()
            }
            repository.getPlaylistVideosWithAddedAtFlow(playlistId).collect { videos ->
                _uiState.update { it.copy(videos = videos) }
                enrichStubs(videos)
            }
        }

        private suspend fun loadRemotePlaylist() {
            val page = playlistRepository.cachedComplete(playlistId) ?: playlistRepository.firstPage(playlistId)
            if (page != null) {
                _uiState.update {
                    it.copy(
                        playlistName = page.title,
                        ownerName = page.ownerName,
                        description = page.description,
                        isPrivate = false,
                        videos = page.videos,
                        thumbnailUrl = page.thumbnailUrl,
                        isLocalPlaylist = false,
                        isSaved = false,
                        isWatchLater = false,
                        isLoading = false,
                        isLoadingMore = page.continuation != null,
                        errorMessage = null,
                    )
                }
                page.continuation?.let { loadRemainingPages(page, it) }
                return
            }

            val musicDetails = runCatching { YouTubeMusicService.fetchPlaylistDetails(playlistId) }.getOrNull()
            if (musicDetails != null) {
                _uiState.update {
                    it.copy(
                        playlistName = musicDetails.title,
                        ownerName = musicDetails.author.takeIf(String::isNotBlank),
                        description = musicDetails.description.orEmpty(),
                        isPrivate = false,
                        videos = musicDetails.tracks.map { track -> track.toPlaylistVideo() },
                        thumbnailUrl = musicDetails.thumbnailUrl,
                        isLocalPlaylist = false,
                        isSaved = false,
                        isWatchLater = false,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                return
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = context.getString(R.string.playlist_load_failed),
                )
            }
        }

        /** Appends the pages after the first as they arrive, so the list is usable while it grows. */
        private suspend fun loadRemainingPages(
            first: RemotePlaylistPage,
            firstToken: String,
        ) {
            var videos = first.videos
            var token: String? = firstToken
            var pages = 1
            while (token != null && pages < YouTubePlaylistRepository.PAGE_LIMIT) {
                val (more, next) = playlistRepository.nextPage(playlistId, token) ?: break
                videos = (videos + more).distinctBy { it.id }
                token = next.takeIf { more.isNotEmpty() }
                pages++
                _uiState.update { it.copy(videos = videos) }
            }
            _uiState.update { it.copy(isLoadingMore = false) }
            if (token == null) playlistRepository.rememberComplete(playlistId, first.copy(videos = videos))
        }

        private fun refreshSavedPlaylist() {
            viewModelScope.launch {
                val details = playlistRepository.complete(playlistId) ?: return@launch
                repository.syncSavedPlaylistVideos(playlistId, details.videos)
                _uiState.update { state ->
                    state.copy(
                        playlistName = details.title.ifBlank { state.playlistName },
                        ownerName = details.ownerName ?: state.ownerName,
                        description = details.description.ifBlank { state.description },
                        thumbnailUrl = details.thumbnailUrl.ifBlank { state.thumbnailUrl },
                    )
                }
            }
        }

        private fun enrichStubs(videos: List<Video>) {
            val stubs =
                videos
                    .asSequence()
                    .filter { it.title.isEmpty() && it.id !in attemptedEnrichment }
                    .take(ENRICHMENT_STUB_LIMIT)
                    .toList()
            if (stubs.isEmpty()) return
            if (!enrichSemaphore.tryAcquire()) return
            attemptedEnrichment.addAll(stubs.map { it.id })
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    stubs.chunked(ENRICHMENT_CHUNK_SIZE).forEach { chunk ->
                        chunk.forEach { video ->
                            try {
                                val refreshed = youTubeRepository.getVideo(video.id) ?: return@forEach
                                repository.updateVideoMetadata(refreshed)
                            } catch (_: Exception) {
                            }
                        }
                        kotlinx.coroutines.delay(ENRICHMENT_CHUNK_DELAY_MS)
                    }
                } finally {
                    enrichSemaphore.release()
                }
            }
        }
    }

private fun io.github.aedev.flow.data.music.model.MusicTrack.toPlaylistVideo(): Video =
    Video(
        id = videoId,
        title = title,
        channelName = artist,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        viewCount = views,
        uploadDate = "",
        isMusic = true,
    )
