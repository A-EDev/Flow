package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.comments.CommentsPager
import io.github.aedev.flow.data.comments.CommentsPlaybackState
import io.github.aedev.flow.data.engagement.VideoEngagementUseCase
import io.github.aedev.flow.data.local.*
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.di.IoDispatcher
import io.github.aedev.flow.di.NetworkIoDispatcher
import io.github.aedev.flow.notification.UpcomingVideoReminderWorker
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.MiniPlayerExpansionState
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.player.stream.MergedPlaybackAssembly
import io.github.aedev.flow.player.stream.PlaybackLoadResolver
import io.github.aedev.flow.player.stream.PlaybackResolutionRequest
import io.github.aedev.flow.player.stream.UpcomingPremiere
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
import kotlinx.coroutines.withContext
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

        private val comments =
            CommentsPager(
                repository = repository,
                scope = viewModelScope,
                playbackState =
                    uiState.map {
                        CommentsPlaybackState(
                            isPlaybackLoading = it.isLoading,
                            currentVideoId = it.cachedVideo?.id ?: it.streamInfo?.id,
                        )
                    },
                isCurrentVideo = { videoId -> _uiState.value.cachedVideo?.id == videoId },
            )

        val commentsState: StateFlow<List<Comment>> = comments.comments
        val isLoadingComments: StateFlow<Boolean> = comments.isLoading
        val hasMoreComments: StateFlow<Boolean> = comments.hasMore
        val isLoadingMoreComments: StateFlow<Boolean> = comments.isLoadingMore

        private val navigationHistory = PlayerNavigationHistory()

        private val playbackPreparer =
            PlaybackPreparer(
                context = context,
                playerManager = playerManager,
                playerPreferences = playerPreferences,
                offlineSubtitleStore = offlineSubtitleStore,
            )

        private val streamPreparer = PlaybackStreamPreparer()

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
                onResult = { result -> sessionApplier.applySecondary(result) },
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
                richVideoFor = { videoId -> _uiState.value.richVideoFor(videoId) },
            )

        private val liveChat =
            LiveChatController(
                repository = liveChatRepository,
                scope = viewModelScope,
                dispatcher = networkDispatcher,
            )

        private val engagementState =
            PlayerEngagementController(
                engagement = engagement,
                scope = viewModelScope,
                state = _uiState,
                richVideoFor = { videoId -> _uiState.value.richVideoFor(videoId) },
            )

        private val sessionApplier: PlaybackSessionApplier =
            PlaybackSessionApplier(
                context = context,
                uiState = _uiState,
                isLoadCurrent = ::isPlaybackLoadCurrent,
                playbackPreparer = playbackPreparer,
                streamPreparer = streamPreparer,
                secondaryMetadata = secondaryMetadata,
                liveChat = liveChat,
                repository = repository,
                viewHistory = viewHistory,
                playerPreferences = playerPreferences,
                sponsorBlockRepository = sponsorBlockRepository,
                videoDownloadManager = videoDownloadManager,
                offlineSubtitleStore = offlineSubtitleStore,
                playerManager = playerManager,
                scope = viewModelScope,
                networkDispatcher = networkDispatcher,
                ioDispatcher = ioDispatcher,
                enterUpcoming = ::enterUpcomingState,
                tryEnterUpcoming = ::tryEnterUpcomingState,
            )

        private var activeLoadJob: Job? = null
        private var playbackLoadToken: Long = 0L
        private var loadingVideoId: String? = null
        private var clearedUnplayableVideoId: String? = null
        private val streamExpiryRecovery = StreamExpiryRecoveryController()

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

            // Re-fetch streams whenever an expired URL is detected (HTTP 403/410 "data changed")
            streamExpiryRecovery.collectExpiryEvents(
                scope = viewModelScope,
                events = playerManager.streamExpiredEvent,
                videoIdInPlayback = { _uiState.value.cachedVideo?.id },
                isLoadInFlight = { activeLoadJob?.isActive == true },
                onReload = ::reloadExpiredStreams,
                onGiveUp = ::abandonExhaustedPlayback,
            )

            playerManager.playbackAbandonedEvent
                .onEach {
                    _uiState.value.cachedVideo
                        ?.id
                        ?.let { videoId -> abandonReportedPlayback(videoId) }
                }.launchIn(viewModelScope)

            playerManager.playerState
                .onEach(::onPlayerStateChanged)
                .launchIn(viewModelScope)

            // Restore last watched video session so the mini player appears on launch
            viewModelScope.launch { restoreLastWatchedSession() }

            FeedInvalidationBus.events
                .onEach { event -> _uiState.update { it.applyFeedInvalidation(event) } }
                .launchIn(viewModelScope)

            playerPreferences.autoplayEnabled
                .distinctUntilChanged()
                .onEach(::applyAutoplayPreference)
                .launchIn(viewModelScope)

            combine(
                playerPreferences.upcomingVideoReminderIds,
                uiState.map { it.cachedVideo?.id }.distinctUntilChanged(),
            ) { reminderIds, videoId ->
                videoId != null && videoId in reminderIds
            }.onEach { isReminderSet ->
                _uiState.update { it.copy(isUpcomingReminderSet = isReminderSet) }
            }.launchIn(viewModelScope)
        }

        private suspend fun reloadExpiredStreams(
            videoId: String,
            reload: StreamExpiryRecoveryController.Decision.Reload,
        ) {
            var recoveryPositionMs = 0L
            playerManager.getPlayer()?.let { player ->
                val positionMs = player.currentPosition
                recoveryPositionMs = positionMs.coerceAtLeast(0L)
                val durationMs =
                    player.duration.takeIf { it > 0L }
                        ?: ((_uiState.value.cachedVideo?.duration ?: 0) * 1000L)
                if (positionMs > 0L && durationMs > 0L) {
                    watchSessions.saveResumePosition(
                        videoId = videoId,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        video = _uiState.value.cachedVideo,
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

        /** The expiry budget is spent: stop the player before the screen turns terminal. */
        private suspend fun abandonExhaustedPlayback(videoId: String) {
            playerPreferences.markVideoUnplayable(videoId)
            cancelActivePlaybackLoad(invalidateToken = true)
            playerManager.getPlayer()?.let { player ->
                player.stop()
                player.clearMediaItems()
            }
            surfaceTerminalStreamFailure()
        }

        /** The player itself gave up, so it needs no stopping — only the latch and the screen. */
        private suspend fun abandonReportedPlayback(videoId: String) {
            streamExpiryRecovery.onPlaybackAbandoned(videoId)
            playerPreferences.markVideoUnplayable(videoId)
            cancelActivePlaybackLoad(invalidateToken = true)
            Log.w("VideoPlayerViewModel", "Playback abandoned for $videoId — surfacing terminal error")
            surfaceTerminalStreamFailure()
        }

        private fun surfaceTerminalStreamFailure() {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = context.getString(R.string.error_all_stream_sources_failed),
                    errorHint = context.getString(R.string.error_playback_retry_hint),
                )
            }
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
                armNotificationFor(currentVideo)
                watchSessions.saveHistoryEntry(currentVideo)
            }
            loadVideoInfo(videoId, isWifi = detectIsWifi(), forceRefresh = true)
        }

        /** Brings the last unfinished video back as a mini player, unless music already owns it. */
        private suspend fun restoreLastWatchedSession() {
            if (!playerPreferences.miniPlayerContinueWatchingEnabled.first()) return
            if (EnhancedMusicPlayerManager.currentTrack.value != null) return
            val lastVideo = withContext(ioDispatcher) { viewHistory.getLatestUnfinishedVideo() } ?: return
            if (_uiState.value.cachedVideo != null) return
            _uiState.update { it.copy(cachedVideo = lastVideo.toVideo(), isRestoredSession = true) }
        }

        private suspend fun applyAutoplayPreference(autoplay: Boolean) {
            _uiState.update { it.copy(autoplayEnabled = autoplay) }
            _uiState.value.cachedVideo?.id?.let { videoId ->
                playerManager.setAutoplayCandidates(
                    sourceVideoId = videoId,
                    videos = _uiState.value.relatedVideos,
                    enabled = autoplay,
                )
            }
        }

        /** The media notification every playback start arms, with the one title fallback it uses. */
        private fun armNotificationFor(video: Video) =
            playerManager.startBackgroundService(
                videoId = video.id,
                title = video.title.ifEmpty { "Flow Player" },
                channel = video.channelName,
                thumbnail = video.thumbnailUrl,
            )

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
            armNotificationFor(video)
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
            releaseMs: Long?,
            relatedVideos: List<Video>,
            loadToken: Long,
        ): Boolean {
            if (!isPlaybackLoadCurrent(loadToken)) return true
            val cached = _uiState.value.cachedVideo?.takeIf { it.id == videoId }
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
        ): Boolean {
            val (isUpcoming, releaseMs) = resolveUpcoming(videoId, knownUpcoming = false)
            if (!isUpcoming) return false
            return enterUpcomingState(videoId, releaseMs, relatedVideos, loadToken)
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
            val isMiniPlayerCollapsed =
                GlobalPlayerState.miniPlayerExpansionState.value == MiniPlayerExpansionState.COLLAPSED
            if (_uiState.value.shouldReopenInsteadOfPlaying(video.id, playerManager.playerState.value, isMiniPlayerCollapsed)) {
                showVideoPlayer()
                _expandPlayerRequest.tryEmit(Unit)
                return
            }

            nextPlaybackLoadToken()
            takeOverPlayback()

            _uiState.value = _uiState.value.startPlaybackOf(video)
            GlobalPlayerState.setCurrentVideo(video)
            GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
            watchSessions.saveHistoryEntry(video)
            armNotificationFor(video)
            if (applyUpcomingState(video)) {
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
            armNotificationFor(video)

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
            streamExpiryRecovery.onPlaybackRequested()
            playerManager.pause()
            playerManager.clearAll()
            EnhancedMusicPlayerManager.stop()
            EnhancedMusicPlayerManager.clearCurrentTrack()
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

            _uiState.update { it.clearedForNoVideo() }

            navigationHistory.clear()
            _canGoPrevious.value = false

            comments.clear()
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
            if (state.blocksLatePrepare() || !state.holdsVideo(videoId)) return
            if (playerManager.isPreparedForPlayback(videoId)) return

            viewModelScope.launch {
                val latest = _uiState.value
                if (latest.blocksLatePrepare()) return@launch
                if (playerManager.isPreparedForPlayback(videoId)) return@launch
                armLatePrepare(videoId, latest, playbackLoadToken)
            }
        }

        /** Re-pushes what the screen already holds when the player turns out to own no media item. */
        private suspend fun armLatePrepare(
            videoId: String,
            latest: VideoPlayerUiState,
            loadToken: Long,
        ) {
            when (val prepare = latest.latePrepare(videoId)) {
                null -> {
                    if (latest.streamInfo != null) {
                        Log.w("VideoPlayerViewModel", "Late prepare skipped for $videoId: no playable streams in UI state")
                    }
                }

                is LatePrepare.LocalFile -> {
                    Log.w("VideoPlayerViewModel", "Late prepare: arming local playback for $videoId")
                    sessionApplier.prepareLocalMedia(
                        load = LoadContext(videoId, loadToken),
                        localFilePath = prepare.localFilePath,
                        offlineSegments = prepare.offlineSegments,
                        savedPosition = prepare.savedPosition ?: viewHistory.getPlaybackPosition(videoId).first(),
                    )
                }

                is LatePrepare.Streams -> {
                    Log.w(
                        "VideoPlayerViewModel",
                        "Late prepare: arming stream playback for $videoId " +
                            "(audio=${prepare.audioStream != null}, videos=${prepare.videoStreams.size})",
                    )
                    playbackPreparer.prepareMergedStreams(
                        videoId = videoId,
                        streamInfo = prepare.streamInfo,
                        videoStream = prepare.videoStream,
                        audioStream = prepare.audioStream,
                        videoStreams = prepare.videoStreams,
                        audioStreams = prepare.streamInfo.audioStreams,
                        subtitles = prepare.streamInfo.subtitles ?: emptyList(),
                        savedPosition = prepare.savedPosition ?: viewHistory.getPlaybackPosition(videoId).first(),
                        fallbackDurationSeconds = prepare.fallbackDurationSeconds,
                        localFilePath = prepare.localFilePath,
                        offlineSegments = prepare.offlineSegments,
                        hlsUrl = prepare.hlsUrl,
                        isAdaptiveMode = prepare.isAdaptiveMode,
                        resumeOverrideRequested = false,
                        isCurrent = { isPlaybackLoadCurrent(loadToken) },
                        preferredVideoCodec = playerPreferences.videoCodecPriority.first(),
                    )
                }
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
            armNotificationFor(startVideo)
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
            streamExpiryRecovery.onLoadStarted(videoId)

            val knownReleaseTimeMs =
                currentState.cachedVideo
                    ?.takeIf { it.id == videoId && it.isUpcoming }
                    ?.let(UpcomingPremierePolicy::releaseTimeFor)
            if (knownReleaseTimeMs != null) {
                _uiState.update { it.applyCachedUpcoming(knownReleaseTimeMs) }
                return
            }

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
                            resolveUpcoming = ::resolveUpcoming,
                            onStep = { step -> sessionApplier.apply(step, load) },
                        )
                    } finally {
                        if (isPlaybackLoadCurrent(loadToken)) {
                            activeLoadJob = null
                        }
                    }
                }
        }

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

                _uiState.value = state.applySelectedQuality(quality, streams.first, streams.second)
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

        fun toggleSkipSilence(isEnabled: Boolean) {
            playerManager.toggleSkipSilence(isEnabled)
        }

        fun toggleStableVolume(isEnabled: Boolean) {
            playerManager.toggleStableVolume(isEnabled)
        }
    }
