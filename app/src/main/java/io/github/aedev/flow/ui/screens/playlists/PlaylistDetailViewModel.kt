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
import io.github.aedev.flow.data.migration.WatchLaterMetadataMigrator
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.player.stream.AudioStreamSelector
import io.github.aedev.flow.ui.components.library.PlaylistSortOrder
import io.github.aedev.flow.ui.components.library.sortedForPlaylist
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject

private const val ENRICHMENT_STUB_LIMIT = 50
private const val ENRICHMENT_CHUNK_SIZE = 5
private const val ENRICHMENT_CHUNK_DELAY_MS = 300L
private const val DOWNLOAD_CONCURRENCY = 2
private const val OFFLINE_QUALITY_CAP = 720
private const val SHARING_TIMEOUT_MS = 5_000L

data class PlaylistUiMessage(
    @param:StringRes val stringRes: Int = 0,
    @param:PluralsRes val pluralRes: Int = 0,
    val count: Int = 0,
    val args: List<Any> = emptyList(),
)

data class PlaylistDetailUiState(
    val playlistName: String = "",
    val description: String = "",
    val isPrivate: Boolean = false,
    val videos: List<Video> = emptyList(),
    val thumbnailUrl: String = "",
    val isLocalPlaylist: Boolean = false,
    val isSaved: Boolean = false,
    val isWatchLater: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class PlaylistDetailViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: PlaylistRepository,
        private val youTubeRepository: YouTubeRepository,
        private val playerPreferences: PlayerPreferences,
        private val watchLaterMetadataMigrator: WatchLaterMetadataMigrator,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])
        private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARING_TIMEOUT_MS)
        private val attemptedEnrichment = HashSet<String>()
        private val enrichSemaphore = Semaphore(1)

        private val _uiState = MutableStateFlow(PlaylistDetailUiState())
        val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

        private val _messages = Channel<PlaylistUiMessage>(Channel.BUFFERED)
        val messages: Flow<PlaylistUiMessage> = _messages.receiveAsFlow()

        val sortOrder: StateFlow<PlaylistSortOrder> =
            playerPreferences.playlistSortOrder
                .map { PlaylistSortOrder.fromStorageValue(it) }
                .stateIn(viewModelScope, sharing, PlaylistSortOrder.MANUAL)

        val sortedVideos: StateFlow<List<Video>> =
            combine(_uiState.map { it.videos }, sortOrder) { videos, order ->
                videos.sortedForPlaylist(order)
            }.flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, sharing, emptyList())

        val userCreatedPlaylists: StateFlow<List<PlaylistInfo>> =
            repository
                .getUserCreatedVideoPlaylistsFlow()
                .stateIn(viewModelScope, sharing, emptyList())

        private val _isDownloadingPlaylist = MutableStateFlow(false)
        val isDownloadingPlaylist: StateFlow<Boolean> = _isDownloadingPlaylist.asStateFlow()

        private val _playlistDownloadProgress = MutableStateFlow(0f)
        val playlistDownloadProgress: StateFlow<Float> = _playlistDownloadProgress.asStateFlow()

        private val _currentDownloadingTitle = MutableStateFlow<String?>(null)
        val currentDownloadingTitle: StateFlow<String?> = _currentDownloadingTitle.asStateFlow()

        init {
            loadPlaylist()
        }

        fun setSortOrder(order: PlaylistSortOrder) {
            viewModelScope.launch {
                playerPreferences.setPlaylistSortOrder(order.storageValue)
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

        fun removeVideo(videoId: String) {
            viewModelScope.launch {
                repository.removeVideoFromPlaylist(playlistId, videoId)
            }
        }

        fun removeVideos(videoIds: Set<String>) {
            if (videoIds.isEmpty()) return
            viewModelScope.launch {
                videoIds.forEach { repository.removeVideoFromPlaylist(playlistId, it) }
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

        fun togglePrivacy() {
            viewModelScope.launch {
                val state = _uiState.value
                val newPrivacy = !state.isPrivate
                repository.updatePlaylistMetadata(playlistId, state.playlistName, state.description, newPrivacy)
                _uiState.update { it.copy(isPrivate = newPrivacy) }
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
            if (_isDownloadingPlaylist.value) return

            viewModelScope.launch {
                val videos = _uiState.value.videos
                if (videos.isEmpty()) {
                    _messages.send(PlaylistUiMessage(stringRes = R.string.ui_playlist_empty))
                    return@launch
                }

                _isDownloadingPlaylist.value = true
                _playlistDownloadProgress.value = 0f
                _messages.send(
                    PlaylistUiMessage(
                        pluralRes = R.plurals.ui_downloading_videos,
                        count = videos.size,
                        args = listOf(videos.size),
                    ),
                )

                val preferredAudioLanguage = playerPreferences.preferredAudioLanguage.first()
                val semaphore = Semaphore(DOWNLOAD_CONCURRENCY)
                var processed = 0
                var queued = 0

                videos
                    .map { video ->
                        async(PerformanceDispatcher.networkIO) {
                            semaphore.withPermit {
                                _currentDownloadingTitle.value = video.title
                                val started = queueDownload(video, preferredAudioLanguage)
                                processed++
                                _playlistDownloadProgress.value = processed.toFloat() / videos.size
                                started
                            }
                        }
                    }.awaitAll()
                    .forEach { if (it) queued++ }

                _messages.send(
                    if (queued > 0) {
                        PlaylistUiMessage(
                            stringRes = R.string.playlist_downloads_queued,
                            args = listOf(queued, videos.size),
                        )
                    } else {
                        PlaylistUiMessage(stringRes = R.string.playlist_download_queue_empty)
                    },
                )

                _isDownloadingPlaylist.value = false
                _currentDownloadingTitle.value = null
                _playlistDownloadProgress.value = 0f
            }
        }

        private suspend fun queueDownload(
            video: Video,
            preferredAudioLanguage: String,
        ): Boolean {
            val streamInfo =
                try {
                    youTubeRepository.getVideoStreamInfo(video.id)
                } catch (_: Exception) {
                    null
                } ?: return false

            val selection = selectOfflineStreams(streamInfo, preferredAudioLanguage) ?: return false
            val fullVideo =
                video.copy(
                    thumbnailUrl =
                        video.thumbnailUrl.ifBlank {
                            streamInfo.thumbnails?.maxByOrNull { it.height }?.url ?: ""
                        },
                )

            withContext(Dispatchers.Main) {
                FlowDownloadService.startDownload(
                    context = context,
                    video = fullVideo,
                    url = selection.videoUrl,
                    quality = selection.qualityLabel,
                    audioUrl = selection.audioUrl,
                    videoCodec = selection.videoCodec,
                )
            }
            return true
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
                }
                enrichStubs(videos)
            }
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
            try {
                val details = youTubeRepository.getPlaylistDetails(playlistId)
                if (details != null) {
                    _uiState.update {
                        it.copy(
                            playlistName = details.name,
                            description = details.description ?: "",
                            isPrivate = false,
                            videos = details.videos,
                            thumbnailUrl = details.thumbnailUrl,
                            isLocalPlaylist = false,
                            isSaved = false,
                            isWatchLater = false,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    return
                }

                val musicDetails = YouTubeMusicService.fetchPlaylistDetails(playlistId)
                if (musicDetails != null) {
                    _uiState.update {
                        it.copy(
                            playlistName = musicDetails.title,
                            description = musicDetails.description ?: "",
                            isPrivate = false,
                            videos =
                                musicDetails.tracks.map { track ->
                                    Video(
                                        id = track.videoId,
                                        title = track.title,
                                        channelName = track.artist,
                                        channelId = track.channelId,
                                        thumbnailUrl = track.thumbnailUrl,
                                        duration = track.duration,
                                        viewCount = track.views ?: 0,
                                        uploadDate = "",
                                        isMusic = true,
                                    )
                                },
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
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = context.getString(R.string.playlist_load_failed),
                    )
                }
            }
        }

        private fun refreshSavedPlaylist() {
            viewModelScope.launch {
                try {
                    val details = youTubeRepository.getPlaylistDetails(playlistId) ?: return@launch
                    repository.syncSavedPlaylistVideos(playlistId, details.videos)
                    _uiState.update { state ->
                        state.copy(
                            playlistName = details.name.ifBlank { state.playlistName },
                            description = (details.description ?: "").ifBlank { state.description },
                            thumbnailUrl = details.thumbnailUrl.ifBlank { state.thumbnailUrl },
                        )
                    }
                } catch (_: Exception) {
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

private data class OfflineStreamSelection(
    val videoUrl: String,
    val audioUrl: String?,
    val qualityLabel: String,
    val videoCodec: String?,
)

private fun selectOfflineStreams(
    streamInfo: StreamInfo,
    preferredAudioLanguage: String,
): OfflineStreamSelection? {
    val videoOnlyStreams = streamInfo.videoOnlyStreams?.filterIsInstance<VideoStream>() ?: emptyList()
    val combinedStreams = streamInfo.videoStreams?.filterIsInstance<VideoStream>() ?: emptyList()
    val audioStreams = streamInfo.audioStreams ?: emptyList()

    val bestVideoOnly =
        videoOnlyStreams
            .filter { it.isMp4() && it.qualityHeight() <= OFFLINE_QUALITY_CAP }
            .maxByOrNull { it.qualityHeight() }
            ?: videoOnlyStreams.filter { it.isMp4() }.maxByOrNull { it.qualityHeight() }

    val selected =
        bestVideoOnly
            ?: combinedStreams.filter { it.isMp4() }.maxByOrNull { it.qualityHeight() }
            ?: (videoOnlyStreams + combinedStreams).maxByOrNull { it.qualityHeight() }
            ?: return null

    val videoUrl = selected.content ?: selected.url ?: return null
    val audioUrl =
        if (selected in videoOnlyStreams) {
            val audio =
                AudioStreamSelector.selectPreferredAudioStream(
                    streams = audioStreams,
                    preferredAudioLanguage = preferredAudioLanguage,
                    compatibilityFilter = AudioStream::isAacCompatible,
                )
            audio?.content ?: audio?.url
        } else {
            null
        }

    return OfflineStreamSelection(
        videoUrl = videoUrl,
        audioUrl = audioUrl,
        qualityLabel = "${selected.qualityHeight()}p",
        videoCodec = VideoPlayerUtils.codecKeyFromStream(selected),
    )
}

private fun VideoStream.isMp4(): Boolean {
    val mime = (format?.mimeType ?: "").lowercase()
    val name = (format?.name ?: "").lowercase()
    return mime.contains("mp4") || name.contains("mp4") || name.contains("mpeg")
}

private fun AudioStream.isAacCompatible(): Boolean {
    val mime = (format?.mimeType ?: "").lowercase()
    val name = (format?.name ?: "").lowercase()
    return !name.contains("opus") && !name.contains("webm") && !mime.contains("opus") && !mime.contains("webm")
}

private fun VideoStream.qualityHeight(): Int = QualityManager.normalizeQualityHeight(VideoPlayerUtils.qualityHeightFromStream(this))
