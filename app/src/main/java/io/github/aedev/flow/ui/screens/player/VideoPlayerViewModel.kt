package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.local.*
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
import io.github.aedev.flow.data.recommendation.InterestProfile
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.ui.components.FeedInvalidationBus
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.notification.UpcomingVideoReminderWorker
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.github.aedev.flow.utils.parsePremiereTimestamp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.*
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import io.github.aedev.flow.ui.screens.player.util.VideoErrorMapper
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext


import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

import kotlinx.coroutines.flow.update

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YouTubeRepository,
    private val viewHistory: ViewHistory,
    private val subscriptionRepository: SubscriptionRepository,
    private val likedVideosRepository: LikedVideosRepository,
    private val playlistRepository: io.github.aedev.flow.data.local.PlaylistRepository,
    private val interestProfile: InterestProfile,
    private val playerPreferences: PlayerPreferences,
    private val videoDownloadManager: VideoDownloadManager,
    private val sponsorBlockRepository: SponsorBlockRepository
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
    
    private val navigationHistory = mutableListOf<String>()
    private var currentHistoryIndex = -1

    // Dedup guard for reportWatchProgress — prevents double-reporting when the
    // DisposableEffect fires multiple times for the same video.
    private var lastReportedVideoId: String? = null
    private var lastReportedTimestamp: Long = 0L
    private var activeLoadJob: Job? = null
    private var playbackLoadToken: Long = 0L
    private var loadingVideoId: String? = null

    private var streamExpiryVideoId: String? = null
    private var streamExpiryCount: Int = 0
    private companion object {
        const val MAX_STREAM_EXPIRY_RETRIES = 3
    }

    private val _canGoPrevious = MutableStateFlow(false)
    val canGoPrevious: StateFlow<Boolean> = _canGoPrevious.asStateFlow()

    private fun nextPlaybackLoadToken(): Long {
        playbackLoadToken += 1L
        return playbackLoadToken
    }

    private fun isPlaybackLoadCurrent(token: Long): Boolean = playbackLoadToken == token

    private fun cancelActivePlaybackLoad() {
        activeLoadJob?.cancel()
        activeLoadJob = null
    }

    val downloadedVideoIds = videoDownloadManager.downloadedVideos
        .map { list -> list.map { it.video.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun isVideoSavedToAnyPlaylist(videoId: String): Flow<Boolean> =
        playlistRepository.isVideoSavedToAnyPlaylistFlow(videoId)

    fun initialize(context: Context) {
        // Handled by Hilt
    }
    
    /**
     * Detect whether the device is currently on Wi-Fi.
     * Used to select the correct quality preference (Wi-Fi vs cellular).
     */
    private fun detectIsWifi(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            true 
        }
    }
    
    init {
        // Re-fetch streams whenever an expired URL is detected (HTTP 403/410 "data changed")
        viewModelScope.launch {
            EnhancedPlayerManager.getInstance().streamExpiredEvent.collect {
                val videoId = _uiState.value.cachedVideo?.id ?: return@collect
                if (activeLoadJob?.isActive == true) {
                    Log.d("VideoPlayerViewModel", "Stream expiry for $videoId coalesced — a stream load is already in flight")
                    return@collect
                }

                if (streamExpiryVideoId != videoId) {
                    streamExpiryVideoId = videoId
                    streamExpiryCount = 0
                }
                streamExpiryCount++

                if (streamExpiryCount > MAX_STREAM_EXPIRY_RETRIES) {
                    Log.e("VideoPlayerViewModel", "Stream expiry retry limit ($MAX_STREAM_EXPIRY_RETRIES) reached for $videoId — giving up")
                    EnhancedPlayerManager.getInstance().getPlayer()?.stop()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Unable to play this video. All stream sources returned errors.",
                            errorHint = "Try again later or check your network connection."
                        )
                    }
                    return@collect
                }

                Log.w("VideoPlayerViewModel", "Stream expired — re-fetching streams for $videoId (attempt $streamExpiryCount/$MAX_STREAM_EXPIRY_RETRIES)")

                if (streamExpiryCount >= 2) {
                    try {
                        EnhancedPlayerManager.getInstance().clearCacheForCurrentVideo()
                    } catch (e: Exception) {
                        Log.w("VideoPlayerViewModel", "Cache eviction failed: ${e.message}")
                    }
                }

                _uiState.update { it.copy(error = null, errorHint = null, isLoading = true) }
                loadVideoInfo(videoId, isWifi = detectIsWifi(), forceRefresh = true, escalateToSabr = true)
            }
        }

        viewModelScope.launch {
            EnhancedPlayerManager.getInstance().playerState.collect { playerState ->
                _uiState.update {
                    it.copy(
                        hasNext = playerState.hasNext,
                        hasPrevious = playerState.hasPrevious,
                        queueTitle = playerState.queueTitle
                    )
                }

                // Handle external video id changes (e.g. from queue auto-advance)
                playerState.currentVideoId?.let { videoId ->
                    val hasActiveStreams = playerState.isPrepared || playerState.isBuffering
                    val isSameVideoNeedsReload = !hasActiveStreams &&
                        _uiState.value.streamInfo == null &&
                        _uiState.value.cachedVideo?.id == videoId
                    if ((videoId != _uiState.value.streamInfo?.id &&
                        videoId != _uiState.value.cachedVideo?.id ||
                        isSameVideoNeedsReload) &&
                        !_uiState.value.isLoading &&
                        (!_uiState.value.isRestoredSession || !hasActiveStreams)) {
                        GlobalPlayerState.currentVideo.value?.takeIf { it.id == videoId }?.let { currentVideo ->
                            _uiState.update {
                                it.copy(
                                    cachedVideo = currentVideo,
                                    isLoading = true,
                                    error = null,
                                    errorHint = null,
                                    metadataError = null,
                                    streamInfo = null,
                                    videoStream = null,
                                    audioStream = null,
                                    savedPosition = null,
                                    relatedVideos = emptyList(),
                                    isSubscribed = false,
                                    likeState = null,
                                    hlsUrl = null,
                                    localFilePath = null,
                                    localFileVideoId = null
                                )
                            }
                            EnhancedPlayerManager.getInstance().startBackgroundService(
                                videoId = currentVideo.id,
                                title = currentVideo.title.ifEmpty { "Flow Player" },
                                channel = currentVideo.channelName,
                                thumbnail = currentVideo.thumbnailUrl
                            )
                            saveHistoryEntry(currentVideo)
                        }
                         loadVideoInfo(videoId, isWifi = detectIsWifi())
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
                val lastVideo = withContext(Dispatchers.IO) { viewHistory.getLatestUnfinishedVideo() }
                if (lastVideo != null && _uiState.value.cachedVideo == null) {
                    _uiState.update { it.copy(
                        cachedVideo = lastVideo.toVideo(),
                        isRestoredSession = true
                    ) }
                }
            }
        }

        viewModelScope.launch {
            FeedInvalidationBus.events.collect { event ->
                when (event) {
                    is FeedInvalidationBus.Event.NotInterested -> {
                        _uiState.update { state ->
                            state.copy(
                                relatedVideos = state.relatedVideos.filter { it.id != event.videoId }
                            )
                        }
                    }
                    is FeedInvalidationBus.Event.ChannelBlocked -> {
                        _uiState.update { state ->
                            state.copy(
                                relatedVideos = state.relatedVideos.filter { it.channelId != event.channelId }
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            combine(
                playerPreferences.upcomingVideoReminderIds,
                uiState.map { it.cachedVideo?.id }.distinctUntilChanged()
            ) { reminderIds, videoId ->
                videoId != null && videoId in reminderIds
            }.collect { isReminderSet ->
                _uiState.update { it.copy(isUpcomingReminderSet = isReminderSet) }
            }
        }
    }
    
    fun initializeViewHistory(context: Context) {
        // Handled by Hilt
    }
    
    /**
     * Called when the user interacts with the restored-session mini player (taps play
     * or expands the sheet). Starts loading streams and transitions to active playback.
     * @param stayMini if true, the player will keep playing in mini mode (don't auto-expand)
     */
    fun resumeRestoredSession(stayMini: Boolean = false) {
        val video = _uiState.value.cachedVideo ?: return
        if (!_uiState.value.isRestoredSession) return
        _uiState.update { it.copy(isRestoredSession = false, resumedInMiniPlayer = stayMini) }
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
        EnhancedPlayerManager.getInstance().startBackgroundService(
            videoId   = video.id,
            title     = video.title.ifEmpty { "Flow Player" },
            channel   = video.channelName,
            thumbnail = video.thumbnailUrl
        )
    }

    fun clearResumedInMiniPlayer() {
        _uiState.update { it.copy(resumedInMiniPlayer = false) }
    }

    private fun resolveUpcomingReleaseTime(video: Video): Long? {
        if (!video.isUpcoming) return null
        val now = System.currentTimeMillis()
        return when {
            video.timestamp > now + 60_000L -> video.timestamp
            else -> parsePremiereTimestamp(video.uploadDate)
        }?.takeIf { it > now }
    }

    private fun applyUpcomingState(video: Video, preserveQueueTitle: String? = _uiState.value.queueTitle): Boolean {
        if (!video.isUpcoming) return false
        _uiState.update {
            it.copy(
                cachedVideo = video,
                isRestoredSession = false,
                resumedInMiniPlayer = it.resumedInMiniPlayer,
                isLoading = false,
                error = null,
                errorHint = null,
                metadataError = null,
                streamInfo = null,
                videoStream = null,
                audioStream = null,
                savedPosition = null,
                relatedVideos = emptyList(),
                isSubscribed = false,
                likeState = null,
                hlsUrl = null,
                localFilePath = null,
                localFileVideoId = null,
                queueTitle = preserveQueueTitle,
                isUpcoming = true,
                upcomingReleaseTimeMs = resolveUpcomingReleaseTime(video)
            )
        }
        return true
    }

    fun toggleUpcomingReminder() {
        val state = _uiState.value
        val video = state.cachedVideo ?: return
        val releaseTimeMs = state.upcomingReleaseTimeMs ?: resolveUpcomingReleaseTime(video) ?: return
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
                    thumbnailUrl = video.thumbnailUrl
                )
            } else {
                UpcomingVideoReminderWorker.cancelReminder(context, video.id)
            }
            _uiState.update { it.copy(isUpcomingReminderSet = enableReminder) }
        }
    }

    fun syncWithCurrentPlayerVideo(video: Video) {
        val state = _uiState.value
        val alreadySynced = state.cachedVideo?.id == video.id &&
            (state.streamInfo?.id == video.id || state.isLoading)
        if (alreadySynced) return

        if (applyUpcomingState(video)) {
            return
        }

        _uiState.update {
            it.copy(
                cachedVideo = video,
                isRestoredSession = false,
                isLoading = true,
                error = null,
                errorHint = null,
                metadataError = null,
                streamInfo = null,
                videoStream = null,
                audioStream = null,
                savedPosition = null,
                relatedVideos = emptyList(),
                isSubscribed = false,
                likeState = null,
                hlsUrl = null,
                localFilePath = null,
                localFileVideoId = null,
                isUpcoming = false,
                upcomingReleaseTimeMs = null
            )
        }
        loadVideoInfo(video.id, isWifi = detectIsWifi(), forceRefresh = true)
    }

    /**
     * Plays a video by immediately caching metadata and triggering stream load.
     * This ensures the UI shows video info immediately while streams are fetched.
     */
    fun playVideo(video: Video) {
        nextPlaybackLoadToken()
        cancelActivePlaybackLoad()

        streamExpiryVideoId = null
        streamExpiryCount = 0

        // Stop current playback and clear everything (including any active queue)
        EnhancedPlayerManager.getInstance().pause()
        EnhancedPlayerManager.getInstance().clearAll()

        // Ensure music player is stopped and hidden
        EnhancedMusicPlayerManager.stop()
        EnhancedMusicPlayerManager.clearCurrentTrack()

        // Cache video metadata for immediate UI display
        _uiState.value = _uiState.value.copy(
            cachedVideo = video,
            isRestoredSession = false,
            resumedInMiniPlayer = _uiState.value.resumedInMiniPlayer,
            isLoading = true,
            error = null,
            errorHint = null,
            metadataError = null,
            streamInfo = null,
            videoStream = null,
            audioStream = null,
            savedPosition = null,
            relatedVideos = emptyList(),
            isSubscribed = false,
            likeState = null,
            isUpcoming = false,
            upcomingReleaseTimeMs = null
        )
        GlobalPlayerState.setCurrentVideo(video)
        saveHistoryEntry(video)
        EnhancedPlayerManager.getInstance().startBackgroundService(
            videoId   = video.id,
            title     = video.title.ifEmpty { "Flow Player" },
            channel   = video.channelName,
            thumbnail = video.thumbnailUrl
        )
        if (applyUpcomingState(video)) {
            return
        }
        // Start loading streams
        loadVideoInfo(video.id, isWifi = detectIsWifi(), forceRefresh = true)
    }

    /**
     * Clears all video player state, stops playback and resets UI.
     * This should be called when the video player is dismissed.
     */
    fun clearVideo() {
        nextPlaybackLoadToken()
        cancelActivePlaybackLoad()
        EnhancedPlayerManager.getInstance().stop()
        EnhancedPlayerManager.getInstance().stopBackgroundService()
        EnhancedPlayerManager.getInstance().clearAll()
        GlobalPlayerState.setCurrentVideo(null)
        GlobalPlayerState.hideMiniPlayer()
        
        _uiState.update { 
            VideoPlayerUiState(
                autoplayEnabled = it.autoplayEnabled,
                isAdaptiveMode = it.isAdaptiveMode
            ) 
        }
        
        // Reset history
        navigationHistory.clear()
        currentHistoryIndex = -1
        _canGoPrevious.value = false
        
        // Clear related content
        _commentsState.value = emptyList()
        _isLoadingComments.value = false
        commentsNextPage = null
        _hasMoreComments.value = false
        _isLoadingMoreComments.value = false
    }

    /**
     * Puts the player into background mode (audio-only) and signals the UI to dismiss.
     * If the current video's audio stream is available, hands off to the music player
     * so the mini music player remains visible and playback continues seamlessly from
     * the current position.
     */
    fun startBackgroundService() {
        val state = _uiState.value
        val audioUrl = state.audioStream?.content
        val videoId = state.cachedVideo?.id ?: state.streamInfo?.id
        if (videoId != null && audioUrl != null) {
            val musicTrack = io.github.aedev.flow.ui.screens.music.MusicTrack(
                videoId = videoId,
                title = state.streamInfo?.name ?: state.cachedVideo?.title ?: "",
                artist = state.streamInfo?.uploaderName ?: state.cachedVideo?.channelName ?: "",
                thumbnailUrl = state.streamInfo?.thumbnails?.maxByOrNull { it.height }?.url
                    ?: state.cachedVideo?.thumbnailUrl ?: "",
                duration = state.streamInfo?.duration?.toInt() ?: state.cachedVideo?.duration ?: 0,
                channelId = state.cachedVideo?.channelId ?: ""
            )
            val positionMs = EnhancedPlayerManager.getInstance().getCurrentPosition()
            EnhancedPlayerManager.getInstance().pause()
            EnhancedMusicPlayerManager.playTrack(musicTrack, audioUrl, startPositionMs = positionMs)
        } else {
            // Fallback: keep video ExoPlayer running in audio-only mode
            EnhancedPlayerManager.getInstance().switchToAudioOnly()
        }
        _uiState.update { it.copy(shouldDismissPlayer = true) }
    }

    fun resetDismissState() {
        _uiState.update { it.copy(shouldDismissPlayer = false) }
    }

    /**
     * Retry loading the current video after an error.
     * Uses the cached video metadata to know which video to reload.
     */
    fun retryLoadVideo() {
        val videoId = _uiState.value.cachedVideo?.id ?: return
        Log.d("VideoPlayerViewModel", "Retrying video load for $videoId")
        if (applyUpcomingState(_uiState.value.cachedVideo ?: return)) {
            return
        }
        EnhancedPlayerManager.getInstance().clearCurrentVideo()
        _uiState.update { it.copy(error = null, errorHint = null, isLoading = true) }
        loadVideoInfo(videoId, isWifi = detectIsWifi(), forceRefresh = true)
    }

    fun ensurePlaybackPrepared(videoId: String) {
        val state = _uiState.value
        if (state.isLoading || state.error != null || state.isRestoredSession) return
        if (state.cachedVideo?.id != videoId && state.streamInfo?.id != videoId && state.localFileVideoId != videoId) return

        val manager = EnhancedPlayerManager.getInstance()
        if (manager.isPreparedForPlayback(videoId)) return

        viewModelScope.launch {
            val latest = _uiState.value
            if (latest.isLoading || latest.error != null || latest.isRestoredSession) return@launch
            if (manager.isPreparedForPlayback(videoId)) return@launch

            val loadToken = playbackLoadToken
            val localFilePath = latest.localFilePath?.takeIf {
                latest.localFileVideoId == null || latest.localFileVideoId == videoId
            }
            if (localFilePath != null && latest.streamInfo == null) {
                Log.w("VideoPlayerViewModel", "Late prepare: arming local playback for $videoId")
                prepareLocalMediaForPlayback(
                    videoId = videoId,
                    localFilePath = localFilePath,
                    offlineSegments = latest.offlineSponsorBlockSegments,
                    savedPosition = latest.savedPosition?.first()
                        ?: viewHistory.getPlaybackPosition(videoId).first(),
                    loadToken = loadToken
                )
                return@launch
            }

            val streamInfo = latest.streamInfo ?: return@launch
            val audioStream = latest.audioStream
            val videoStreams = (streamInfo.videoStreams + (streamInfo.videoOnlyStreams ?: emptyList()))
                .filterIsInstance<VideoStream>()
            if (audioStream == null && videoStreams.isEmpty() && streamInfo.dashMpdUrl.isNullOrEmpty() && latest.hlsUrl.isNullOrEmpty()) {
                Log.w("VideoPlayerViewModel", "Late prepare skipped for $videoId: no playable streams in UI state")
                return@launch
            }

            Log.w(
                "VideoPlayerViewModel",
                "Late prepare: arming stream playback for $videoId (audio=${audioStream != null}, videos=${videoStreams.size})"
            )
            prepareLoadedMediaForPlayback(
                videoId = videoId,
                streamInfo = streamInfo,
                videoStream = latest.videoStream,
                audioStream = audioStream,
                videoStreams = videoStreams,
                audioStreams = streamInfo.audioStreams,
                subtitles = streamInfo.subtitles ?: emptyList(),
                savedPosition = latest.savedPosition?.first()
                    ?: viewHistory.getPlaybackPosition(videoId).first(),
                localFilePath = localFilePath,
                offlineSegments = latest.offlineSponsorBlockSegments,
                hlsUrl = latest.hlsUrl,
                isAdaptiveMode = latest.isAdaptiveMode,
                loadToken = loadToken
            )
        }
    }

    fun playPlaylist(videos: List<Video>, startIndex: Int, title: String? = null) {
        if (videos.isEmpty()) return
        val startVideo = videos.getOrNull(startIndex) ?: videos.first()
        
        // Stop music player
        EnhancedMusicPlayerManager.stop()
        EnhancedMusicPlayerManager.clearCurrentTrack()
        
        // Update Player Manager Queue
        EnhancedPlayerManager.getInstance().setQueue(videos, startIndex, title)
        
        // Update UI state immediately
        _uiState.update { 
            it.copy(
                cachedVideo = startVideo,
                isLoading = true,
                error = null,
                errorHint = null,
                metadataError = null,
                streamInfo = null,
                videoStream = null,
                audioStream = null,
                relatedVideos = emptyList(),
                isSubscribed = false,
                likeState = null,
                queueTitle = title,
                isUpcoming = false,
                upcomingReleaseTimeMs = null
            )
        }
        saveHistoryEntry(startVideo)
        EnhancedPlayerManager.getInstance().startBackgroundService(
            videoId   = startVideo.id,
            title     = startVideo.title.ifEmpty { "Flow Player" },
            channel   = startVideo.channelName,
            thumbnail = startVideo.thumbnailUrl
        )
        if (applyUpcomingState(startVideo, preserveQueueTitle = title)) {
            return
        }
        // Start loading the first video
        loadVideoInfo(startVideo.id, isWifi = detectIsWifi(), forceRefresh = true)
    }

    fun playNext() {
        val handledByPlayer = EnhancedPlayerManager.getInstance().playNext()
        if (!handledByPlayer) {
            _uiState.value.relatedVideos.firstOrNull()?.let { nextVideo ->
                playVideo(nextVideo)
                io.github.aedev.flow.player.GlobalPlayerState.setCurrentVideo(nextVideo)
            }
        }
    }

    fun playPrevious() {
        val handledByPlayer = EnhancedPlayerManager.getInstance().playPrevious()
        if (!handledByPlayer) {
            getPreviousVideoId()?.let { prevId ->
                val prevVideo = Video(
                    id = prevId, 
                    title = "", 
                    channelName = "", 
                    channelId = "", 
                    thumbnailUrl = "", 
                    duration = 0, 
                    viewCount = 0, 
                    uploadDate = ""
                )
                playVideo(prevVideo)
                io.github.aedev.flow.player.GlobalPlayerState.setCurrentVideo(prevVideo)
            }
        }
    }

    fun addVideoToQueueNext(video: Video) {
        EnhancedPlayerManager.getInstance().addVideoToQueueNext(video)
    }

    fun addVideoToQueue(video: Video) {
        EnhancedPlayerManager.getInstance().addVideoToQueue(video)
    }
    
    /**
     * PERFORMANCE OPTIMIZED: Load video info with aggressive parallel fetching
     * Uses SupervisorScope for error isolation and optimized dispatcher for network operations
     * @param forceRefresh If true, forces a fresh load even if the video appears to be already loaded
     * @param escalateToSabr If true (a 403-expiry reload), skip the fast direct-URL clients and
     *   extract straight through the durable WEB+PoToken+SABR path — fast clients return the same
     *   session-gated URLs that just 403'd, so re-trying them loops.
     */
    fun loadVideoInfo(videoId: String, isWifi: Boolean = true, forceRefresh: Boolean = false, escalateToSabr: Boolean = false) {
        val currentState = _uiState.value
        Log.d("VideoPlayerViewModel", "loadVideoInfo: Request=$videoId. Current=${currentState.streamInfo?.id}, IsLoading=${currentState.isLoading}, ForceRefresh=$forceRefresh, escalateToSabr=$escalateToSabr")

        currentState.cachedVideo
            ?.takeIf { it.id == videoId && it.isUpcoming }
            ?.let { cachedVideo ->
                val releaseTimeMs = resolveUpcomingReleaseTime(cachedVideo)
                if (releaseTimeMs == null || releaseTimeMs > System.currentTimeMillis()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            errorHint = null,
                            metadataError = null,
                            streamInfo = null,
                            videoStream = null,
                            audioStream = null,
                            localFilePath = null,
                            localFileVideoId = null,
                            isUpcoming = true,
                            upcomingReleaseTimeMs = releaseTimeMs
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
            (currentState.streamInfo?.id == videoId || currentState.cachedVideo?.id == videoId)) {
             Log.d("VideoPlayerViewModel", "Video $videoId is currently loading. Skipping redundant request.")
             return
        }

        // Track history
        if (navigationHistory.isEmpty() || navigationHistory[currentHistoryIndex] != videoId) {
            if (currentHistoryIndex < navigationHistory.size - 1) {
                val toRemove = navigationHistory.size - 1 - currentHistoryIndex
                repeat(toRemove) { navigationHistory.removeAt(navigationHistory.size - 1) }
            }
            navigationHistory.add(videoId)
            currentHistoryIndex = navigationHistory.size - 1
            _canGoPrevious.value = currentHistoryIndex > 0
        }
        
        _uiState.value = _uiState.value.copy(
            isLoading = true, 
            error = null, 
            errorHint = null,
            metadataError = null,
            streamInfo = null,
            videoStream = null,
            audioStream = null,
            savedPosition = null,
            relatedVideos = emptyList(),
            dislikeCount = null,
            // Also reset subscription and like state for new video
            isSubscribed = false,
            likeState = null,
            hlsUrl = null,
            localFilePath = null,
            localFileVideoId = null,
            isUpcoming = false,
            upcomingReleaseTimeMs = null
        )

        if (activeLoadJob?.isActive == true && loadingVideoId == videoId) {
            Log.d("VideoPlayerViewModel", "loadVideoInfo: extraction already in flight for $videoId — ignoring redundant trigger")
            return
        }

        cancelActivePlaybackLoad()
        val loadToken = nextPlaybackLoadToken()
        loadingVideoId = videoId

        activeLoadJob = viewModelScope.launch(PerformanceDispatcher.networkIO) {
            Log.d("VideoPlayerViewModel", "Starting loadVideoInfo for $videoId")
            var isOfflineAvailable = false
            var offlineLocalPath: String? = null

            try {
                val streamInfoDeferred = async(PerformanceDispatcher.networkIO) {
                    var info: StreamInfo? = null
                    var lastError: Throwable? = null
                    var attempt = 0
                    val maxAttempts = 3
                    while (info == null && attempt < maxAttempts) {
                        try {
                            attempt++
                            info = withTimeoutOrNull(10_000L) {
                                repository.getVideoStreamInfo(videoId)
                            }
                            if (info == null && attempt < maxAttempts) {
                                Log.w("VideoPlayerViewModel", "Stream info fetch failed (attempt $attempt), retrying in ${attempt * 300}ms...")
                                delay(attempt * 300L)
                            }
                        } catch (e: org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException) {
                            Log.e("VideoPlayerViewModel", "Content restriction for $videoId: ${e.javaClass.simpleName}: ${e.message}")
                            lastError = e
                            break
                        } catch (e: Exception) {
                            Log.e("VideoPlayerViewModel", "Failed to load stream info (attempt $attempt)", e)
                            lastError = e
                            if (attempt < maxAttempts) {
                                delay(attempt * 300L)
                            }
                        }
                    }
                    if (info == null) {
                        Log.e("VideoPlayerViewModel", "Stream info fetch failed after $maxAttempts attempts")
                    }
                    Pair(info, lastError)
                }

                val innerTubeDeferred = async(PerformanceDispatcher.networkIO) {
                    try {
                        withTimeoutOrNull(25000L) {
                            InnerTubeVideoStreamExtractor.extract(videoId, forceSabr = escalateToSabr)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.d("VideoPlayerViewModel", "InnerTube extraction failed for $videoId: ${e.message}")
                        null
                    }
                }

                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    if (playerPreferences.rytdEnabled.first()) {
                        withTimeoutOrNull(5000L) { fetchReturnYouTubeDislike(videoId) }?.let { dislikeCount ->
                            if (isPlaybackLoadCurrent(loadToken) && (_uiState.value.cachedVideo?.id == videoId || _uiState.value.streamInfo?.id == videoId)) {
                                _uiState.update { it.copy(dislikeCount = dislikeCount) }
                            }
                        }
                    }
                }

                val (qualityAndAudioPrefs, downloadedVideo) = supervisorScope {
                    val prefsDeferred = async(PerformanceDispatcher.diskIO) {
                        val preferredQuality = if (isWifi) {
                            playerPreferences.defaultQualityWifi.first()
                        } else {
                            playerPreferences.defaultQualityCellular.first()
                        }
                        val preferredAudioLang = playerPreferences.preferredAudioLanguage.first()
                        Pair(preferredQuality, preferredAudioLang)
                    }
                    
                    val downloadedDeferred = async(PerformanceDispatcher.diskIO) {
                        try {
                            videoDownloadManager.downloadedVideos.map { list -> 
                                list.find { it.video.id == videoId } 
                            }.first()
                        } catch (e: Exception) { null }
                    }
                    
                    prefsDeferred.await() to downloadedDeferred.await()
                }
                
                val preferredQuality = qualityAndAudioPrefs.first
                val preferredAudioLanguage = qualityAndAudioPrefs.second

                // Check for offline file immediately (video downloads and audio-only downloads)
                val localFile = if (downloadedVideo != null) java.io.File(downloadedVideo.filePath) else null
                isOfflineAvailable = localFile?.exists() == true
                offlineLocalPath = localFile?.absolutePath?.takeIf { isOfflineAvailable }
                
                if (isOfflineAvailable) {
                    Log.d("VideoPlayerViewModel", "Found offline video at ${localFile?.absolutePath}")
                    val sbJson = videoDownloadManager.getSponsorBlockData(videoId)
                    val offlineSegments = deserializeSponsorBlockSegments(sbJson)
                    ensureActive()
                    if (!isPlaybackLoadCurrent(loadToken)) return@launch
                    val localPath = offlineLocalPath
                    _uiState.update { 
                        it.copy(
                            localFilePath = localPath,
                            localFileVideoId = videoId,
                            offlineSponsorBlockSegments = offlineSegments,
                            error = null,
                            errorHint = null,
                            isLoading = false,
                            isUpcoming = false,
                            upcomingReleaseTimeMs = null
                        )
                    }
                    if (localPath != null) {
                        prepareLocalMediaForPlayback(
                            videoId = videoId,
                            localFilePath = localPath,
                            offlineSegments = offlineSegments,
                            savedPosition = viewHistory.getPlaybackPosition(videoId).first(),
                            loadToken = loadToken
                        )
                    }
                }

                kotlinx.coroutines.withTimeout(30_000) {
                    Log.d("VideoPlayerViewModel", "Loading video $videoId with preferred quality: ${preferredQuality.label} (isWifi=$isWifi)")

                    val (streamInfo, streamError) = streamInfoDeferred.await()
                    ensureActive()
                    if (!isPlaybackLoadCurrent(loadToken)) return@withTimeout
                    
                    // Extract related videos directly from the stream info (avoids extra network call)
                    val relatedVideos = if (streamInfo != null) {
                        repository.getRelatedVideosFromStreamInfo(streamInfo)
                    } else {
                        emptyList()
                    }
                    
                    if (streamInfo != null) {
                        // Record interaction for Flow Neuro Engine
                        try {
                            val video = Video(
                                id = videoId,
                                title = streamInfo.name ?: "",
                                channelName = streamInfo.uploaderName ?: "",
                                channelId = streamInfo.uploaderUrl?.split("/")?.last() ?: "",
                                thumbnailUrl = streamInfo.thumbnails?.maxByOrNull { it.height }?.url ?: "",
                                duration = streamInfo.duration.toInt(),
                                viewCount = streamInfo.viewCount,
                                uploadDate = "",
                                description = streamInfo.description?.content ?: "",
                                tags = streamInfo.tags ?: emptyList()
                            )
                            FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.CLICK)
                        } catch (e: Exception) {
                            Log.e("VideoPlayerViewModel", "Failed to record interaction", e)
                        }

                        val realTitle = streamInfo.name?.takeIf { it.isNotBlank() }
                        val realChannel = streamInfo.uploaderName?.takeIf { it.isNotBlank() }
                        val realThumbnail = streamInfo.thumbnails?.maxByOrNull { it.height }?.url?.takeIf { it.isNotBlank() }
                        if (realTitle != null) {
                            val currentCached = _uiState.value.cachedVideo
                            val enrichedVideo = (currentCached ?: Video(
                                id = videoId, title = "", channelName = "", channelId = "",
                                thumbnailUrl = "", duration = 0, viewCount = 0L, uploadDate = ""
                            )).copy(
                                title = realTitle,
                                channelName = realChannel ?: currentCached?.channelName ?: "",
                                channelId = currentCached?.channelId?.takeIf { it.isNotBlank() }
                                    ?: streamInfo.uploaderUrl?.split("/")?.last() ?: "",
                                thumbnailUrl = realThumbnail ?: currentCached?.thumbnailUrl ?: "",
                                duration = streamInfo.duration.toInt().takeIf { it > 0 } ?: (currentCached?.duration ?: 0)
                            )
                            if (isPlaybackLoadCurrent(loadToken)) {
                                GlobalPlayerState.setCurrentVideo(enrichedVideo)
                                EnhancedPlayerManager.getInstance().startBackgroundService(
                                    videoId   = videoId,
                                    title     = realTitle,
                                    channel   = realChannel ?: "",
                                    thumbnail = realThumbnail ?: ""
                                )
                            }
                        }

                        val innerTubeResult = innerTubeDeferred.await()
                        val innerTubeVideoStreams = innerTubeResult?.let {
                            InnerTubeStreamBridge.convertVideoFormats(it.videoFormats)
                        } ?: emptyList()
                        val innerTubeAudioStreams = innerTubeResult?.let {
                            InnerTubeStreamBridge.convertAudioFormats(it.audioFormats)
                        } ?: emptyList()

                        val extractorVideoStreams = (streamInfo.videoStreams + streamInfo.videoOnlyStreams).filterIsInstance<VideoStream>()
                        val effectiveVideoStreams: List<VideoStream> = mergeVideoStreams(innerTubeVideoStreams, extractorVideoStreams)
                        val effectiveAudioStreams: List<AudioStream> = mergeAudioStreams(innerTubeAudioStreams, streamInfo.audioStreams)

                        if (innerTubeVideoStreams.isNotEmpty()) {
                            Log.i("VideoPlayerViewModel", "Using InnerTube streams: ${innerTubeVideoStreams.size} video, ${innerTubeAudioStreams.size} audio (client=${innerTubeResult?.usedClient?.clientName})")
                        } else {
                            Log.d("VideoPlayerViewModel", "Using NewPipe streams: ${effectiveVideoStreams.size} video, ${effectiveAudioStreams.size} audio")
                        }

                        val availableQualities = extractAvailableQualitiesFromStreams(effectiveVideoStreams)
                        val initialQuality = preferredQuality
                        val selectedStreams = selectStreamsFromLists(effectiveVideoStreams, effectiveAudioStreams, initialQuality, preferredAudioLanguage)
                        var localFilePath: String? = null
                        
                        // If downloaded (video or audio-only), override with local path
                        if (downloadedVideo != null && java.io.File(downloadedVideo.filePath).exists()) {
                            localFilePath = downloadedVideo.filePath
                        }

                        // Load SponsorBlock segments from DB if we're going to play offline
                        val offlineSegments = if (localFilePath != null) {
                            val sbJson = videoDownloadManager.getSponsorBlockData(videoId)
                            if (sbJson != null) {
                                deserializeSponsorBlockSegments(sbJson)
                            } else {
                                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                                    try {
                                        val segments = sponsorBlockRepository.getSegments(videoId)
                                        if (segments.isNotEmpty()) {
                                            videoDownloadManager.saveSponsorBlockData(videoId, Gson().toJson(segments))
                                            Log.d("VideoPlayerViewModel", "Backfilled ${segments.size} SB segments for $videoId")
                                            _uiState.update { it.copy(offlineSponsorBlockSegments = segments) }
                                        } else {
                                            Log.d("VideoPlayerViewModel", "No SB segments available for $videoId (backfill)")
                                        }
                                    } catch (e: Exception) {
                                        Log.w("VideoPlayerViewModel", "SB backfill failed for $videoId", e)
                                    }
                                }
                                null
                            }
                        } else null

                        val subtitles = extractSubtitles(streamInfo)
                        val chapters = streamInfo.streamSegments ?: emptyList()
                        val liveHlsUrl = streamInfo.hlsUrl.takeIf {
                            streamInfo.streamType == StreamType.LIVE_STREAM ||
                                streamInfo.streamType == StreamType.POST_LIVE_STREAM
                        }
                        
                        // Load saved playback position
                        val savedPosition = viewHistory.getPlaybackPosition(videoId)
                        
                        // Load autoplay preference
                        val autoplay = playerPreferences.autoplayEnabled.first()
                        EnhancedPlayerManager.getInstance().setAutoplayCandidates(
                            sourceVideoId = videoId,
                            videos = relatedVideos,
                            enabled = autoplay
                        )
                        
                        val resolvedSabrInfo = innerTubeResult?.sabrInfo
                        if (resolvedSabrInfo != null) {
                            Log.d("VideoPlayerViewModel", "SABR available: audioItag=${resolvedSabrInfo.audioItag}, videoItag=${resolvedSabrInfo.videoItag}")
                        }

                        _uiState.value = _uiState.value.copy(
                            streamInfo = streamInfo,
                            relatedVideos = relatedVideos,
                            videoStream = selectedStreams.first,
                            audioStream = selectedStreams.second,
                            availableQualities = availableQualities,
                            selectedQuality = selectedStreams.third,
                            subtitles = subtitles,
                            chapters = chapters,
                            isLoading = false,
                            savedPosition = savedPosition,
                            isAdaptiveMode = preferredQuality == VideoQuality.AUTO,
                            autoplayEnabled = autoplay,
                            streamSizes = emptyMap(),
                            localFilePath = localFilePath,
                            localFileVideoId = if (localFilePath != null) videoId else null,
                            offlineSponsorBlockSegments = offlineSegments,
                            hlsUrl = liveHlsUrl,
                            isUpcoming = false,
                            upcomingReleaseTimeMs = null,
                            innerTubeVideoFormats = innerTubeResult?.videoFormats ?: emptyList(),
                            innerTubeAudioFormats = innerTubeResult?.audioFormats ?: emptyList()
                        )

                        ensureActive()
                        if (!isPlaybackLoadCurrent(loadToken)) return@withTimeout

                        prepareLoadedMediaForPlayback(
                            videoId = videoId,
                            streamInfo = streamInfo,
                            videoStream = selectedStreams.first,
                            audioStream = selectedStreams.second,
                            videoStreams = effectiveVideoStreams,
                            audioStreams = effectiveAudioStreams,
                            subtitles = streamInfo.subtitles ?: emptyList(),
                            savedPosition = savedPosition.first(),
                            localFilePath = localFilePath,
                            offlineSegments = offlineSegments,
                            hlsUrl = liveHlsUrl,
                            isAdaptiveMode = preferredQuality == VideoQuality.AUTO,
                            loadToken = loadToken,
                            sabrInfo = resolvedSabrInfo,
                            itVideoFormats = innerTubeResult?.videoFormats ?: emptyList(),
                            itAudioFormats = innerTubeResult?.audioFormats ?: emptyList()
                        )

                        // PARALLEL FETCH: Channel info and stream sizes simultaneously
                        viewModelScope.launch(PerformanceDispatcher.networkIO) {
                            supervisorScope {
                                // Fetch channel info
                                val channelDeferred = async(PerformanceDispatcher.networkIO) {
                                    withTimeoutOrNull(8000L) {
                                        try {
                                            val channelUrl = streamInfo.uploaderUrl ?: ""
                                            if (channelUrl.isNotBlank()) {
                                                val channelInfo = repository.getChannelInfo(channelUrl)
                                                channelInfo?.let { ci ->
                                    val subCount = ci.subscriberCount
                                                    val avatarUrl = try {
                                                        val thumbnailsMethod = ci::class.java.methods.firstOrNull { 
                                                            it.name.equals("getThumbnails", true) || it.name.equals("getAvatars", true)
                                                        }
                                                        val thumbnails = thumbnailsMethod?.invoke(ci) as? List<*>
                                                        thumbnails?.firstOrNull()?.let { img ->
                                                            val urlMethod = img::class.java.methods.firstOrNull { it.name.equals("getUrl", true) }
                                                            urlMethod?.invoke(img) as? String
                                                        } ?: ""
                                                    } catch (ex: Exception) { "" }

                                                    Pair(subCount, avatarUrl)
                                                }
                                            } else null
                                        } catch (e: Exception) { 
                                            Log.e("VideoPlayerViewModel", "Failed to fetch channel info", e)
                                            null
                                        }
                                    }
                                }

                                // Compute stream sizes — use already-fetched innertube data when available
                                val sizesDeferred = async(PerformanceDispatcher.networkIO) {
                                    try {
                                        val sourceFormats = innerTubeResult?.playerResponse?.streamingData
                                            ?: withTimeoutOrNull(8000L) {
                                                YouTube.player(videoId, client = YouTubeClient.MOBILE)
                                                    .getOrNull()?.streamingData
                                            }

                                        sourceFormats?.let { streamingData ->
                                            val sizes = mutableMapOf<String, Long>()
                                            val audioFmts = streamingData.adaptiveFormats.filter { it.isAudio }
                                            val bestAacSize = audioFmts
                                                .filter { it.mimeType.contains("mp4", ignoreCase = true) }
                                                .maxByOrNull { it.bitrate }?.contentLength ?: 0L
                                            val bestOpusSize = audioFmts
                                                .filter { it.mimeType.contains("webm", ignoreCase = true) }
                                                .maxByOrNull { it.bitrate }?.contentLength ?: 0L
                                            val bestAnyAudioSize = audioFmts
                                                .maxByOrNull { it.bitrate }?.contentLength ?: 0L

                                            streamingData.formats?.forEach { format ->
                                                if (format.height != null && format.contentLength != null) {
                                                    val codecKey = VideoPlayerUtils.codecKeyFromMimeType(format.mimeType)
                                                    val key = VideoPlayerUtils.streamSizeKey(qualityHeightFromFormat(format.qualityLabel, format.height), codecKey)
                                                    sizes[key] = format.contentLength
                                                }
                                            }
                                            streamingData.adaptiveFormats.forEach { format ->
                                                if (format.height != null && format.contentLength != null && !format.isAudio) {
                                                    val codecKey = VideoPlayerUtils.codecKeyFromMimeType(format.mimeType)
                                                    val isMp4Video = format.mimeType.contains("mp4", ignoreCase = true)
                                                    val audioSize = when {
                                                        isMp4Video && bestAacSize > 0 -> bestAacSize
                                                        !isMp4Video && bestOpusSize > 0 -> bestOpusSize
                                                        else -> bestAnyAudioSize
                                                    }
                                                    val totalSize = format.contentLength + audioSize
                                                    val key = VideoPlayerUtils.streamSizeKey(qualityHeightFromFormat(format.qualityLabel, format.height), codecKey)
                                                    val currentSize = sizes[key] ?: 0L
                                                    if (totalSize > currentSize) sizes[key] = totalSize
                                                }
                                            }
                                            sizes
                                        }
                                    } catch (e: Exception) {
                                        Log.e("VideoPlayerViewModel", "Failed to compute stream sizes", e)
                                        null
                                    }
                                }
                                
                                // Await both and update UI
                                val channelResult = channelDeferred.await()
                                val sizesResult = sizesDeferred.await()
                                
                                if (isPlaybackLoadCurrent(loadToken)) {
                                    channelResult?.let { (subCount, avatarUrl) ->
                                        _uiState.value = _uiState.value.copy(
                                            channelSubscriberCount = subCount.takeIf { it > 0L },
                                            channelAvatarUrl = avatarUrl
                                        )
                                    }
                                    
                                    sizesResult?.let { sizes ->
                                        _uiState.value = _uiState.value.copy(streamSizes = sizes)
                                    }
                                }
                            }
                        }
                    } else {
                        // Offline fallback
                        if (isOfflineAvailable) {
                            Log.d("VideoPlayerViewModel", "Using offline video for $videoId (Network fetch failed)")
                            val sbJson = videoDownloadManager.getSponsorBlockData(videoId)
                            if (isPlaybackLoadCurrent(loadToken)) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        error = null,
                                        errorHint = null,
                                        relatedVideos = relatedVideos,
                                        localFilePath = localFile?.absolutePath,
                                        offlineSponsorBlockSegments = deserializeSponsorBlockSegments(sbJson),
                                        isUpcoming = false,
                                        upcomingReleaseTimeMs = null
                                    )
                                }
                            }
                        } else {
                            Log.e("VideoPlayerViewModel", "Stream info is null for $videoId and no offline copy found.")
                            val videoError = VideoErrorMapper.from(context, streamError, videoId)
                            if (isPlaybackLoadCurrent(loadToken)) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        relatedVideos = relatedVideos,
                                        error = videoError.message,
                                        errorHint = videoError.hint
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("VideoPlayerViewModel", "Video info load timed out for $videoId after 30s")
                if (isPlaybackLoadCurrent(loadToken) && isOfflineAvailable) {
                     Log.d("VideoPlayerViewModel", "Ignoring timeout, playing offline video")
                     _uiState.update { it.copy(isLoading = false, error = null, errorHint = null) }
                     offlineLocalPath?.let { localPath ->
                         prepareLocalMediaForPlayback(
                             videoId = videoId,
                             localFilePath = localPath,
                             offlineSegments = deserializeSponsorBlockSegments(videoDownloadManager.getSponsorBlockData(videoId)),
                             savedPosition = viewHistory.getPlaybackPosition(videoId).first(),
                             loadToken = loadToken
                         )
                     }
                } else if (isPlaybackLoadCurrent(loadToken)) {
                    val videoError = VideoErrorMapper.fromTimeout(context)
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = videoError.message,
                            errorHint = videoError.hint
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Exception loading video $videoId", e)
                
                if (isPlaybackLoadCurrent(loadToken) && isOfflineAvailable) {
                     Log.d("VideoPlayerViewModel", "Ignoring exception, playing offline video")
                     _uiState.update { it.copy(isLoading = false, error = null, errorHint = null) }
                     val downloadedVideo = videoDownloadManager.downloadedVideos.map { list ->
                         list.find { it.video.id == videoId }
                     }.first()
                     downloadedVideo?.filePath?.takeIf { java.io.File(it).exists() }?.let { localPath ->
                         prepareLocalMediaForPlayback(
                             videoId = videoId,
                             localFilePath = localPath,
                             offlineSegments = deserializeSponsorBlockSegments(videoDownloadManager.getSponsorBlockData(videoId)),
                             savedPosition = viewHistory.getPlaybackPosition(videoId).first(),
                             loadToken = loadToken
                         )
                     }
                } else if (isPlaybackLoadCurrent(loadToken)) {
                    // Final fallback if everything fails
                    val downloadedVideo = videoDownloadManager.downloadedVideos.map { list -> 
                        list.find { it.video.id == videoId } 
                    }.first()

                    if (downloadedVideo != null && java.io.File(downloadedVideo.filePath).exists()) {
                        val offlineSegments = deserializeSponsorBlockSegments(videoDownloadManager.getSponsorBlockData(videoId))
                        _uiState.update { 
                            it.copy(
                                streamInfo = null,
                                isLoading = false,
                                error = null,
                                errorHint = null,
                                localFilePath = downloadedVideo.filePath,
                                localFileVideoId = videoId,
                                offlineSponsorBlockSegments = offlineSegments,
                                isUpcoming = false,
                                upcomingReleaseTimeMs = null
                            )
                        }
                        prepareLocalMediaForPlayback(
                            videoId = videoId,
                            localFilePath = downloadedVideo.filePath,
                            offlineSegments = offlineSegments,
                            savedPosition = viewHistory.getPlaybackPosition(videoId).first(),
                            loadToken = loadToken
                        )
                    } else {
                        val videoError = VideoErrorMapper.from(context, e, videoId)
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = videoError.message,
                                errorHint = videoError.hint
                            )
                        }
                    }
                }
            } finally {
                if (isPlaybackLoadCurrent(loadToken)) {
                    activeLoadJob = null
                }
            }
        }
    }

    private suspend fun prepareLoadedMediaForPlayback(
        videoId: String,
        streamInfo: StreamInfo,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        subtitles: List<SubtitlesStream>,
        savedPosition: Long,
        localFilePath: String?,
        offlineSegments: List<SponsorBlockSegment>?,
        hlsUrl: String?,
        isAdaptiveMode: Boolean,
        loadToken: Long,
        sabrInfo: SabrStreamInfo? = null,
        itVideoFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
        itAudioFormats: List<PlayerResponse.StreamingData.Format> = emptyList()
    ) = withContext(Dispatchers.Main) {
        if (!isPlaybackLoadCurrent(loadToken)) return@withContext
        val manager = EnhancedPlayerManager.getInstance()
        if (manager.isPreparedForPlayback(videoId)) return@withContext

        manager.initialize(context)
        val queueSize = manager.playerState.value.queueSize

        val durationMs = when {
            streamInfo.duration > 0L -> streamInfo.duration * 1000L
            else -> (_uiState.value.cachedVideo?.duration?.toLong() ?: 0L) * 1000L
        }
        val resumePosition = savedPosition
            .takeIf { it > 500L }
            ?.takeIf { queueSize <= 1 }
            ?.takeIf { hlsUrl.isNullOrEmpty() }
            ?.takeUnless { shouldRestartCompletedPlayback(it, durationMs) }
            ?: 0L

        if (localFilePath != null) {
            manager.playLocalFile(
                videoId = videoId,
                filePath = localFilePath,
                savedSegments = offlineSegments,
                preservePosition = resumePosition.takeIf { it > 0L }
            )
        } else if (audioStream != null || videoStreams.isNotEmpty() || !streamInfo.dashMpdUrl.isNullOrEmpty() || !hlsUrl.isNullOrEmpty()) {
            if (audioStream == null) {
                Log.w("VideoPlayerViewModel", "Preparing $videoId without a separate audio stream")
            }
            manager.setStreams(
                videoId = videoId,
                videoStream = if (isAdaptiveMode) null else videoStream,
                audioStream = audioStream,
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                subtitles = subtitles,
                durationSeconds = streamInfo.duration,
                dashManifestUrl = streamInfo.dashMpdUrl,
                hlsUrl = hlsUrl,
                streamType = streamInfo.streamType,
                startPosition = resumePosition,
                sabrInfo = sabrInfo,
                itVideoFormats = itVideoFormats,
                itAudioFormats = itAudioFormats
            )
        }
        applyRememberedPlaybackSpeed(isLive = !hlsUrl.isNullOrEmpty(), manager = manager)

        if (!isPlaybackLoadCurrent(loadToken)) return@withContext
        manager.play()
    }

    private suspend fun prepareLocalMediaForPlayback(
        videoId: String,
        localFilePath: String,
        offlineSegments: List<SponsorBlockSegment>?,
        savedPosition: Long,
        loadToken: Long
    ) = withContext(Dispatchers.Main) {
        if (!isPlaybackLoadCurrent(loadToken)) return@withContext
        val manager = EnhancedPlayerManager.getInstance()
        if (manager.isPreparedForPlayback(videoId)) return@withContext

        manager.initialize(context)
        manager.playLocalFile(
            videoId = videoId,
            filePath = localFilePath,
            savedSegments = offlineSegments,
            preservePosition = savedPosition.takeIf { it > 500L }
        )
        applyRememberedPlaybackSpeed(isLive = false, manager = manager)

        if (!isPlaybackLoadCurrent(loadToken)) return@withContext
        manager.play()
    }

    private suspend fun applyRememberedPlaybackSpeed(
        isLive: Boolean,
        manager: EnhancedPlayerManager
    ) {
        if (isLive) {
            manager.setPlaybackSpeed(1.0f)
            return
        }

        if (playerPreferences.rememberPlaybackSpeed.first()) {
            manager.setPlaybackSpeed(playerPreferences.playbackSpeed.first())
        }
    }

    private fun shouldRestartCompletedPlayback(savedPosition: Long, durationMs: Long): Boolean {
        if (savedPosition <= 0L) return false
        if (durationMs > 0L) {
            val remainingMs = durationMs - savedPosition
            return remainingMs <= 1_500L || savedPosition >= (durationMs * 0.98f).toLong()
        }
        return savedPosition > 4 * 60 * 60 * 1000L
    }
    
    fun switchQuality(quality: VideoQuality) {
        val state = _uiState.value
        val streamInfo = state.streamInfo ?: return
        viewModelScope.launch {
            val audioLangPref = playerPreferences.preferredAudioLanguage.first()
            val innerTubeVideoStreams = InnerTubeStreamBridge.convertVideoFormats(state.innerTubeVideoFormats)
            val innerTubeAudioStreams = InnerTubeStreamBridge.convertAudioFormats(state.innerTubeAudioFormats)
            val effectiveVideo = mergeVideoStreams(
                innerTubeVideoStreams,
                (streamInfo.videoStreams + streamInfo.videoOnlyStreams).filterIsInstance<VideoStream>()
            )
            val effectiveAudio: List<AudioStream> = mergeAudioStreams(innerTubeAudioStreams, streamInfo.audioStreams)
            val streams = selectStreamsFromLists(effectiveVideo, effectiveAudio, quality, audioLangPref)

            _uiState.value = state.copy(
                videoStream = streams.first,
                audioStream = streams.second,
                selectedQuality = streams.third,
                isAdaptiveMode = quality == VideoQuality.AUTO
            )
        }
    }

    fun getPreviousVideoId(): String? {
        if (currentHistoryIndex > 0 && currentHistoryIndex < navigationHistory.size) {
            currentHistoryIndex--
            _canGoPrevious.value = currentHistoryIndex > 0
            return navigationHistory.getOrNull(currentHistoryIndex)
        }
        return null
    }
    
    fun scaleUpQuality() {
        if (!_uiState.value.isAdaptiveMode) return
        val currentQuality = _uiState.value.selectedQuality
        val availableQualities = _uiState.value.availableQualities
            .filter { it != VideoQuality.AUTO }
            .sortedBy { it.height }
        val currentIndex = availableQualities.indexOf(currentQuality)
        if (currentIndex != -1 && currentIndex < availableQualities.size - 1) {
            switchQuality(availableQualities[currentIndex + 1])
        }
    }
    
    fun scaleDownQuality() {
        if (!_uiState.value.isAdaptiveMode) return
        val currentQuality = _uiState.value.selectedQuality
        val availableQualities = _uiState.value.availableQualities
            .filter { it != VideoQuality.AUTO }
            .sortedBy { it.height }
        val currentIndex = availableQualities.indexOf(currentQuality)
        if (currentIndex > 0) {
            switchQuality(availableQualities[currentIndex - 1])
        }
    }
    
    /**
     * Eagerly records a video as opened in history (position = 0).
     * Called the moment the user opens any video so history is always populated,
     * regardless of how quickly they close the player.
     */
    private fun saveHistoryEntry(video: Video) {
        if (video.id.startsWith("recovered_")) return
        viewModelScope.launch {
            viewHistory.touchHistoryEntry(
                videoId     = video.id,
                duration    = if (video.duration > 0) video.duration * 1000L else 0L,
                title       = video.title,
                thumbnailUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg",
                channelName = video.channelName,
                channelId   = video.channelId,
                isShort     = video.isShort
            )
        }
    }

    fun savePlaybackPosition(
        videoId: String, 
        position: Long, 
        duration: Long, 
        title: String, 
        thumbnailUrl: String,
        channelName: String = "",
        channelId: String = ""
    ) {
        viewModelScope.launch {
            viewHistory.savePlaybackPosition(
                videoId = videoId,
                position = position,
                duration = duration,
                title = title,
                thumbnailUrl = thumbnailUrl,
                channelName = channelName,
                channelId = channelId
            )
            if (duration > 0) {
                interestProfile.recordWatch(
                    videoTitle = title,
                    channelId = channelId,
                    channelName = channelName,
                    watchDuration = (position / 1000).toInt(),
                    totalDuration = (duration / 1000).toInt()
                )
            }
        }
    }
    
    fun reportWatchProgress(video: io.github.aedev.flow.data.model.Video, position: Long, duration: Long) {
        if (duration <= 0) return
        val watchFraction = position.toDouble() / duration
        // Only report if watched at least 20% and not already reported for this video
        // within a 10-second dedup window (guards against DisposableEffect re-fires).
        val now = System.currentTimeMillis()
        if (video.id == lastReportedVideoId && (now - lastReportedTimestamp) < 10_000L) return
        if (watchFraction < 0.20) return

        lastReportedVideoId = video.id
        lastReportedTimestamp = now

        val interactionType = when {
            watchFraction >= 0.85 -> InteractionType.WATCHED
            watchFraction >= 0.40 -> InteractionType.WATCHED
            else -> InteractionType.SKIPPED
        }

        viewModelScope.launch {
            FlowNeuroEngine.onVideoInteraction(
                context,
                video,
                interactionType,
                percentWatched = watchFraction.toFloat()
            )
        }
    }

    fun toggleSubscription(channelId: String, channelName: String, channelThumbnail: String) {
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
                        channelThumbnail = channelThumbnail
                    )
                )
                _uiState.value = _uiState.value.copy(isSubscribed = true)
                interestProfile.recordSubscription(channelId, channelName)
            }
        }
    }
    
    fun setNotificationEnabled(channelId: String, enabled: Boolean) {
        viewModelScope.launch {
            subscriptionRepository.updateNotificationState(channelId, enabled)
            _uiState.value = _uiState.value.copy(isNotificationsEnabled = enabled)
        }
    }

    fun likeVideo(videoId: String, title: String, thumbnail: String, channelName: String, channelId: String = "") {
        viewModelScope.launch {
            likedVideosRepository.likeVideo(
                LikedVideoInfo(
                    videoId = videoId,
                    title = title,
                    thumbnail = thumbnail,
                    channelName = channelName
                )
            )
            _uiState.value = _uiState.value.copy(likeState = "LIKED")
            interestProfile.recordLike(title, channelId, channelName)
            try {
                val video = Video(
                    id = videoId,
                    title = title,
                    channelName = channelName,
                    channelId = channelId,
                    thumbnailUrl = thumbnail,
                    duration = 0,
                    viewCount = 0,
                    uploadDate = ""
                )
                FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.LIKED)
            } catch (e: Exception) { }
        }
    }
    
    fun dislikeVideo(videoId: String) {
        viewModelScope.launch {
            likedVideosRepository.dislikeVideo(videoId)
            _uiState.value = _uiState.value.copy(likeState = "DISLIKED")
            try {
                val state = _uiState.value
                val video = state.cachedVideo?.takeIf { it.id == videoId }
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
                            tags = info.tags ?: emptyList()
                        )
                    }
                if (video != null) {
                    FlowNeuroEngine.onVideoInteraction(
                        context,
                        video,
                        InteractionType.DISLIKED
                    )
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
    
    fun loadSubscriptionAndLikeState(channelId: String, videoId: String) {
        viewModelScope.launch {
            subscriptionRepository.isSubscribed(channelId).collect { isSubscribed ->
                _uiState.value = _uiState.value.copy(isSubscribed = isSubscribed)
            }
        }
        viewModelScope.launch {
            subscriptionRepository.getSubscription(channelId).collect { subscription ->
                _uiState.value = _uiState.value.copy(
                    isNotificationsEnabled = subscription?.isNotificationEnabled ?: false
                )
            }
        }
        viewModelScope.launch {
            likedVideosRepository.getLikeState(videoId).collect { likeState ->
                _uiState.value = _uiState.value.copy(likeState = likeState)
            }
        }
    }
    
    fun toggleSubtitles(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(subtitlesEnabled = enabled)
    }
    
    fun selectSubtitleTrack(subtitle: SubtitleInfo) {
        _uiState.value = _uiState.value.copy(selectedSubtitle = subtitle)
        val idx = _uiState.value.subtitles.indexOfFirst { it.languageCode == subtitle.languageCode && it.url == subtitle.url }
        if (idx >= 0) {
            EnhancedPlayerManager.getInstance().selectSubtitle(idx)
        }
    }
    
    fun setMiniPlayerMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isMiniPlayer = enabled)
    }
    
    fun setFullscreen(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = enabled)
    }

    fun toggleAutoplay(enabled: Boolean) {
        viewModelScope.launch {
            val resolvedEnabled = enabled && !EnhancedPlayerManager.getInstance().playerState.value.isLooping
            playerPreferences.setAutoplayEnabled(resolvedEnabled)
            _uiState.value = _uiState.value.copy(autoplayEnabled = resolvedEnabled)
            _uiState.value.cachedVideo?.id?.let { videoId ->
                EnhancedPlayerManager.getInstance().setAutoplayCandidates(
                    sourceVideoId = videoId,
                    videos = _uiState.value.relatedVideos,
                    enabled = resolvedEnabled
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
        EnhancedPlayerManager.getInstance().toggleLoop(enabled)
    }

    fun loadComments(videoId: String) {
        viewModelScope.launch {
            _isLoadingComments.value = true
            _commentsState.value = emptyList()
            commentsNextPage = null
            _hasMoreComments.value = false
            try {
                val (comments, nextPage) = repository.getComments(videoId)
                _commentsState.value = comments
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
                _commentsState.value = _commentsState.value + newComments
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
                _commentsState.value = _commentsState.value.map { c ->
                    if (c.id == comment.id) {
                        c.copy(
                            replies = replies,
                            repliesPage = nextPage
                        )
                    } else c
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

                _commentsState.value = _commentsState.value.map { currentComment ->
                    if (currentComment.id == comment.id) {
                        currentComment.copy(
                            replies = currentComment.replies + replies,
                            repliesPage = nextPage
                        )
                    } else currentComment
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Error loading more replies", e)
            }
        }
    }
    
    private fun selectStreams(
        streamInfo: StreamInfo,
        preferredQuality: VideoQuality,
        preferredAudioLanguage: String = "original"
    ): Triple<VideoStream?, AudioStream?, VideoQuality> {
        val videoStreams = (streamInfo.videoStreams + streamInfo.videoOnlyStreams).filterIsInstance<VideoStream>()
        return selectStreamsFromLists(videoStreams, streamInfo.audioStreams, preferredQuality, preferredAudioLanguage)
    }

    private fun mergeVideoStreams(
        primary: List<VideoStream>,
        fallback: List<VideoStream>
    ): List<VideoStream> {
        return (primary + fallback)
            .filter { it.getContent().isNotBlank() }
            .distinctBy { stream ->
                val url = stream.getContent()
                if (url.isNotBlank()) url else "${VideoCodecUtils.qualityHeightFromStream(stream)}_${VideoCodecUtils.codecKeyFromStream(stream)}_${stream.bitrate}"
            }
            .sortedWith(
                compareByDescending<VideoStream> { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) }
                    .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                    .thenByDescending { it.bitrate }
            )
    }

    private fun mergeAudioStreams(
        primary: List<AudioStream>,
        fallback: List<AudioStream>
    ): List<AudioStream> {
        return (primary + fallback)
            .filter { it.getContent().isNotBlank() }
            .distinctBy { stream ->
                listOf(
                    stream.getContent(),
                    stream.format?.mimeType.orEmpty(),
                    stream.audioTrackId.orEmpty(),
                    stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate
                ).joinToString("|")
            }
            .sortedByDescending { it.averageBitrate.takeIf { bitrate -> bitrate > 0 } ?: it.bitrate }
    }

    private fun selectStreamsFromLists(
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        preferredQuality: VideoQuality,
        preferredAudioLanguage: String = "original"
    ): Triple<VideoStream?, AudioStream?, VideoQuality> {
        val audioCandidates = audioStreams
            .distinctBy { it.content ?: "" }
            .sortedByDescending { it.bitrate }

        val audioStream = when (preferredAudioLanguage) {
            "original" -> {
                audioCandidates.firstOrNull { stream ->
                    stream.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
                }
                ?: audioCandidates.firstOrNull { stream ->
                    stream.audioTrackType != org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED
                }
                ?: audioCandidates.firstOrNull()
            }
            else -> {
                audioCandidates.firstOrNull { a ->
                    val lang = a.audioLocale?.language ?: ""
                    lang.startsWith(preferredAudioLanguage, true)
                }
                ?: audioCandidates.firstOrNull { stream ->
                    stream.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
                }
                ?: audioCandidates.firstOrNull()
            }
        }

        val allVideoStreams = videoStreams.filter {
            val mime = it.format?.mimeType
            mime?.contains("mp4") == true || mime?.contains("webm") == true
        }

        val videoStream = when (preferredQuality) {
            VideoQuality.AUTO -> null
            else -> allVideoStreams
                .sortedWith(
                    compareBy<VideoStream> {
                        kotlin.math.abs(
                            QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) - preferredQuality.height
                        )
                    }
                        .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                        .thenByDescending { it.bitrate }
                )
                .firstOrNull()
        }

        val safeAudio = audioStream ?: audioStreams.firstOrNull()
        val playableVideoStream = if (safeAudio == null && videoStream == null) {
            allVideoStreams
                .sortedWith(
                    compareBy<VideoStream> { if (it.isVideoOnly) 1 else 0 }
                        .thenByDescending { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) }
                        .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                        .thenByDescending { it.bitrate }
                )
                .firstOrNull()
        } else {
            videoStream
        }

        val actualQuality = playableVideoStream?.let {
            VideoQuality.fromHeight(QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)))
        } ?: VideoQuality.AUTO

        return Triple(playableVideoStream, safeAudio, actualQuality)
    }

    /** Deserialize a JSON string into a list of SponsorBlock segments; returns null on failure. */
    private fun deserializeSponsorBlockSegments(json: String?): List<SponsorBlockSegment>? {
        if (json.isNullOrBlank()) return null
        return try {
            val type = object : TypeToken<List<SponsorBlockSegment>>() {}.type
            Gson().fromJson<List<SponsorBlockSegment>>(json, type)
        } catch (e: Exception) {
            Log.w("VideoPlayerViewModel", "Failed to deserialize SponsorBlock segments", e)
            null
        }
    }

    private fun extractAvailableQualities(streamInfo: StreamInfo): List<VideoQuality> {
        val videoStreams = (streamInfo.videoStreams + streamInfo.videoOnlyStreams).filterIsInstance<VideoStream>()
        return extractAvailableQualitiesFromStreams(videoStreams)
    }

    private fun extractAvailableQualitiesFromStreams(videoStreams: List<VideoStream>): List<VideoQuality> {
        val heights = videoStreams
            .map { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) }
            .distinct()
            .sorted()

        return heights.map { height ->
            VideoQuality.fromHeight(height)
        }.distinct() + listOf(VideoQuality.AUTO)
    }

    private fun qualityHeightFromFormat(qualityLabel: String?, fallbackHeight: Int): Int {
        val labelHeight = qualityLabel
            ?.let { Regex("""(\d+)p""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        return QualityManager.normalizeQualityHeight(labelHeight ?: fallbackHeight)
    }
    
    private fun extractSubtitles(streamInfo: StreamInfo): List<SubtitleInfo> {
        return streamInfo.subtitles.map { subtitle ->
            SubtitleInfo(
                url = subtitle.url ?: "",
                format = subtitle.format?.mimeType ?: "text/vtt",
                language = subtitle.displayLanguageName ?: subtitle.languageTag,
                languageCode = subtitle.languageTag,
                isAutoGenerated = subtitle.isAutoGenerated
            )
        }
    }

    fun toggleWatchLater(video: Video) {
        viewModelScope.launch {
            if (playlistRepository.isInWatchLater(video.id)) {
                playlistRepository.removeFromWatchLater(video.id)
            } else {
                playlistRepository.addToWatchLater(video)
            }
        }
    }
    
    fun addToWatchLater(video: Video) {
        viewModelScope.launch {
            playlistRepository.addToWatchLater(video)
        }
    }

    fun toggleSkipSilence(isEnabled: Boolean) {
        EnhancedPlayerManager.getInstance().toggleSkipSilence(isEnabled)
    }

    fun toggleStableVolume(isEnabled: Boolean) {
        EnhancedPlayerManager.getInstance().toggleStableVolume(isEnabled)
    }
    private suspend fun fetchReturnYouTubeDislike(videoId: String): Long? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = java.net.URL("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(response)
                json.getLong("dislikes")
            } else {
                null
            }
        } catch (e: Exception) {
            // Log.e("VideoPlayerViewModel", "Failed to fetch dislikes", e)
            null
        }
    }
}


data class VideoPlayerUiState(
    val cachedVideo: Video? = null,
    val streamInfo: StreamInfo? = null,
    val relatedVideos: List<Video> = emptyList(),
    val videoStream: VideoStream? = null,
    val audioStream: AudioStream? = null,
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality = VideoQuality.AUTO,
    val subtitles: List<SubtitleInfo> = emptyList(),
    val selectedSubtitle: SubtitleInfo? = null,
    val subtitlesEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Optional secondary hint shown below the primary error in the player's error panel. */
    val errorHint: String? = null,
    val savedPosition: kotlinx.coroutines.flow.Flow<Long>? = null,
    val isAdaptiveMode: Boolean = false,
    val isMiniPlayer: Boolean = false,
    val isFullscreen: Boolean = false,
    val isSubscribed: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val likeState: String? = null, 
    val channelSubscriberCount: Long? = null,
    val channelAvatarUrl: String? = null,
    val chapters: List<StreamSegment> = emptyList(),
    val autoplayEnabled: Boolean = true,
    val streamSizes: Map<String, Long> = emptyMap(),
    val localFilePath: String? = null,
    val localFileVideoId: String? = null,
    val metadataError: String? = null,
    val dislikeCount: Long? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueTitle: String? = null,
    val hlsUrl: String? = null,
    val shouldDismissPlayer: Boolean = false,
    val isRestoredSession: Boolean = false,
    val resumedInMiniPlayer: Boolean = false,
    val isUpcoming: Boolean = false,
    val upcomingReleaseTimeMs: Long? = null,
    val isUpcomingReminderSet: Boolean = false,
    /** SponsorBlock segments loaded from local DB for offline playback. Null when streaming online. */
    val offlineSponsorBlockSegments: List<SponsorBlockSegment>? = null,
    val innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
    val innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format> = emptyList()
)

data class SubtitleInfo(
    val url: String,
    val format: String,
    val language: String,
    val languageCode: String,
    val isAutoGenerated: Boolean
)
