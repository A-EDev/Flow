package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.engagement.VideoEngagementUseCase
import io.github.aedev.flow.data.local.*
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.di.IoDispatcher
import io.github.aedev.flow.di.NetworkIoDispatcher
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.MiniPlayerExpansionState
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.player.stream.PlaybackLoadResolver
import io.github.aedev.flow.player.stream.PlaybackResolutionRequest
import io.github.aedev.flow.player.stream.UpcomingPremiereProbe
import io.github.aedev.flow.ui.components.FeedInvalidationBus
import io.github.aedev.flow.ui.screens.player.state.*
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.*
import javax.inject.Inject

/**
 * Owns the player screen's state and every session entry point the UI calls: what plays, what the
 * player reports back, and what the surrounding controllers are armed with.
 *
 * The state has two writers by responsibility: this class writes what an entry point and the
 * player's own state changes land on, [PlaybackSessionApplier] writes what a resolved load lands on.
 * Both hold the one flow constructed here and gate on the same load token.
 */
@HiltViewModel
class VideoPlayerViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: YouTubeRepository,
        private val viewHistory: ViewHistory,
        private val engagement: VideoEngagementUseCase,
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

        private val collaborators =
            PlayerCollaborators(
                context = context,
                repository = repository,
                viewHistory = viewHistory,
                engagement = engagement,
                playerPreferences = playerPreferences,
                videoDownloadManager = videoDownloadManager,
                offlineSubtitleStore = offlineSubtitleStore,
                sponsorBlockRepository = sponsorBlockRepository,
                liveChatRepository = liveChatRepository,
                homeFeedCacheRepository = homeFeedCacheRepository,
                playerManager = playerManager,
                upcomingPremiereProbe = upcomingPremiereProbe,
                uiState = _uiState,
                scope = viewModelScope,
                networkDispatcher = networkDispatcher,
                ioDispatcher = ioDispatcher,
                isLoadCurrent = ::isPlaybackLoadCurrent,
                shortsEnabled = { shortsContentEnabled },
            )

        private val comments = collaborators.comments
        private val secondaryMetadata = collaborators.secondaryMetadata
        private val watchSessions = collaborators.watchSessions
        private val liveChat = collaborators.liveChat
        private val engagementState = collaborators.engagementState
        private val upcomingPremiere = collaborators.upcomingPremiere
        private val sessionApplier = collaborators.sessionApplier

        val commentsState: StateFlow<List<Comment>> = comments.comments
        val isLoadingComments: StateFlow<Boolean> = comments.isLoading
        val hasMoreComments: StateFlow<Boolean> = comments.hasMore
        val isLoadingMoreComments: StateFlow<Boolean> = comments.isLoadingMore

        private val navigationHistory = PlayerNavigationHistory()

        private var activeLoadJob: Job? = null
        private var playbackLoadToken: Long = 0L
        private var loadingVideoId: String? = null
        private var clearedUnplayableVideoId: String? = null

        private val recovery =
            PlaybackRecoveryController(
                context = context,
                uiState = _uiState,
                playerManager = playerManager,
                playerPreferences = playerPreferences,
                watchSessions = watchSessions,
                scope = viewModelScope,
                isLoadInFlight = { activeLoadJob?.isActive == true },
                cancelLoad = { cancelActivePlaybackLoad(invalidateToken = true) },
                reloadStreams = { videoId, resumePositionMs ->
                    loadVideoInfo(
                        videoId = videoId,
                        isWifi = detectIsWifi(),
                        forceRefresh = true,
                        escalateToSabr = true,
                        resumePositionOverrideMs = resumePositionMs,
                    )
                },
            )

        private val settings =
            PlaybackSettingsController(
                uiState = _uiState,
                playerManager = playerManager,
                playerPreferences = playerPreferences,
                scope = viewModelScope,
            )

        private val presence =
            PlaybackPresenceController(
                uiState = _uiState,
                playerManager = playerManager,
                playerPreferences = playerPreferences,
                viewHistory = viewHistory,
                scope = viewModelScope,
                ioDispatcher = ioDispatcher,
                resumePlayback = ::playVideo,
            )

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
            playerPreferences.shortsContentEnabled
                .onEach { shortsContentEnabled = it }
                .launchIn(viewModelScope)

            combine(liveChat.messages, liveChat.isLoading, liveChat.isAvailable, ::Triple)
                .onEach { (messages, isLoading, isAvailable) ->
                    _uiState.update { it.applyLiveChat(messages, isLoading, isAvailable) }
                }.launchIn(viewModelScope)

            recovery.collectPlayerEvents()

            playerManager.playerState
                .onEach(::onPlayerStateChanged)
                .launchIn(viewModelScope)

            presence.restoreLastWatchedSession()

            FeedInvalidationBus.events
                .onEach { event -> _uiState.update { it.applyFeedInvalidation(event) } }
                .launchIn(viewModelScope)

            settings.collectAutoplayPreference()
            upcomingPremiere.collectReminderState()
        }

        private suspend fun onPlayerStateChanged(playerState: EnhancedPlayerState) {
            _uiState.update { it.mirrorPlayerState(playerState) }

            // A video that prepares successfully is not unplayable, whatever a past failure said.
            playerState.currentVideoId
                ?.takeIf { playerState.isPrepared && it != clearedUnplayableVideoId }
                ?.let { preparedVideoId ->
                    clearedUnplayableVideoId = preparedVideoId
                    playerPreferences.clearVideoUnplayable(preparedVideoId)
                }

            val videoId = _uiState.value.foreignVideoIdNeedingLoad(playerState) ?: return
            GlobalPlayerState.currentVideo.value?.takeIf { it.id == videoId }?.let { currentVideo ->
                _uiState.update { it.resetForVideo(currentVideo) }
                presence.armNotificationFor(currentVideo)
                watchSessions.saveHistoryEntry(currentVideo)
            }
            loadVideoInfo(videoId, isWifi = detectIsWifi(), forceRefresh = true)
        }

        fun resumeRestoredSession(stayMini: Boolean = false) = presence.resumeRestoredSession(stayMini)

        fun dismissContinueWatching() = presence.dismissContinueWatching()

        fun ensureNotificationServiceRunning() = presence.ensureNotificationServiceRunning()

        fun clearResumedInMiniPlayer() = presence.clearResumedInMiniPlayer()

        fun toggleUpcomingReminder() = upcomingPremiere.toggleReminder()

        fun syncWithCurrentPlayerVideo(video: Video) {
            val state = _uiState.value
            val alreadySynced =
                state.cachedVideo?.id == video.id &&
                    (state.streamInfo?.id == video.id || state.isLoading || state.isLive || !state.hlsUrl.isNullOrEmpty())
            if (alreadySynced) return

            if (upcomingPremiere.applyCountdown(video)) {
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
            val isMiniPlayerCollapsed =
                GlobalPlayerState.miniPlayerExpansionState.value == MiniPlayerExpansionState.COLLAPSED
            if (_uiState.value.shouldReopenInsteadOfPlaying(video.id, playerManager.playerState.value, isMiniPlayerCollapsed)) {
                presence.showVideoPlayer()
                _expandPlayerRequest.tryEmit(Unit)
                return
            }

            nextPlaybackLoadToken()
            takeOverPlayback()

            _uiState.value = _uiState.value.startPlaybackOf(video)
            GlobalPlayerState.setCurrentVideo(video)
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
            watchSessions.saveHistoryEntry(video)
            presence.armNotificationFor(video)
            if (upcomingPremiere.applyCountdown(video)) {
                return
            }
            loadVideoInfo(video.id, isWifi = detectIsWifi(), forceRefresh = true)
        }

        fun playLocalVideo(
            video: Video,
            contentUri: String,
        ) {
            val loadToken = nextPlaybackLoadToken()
            takeOverPlayback()

            _uiState.value = _uiState.value.startLocalPlaybackOf(video, contentUri)
            GlobalPlayerState.setCurrentVideo(video)
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
            presence.armNotificationFor(video)

            viewModelScope.launch {
                sessionApplier.prepareLocalMedia(
                    load = LoadContext(video.id, loadToken),
                    localFilePath = contentUri,
                    offlineSegments = null,
                    savedPosition = runCatching { viewHistory.getSavedPosition(video.id) }.getOrDefault(0L),
                )
            }
        }

        /** Drops the load, the queue and the music player so this screen owns playback outright. */
        private fun takeOverPlayback() {
            cancelActivePlaybackLoad()
            recovery.onPlaybackRequested()
            playerManager.pause()
            playerManager.clearAll()
            EnhancedMusicPlayerManager.stop()
            EnhancedMusicPlayerManager.clearCurrentTrack()
        }

        fun clearVideo() {
            nextPlaybackLoadToken()
            cancelActivePlaybackLoad()
            recovery.onPlaybackRequested()
            playerManager.stop()
            playerManager.stopBackgroundService()
            playerManager.clearAll()
            GlobalPlayerState.setCurrentVideo(null)
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
            GlobalPlayerState.hideMiniPlayer()

            _uiState.update { it.clearedForNoVideo() }

            navigationHistory.clear()
            _canGoPrevious.value = false

            comments.clear()
        }

        fun startBackgroundPlayback() = presence.startBackgroundPlayback()

        fun resetDismissState() = presence.resetDismissState()

        fun showVideoPlayer() = presence.showVideoPlayer()

        fun retryLoadVideo() {
            val videoId = _uiState.value.cachedVideo?.id ?: return
            Log.d("VideoPlayerViewModel", "Retrying video load for $videoId")
            if (upcomingPremiere.applyCountdown(_uiState.value.cachedVideo ?: return)) {
                return
            }
            recovery.onPlaybackRequested()
            playerManager.clearCurrentVideo()
            _uiState.update { it.copy(error = null, errorHint = null, isLoading = true) }
            loadVideoInfo(videoId, isWifi = detectIsWifi(), forceRefresh = true)
        }

        fun ensurePlaybackPrepared(videoId: String) {
            val state = _uiState.value
            if (state.blocksLatePrepare() || !state.holdsVideo(videoId)) return
            if (playerManager.isPreparedForPlayback(videoId)) return

            viewModelScope.launch {
                val latest = _uiState.value
                if (latest.blocksLatePrepare()) return@launch
                if (playerManager.isPreparedForPlayback(videoId)) return@launch
                sessionApplier.armLatePrepare(LoadContext(videoId, playbackLoadToken), latest)
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
            presence.armNotificationFor(startVideo)
            if (upcomingPremiere.applyCountdown(startVideo, preserveQueueTitle = title)) {
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
                    val prevVideo = blankVideo(prevId, cached = null)
                    playVideo(prevVideo)
                    GlobalPlayerState.setCurrentVideo(prevVideo)
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
            recovery.onLoadStarted(videoId)

            if (upcomingPremiere.applyCachedCountdown(videoId)) return

            currentState.loadSkipReason(videoId, forceRefresh)?.let { skip ->
                Log.d("VideoPlayerViewModel", "Video $videoId skipped: $skip")
                return
            }

            navigationHistory.push(videoId)
            _canGoPrevious.value = navigationHistory.canGoPrevious

            _uiState.value = _uiState.value.beginLoadFor(videoId)
            stopLiveChat()

            if (activeLoadJob?.isActive == true && loadingVideoId == videoId) {
                Log.d("VideoPlayerViewModel", "loadVideoInfo: extraction already in flight for $videoId — ignoring redundant trigger")
                return
            }

            cancelActivePlaybackLoad()
            val loadToken = nextPlaybackLoadToken()
            loadingVideoId = videoId

            val load = LoadContext(videoId, loadToken)
            activeLoadJob =
                viewModelScope.launch(networkDispatcher) {
                    Log.d("VideoPlayerViewModel", "Starting loadVideoInfo for $videoId")
                    sessionApplier.startDislikeLoad(load)
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
                            resolveUpcoming = upcomingPremiere::resolve,
                            onStep = { step -> sessionApplier.apply(step, load) },
                        )
                    } finally {
                        if (isPlaybackLoadCurrent(loadToken)) {
                            activeLoadJob = null
                        }
                    }
                }
        }

        fun switchQuality(quality: VideoQuality) = settings.switchQuality(quality)

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

        fun toggleSubscription(
            channelId: String,
            channelName: String,
            channelThumbnail: String,
        ) = engagementState.toggleSubscription(channelId, channelName, channelThumbnail)

        fun setNotificationEnabled(
            channelId: String,
            enabled: Boolean,
        ) = engagementState.setNotificationEnabled(channelId, enabled)

        fun likeVideo(
            videoId: String,
            title: String,
            thumbnail: String,
            channelName: String,
            channelId: String = "",
        ) = engagementState.like(videoId, title, thumbnail, channelName, channelId)

        fun dislikeVideo(videoId: String) = engagementState.dislike(videoId)

        fun removeLikeState(videoId: String) = engagementState.removeLike(videoId)

        fun loadSubscriptionAndLikeState(
            channelId: String,
            videoId: String,
        ) = engagementState.observe(channelId, videoId)

        fun toggleSubtitles(enabled: Boolean) = settings.setSubtitlesEnabled(enabled)

        fun toggleAutoplay(enabled: Boolean) = settings.toggleAutoplay(enabled)

        fun toggleLoop(enabled: Boolean) = settings.toggleLoop(enabled)

        fun loadComments(videoId: String) {
            if (isLocalMediaId(videoId)) {
                comments.clear()
                return
            }
            comments.load(videoId)
        }

        fun loadMoreComments(videoId: String) = comments.loadMore(videoId)

        fun loadCommentReplies(comment: Comment) {
            val videoId = _uiState.value.streamInfo?.id ?: return
            comments.loadReplies(videoId, comment)
        }

        fun loadMoreCommentReplies(comment: Comment) {
            val videoId = _uiState.value.streamInfo?.id ?: return
            comments.loadMoreReplies(videoId, comment)
        }

        fun toggleSkipSilence(isEnabled: Boolean) = settings.toggleSkipSilence(isEnabled)

        fun toggleStableVolume(isEnabled: Boolean) = settings.toggleStableVolume(isEnabled)
    }
