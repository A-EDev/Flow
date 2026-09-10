package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.*
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.distinctByNonBlankKey
import io.github.aedev.flow.data.model.mergeDistinctByNonBlankKey
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.di.IoDispatcher
import io.github.aedev.flow.di.NetworkIoDispatcher
import io.github.aedev.flow.notification.UpcomingVideoReminderWorker
import io.github.aedev.flow.player.BackgroundPlaybackPolicy
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.MiniPlayerExpansionState
import io.github.aedev.flow.player.PlaybackStartupPolicy
import io.github.aedev.flow.player.PlayerChannelMetadataPolicy
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.error.VideoErrorMapper
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.player.stream.CaptionTrackResolver
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.MergedPlaybackAssembly
import io.github.aedev.flow.player.stream.PlaybackFailure
import io.github.aedev.flow.player.stream.PlaybackLoadResolver
import io.github.aedev.flow.player.stream.PlaybackResolutionRequest
import io.github.aedev.flow.player.stream.ResolvedPlayback
import io.github.aedev.flow.player.stream.ServicePlaybackStreamSelector
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.StreamSizeEstimator
import io.github.aedev.flow.player.stream.UpcomingPremiere
import io.github.aedev.flow.player.stream.UpcomingPremiereProbe
import io.github.aedev.flow.player.stream.VideoQualityOptions
import io.github.aedev.flow.ui.components.FeedInvalidationBus
import io.github.aedev.flow.ui.screens.player.state.PlayerNavigationHistory
import io.github.aedev.flow.ui.screens.player.state.UpcomingPremierePolicy
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import io.github.aedev.flow.utils.NetworkState
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.distinctBestImageUrls
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.*
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: YouTubeRepository,
        private val viewHistory: ViewHistory,
        private val subscriptionRepository: SubscriptionRepository,
        private val likedVideosRepository: LikedVideosRepository,
        private val playlistRepository: io.github.aedev.flow.data.local.PlaylistRepository,
        private val playerPreferences: PlayerPreferences,
        private val videoDownloadManager: VideoDownloadManager,
        private val offlineSubtitleStore: io.github.aedev.flow.data.video.OfflineSubtitleStore,
        private val sponsorBlockRepository: SponsorBlockRepository,
        private val liveChatRepository: io.github.aedev.flow.data.repository.LiveChatRepository,
        private val homeFeedCacheRepository: HomeFeedCacheRepository,
        private val playerManager: EnhancedPlayerManager,
        private val upcomingPremiereProbe: UpcomingPremiereProbe,
        private val playbackResolver: PlaybackLoadResolver,
        @NetworkIoDispatcher private val networkDispatcher: CoroutineDispatcher,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(VideoPlayerUiState())
        val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

        private val _commentsState = MutableStateFlow<List<io.github.aedev.flow.data.model.Comment>>(emptyList())
        val commentsState: StateFlow<List<io.github.aedev.flow.data.model.Comment>> = _commentsState.asStateFlow()

        private val _isLoadingComments = MutableStateFlow(false)
        val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

        private var commentsNextPage: org.schabi.newpipe.extractor.Page? = null

        private val _hasMoreComments = MutableStateFlow(false)
        val hasMoreComments: StateFlow<Boolean> = _hasMoreComments.asStateFlow()

        private val _isLoadingMoreComments = MutableStateFlow(false)
        val isLoadingMoreComments: StateFlow<Boolean> = _isLoadingMoreComments.asStateFlow()

        private val navigationHistory = PlayerNavigationHistory()

        private val playbackPreparer =
            PlaybackPreparer(
                context = context,
                playerManager = playerManager,
                playerPreferences = playerPreferences,
                offlineSubtitleStore = offlineSubtitleStore,
            )

        private val secondaryMetadata =
            PlayerSecondaryMetadataLoader(
                repository = repository,
                playerManager = playerManager,
                playerPreferences = playerPreferences,
                scope = viewModelScope,
                networkDispatcher = networkDispatcher,
                currentState = { _uiState.value },
                relatedVideosFor = ::relatedVideosFor,
                shortsEnabled = { shortsContentEnabled },
                isPlaybackCurrent = ::isPlaybackLoadCurrent,
                onResult = ::applySecondaryMetadata,
            )

        private val watchSessions =
            WatchSessionTracker(
                context = context,
                viewHistory = viewHistory,
                repository = repository,
                homeFeedCacheRepository = homeFeedCacheRepository,
                scope = viewModelScope,
                networkDispatcher = networkDispatcher,
                shortsEnabled = { shortsContentEnabled },
                relatedVideosFor = ::relatedVideosFor,
                richVideoFor = ::resolveRichVideo,
            )

        private val liveChat =
            LiveChatController(
                repository = liveChatRepository,
                scope = viewModelScope,
                dispatcher = networkDispatcher,
            )

        private var activeLoadJob: Job? = null
        private var playbackLoadToken: Long = 0L
        private var loadingVideoId: String? = null
        private var clearedUnplayableVideoId: String? = null
        private var subscriptionStateJob: Job? = null
        private var subscriptionStateChannelId: String? = null
        private var likeStateJob: Job? = null
        private var likeStateVideoId: String? = null
        private val streamExpiryRecovery = StreamExpiryRecoveryController()

        private companion object {
            const val SECONDARY_CONTENT_STARTUP_TIMEOUT_MS = 20_000L
        }

        private val _canGoPrevious = MutableStateFlow(false)
        val canGoPrevious: StateFlow<Boolean> = _canGoPrevious.asStateFlow()

        private val _expandPlayerRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val expandPlayerRequest: SharedFlow<Unit> = _expandPlayerRequest.asSharedFlow()

        private fun nextPlaybackLoadToken(): Long {
            playbackLoadToken += 1L
            return playbackLoadToken
        }

        private fun isPlaybackLoadCurrent(token: Long): Boolean = playbackLoadToken == token

        private fun isLocalMediaId(id: String?): Boolean = id?.startsWith("local_") == true

        private fun cancelActivePlaybackLoad(invalidateToken: Boolean = false) {
            if (invalidateToken) {
                nextPlaybackLoadToken()
            }
            activeLoadJob?.cancel()
            activeLoadJob = null
            loadingVideoId = null
            secondaryMetadata.cancel()
        }

        /** Arms the live chat for [videoId]; the drip loop itself waits for a visible panel. */
        fun maybeStartLiveChat(videoId: String) = liveChat.start(videoId)

        fun stopLiveChat() = liveChat.stop()

        fun setLiveChatPanelVisible(visible: Boolean) = liveChat.setPanelVisible(visible)

        override fun onCleared() {
            super.onCleared()
            watchSessions.finalizeActiveSession()
            stopLiveChat()
        }

        val downloadedVideoIds =
            videoDownloadManager.downloadedVideos
                .map { list -> list.map { it.video.id }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

        fun isVideoSavedToAnyPlaylist(videoId: String): Flow<Boolean> = playlistRepository.isVideoSavedToAnyPlaylistFlow(videoId)

        /**
         * Detect whether the device is currently on Wi-Fi.
         * Used to select the correct quality preference (Wi-Fi vs cellular).
         */
        private fun detectIsWifi(): Boolean = NetworkState.isOnWifi(context)

        @Volatile
        private var shortsContentEnabled: Boolean = true

        init {
            viewModelScope.launch {
                playerPreferences.shortsContentEnabled.collect { shortsContentEnabled = it }
            }

            combine(liveChat.messages, liveChat.isLoading, liveChat.isAvailable, ::Triple)
                .onEach { (messages, isLoading, isAvailable) ->
                    _uiState.update {
                        it.copy(
                            liveChatMessages = messages,
                            isLiveChatLoading = isLoading,
                            isLiveChatAvailable = isAvailable,
                        )
                    }
                }.launchIn(viewModelScope)

            // Re-fetch streams whenever an expired URL is detected (HTTP 403/410 "data changed")
            viewModelScope.launch {
                playerManager.streamExpiredEvent.collect {
                    val videoId = _uiState.value.cachedVideo?.id ?: return@collect
                    if (activeLoadJob?.isActive == true) {
                        Log.d("VideoPlayerViewModel", "Stream expiry for $videoId coalesced — a stream load is already in flight")
                        return@collect
                    }

                    val recovery = streamExpiryRecovery.onStreamExpired(videoId)
                    if (recovery is StreamExpiryRecoveryController.Decision.Ignored) {
                        Log.d("VideoPlayerViewModel", "Ignoring stream expiry for abandoned playback $videoId")
                        return@collect
                    }
                    if (recovery is StreamExpiryRecoveryController.Decision.GiveUp) {
                        Log.e("VideoPlayerViewModel", "Stream expiry retry limit reached for $videoId — giving up")
                        playerPreferences.markVideoUnplayable(videoId)
                        cancelActivePlaybackLoad(invalidateToken = true)
                        playerManager.getPlayer()?.let { p ->
                            p.stop()
                            p.clearMediaItems()
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = context.getString(R.string.error_all_stream_sources_failed),
                                errorHint = context.getString(R.string.error_playback_retry_hint),
                            )
                        }
                        return@collect
                    }
                    val reload = recovery as StreamExpiryRecoveryController.Decision.Reload

                    Log.w(
                        "VideoPlayerViewModel",
                        "Stream expired — re-fetching streams for $videoId " +
                            "(attempt ${reload.attempt}/${reload.limit})",
                    )

                    var recoveryPositionMs = 0L
                    playerManager.getPlayer()?.let { player ->
                        val positionMs = player.currentPosition
                        recoveryPositionMs = positionMs.coerceAtLeast(0L)
                        val durationMs =
                            player.duration.takeIf { it > 0L }
                                ?: ((_uiState.value.cachedVideo?.duration ?: 0) * 1000L)
                        if (positionMs > 0L && durationMs > 0L) {
                            val video = _uiState.value.cachedVideo
                            watchSessions.saveResumePosition(
                                videoId = videoId,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                video = video,
                            )
                        }
                        player.pause()
                        player.stop()
                        player.clearMediaItems()
                    }

                    if (reload.evictCache) {
                        try {
                            playerManager.clearCacheForCurrentVideo()
                        } catch (e: Exception) {
                            Log.w("VideoPlayerViewModel", "Cache eviction failed: ${e.message}")
                        }
                    }

                    _uiState.update { it.copy(error = null, errorHint = null, isLoading = true) }
                    loadVideoInfo(
                        videoId = videoId,
                        isWifi = detectIsWifi(),
                        forceRefresh = true,
                        escalateToSabr = true,
                        resumePositionOverrideMs = recoveryPositionMs,
                    )
                }
            }

            viewModelScope.launch {
                playerManager.playbackAbandonedEvent.collect {
                    val videoId = _uiState.value.cachedVideo?.id ?: return@collect
                    streamExpiryRecovery.onPlaybackAbandoned(videoId)
                    playerPreferences.markVideoUnplayable(videoId)
                    cancelActivePlaybackLoad(invalidateToken = true)
                    Log.w("VideoPlayerViewModel", "Playback abandoned for $videoId — surfacing terminal error")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = context.getString(R.string.error_all_stream_sources_failed),
                            errorHint = context.getString(R.string.error_playback_retry_hint),
                        )
                    }
                }
            }

            viewModelScope.launch {
                playerManager.playerState.collect { playerState ->
                    _uiState.update {
                        it.copy(
                            queueTitle = playerState.queueTitle,
                        )
                    }

                    // A video that prepares successfully is not unplayable, whatever a past failure said.
                    playerState.currentVideoId
                        ?.takeIf { playerState.isPrepared && it != clearedUnplayableVideoId }
                        ?.let { preparedVideoId ->
                            clearedUnplayableVideoId = preparedVideoId
                            playerPreferences.clearVideoUnplayable(preparedVideoId)
                        }

                    // Handle external video id changes (e.g. from queue auto-advance)
                    playerState.currentVideoId?.let { videoId ->
                        val hasActiveStreams = playerState.isPrepared || playerState.isBuffering
                        val isSameVideoNeedsReload =
                            !hasActiveStreams &&
                                _uiState.value.streamInfo == null &&
                                _uiState.value.cachedVideo?.id == videoId
                        if ((
                                (
                                    videoId != _uiState.value.streamInfo?.id &&
                                        videoId != _uiState.value.cachedVideo?.id
                                ) ||
                                    isSameVideoNeedsReload
                            ) &&
                            !_uiState.value.isLoading &&
                            (!_uiState.value.isRestoredSession || !hasActiveStreams)
                        ) {
                            GlobalPlayerState.currentVideo.value?.takeIf { it.id == videoId }?.let { currentVideo ->
                                _uiState.update { it.resetForVideo(currentVideo) }
                                playerManager.startBackgroundService(
                                    videoId = currentVideo.id,
                                    title = currentVideo.title.ifEmpty { "Flow Player" },
                                    channel = currentVideo.channelName,
                                    thumbnail = currentVideo.thumbnailUrl,
                                )
                                watchSessions.saveHistoryEntry(currentVideo)
                            }
                            loadVideoInfo(videoId, isWifi = detectIsWifi(), forceRefresh = true)
                        }
                    }
                }
            }

            // Restore last watched video session so the mini player appears on launch
            viewModelScope.launch {
                val isEnabled = playerPreferences.miniPlayerContinueWatchingEnabled.first()
                if (isEnabled) {
                    // Don't restore video session if music is already playing
                    if (EnhancedMusicPlayerManager.currentTrack.value != null) return@launch
                    val lastVideo = withContext(ioDispatcher) { viewHistory.getLatestUnfinishedVideo() }
                    if (lastVideo != null && _uiState.value.cachedVideo == null) {
                        _uiState.update {
                            it.copy(
                                cachedVideo = lastVideo.toVideo(),
                                isRestoredSession = true,
                            )
                        }
                    }
                }
            }

            viewModelScope.launch {
                FeedInvalidationBus.events.collect { event ->
                    when (event) {
                        is FeedInvalidationBus.Event.NotInterested -> {
                            _uiState.update { state ->
                                state.copy(
                                    relatedVideos = state.relatedVideos.filter { it.id != event.videoId },
                                )
                            }
                        }

                        is FeedInvalidationBus.Event.ChannelBlocked -> {
                            _uiState.update { state ->
                                state.copy(
                                    relatedVideos =
                                        state.relatedVideos.filter {
                                            it.id != event.videoId && it.channelId != event.channelId
                                        },
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }

            viewModelScope.launch {
                playerPreferences.autoplayEnabled
                    .distinctUntilChanged()
                    .collect { autoplay ->
                        _uiState.update { it.copy(autoplayEnabled = autoplay) }
                        _uiState.value.cachedVideo?.id?.let { videoId ->
                            playerManager.setAutoplayCandidates(
                                sourceVideoId = videoId,
                                videos = _uiState.value.relatedVideos,
                                enabled = autoplay,
                            )
                        }
                    }
            }

            viewModelScope.launch {
                combine(
                    playerPreferences.upcomingVideoReminderIds,
                    uiState.map { it.cachedVideo?.id }.distinctUntilChanged(),
                ) { reminderIds, videoId ->
                    videoId != null && videoId in reminderIds
                }.collect { isReminderSet ->
                    _uiState.update { it.copy(isUpcomingReminderSet = isReminderSet) }
                }
            }
        }

        /**
         * Called when the user interacts with the restored-session mini player (taps play
         * or expands the sheet). Starts loading streams and transitions to active playback.
         * @param stayMini if true, the player will keep playing in mini mode (don't auto-expand)
         */
        fun resumeRestoredSession(stayMini: Boolean = false) {
            val video = _uiState.value.cachedVideo ?: return
            if (!_uiState.value.isRestoredSession) return
            _uiState.update {
                it.copy(
                    isRestoredSession = false,
                    resumedInMiniPlayer = stayMini,
                    isBackgroundPlaybackMode = false,
                )
            }
            playVideo(video)
        }

        fun dismissContinueWatching() {
            val videoId = _uiState.value.cachedVideo?.id ?: return
            viewModelScope.launch {
                viewHistory.markAsWatched(videoId)
            }
        }

        fun ensureNotificationServiceRunning() {
            val video = _uiState.value.cachedVideo ?: return
            playerManager.startBackgroundService(
                videoId = video.id,
                title = video.title.ifEmpty { "Flow Player" },
                channel = video.channelName,
                thumbnail = video.thumbnailUrl,
            )
        }

        fun clearResumedInMiniPlayer() {
            _uiState.update { it.copy(resumedInMiniPlayer = false) }
        }

        private fun applyUpcomingState(
            video: Video,
            preserveQueueTitle: String? = _uiState.value.queueTitle,
        ): Boolean {
            val releaseTimeMs = UpcomingPremierePolicy.releaseTimeFor(video) ?: return false
            _uiState.update { UpcomingPremierePolicy.applyTo(it, video, releaseTimeMs, preserveQueueTitle) }
            return true
        }

        private fun enterUpcomingState(
            videoId: String,
            cached: Video?,
            releaseMs: Long?,
            relatedVideos: List<Video>,
            loadToken: Long,
        ): Boolean {
            if (!isPlaybackLoadCurrent(loadToken)) return true
            val upcomingVideo = UpcomingPremierePolicy.upcomingVideo(videoId, cached, releaseMs)
            _uiState.update { UpcomingPremierePolicy.enterFrom(it, upcomingVideo, relatedVideos, releaseMs) }
            GlobalPlayerState.setCurrentVideo(upcomingVideo)
            return true
        }

        private suspend fun resolveUpcoming(
            videoId: String,
            knownUpcoming: Boolean,
        ): UpcomingPremiere {
            val cached = _uiState.value.cachedVideo?.takeIf { it.id == videoId }
            val flagged = knownUpcoming || cached?.isUpcoming == true
            val listReleaseMs = cached?.let { UpcomingPremierePolicy.releaseTimeFor(it) }
            if (!UpcomingPremierePolicy.needsProbe(flagged, listReleaseMs)) {
                return UpcomingPremiere(isUpcoming = true, scheduledStartMs = listReleaseMs)
            }
            val probe = upcomingPremiereProbe.probe(videoId)
            PlayerDiagnostics.logWarning(
                "Upcoming",
                "videoId=$videoId flagged=$flagged known=$knownUpcoming " +
                    "probe=${probe.isUpcoming} probeTime=${probe.scheduledStartMs}",
            )
            return UpcomingPremierePolicy.resolve(flagged, listReleaseMs, probe)
        }

        private suspend fun tryEnterUpcomingState(
            videoId: String,
            relatedVideos: List<Video>,
            loadToken: Long,
            knownUpcoming: Boolean = false,
        ): Boolean {
            val (isUpcoming, releaseMs) = resolveUpcoming(videoId, knownUpcoming)
            if (!isUpcoming) return false
            val cached = _uiState.value.cachedVideo?.takeIf { it.id == videoId }
            return enterUpcomingState(videoId, cached, releaseMs, relatedVideos, loadToken)
        }

        fun toggleUpcomingReminder() {
            val state = _uiState.value
            val video = state.cachedVideo ?: return
            val releaseTimeMs = state.upcomingReleaseTimeMs ?: UpcomingPremierePolicy.releaseTimeFor(video) ?: return
            if (!state.isUpcoming) return

            viewModelScope.launch {
                val enableReminder = !state.isUpcomingReminderSet
                playerPreferences.setUpcomingVideoReminder(video.id, enableReminder)
                if (enableReminder) {
                    UpcomingVideoReminderWorker.scheduleReminder(
                        context = context,
                        videoId = video.id,
                        releaseTimeMs = releaseTimeMs,
                        title = video.title,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                    )
                } else {
                    UpcomingVideoReminderWorker.cancelReminder(context, video.id)
                }
                _uiState.update { it.copy(isUpcomingReminderSet = enableReminder) }
            }
        }

        fun syncWithCurrentPlayerVideo(video: Video) {
            val state = _uiState.value
            val alreadySynced =
                state.cachedVideo?.id == video.id &&
                    (state.streamInfo?.id == video.id || state.isLoading || state.isLive || !state.hlsUrl.isNullOrEmpty())
            if (alreadySynced) return

            if (applyUpcomingState(video)) {
                return
            }

            _uiState.update { it.resetForVideo(video) }
            loadVideoInfo(video.id, isWifi = detectIsWifi(), forceRefresh = true)
        }

        /**
         * Plays a video by immediately caching metadata and triggering stream load.
         * This ensures the UI shows video info immediately while streams are fetched.
         */
        fun playVideo(video: Video) {
            val playbackState = playerManager.playerState.value
            val isMiniPlayerCollapsed =
                GlobalPlayerState.miniPlayerExpansionState.value == MiniPlayerExpansionState.COLLAPSED
            val hasReusablePlayback =
                playbackState.isPrepared ||
                    playbackState.isPlaying ||
                    playbackState.playWhenReady ||
                    playbackState.isBuffering
            if (
                BackgroundPlaybackPolicy.shouldReopenCurrentVideo(
                    requestedVideoId = video.id,
                    currentVideoId = playbackState.currentVideoId,
                    isBackgroundPlaybackMode = _uiState.value.isBackgroundPlaybackMode,
                    isMiniPlayerCollapsed = isMiniPlayerCollapsed,
                    hasReusablePlayback = hasReusablePlayback,
                )
            ) {
                showVideoPlayer()
                _expandPlayerRequest.tryEmit(Unit)
                return
            }

            nextPlaybackLoadToken()
            cancelActivePlaybackLoad()

            streamExpiryRecovery.onPlaybackRequested()

            // Stop current playback and clear everything (including any active queue)
            playerManager.pause()
            playerManager.clearAll()

            // Ensure music player is stopped and hidden
            EnhancedMusicPlayerManager.stop()
            EnhancedMusicPlayerManager.clearCurrentTrack()

            // Cache video metadata for immediate UI display
            _uiState.value =
                _uiState.value.resetForVideo(video).copy(
                    isBackgroundPlaybackMode = false,
                    shouldDismissPlayer = false,
                    channelAvatarUrl = video.channelThumbnailUrl.takeIf { it.isNotBlank() },
                    channelSubscriberCount = null,
                )
            GlobalPlayerState.setCurrentVideo(video)
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
            watchSessions.saveHistoryEntry(video)
            playerManager.startBackgroundService(
                videoId = video.id,
                title = video.title.ifEmpty { "Flow Player" },
                channel = video.channelName,
                thumbnail = video.thumbnailUrl,
            )
            if (applyUpcomingState(video)) {
                return
            }
            // Start loading streams
            loadVideoInfo(video.id, isWifi = detectIsWifi(), forceRefresh = true)
        }

        fun playLocalVideo(
            video: Video,
            contentUri: String,
        ) {
            val loadToken = nextPlaybackLoadToken()
            cancelActivePlaybackLoad()

            streamExpiryRecovery.onPlaybackRequested()

            playerManager.pause()
            playerManager.clearAll()
            EnhancedMusicPlayerManager.stop()
            EnhancedMusicPlayerManager.clearCurrentTrack()

            _uiState.value =
                _uiState.value.resetForVideo(video).copy(
                    isBackgroundPlaybackMode = false,
                    shouldDismissPlayer = false,
                    isLoading = false,
                    channelAvatarUrl = video.channelThumbnailUrl.takeIf { it.isNotBlank() },
                    channelSubscriberCount = null,
                    localFilePath = contentUri,
                    localFileVideoId = video.id,
                    offlineSponsorBlockSegments = null,
                )
            GlobalPlayerState.setCurrentVideo(video)
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
            playerManager.startBackgroundService(
                videoId = video.id,
                title = video.title.ifEmpty { "Flow Player" },
                channel = video.channelName,
                thumbnail = video.thumbnailUrl,
            )

            viewModelScope.launch {
                val resumePosition = runCatching { viewHistory.getSavedPosition(video.id) }.getOrDefault(0L)
                prepareLocalMediaForPlayback(
                    videoId = video.id,
                    localFilePath = contentUri,
                    offlineSegments = null,
                    savedPosition = resumePosition,
                    loadToken = loadToken,
                )
            }
        }

        fun clearVideo() {
            nextPlaybackLoadToken()
            cancelActivePlaybackLoad()
            streamExpiryRecovery.onPlaybackRequested()
            playerManager.stop()
            playerManager.stopBackgroundService()
            playerManager.clearAll()
            GlobalPlayerState.setCurrentVideo(null)
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
            GlobalPlayerState.hideMiniPlayer()

            _uiState.update {
                VideoPlayerUiState(
                    autoplayEnabled = it.autoplayEnabled,
                    isAdaptiveMode = it.isAdaptiveMode,
                )
            }

            navigationHistory.clear()
            _canGoPrevious.value = false

            _commentsState.value = emptyList()
            _isLoadingComments.value = false
            commentsNextPage = null
            _hasMoreComments.value = false
            _isLoadingMoreComments.value = false
        }

        fun startBackgroundPlayback() {
            val state = _uiState.value
            val video = state.cachedVideo ?: GlobalPlayerState.currentVideo.value ?: return
            playerManager.startBackgroundService(
                videoId = video.id,
                title = video.title,
                channel = video.channelName,
                thumbnail = video.thumbnailUrl,
            )
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(true)
            playerManager.continueVideoPlaybackInBackground()
            _uiState.update {
                it.copy(
                    shouldDismissPlayer = true,
                    isBackgroundPlaybackMode = true,
                )
            }
        }

        fun resetDismissState() {
            _uiState.update { it.copy(shouldDismissPlayer = false) }
        }

        fun showVideoPlayer() {
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
            playerManager.restoreVideoOutput()
            _uiState.update {
                it.copy(
                    shouldDismissPlayer = false,
                    isBackgroundPlaybackMode = false,
                )
            }
        }

        fun retryLoadVideo() {
            val videoId = _uiState.value.cachedVideo?.id ?: return
            Log.d("VideoPlayerViewModel", "Retrying video load for $videoId")
            if (applyUpcomingState(_uiState.value.cachedVideo ?: return)) {
                return
            }
            streamExpiryRecovery.onPlaybackRequested()
            playerManager.clearCurrentVideo()
            _uiState.update { it.copy(error = null, errorHint = null, isLoading = true) }
            loadVideoInfo(videoId, isWifi = detectIsWifi(), forceRefresh = true)
        }

        fun ensurePlaybackPrepared(videoId: String) {
            val state = _uiState.value
            if (state.isLoading || state.error != null || state.isRestoredSession) return
            if (state.cachedVideo?.id != videoId && state.streamInfo?.id != videoId && state.localFileVideoId != videoId) return

            val manager = playerManager
            if (manager.isPreparedForPlayback(videoId)) return

            viewModelScope.launch {
                val latest = _uiState.value
                if (latest.isLoading || latest.error != null || latest.isRestoredSession) return@launch
                if (manager.isPreparedForPlayback(videoId)) return@launch

                val loadToken = playbackLoadToken
                val localFilePath =
                    latest.localFilePath?.takeIf {
                        latest.localFileVideoId == null || latest.localFileVideoId == videoId
                    }
                if (localFilePath != null && latest.streamInfo == null) {
                    Log.w("VideoPlayerViewModel", "Late prepare: arming local playback for $videoId")
                    prepareLocalMediaForPlayback(
                        videoId = videoId,
                        localFilePath = localFilePath,
                        offlineSegments = latest.offlineSponsorBlockSegments,
                        savedPosition =
                            latest.savedPosition
                                ?: viewHistory.getPlaybackPosition(videoId).first(),
                        loadToken = loadToken,
                    )
                    return@launch
                }

                val streamInfo = latest.streamInfo ?: return@launch
                val audioStream = latest.audioStream
                val videoStreams =
                    (streamInfo.videoStreams + (streamInfo.videoOnlyStreams ?: emptyList()))
                        .filterIsInstance<VideoStream>()
                if (audioStream == null &&
                    videoStreams.isEmpty() &&
                    streamInfo.dashMpdUrl.isNullOrEmpty() &&
                    latest.hlsUrl.isNullOrEmpty()
                ) {
                    Log.w("VideoPlayerViewModel", "Late prepare skipped for $videoId: no playable streams in UI state")
                    return@launch
                }

                Log.w(
                    "VideoPlayerViewModel",
                    "Late prepare: arming stream playback for $videoId (audio=${audioStream != null}, videos=${videoStreams.size})",
                )
                playbackPreparer.prepareMergedStreams(
                    videoId = videoId,
                    streamInfo = streamInfo,
                    videoStream = latest.videoStream,
                    audioStream = audioStream,
                    videoStreams = videoStreams,
                    audioStreams = streamInfo.audioStreams,
                    subtitles = streamInfo.subtitles ?: emptyList(),
                    savedPosition =
                        latest.savedPosition
                            ?: viewHistory.getPlaybackPosition(videoId).first(),
                    fallbackDurationSeconds = cachedDurationSeconds(),
                    localFilePath = localFilePath,
                    offlineSegments = latest.offlineSponsorBlockSegments,
                    hlsUrl = latest.hlsUrl,
                    isAdaptiveMode = latest.isAdaptiveMode,
                    resumeOverrideRequested = false,
                    isCurrent = { isPlaybackLoadCurrent(loadToken) },
                    preferredVideoCodec = playerPreferences.videoCodecPriority.first(),
                )
            }
        }

        fun playPlaylist(
            videos: List<Video>,
            startIndex: Int,
            title: String? = null,
        ) {
            if (videos.isEmpty()) return
            val startVideo = videos.getOrNull(startIndex) ?: videos.first()

            EnhancedMusicPlayerManager.stop()
            EnhancedMusicPlayerManager.clearCurrentTrack()

            playerManager.setQueue(videos, startIndex, title)

            _uiState.update { it.resetForVideo(startVideo).copy(queueTitle = title) }
            watchSessions.saveHistoryEntry(startVideo)
            playerManager.startBackgroundService(
                videoId = startVideo.id,
                title = startVideo.title.ifEmpty { "Flow Player" },
                channel = startVideo.channelName,
                thumbnail = startVideo.thumbnailUrl,
            )
            if (applyUpcomingState(startVideo, preserveQueueTitle = title)) {
                return
            }
            loadVideoInfo(startVideo.id, isWifi = detectIsWifi(), forceRefresh = true)
        }

        fun playNext() {
            val handledByPlayer = playerManager.playNext(loadStreamsInPlayer = false)
            if (!handledByPlayer) {
                _uiState.value.relatedVideos.firstOrNull()?.let { nextVideo ->
                    playVideo(nextVideo)
                    io.github.aedev.flow.player.GlobalPlayerState
                        .setCurrentVideo(nextVideo)
                }
            }
        }

        fun playPrevious() {
            val handledByPlayer = playerManager.playPrevious(loadStreamsInPlayer = false)
            if (!handledByPlayer) {
                getPreviousVideoId()?.let { prevId ->
                    val prevVideo =
                        Video(
                            id = prevId,
                            title = "",
                            channelName = "",
                            channelId = "",
                            thumbnailUrl = "",
                            duration = 0,
                            viewCount = 0,
                            uploadDate = "",
                        )
                    playVideo(prevVideo)
                    io.github.aedev.flow.player.GlobalPlayerState
                        .setCurrentVideo(prevVideo)
                }
            }
        }

        /**
         * PERFORMANCE OPTIMIZED: Load video info with aggressive parallel fetching
         * Uses SupervisorScope for error isolation and optimized dispatcher for network operations
         * @param forceRefresh If true, forces a fresh load even if the video appears to be already loaded
         * @param escalateToSabr If true (a 403-expiry reload), skip the fast direct-URL clients and
         *   extract straight through the durable WEB+PoToken+SABR path — fast clients return the same
         *   session-gated URLs that just 403'd, so re-trying them loops.
         */
        fun loadVideoInfo(
            videoId: String,
            isWifi: Boolean = true,
            forceRefresh: Boolean = false,
            escalateToSabr: Boolean = false,
            resumePositionOverrideMs: Long? = null,
        ) {
            if (isLocalMediaId(videoId)) {
                Log.d("VideoPlayerViewModel", "loadVideoInfo: $videoId is a local file — skipping all network loading")
                return
            }
            val currentState = _uiState.value
            Log.d(
                "VideoPlayerViewModel",
                "loadVideoInfo: Request=$videoId. Current=${currentState.streamInfo?.id}, " +
                    "IsLoading=${currentState.isLoading}, ForceRefresh=$forceRefresh, " +
                    "escalateToSabr=$escalateToSabr",
            )
            streamExpiryRecovery.onLoadStarted(videoId)

            currentState.cachedVideo
                ?.takeIf { it.id == videoId && it.isUpcoming }
                ?.let { cachedVideo ->
                    val releaseTimeMs = UpcomingPremierePolicy.releaseTimeFor(cachedVideo)
                    if (releaseTimeMs != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                errorHint = null,
                                streamInfo = null,
                                videoStream = null,
                                audioStream = null,
                                localFilePath = null,
                                localFileVideoId = null,
                                isUpcoming = true,
                                upcomingReleaseTimeMs = releaseTimeMs,
                            )
                        }
                        return
                    }
                }

            // Don't reload if already loaded the same video successfully (unless forceRefresh)
            if (!forceRefresh && currentState.streamInfo?.id == videoId && !currentState.isLoading && currentState.error == null) {
                Log.d("VideoPlayerViewModel", "Video $videoId already loaded successfully. Skipping.")
                return
            }

            if (!forceRefresh && currentState.isLoading &&
                (currentState.streamInfo?.id == videoId || currentState.cachedVideo?.id == videoId)
            ) {
                Log.d("VideoPlayerViewModel", "Video $videoId is currently loading. Skipping redundant request.")
                return
            }

            navigationHistory.push(videoId)
            _canGoPrevious.value = navigationHistory.canGoPrevious

            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    errorHint = null,
                    streamInfo = null,
                    videoStream = null,
                    audioStream = null,
                    streamSizes = emptyMap(),
                    savedPosition = null,
                    relatedVideos = emptyList(),
                    channelAvatarUrl =
                        _uiState.value.cachedVideo
                            ?.takeIf { it.id == videoId }
                            ?.channelThumbnailUrl
                            ?.takeIf { it.isNotBlank() },
                    channelSubscriberCount = null,
                    dislikeCount = null,
                    // Also reset subscription and like state for new video
                    isSubscribed = false,
                    likeState = null,
                    hlsUrl = null,
                    localFilePath = null,
                    localFileVideoId = null,
                    isUpcoming = false,
                    upcomingReleaseTimeMs = null,
                    isLive = false,
                    isLiveChatAvailable = false,
                    liveChatMessages = emptyList(),
                    isLiveChatLoading = false,
                )
            stopLiveChat()

            if (activeLoadJob?.isActive == true && loadingVideoId == videoId) {
                Log.d("VideoPlayerViewModel", "loadVideoInfo: extraction already in flight for $videoId — ignoring redundant trigger")
                return
            }

            cancelActivePlaybackLoad()
            val loadToken = nextPlaybackLoadToken()
            loadingVideoId = videoId

            activeLoadJob =
                viewModelScope.launch(networkDispatcher) {
                    Log.d("VideoPlayerViewModel", "Starting loadVideoInfo for $videoId")
                    loadDislikeCount(videoId, loadToken)
                    try {
                        playbackResolver.resolve(
                            scope = this,
                            request =
                                PlaybackResolutionRequest(
                                    videoId = videoId,
                                    isWifi = isWifi,
                                    escalateToSabr = escalateToSabr,
                                    resumePositionOverrideMs = resumePositionOverrideMs,
                                    allowShorts = shortsContentEnabled,
                                ),
                            isCurrent = { isPlaybackLoadCurrent(loadToken) },
                            resolveUpcoming = ::resolveUpcoming,
                            onStep = { step -> applyResolvedPlayback(videoId, step, loadToken) },
                        )
                    } finally {
                        if (isPlaybackLoadCurrent(loadToken)) {
                            activeLoadJob = null
                        }
                    }
                }
        }

        private fun loadDislikeCount(
            videoId: String,
            loadToken: Long,
        ) {
            viewModelScope.launch(networkDispatcher) {
                if (playerPreferences.rytdEnabled.first()) {
                    withTimeoutOrNull(5000L) {
                        repository.returnYouTubeDislikeCounts(videoId)
                    }?.dislikes?.let { dislikeCount ->
                        if (isPlaybackLoadCurrent(loadToken) &&
                            (_uiState.value.cachedVideo?.id == videoId || _uiState.value.streamInfo?.id == videoId)
                        ) {
                            _uiState.update { it.copy(dislikeCount = dislikeCount) }
                        }
                    }
                }
            }
        }

        private suspend fun applyResolvedPlayback(
            videoId: String,
            step: ResolvedPlayback,
            loadToken: Long,
        ) {
            when (step) {
                is ResolvedPlayback.PrimaryMetadata -> {
                    applyPrimaryMetadata(videoId, step.streamInfo, loadToken)
                }

                is ResolvedPlayback.LocalCopyReady -> {
                    _uiState.update {
                        it.copy(
                            streamInfo = if (step.clearStreamInfo) null else it.streamInfo,
                            localFilePath = step.localFilePath,
                            localFileVideoId = videoId,
                            offlineSponsorBlockSegments = step.offlineSegments,
                            error = null,
                            errorHint = null,
                            isLoading = false,
                            isUpcoming = false,
                            upcomingReleaseTimeMs = null,
                        )
                    }
                    prepareLocalMediaForPlayback(
                        videoId = videoId,
                        localFilePath = step.localFilePath,
                        offlineSegments = step.offlineSegments,
                        savedPosition = viewHistory.getPlaybackPosition(videoId).first(),
                        loadToken = loadToken,
                    )
                }

                is ResolvedPlayback.LocalCopyAfterFailure -> {
                    _uiState.update { it.copy(isLoading = false, error = null, errorHint = null) }
                    step.localFilePath?.let { localPath ->
                        prepareLocalMediaForPlayback(
                            videoId = videoId,
                            localFilePath = localPath,
                            offlineSegments = step.offlineSegments,
                            savedPosition = viewHistory.getPlaybackPosition(videoId).first(),
                            loadToken = loadToken,
                        )
                    }
                }

                is ResolvedPlayback.OfflineFallback -> {
                    if (isPlaybackLoadCurrent(loadToken)) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                errorHint = null,
                                relatedVideos = step.relatedVideos,
                                localFilePath = step.localFilePath,
                                offlineSponsorBlockSegments = step.offlineSegments,
                                isUpcoming = false,
                                upcomingReleaseTimeMs = null,
                            )
                        }
                    }
                }

                is ResolvedPlayback.Merged -> {
                    applyMergedPlayback(videoId, step, loadToken)
                }

                is ResolvedPlayback.Live -> {
                    prepareLiveStreamFromInnerTube(videoId, step.result, step.relatedVideos, loadToken)
                    step.lateStreamInfo?.let { secondaryMetadata.enrichWhenReady(videoId, it, loadToken) }
                }

                is ResolvedPlayback.VodFromInnerTube -> {
                    applyVodFromInnerTube(videoId, step, loadToken)
                }

                is ResolvedPlayback.Upcoming -> {
                    enterUpcomingState(
                        videoId = videoId,
                        cached = _uiState.value.cachedVideo?.takeIf { it.id == videoId },
                        releaseMs = step.releaseTimeMs,
                        relatedVideos = step.relatedVideos,
                        loadToken = loadToken,
                    )
                }

                is ResolvedPlayback.Failed -> {
                    applyPlaybackFailure(videoId, step, loadToken)
                }
            }
        }

        private fun applyPrimaryMetadata(
            videoId: String,
            streamInfo: StreamInfo,
            loadToken: Long,
        ) {
            // Record interaction for Flow Neuro Engine — off the startup path: it takes the brain
            // mutex and updates vectors, none of which first frame needs.
            viewModelScope.launch(ioDispatcher) {
                try {
                    val video =
                        Video(
                            id = videoId,
                            title = streamInfo.name ?: "",
                            channelName = streamInfo.uploaderName ?: "",
                            channelId = streamInfo.uploaderUrl?.split("/")?.last() ?: "",
                            thumbnailUrl = streamInfo.thumbnails?.maxByOrNull { it.height }?.url ?: "",
                            duration = streamInfo.duration.toInt(),
                            viewCount = streamInfo.viewCount,
                            uploadDate = "",
                            description = streamInfo.description?.content ?: "",
                            tags = streamInfo.tags ?: emptyList(),
                        )
                    FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.CLICK)
                } catch (e: Exception) {
                    Log.e("VideoPlayerViewModel", "Failed to record interaction", e)
                }
            }

            val realTitle = streamInfo.name?.takeIf { it.isNotBlank() } ?: return
            val realChannel = streamInfo.uploaderName?.takeIf { it.isNotBlank() }
            val realThumbnail =
                streamInfo.thumbnails
                    ?.maxByOrNull { it.height }
                    ?.url
                    ?.takeIf { it.isNotBlank() }
            val currentCached = _uiState.value.cachedVideo
            val enrichedVideo =
                (
                    currentCached ?: Video(
                        id = videoId,
                        title = "",
                        channelName = "",
                        channelId = "",
                        thumbnailUrl = "",
                        duration = 0,
                        viewCount = 0L,
                        uploadDate = "",
                    )
                ).copy(
                    title = realTitle,
                    channelName = realChannel ?: currentCached?.channelName ?: "",
                    channelId =
                        currentCached?.channelId?.takeIf { it.isNotBlank() }
                            ?: streamInfo.uploaderUrl?.split("/")?.last() ?: "",
                    thumbnailUrl = realThumbnail ?: currentCached?.thumbnailUrl ?: "",
                    duration = streamInfo.duration.toInt().takeIf { it > 0 } ?: (currentCached?.duration ?: 0),
                )
            if (isPlaybackLoadCurrent(loadToken)) {
                GlobalPlayerState.setCurrentVideo(enrichedVideo)
                playerManager.startBackgroundService(
                    videoId = videoId,
                    title = realTitle,
                    channel = realChannel ?: "",
                    thumbnail = realThumbnail ?: "",
                )
            }
        }

        private suspend fun applyMergedPlayback(
            videoId: String,
            step: ResolvedPlayback.Merged,
            loadToken: Long,
        ) {
            val streamInfo = step.streamInfo
            val streams = step.streams
            if (step.sponsorBlockBackfillNeeded) {
                backfillSponsorBlockSegments(videoId)
            }

            playerManager.setAutoplayCandidates(
                sourceVideoId = videoId,
                videos = step.relatedVideos,
                enabled = step.autoplayEnabled,
            )

            _uiState.value =
                _uiState.value.copy(
                    streamInfo = streamInfo,
                    relatedVideos = step.relatedVideos,
                    videoStream = if (step.isUpcomingContent) null else streams.selectedVideoStream,
                    audioStream = if (step.isUpcomingContent) null else streams.selectedAudioStream,
                    availableQualities = streams.availableQualities,
                    selectedQuality = VideoQualityOptions.qualityOf(streams.selectedVideoStream),
                    chapters = streams.chapters,
                    isLoading = false,
                    savedPosition = step.savedPositionMs,
                    isAdaptiveMode = streams.isAdaptiveMode,
                    autoplayEnabled = step.autoplayEnabled,
                    streamSizes = streams.streamSizes,
                    localFilePath = streams.localFilePath,
                    localFileVideoId = if (streams.localFilePath != null) videoId else null,
                    offlineSponsorBlockSegments = step.offlineSegments,
                    hlsUrl = if (step.isUpcomingContent) null else streams.hlsUrl,
                    isLive = !step.isUpcomingContent && streams.isLiveStream,
                    isUpcoming = step.isUpcomingContent,
                    upcomingReleaseTimeMs = step.upcomingReleaseTimeMs,
                    innerTubeVideoFormats = streams.innerTubeVideoFormats,
                    innerTubeAudioFormats = streams.innerTubeAudioFormats,
                )

            currentCoroutineContext().ensureActive()
            if (!isPlaybackLoadCurrent(loadToken)) return

            if (!step.isUpcomingContent) {
                playbackPreparer.prepareMergedStreams(
                    videoId = videoId,
                    streamInfo = streamInfo,
                    videoStream = streams.selectedVideoStream,
                    audioStream = streams.selectedAudioStream,
                    videoStreams = streams.videoStreams,
                    audioStreams = streams.audioStreams,
                    subtitles = streams.subtitles,
                    savedPosition = step.savedPositionMs,
                    fallbackDurationSeconds = cachedDurationSeconds(),
                    localFilePath = streams.localFilePath,
                    offlineSegments = step.offlineSegments,
                    hlsUrl = streams.hlsUrl,
                    dashManifestUrl = streams.dashManifestUrl,
                    isAdaptiveMode = streams.isAdaptiveMode,
                    resumeOverrideRequested = step.resumeOverrideRequested,
                    isCurrent = { isPlaybackLoadCurrent(loadToken) },
                    sabrInfo = streams.sabrInfo,
                    itVideoFormats = streams.innerTubeVideoFormats,
                    itAudioFormats = streams.innerTubeAudioFormats,
                    preferredVideoCodec = streams.preferredCodecKey,
                    preferSabr = streams.preferSabr,
                    preferredLiveQualityHeight = streams.preferredQuality.height,
                )
                secondaryMetadata.loadChannelMetadata(
                    videoId = videoId,
                    uploaderUrl = streamInfo.uploaderUrl,
                    channelId = _uiState.value.cachedVideo?.channelId,
                    embeddedAvatarUrls = streamInfo.uploaderAvatars.distinctBestImageUrls(),
                    loadToken = loadToken,
                )
                if (!streams.isLiveType) {
                    secondaryMetadata.loadRelatedVideos(videoId, step.relatedVideos, loadToken)
                }
            }

            if (!step.isUpcomingContent && streams.isLiveStream) {
                maybeStartLiveChat(videoId)
                secondaryMetadata.refreshLiveWatchMetadata(
                    videoId = videoId,
                    fallbackVideo =
                        Video(
                            id = videoId,
                            title = streamInfo.name ?: _uiState.value.cachedVideo?.title ?: "Live",
                            channelName = streamInfo.uploaderName ?: _uiState.value.cachedVideo?.channelName ?: "",
                            channelId =
                                streamInfo.uploaderUrl?.substringAfterLast("/")
                                    ?: _uiState.value.cachedVideo?.channelId ?: "",
                            thumbnailUrl =
                                streamInfo.thumbnails.maxByOrNull { it.height }?.url
                                    ?: _uiState.value.cachedVideo?.thumbnailUrl
                                    ?: ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, null),
                            duration = 0,
                            viewCount = streamInfo.viewCount,
                            uploadDate = "",
                            description =
                                streamInfo.description?.content
                                    ?: _uiState.value.cachedVideo?.description
                                    ?: "",
                            isLive = true,
                        ),
                    loadToken = loadToken,
                )
            }
        }

        private suspend fun applyVodFromInnerTube(
            videoId: String,
            step: ResolvedPlayback.VodFromInnerTube,
            loadToken: Long,
        ) {
            try {
                prepareVodStreamFromInnerTube(
                    videoId = videoId,
                    result = step.result,
                    relatedVideos = step.relatedVideos,
                    preferredQuality = step.preferredQuality,
                    preferredAudioLanguage = step.preferredAudioLanguage,
                    preferredCodecKey = step.preferredCodecKey,
                    resumePositionOverrideMs = step.resumePositionOverrideMs,
                    loadToken = loadToken,
                )
                step.lateStreamInfo?.let { secondaryMetadata.enrichWhenReady(videoId, it, loadToken) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "InnerTube VOD fallback failed for $videoId", e)
                if (!tryEnterUpcomingState(videoId, step.relatedVideos, loadToken)) {
                    val videoError = VideoErrorMapper.from(context, step.streamError ?: e, videoId)
                    if (isPlaybackLoadCurrent(loadToken)) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                relatedVideos = step.relatedVideos,
                                error = videoError.message,
                                errorHint = videoError.hint,
                            )
                        }
                    }
                }
            }
        }

        private suspend fun applyPlaybackFailure(
            videoId: String,
            step: ResolvedPlayback.Failed,
            loadToken: Long,
        ) {
            if (!isPlaybackLoadCurrent(loadToken)) return
            val videoError =
                when (step.failure) {
                    PlaybackFailure.TIMEOUT -> VideoErrorMapper.fromTimeout(context)
                    else -> VideoErrorMapper.from(context, step.cause, videoId)
                }
            if (step.failure == PlaybackFailure.UNEXPECTED && !videoError.isRetryable) {
                playerPreferences.markVideoUnplayable(videoId)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    relatedVideos = step.relatedVideos ?: it.relatedVideos,
                    error = videoError.message,
                    errorHint = videoError.hint,
                )
            }
        }

        private fun backfillSponsorBlockSegments(videoId: String) {
            viewModelScope.launch(networkDispatcher) {
                try {
                    val segments = sponsorBlockRepository.getSegments(videoId)
                    if (segments.isNotEmpty()) {
                        videoDownloadManager.saveSponsorBlockData(
                            videoId,
                            sponsorBlockRepository.serializeSegments(segments),
                        )
                        Log.d("VideoPlayerViewModel", "Backfilled ${segments.size} SB segments for $videoId")
                        _uiState.update { it.copy(offlineSponsorBlockSegments = segments) }
                    } else {
                        Log.d("VideoPlayerViewModel", "No SB segments available for $videoId (backfill)")
                    }
                } catch (e: Exception) {
                    Log.w("VideoPlayerViewModel", "SB backfill failed for $videoId", e)
                }
            }
        }

        private suspend fun prepareLiveStreamFromInnerTube(
            videoId: String,
            result: InnerTubeVideoStreamExtractor.VideoExtractionResult,
            relatedVideos: List<Video>,
            loadToken: Long,
        ) = withContext(Dispatchers.Main) {
            if (!isPlaybackLoadCurrent(loadToken)) return@withContext

            val details = result.playerResponse.videoDetails
            val cached = _uiState.value.cachedVideo
            val title = details?.title?.takeIf { it.isNotBlank() } ?: cached?.title ?: "Live"
            val channel = details?.author?.takeIf { it.isNotBlank() } ?: cached?.channelName ?: ""
            val channelId = details?.channelId?.takeIf { it.isNotBlank() } ?: cached?.channelId ?: ""
            val thumbnail =
                details
                    ?.thumbnail
                    ?.thumbnails
                    ?.maxByOrNull { it.height ?: 0 }
                    ?.url
                    ?: cached?.thumbnailUrl ?: ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, null)

            val enrichedVideo =
                (
                    cached ?: Video(
                        id = videoId,
                        title = "",
                        channelName = "",
                        channelId = "",
                        thumbnailUrl = "",
                        duration = 0,
                        viewCount = 0L,
                        uploadDate = "",
                    )
                ).copy(
                    title = title,
                    channelName = channel,
                    channelId = channelId,
                    thumbnailUrl = thumbnail,
                    duration = 0,
                )
            GlobalPlayerState.setCurrentVideo(enrichedVideo)

            playbackPreparer.beginSession(videoId = videoId, title = title, channel = channel, thumbnail = thumbnail)
            playbackPreparer.applyAutoplayCandidates(videoId = videoId, videos = relatedVideos)

            val liveCaptionStreams =
                StreamProcessor.processSubtitleStreams(
                    CaptionTrackResolver.resolve(result.playerResponse),
                )

            _uiState.update {
                it.copy(
                    streamInfo = null,
                    relatedVideos = relatedVideos,
                    isLoading = false,
                    error = null,
                    errorHint = null,
                    hlsUrl = result.liveHlsUrl,
                    isLive = true,
                    isUpcoming = false,
                    upcomingReleaseTimeMs = null,
                    innerTubeVideoFormats = emptyList(),
                    innerTubeAudioFormats = emptyList(),
                )
            }

            val liveStarted =
                playbackPreparer.prepareLiveStreams(
                    videoId = videoId,
                    hlsUrl = result.liveHlsUrl,
                    dashManifestUrl = result.liveDashUrl,
                    subtitles = liveCaptionStreams,
                    isCurrent = { isPlaybackLoadCurrent(loadToken) },
                )
            if (!liveStarted) return@withContext

            secondaryMetadata.loadChannelMetadata(
                videoId = videoId,
                uploaderUrl = null,
                channelId = channelId,
                embeddedAvatarUrls =
                    listOfNotNull(cached?.channelThumbnailUrl) +
                        cached?.channelThumbnailUrls.orEmpty(),
                loadToken = loadToken,
            )

            maybeStartLiveChat(videoId)

            secondaryMetadata.refreshLiveWatchMetadata(videoId, enrichedVideo, loadToken)
        }

        private fun applySecondaryMetadata(result: SecondaryMetadata) {
            when (result) {
                is SecondaryMetadata.Channel -> applyChannelMetadata(result)
                is SecondaryMetadata.Related -> publishRelatedVideos(result.videoId, result.videos, result.loadToken)
                is SecondaryMetadata.Enriched -> applyEnrichedMetadata(result)
                is SecondaryMetadata.LiveWatch -> applyLiveWatchMetadata(result)
            }
        }

        private fun applyChannelMetadata(result: SecondaryMetadata.Channel) {
            if (!isPlaybackLoadCurrent(result.loadToken)) return

            val avatarUrl =
                PlayerChannelMetadataPolicy.selectAvatarUrl(
                    fetchedAvatarUrl = result.fetchedAvatarUrl,
                    embeddedAvatarUrl = result.embeddedAvatarUrl,
                    currentAvatarUrl = _uiState.value.channelAvatarUrl,
                )

            _uiState.update { state ->
                val cached = state.cachedVideo
                if (cached?.id != result.videoId) return@update state

                val selectedAvatar =
                    PlayerChannelMetadataPolicy.selectAvatarUrl(
                        fetchedAvatarUrl = avatarUrl,
                        embeddedAvatarUrl = cached.channelThumbnailUrl,
                        currentAvatarUrl = state.channelAvatarUrl,
                    )
                val updatedCached =
                    if (selectedAvatar != null) {
                        cached.copy(
                            channelThumbnailUrl = selectedAvatar,
                            channelThumbnailUrls =
                                (listOf(selectedAvatar) + cached.channelThumbnailUrls)
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .take(2),
                        )
                    } else {
                        cached
                    }

                state.copy(
                    cachedVideo = updatedCached,
                    channelAvatarUrl = selectedAvatar,
                    channelSubscriberCount = result.subscriberCount ?: state.channelSubscriberCount,
                )
            }

            _uiState.value.cachedVideo
                ?.takeIf { it.id == result.videoId }
                ?.let(GlobalPlayerState::setCurrentVideo)
        }

        /** The one place related items reach the player: the autoplay queue and the lane together. */
        private fun publishRelatedVideos(
            videoId: String,
            videos: List<Video>,
            loadToken: Long,
        ) {
            if (!isPlaybackLoadCurrent(loadToken) || videos.isEmpty()) return
            val state = _uiState.value
            if (state.cachedVideo?.id != videoId && state.streamInfo?.id != videoId) return

            viewModelScope.launch {
                if (!isPlaybackLoadCurrent(loadToken)) return@launch
                val autoplay = playerPreferences.autoplayEnabled.first()
                if (!isPlaybackLoadCurrent(loadToken)) return@launch
                playerManager.setAutoplayCandidates(
                    sourceVideoId = videoId,
                    videos = videos,
                    enabled = autoplay,
                )
                _uiState.update { current ->
                    if (current.cachedVideo?.id != videoId && current.streamInfo?.id != videoId) {
                        current
                    } else {
                        current.copy(relatedVideos = videos)
                    }
                }
            }
        }

        private fun applyEnrichedMetadata(result: SecondaryMetadata.Enriched) {
            if (!isPlaybackLoadCurrent(result.loadToken) || _uiState.value.cachedVideo?.id != result.videoId) return

            val streamInfo = result.streamInfo
            GlobalPlayerState.setCurrentVideo(result.video)
            _uiState.update {
                it.copy(
                    cachedVideo = result.video,
                    streamInfo = streamInfo,
                    relatedVideos = result.relatedVideos.ifEmpty { it.relatedVideos },
                    chapters = streamInfo.streamSegments ?: it.chapters,
                    // Late NewPipe metadata adds its own streams to the download dialog's list,
                    // so their sizes have to join the map the dialog looks them up in.
                    streamSizes =
                        StreamSizeEstimator.merge(
                            it.streamSizes,
                            StreamSizeEstimator.fromExtractorStreams(
                                (streamInfo.videoStreams + streamInfo.videoOnlyStreams).filterIsInstance<VideoStream>(),
                                streamInfo.audioStreams,
                                streamInfo.duration,
                            ),
                        ),
                )
            }
        }

        private fun applyLiveWatchMetadata(result: SecondaryMetadata.LiveWatch) {
            if (!isPlaybackLoadCurrent(result.loadToken)) return

            GlobalPlayerState.setCurrentVideo(result.video)
            _uiState.update {
                it.copy(
                    cachedVideo = result.video,
                    channelAvatarUrl = result.channelAvatarUrl ?: it.channelAvatarUrl,
                    channelSubscriberCount = result.subscriberCount ?: it.channelSubscriberCount,
                )
            }
            publishRelatedVideos(result.videoId, result.relatedVideos, result.loadToken)
        }

        private suspend fun prepareVodStreamFromInnerTube(
            videoId: String,
            result: InnerTubeVideoStreamExtractor.VideoExtractionResult,
            relatedVideos: List<Video>,
            preferredQuality: VideoQuality,
            preferredAudioLanguage: String,
            preferredCodecKey: String,
            resumePositionOverrideMs: Long? = null,
            loadToken: Long,
        ) = withContext(Dispatchers.Main) {
            if (!isPlaybackLoadCurrent(loadToken)) return@withContext

            val details = result.playerResponse.videoDetails
            val cached = _uiState.value.cachedVideo
            val title = details?.title?.takeIf { it.isNotBlank() } ?: cached?.title ?: ""
            val channel = details?.author?.takeIf { it.isNotBlank() } ?: cached?.channelName ?: ""
            val channelId = details?.channelId?.takeIf { it.isNotBlank() } ?: cached?.channelId ?: ""
            val thumbnail =
                details
                    ?.thumbnail
                    ?.thumbnails
                    ?.maxByOrNull { it.height ?: 0 }
                    ?.url
                    ?: cached?.thumbnailUrl ?: ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, null)
            val durationSeconds =
                details?.lengthSeconds?.toLongOrNull()?.takeIf { it > 0 }
                    ?: cached?.duration?.toLong()?.takeIf { it > 0 }
                    ?: 0L

            val enrichedVideo =
                (
                    cached ?: Video(
                        id = videoId,
                        title = "",
                        channelName = "",
                        channelId = "",
                        thumbnailUrl = "",
                        duration = 0,
                        viewCount = 0L,
                        uploadDate = "",
                    )
                ).copy(
                    title = title,
                    channelName = channel,
                    channelId = channelId,
                    thumbnailUrl = thumbnail,
                    duration = durationSeconds.toInt(),
                )
            GlobalPlayerState.setCurrentVideo(enrichedVideo)

            playbackPreparer.beginSession(videoId = videoId, title = title, channel = channel, thumbnail = thumbnail)

            val videoStreams = InnerTubeStreamBridge.convertVideoFormats(result.videoFormats)
            val audioStreams = InnerTubeStreamBridge.convertAudioFormats(result.audioFormats)
            val availableQualities = VideoQualityOptions.availableQualities(videoStreams)
            val selected =
                ServicePlaybackStreamSelector.selectStreams(
                    videoCandidates = videoStreams,
                    audioCandidatesAll = audioStreams,
                    preferredQuality = preferredQuality,
                    preferredAudioLanguage = preferredAudioLanguage,
                    preferredCodecKey = preferredCodecKey,
                )

            val captionStreams =
                StreamProcessor.processSubtitleStreams(
                    CaptionTrackResolver.resolve(result.playerResponse),
                )

            val autoplay = playbackPreparer.applyAutoplayCandidates(videoId = videoId, videos = relatedVideos)

            val savedPositionMs =
                resumePositionOverrideMs
                    ?.takeIf { it > 0L }
                    ?: viewHistory.getPlaybackPosition(videoId).first()
            val isAdaptiveMode = preferredQuality == VideoQuality.AUTO

            Log.w(
                "VideoPlayerViewModel",
                "VOD fallback playing $videoId via InnerTube ${result.usedClient.clientName} " +
                    "(sabr=${result.sabrInfo != null}, video=${videoStreams.size}, audio=${audioStreams.size})",
            )

            _uiState.update {
                it.copy(
                    streamInfo = null,
                    relatedVideos = relatedVideos,
                    videoStream = selected.first,
                    audioStream = selected.second,
                    availableQualities = availableQualities,
                    selectedQuality = VideoQualityOptions.qualityOf(selected.first),
                    isLoading = false,
                    error = null,
                    errorHint = null,
                    savedPosition = savedPositionMs,
                    isAdaptiveMode = isAdaptiveMode,
                    autoplayEnabled = autoplay,
                    isLive = false,
                    isUpcoming = false,
                    upcomingReleaseTimeMs = null,
                    innerTubeVideoFormats = result.videoFormats,
                    innerTubeAudioFormats = result.audioFormats,
                    streamSizes =
                        StreamSizeEstimator.fromInnerTubeFormats(
                            result.videoFormats,
                            result.audioFormats,
                            durationSeconds * 1000L,
                        ),
                )
            }

            // Queue and preloaded playback may already own this media item. Arm secondary metadata
            // before the prepared-player return so those transitions still populate the screen.
            secondaryMetadata.loadRelatedVideos(videoId, relatedVideos, loadToken)
            secondaryMetadata.loadChannelMetadata(
                videoId = videoId,
                uploaderUrl = null,
                channelId = channelId,
                embeddedAvatarUrls =
                    listOfNotNull(cached?.channelThumbnailUrl) +
                        cached?.channelThumbnailUrls.orEmpty(),
                loadToken = loadToken,
            )

            playbackPreparer.prepareVodStreams(
                videoId = videoId,
                videoStream = selected.first,
                audioStream = selected.second,
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                subtitles = captionStreams,
                durationSeconds = durationSeconds,
                savedPositionMs = savedPositionMs,
                resumeOverrideRequested = resumePositionOverrideMs != null,
                isAdaptiveMode = isAdaptiveMode,
                sabrInfo = result.sabrInfo,
                itVideoFormats = result.videoFormats,
                itAudioFormats = result.audioFormats,
                preferredVideoCodec = preferredCodecKey,
                preferredLiveQualityHeight = preferredQuality.height,
                isCurrent = { isPlaybackLoadCurrent(loadToken) },
            )
        }

        private suspend fun prepareLocalMediaForPlayback(
            videoId: String,
            localFilePath: String,
            offlineSegments: List<SponsorBlockSegment>?,
            savedPosition: Long,
            loadToken: Long,
        ) {
            playbackPreparer.prepareLocalMedia(
                videoId = videoId,
                localFilePath = localFilePath,
                offlineSegments = offlineSegments,
                savedPosition = savedPosition,
                subtitles = offlineSubtitlesFor(videoId),
                isCurrent = { isPlaybackLoadCurrent(loadToken) },
            )
        }

        private suspend fun offlineSubtitlesFor(videoId: String): List<SubtitlesStream> {
            val stored = offlineSubtitleStore.load(videoId)
            if (stored.isEmpty() && NetworkState.isOnline(context)) {
                viewModelScope.launch(networkDispatcher) {
                    offlineSubtitleStore.saveForVideo(videoId)
                }
            }
            return stored
        }

        private fun cachedDurationSeconds(): Long =
            _uiState.value.cachedVideo
                ?.duration
                ?.toLong() ?: 0L

        fun switchQuality(quality: VideoQuality) {
            val state = _uiState.value
            val streamInfo = state.streamInfo ?: return
            viewModelScope.launch {
                val streams =
                    MergedPlaybackAssembly.selectQualityStreams(
                        streamInfo = streamInfo,
                        innerTubeVideoFormats = state.innerTubeVideoFormats,
                        innerTubeAudioFormats = state.innerTubeAudioFormats,
                        quality = quality,
                        preferredAudioLanguage = playerPreferences.preferredAudioLanguage.first(),
                        preferredCodecKey = playerPreferences.videoCodecPriority.first(),
                    )

                _uiState.value =
                    state.copy(
                        videoStream = streams.first,
                        audioStream = streams.second,
                        selectedQuality = VideoQualityOptions.qualityOf(streams.first),
                        isAdaptiveMode = quality == VideoQuality.AUTO,
                    )
            }
        }

        private fun getPreviousVideoId(): String? =
            navigationHistory.previous()?.also {
                _canGoPrevious.value = navigationHistory.canGoPrevious
            }

        fun savePlaybackPosition(
            videoId: String,
            position: Long,
            duration: Long,
            title: String,
            thumbnailUrl: String,
            channelName: String = "",
            channelId: String = "",
            isShort: Boolean = false,
        ) = watchSessions.savePlaybackPosition(
            videoId = videoId,
            positionMs = position,
            durationMs = duration,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            channelId = channelId,
            isShort = isShort,
            isLocal = isLocalMediaId(videoId),
        )

        private fun relatedVideosFor(videoId: String): List<Video> =
            _uiState.value
                .takeIf { it.cachedVideo?.id == videoId || it.streamInfo?.id == videoId }
                ?.relatedVideos
                .orEmpty()

        fun toggleSubscription(
            channelId: String,
            channelName: String,
            channelThumbnail: String,
        ) {
            viewModelScope.launch {
                val isSubscribed = subscriptionRepository.isSubscribed(channelId).first()
                if (isSubscribed) {
                    subscriptionRepository.unsubscribe(channelId)
                    _uiState.value = _uiState.value.copy(isSubscribed = false)
                } else {
                    subscriptionRepository.subscribe(
                        ChannelSubscription(
                            channelId = channelId,
                            channelName = channelName,
                            channelThumbnail = channelThumbnail,
                        ),
                    )
                    _uiState.value = _uiState.value.copy(isSubscribed = true)
                }
                runCatching {
                    FlowNeuroEngine.onChannelSubscriptionChanged(
                        context,
                        channelId,
                        channelName,
                        subscribed = !isSubscribed,
                    )
                }.onFailure { Log.w("VideoPlayerViewModel", "Failed to record subscription signal", it) }
                if (!isSubscribed) {
                    // Newly subscribed: learn the channel's declared keyword tags.
                    runCatching { repository.learnChannelTags(context, channelId) }
                }
            }
        }

        fun setNotificationEnabled(
            channelId: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                subscriptionRepository.updateNotificationState(channelId, enabled)
                _uiState.value = _uiState.value.copy(isNotificationsEnabled = enabled)
            }
        }

        // Rich Video for the currently-open item, used to feed strong learning signals
        // (tags/description/duration) instead of a title-only stub.
        private fun resolveRichVideo(videoId: String): Video? {
            val state = _uiState.value
            return state.cachedVideo?.takeIf { it.id == videoId }
                ?: state.streamInfo?.takeIf { it.id == videoId }?.let { info ->
                    Video(
                        id = videoId,
                        title = info.name ?: "",
                        channelName = info.uploaderName ?: "",
                        channelId = info.uploaderUrl?.split("/")?.last() ?: "",
                        thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url ?: "",
                        duration = info.duration.toInt(),
                        viewCount = info.viewCount,
                        uploadDate = "",
                        description = info.description?.content ?: "",
                        tags = info.tags ?: emptyList(),
                    )
                }
        }

        fun likeVideo(
            videoId: String,
            title: String,
            thumbnail: String,
            channelName: String,
            channelId: String = "",
        ) {
            viewModelScope.launch {
                likedVideosRepository.likeVideo(
                    LikedVideoInfo(
                        videoId = videoId,
                        title = title,
                        thumbnail = thumbnail,
                        channelName = channelName,
                    ),
                )
                _uiState.value = _uiState.value.copy(likeState = "LIKED")
                try {
                    val video =
                        resolveRichVideo(videoId) ?: Video(
                            id = videoId,
                            title = title,
                            channelName = channelName,
                            channelId = channelId,
                            thumbnailUrl = thumbnail,
                            duration = 0,
                            viewCount = 0,
                            uploadDate = "",
                        )
                    FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.LIKED)
                } catch (e: Exception) {
                    Log.w("VideoPlayerViewModel", "Failed to record like signal", e)
                }
            }
        }

        fun dislikeVideo(videoId: String) {
            viewModelScope.launch {
                likedVideosRepository.dislikeVideo(videoId)
                _uiState.value = _uiState.value.copy(likeState = "DISLIKED")
                try {
                    val video = resolveRichVideo(videoId)
                    if (video != null) {
                        FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.DISLIKED)
                    }
                } catch (e: Exception) {
                    Log.w("VideoPlayerViewModel", "Failed to record dislike", e)
                }
            }
        }

        fun removeLikeState(videoId: String) {
            viewModelScope.launch {
                likedVideosRepository.removeLikeState(videoId)
                _uiState.value = _uiState.value.copy(likeState = null)
            }
        }

        fun loadSubscriptionAndLikeState(
            channelId: String,
            videoId: String,
        ) {
            if (subscriptionStateChannelId != channelId || subscriptionStateJob?.isActive != true) {
                subscriptionStateJob?.cancel()
                subscriptionStateChannelId = channelId
                subscriptionStateJob =
                    viewModelScope.launch {
                        launch {
                            subscriptionRepository.isSubscribed(channelId).collect { isSubscribed ->
                                _uiState.update { it.copy(isSubscribed = isSubscribed) }
                            }
                        }
                        launch {
                            subscriptionRepository.getSubscription(channelId).collect { subscription ->
                                _uiState.update {
                                    it.copy(isNotificationsEnabled = subscription?.isNotificationEnabled ?: false)
                                }
                            }
                        }
                    }
            }
            if (likeStateVideoId != videoId || likeStateJob?.isActive != true) {
                likeStateJob?.cancel()
                likeStateVideoId = videoId
                likeStateJob =
                    viewModelScope.launch {
                        likedVideosRepository.getLikeState(videoId).collect { likeState ->
                            _uiState.update { it.copy(likeState = likeState) }
                        }
                    }
            }
        }

        fun toggleSubtitles(enabled: Boolean) {
            _uiState.value = _uiState.value.copy(subtitlesEnabled = enabled)
        }

        fun toggleAutoplay(enabled: Boolean) {
            viewModelScope.launch {
                val resolvedEnabled =
                    enabled &&
                        !playerManager.playerState.value.isLooping
                playerPreferences.setAutoplayEnabled(resolvedEnabled)
                _uiState.value = _uiState.value.copy(autoplayEnabled = resolvedEnabled)
                _uiState.value.cachedVideo?.id?.let { videoId ->
                    playerManager.setAutoplayCandidates(
                        sourceVideoId = videoId,
                        videos = _uiState.value.relatedVideos,
                        enabled = resolvedEnabled,
                    )
                }
            }
        }

        fun toggleLoop(enabled: Boolean) {
            if (enabled) {
                viewModelScope.launch {
                    playerPreferences.setAutoplayEnabled(false)
                    _uiState.update { it.copy(autoplayEnabled = false) }
                }
            }
            playerManager.toggleLoop(enabled)
        }

        fun loadComments(videoId: String) {
            if (isLocalMediaId(videoId)) {
                _commentsState.value = emptyList()
                _isLoadingComments.value = false
                _hasMoreComments.value = false
                return
            }
            viewModelScope.launch {
                _isLoadingComments.value = true
                _commentsState.value = emptyList()
                commentsNextPage = null
                _hasMoreComments.value = false
                try {
                    withTimeoutOrNull(SECONDARY_CONTENT_STARTUP_TIMEOUT_MS) {
                        uiState.first { state ->
                            !PlaybackStartupPolicy.shouldDelaySecondaryContent(
                                isPlaybackLoading = state.isLoading,
                                currentVideoId = state.cachedVideo?.id ?: state.streamInfo?.id,
                                requestedVideoId = videoId,
                            )
                        }
                    }
                    if (_uiState.value.cachedVideo?.id != videoId) return@launch
                    val (comments, nextPage) = repository.getComments(videoId)
                    if (_uiState.value.cachedVideo?.id != videoId) return@launch
                    _commentsState.value = comments.distinctByNonBlankKey(Comment::id)
                    commentsNextPage = nextPage
                    _hasMoreComments.value = nextPage != null
                } catch (e: Exception) {
                    Log.e("VideoPlayerViewModel", "Error loading comments", e)
                } finally {
                    _isLoadingComments.value = false
                }
            }
        }

        fun loadMoreComments(videoId: String) {
            val nextPage = commentsNextPage ?: return
            if (_isLoadingMoreComments.value) return
            viewModelScope.launch {
                _isLoadingMoreComments.value = true
                try {
                    val (newComments, newNextPage) = repository.getMoreComments(videoId, nextPage)
                    _commentsState.value =
                        _commentsState.value.mergeDistinctByNonBlankKey(
                            newComments,
                            Comment::id,
                        )
                    commentsNextPage = newNextPage
                    _hasMoreComments.value = newNextPage != null
                } catch (e: Exception) {
                    Log.e("VideoPlayerViewModel", "Error loading more comments", e)
                } finally {
                    _isLoadingMoreComments.value = false
                }
            }
        }

        fun loadCommentReplies(comment: io.github.aedev.flow.data.model.Comment) {
            val videoId = _uiState.value.streamInfo?.id ?: return
            val repliesPage = comment.repliesPage ?: return

            viewModelScope.launch {
                try {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    val (replies, nextPage) = repository.getCommentReplies(url, repliesPage)

                    // Update the comment in the list
                    _commentsState.value =
                        _commentsState.value.map { c ->
                            if (c.id == comment.id) {
                                c.copy(
                                    replies = replies.distinctByNonBlankKey(Comment::id),
                                    repliesPage = nextPage,
                                )
                            } else {
                                c
                            }
                        }
                } catch (e: Exception) {
                    Log.e("VideoPlayerViewModel", "Error loading replies", e)
                }
            }
        }

        fun loadMoreCommentReplies(comment: io.github.aedev.flow.data.model.Comment) {
            val videoId = _uiState.value.streamInfo?.id ?: return
            val repliesPage = comment.repliesPage ?: return

            viewModelScope.launch {
                try {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    val (replies, nextPage) = repository.getCommentReplies(url, repliesPage)

                    _commentsState.value =
                        _commentsState.value.map { currentComment ->
                            if (currentComment.id == comment.id) {
                                currentComment.copy(
                                    replies =
                                        currentComment.replies.mergeDistinctByNonBlankKey(
                                            replies,
                                            Comment::id,
                                        ),
                                    repliesPage = nextPage,
                                )
                            } else {
                                currentComment
                            }
                        }
                } catch (e: Exception) {
                    Log.e("VideoPlayerViewModel", "Error loading more replies", e)
                }
            }
        }

        fun toggleSkipSilence(isEnabled: Boolean) {
            playerManager.toggleSkipSilence(isEnabled)
        }

        fun toggleStableVolume(isEnabled: Boolean) {
            playerManager.toggleStableVolume(isEnabled)
        }
    }
