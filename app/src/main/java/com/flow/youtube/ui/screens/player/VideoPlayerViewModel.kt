package com.flow.youtube.ui.screens.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.*
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.InterestProfile
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.recommendation.FlowNeuroEngine.InteractionType
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.YouTubeClient
import com.flow.youtube.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.*
import com.flow.youtube.data.video.VideoDownloadManager
import com.flow.youtube.data.video.DownloadedVideo
import com.flow.youtube.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay


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
    private val playlistRepository: com.flow.youtube.data.local.PlaylistRepository,
    private val interestProfile: InterestProfile,
    private val playerPreferences: PlayerPreferences,
    private val videoDownloadManager: VideoDownloadManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()
    
    private val _commentsState = MutableStateFlow<List<com.flow.youtube.data.model.Comment>>(emptyList())
    val commentsState: StateFlow<List<com.flow.youtube.data.model.Comment>> = _commentsState.asStateFlow()
    
    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()
    
    private val navigationHistory = mutableListOf<String>()
    private var currentHistoryIndex = -1
    
    private val _canGoPrevious = MutableStateFlow(false)
    val canGoPrevious: StateFlow<Boolean> = _canGoPrevious.asStateFlow()
    
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
                    if (videoId != _uiState.value.streamInfo?.id && 
                        videoId != _uiState.value.cachedVideo?.id &&
                        !_uiState.value.isLoading) {
                         loadVideoInfo(videoId, isWifi = detectIsWifi())
                    }
                }
            }
        }
    }
    
    fun initializeViewHistory(context: Context) {
        // Handled by Hilt
    }
    
    /**
     * Plays a video by immediately caching metadata and triggering stream load.
     * This ensures the UI shows video info immediately while streams are fetched.
     */
    fun playVideo(video: Video) {
        // Stop current playback and clear everything (including any active queue)
        EnhancedPlayerManager.getInstance().pause()
        EnhancedPlayerManager.getInstance().clearAll()
        
        GlobalPlayerState.setCurrentVideo(video)
        
        // Ensure music player is stopped and hidden
        EnhancedMusicPlayerManager.stop()
        EnhancedMusicPlayerManager.clearCurrentTrack()
        
        // Cache video metadata for immediate UI display
        _uiState.value = _uiState.value.copy(
            cachedVideo = video,
            isLoading = true,
            error = null,
            metadataError = null,
            streamInfo = null,
            videoStream = null,
            audioStream = null,
            relatedVideos = emptyList(),
            isSubscribed = false,
            likeState = null
        )
        saveHistoryEntry(video)
        // Start loading streams
        loadVideoInfo(video.id, isWifi = detectIsWifi(), forceRefresh = true)
    }

    /**
     * Clears all video player state, stops playback and resets UI.
     * This should be called when the video player is dismissed.
     */
    fun clearVideo() {
        EnhancedPlayerManager.getInstance().stop()
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
                metadataError = null,
                streamInfo = null,
                videoStream = null,
                audioStream = null,
                relatedVideos = emptyList(),
                isSubscribed = false,
                likeState = null,
                queueTitle = title
            )
        }
        saveHistoryEntry(startVideo)
        // Start loading the first video
        loadVideoInfo(startVideo.id, isWifi = detectIsWifi(), forceRefresh = true)
    }

    fun playNext() {
        EnhancedPlayerManager.getInstance().playNext()
    }

    fun playPrevious() {
        EnhancedPlayerManager.getInstance().playPrevious()
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
     */
    fun loadVideoInfo(videoId: String, isWifi: Boolean = true, forceRefresh: Boolean = false) {
        val currentState = _uiState.value
        Log.d("VideoPlayerViewModel", "loadVideoInfo: Request=$videoId. Current=${currentState.streamInfo?.id}, IsLoading=${currentState.isLoading}, ForceRefresh=$forceRefresh")

        // Don't reload if already loaded the same video successfully (unless forceRefresh)
        if (!forceRefresh && currentState.streamInfo?.id == videoId && !currentState.isLoading && currentState.error == null) {
            Log.d("VideoPlayerViewModel", "Video $videoId already loaded successfully. Skipping.")
            return
        }
        
        // If loading the same video and not forcing refresh, skip
        if (!forceRefresh && currentState.isLoading && currentState.streamInfo?.id == videoId) {
             Log.d("VideoPlayerViewModel", "Video $videoId is currently loading. Skipping redundant request.")
             return
        }

        // Track history
        if (navigationHistory.isEmpty() || navigationHistory[currentHistoryIndex] != videoId) {
            // If we are not at the end of history, clear the forward history
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
            metadataError = null,
            streamInfo = null,
            videoStream = null,
            audioStream = null,
            relatedVideos = emptyList(),
            dislikeCount = null,
            // Also reset subscription and like state for new video
            isSubscribed = false,
            likeState = null,
            hlsUrl = null
        )
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            Log.d("VideoPlayerViewModel", "Starting loadVideoInfo for $videoId")
            var isOfflineAvailable = false
            
            try {
                // PARALLEL FETCH: Fetch dislikes, preferences, and downloaded status simultaneously
                val parallelData = supervisorScope {
                    val dislikesDeferred = async(PerformanceDispatcher.networkIO) {
                        withTimeoutOrNull(5000L) { fetchReturnYouTubeDislike(videoId) }
                    }
                    
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
                    
                    Triple(
                        dislikesDeferred.await(),
                        prefsDeferred.await(),
                        downloadedDeferred.await()
                    )
                }
                
                val (dislikeCount, qualityAndAudioPrefs, downloadedVideo) = parallelData
                val preferredQuality = qualityAndAudioPrefs.first
                val preferredAudioLanguage = qualityAndAudioPrefs.second
                
                // Update dislikes immediately
                dislikeCount?.let { 
                    _uiState.value = _uiState.value.copy(dislikeCount = it)
                }

                // Check for offline video immediately
                val localFile = if (downloadedVideo != null) java.io.File(downloadedVideo.filePath) else null
                isOfflineAvailable = localFile?.exists() == true
                
                if (isOfflineAvailable) {
                    Log.d("VideoPlayerViewModel", "Found offline video at ${localFile?.absolutePath}")
                    _uiState.update { 
                        it.copy(
                            localFilePath = localFile?.absolutePath,
                            error = null,
                            // Don't set isLoading=false yet if we want to try fetching metadata/comments
                            // But usually users want instant playback.
                            // Let's set it false so player starts, metadata can load in background
                            isLoading = false
                        )
                    }
                }

                kotlinx.coroutines.withTimeout(30_000) {
                    Log.d("VideoPlayerViewModel", "Loading video $videoId with preferred quality: ${preferredQuality.label} (isWifi=$isWifi)")

                    // OPTIMIZED: Fetch stream info with retry and timeout
                    // Run logic even if offline video exists, to get fresh metadata/comments and related videos
                    val streamInfoDeferred = async(PerformanceDispatcher.networkIO) { 
                        var info: StreamInfo? = null
                        var attempt = 0
                        val maxAttempts = if (isOfflineAvailable) 1 else 3 // Don't retry much if we have offline video
                        while (info == null && attempt < maxAttempts) {
                            try {
                                attempt++
                                info = withTimeoutOrNull(10_000L) {
                                    repository.getVideoStreamInfo(videoId)
                                }
                                
                                if (info == null && attempt < maxAttempts) {
                                    Log.w("VideoPlayerViewModel", "Stream info fetch failed (attempt $attempt), retrying in ${attempt * 300}ms...")
                                    delay(attempt * 300L) // Faster backoff: 300ms, 600ms
                                }
                            } catch (e: Exception) {
                                Log.e("VideoPlayerViewModel", "Failed to load stream info (attempt $attempt)", e)
                                if (attempt < maxAttempts) {
                                    delay(attempt * 300L)
                                }
                            }
                        }
                        if (info == null) {
                            Log.e("VideoPlayerViewModel", "Stream info fetch failed after $maxAttempts attempts")
                        }
                        info
                    }
                    
                    val streamInfo = streamInfoDeferred.await()
                    
                    // Extract related videos directly from the stream info (avoids extra network call)
                    val relatedVideos = if (streamInfo != null) {
                        repository.getRelatedVideosFromStreamInfo(streamInfo)
                    } else {
                        // Fallback: try separate fetch if stream info failed but we might want related? 
                        // Unlikely to work if main fetch failed, but consistent with safe defaults.
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
                                description = streamInfo.description?.content ?: ""
                            )
                            FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.CLICK)
                        } catch (e: Exception) {
                            Log.e("VideoPlayerViewModel", "Failed to record interaction", e)
                        }

                        val availableQualities = extractAvailableQualities(streamInfo)
                        
                        // For AUTO mode, let DASH's adaptive track selection handle quality
                        // Don't force a specific quality - DASH will start low and scale up automatically
                        val initialQuality = preferredQuality
                        
                        val selectedStreams = selectStreams(streamInfo, initialQuality, preferredAudioLanguage)
                        var localFilePath: String? = null
                        
                        // If downloaded, override with local path
                        if (downloadedVideo != null && (java.io.File(downloadedVideo.filePath).exists())) {
                            localFilePath = downloadedVideo.filePath
                        }

                        val subtitles = extractSubtitles(streamInfo)
                        val chapters = streamInfo.streamSegments ?: emptyList()
                        
                        // Load saved playback position
                        val savedPosition = viewHistory.getPlaybackPosition(videoId)
                        
                        // Load autoplay preference
                        val autoplay = playerPreferences.autoplayEnabled.first()
                        
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
                            hlsUrl = streamInfo.hlsUrl
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
                                                    val subCount = try {
                                                        val method = ci::class.java.methods.firstOrNull { it.name.equals("getSubscriberCount", true) }
                                                        (method?.invoke(ci) as? Long) ?: 0L
                                                    } catch (ex: Exception) { 0L }
                                                    
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

                                // Fetch stream sizes
                                val sizesDeferred = async(PerformanceDispatcher.networkIO) {
                                    withTimeoutOrNull(8000L) {
                                        try {
                                            val playerResult = YouTube.player(videoId, client = YouTubeClient.MOBILE)
                                            playerResult.getOrNull()?.let { playerResponse ->
                                                val sizes = mutableMapOf<String, Long>()

                                                val audioFormats = playerResponse.streamingData
                                                    ?.adaptiveFormats?.filter { it.isAudio } ?: emptyList()
                                                val bestAacSize = audioFormats
                                                    .filter { it.mimeType.contains("mp4", ignoreCase = true) }
                                                    .maxByOrNull { it.bitrate }?.contentLength ?: 0L
                                                val bestOpusSize = audioFormats
                                                    .filter { it.mimeType.contains("webm", ignoreCase = true) }
                                                    .maxByOrNull { it.bitrate }?.contentLength ?: 0L
                                                val bestAnyAudioSize = audioFormats
                                                    .maxByOrNull { it.bitrate }?.contentLength ?: 0L

                                                playerResponse.streamingData?.formats?.forEach { format ->
                                                    if (format.height != null && format.contentLength != null) {
                                                        val codecKey = VideoPlayerUtils.codecKeyFromMimeType(format.mimeType)
                                                        val key = VideoPlayerUtils.streamSizeKey(format.height, codecKey)
                                                        sizes[key] = format.contentLength
                                                    }
                                                }
                                                playerResponse.streamingData?.adaptiveFormats?.forEach { format ->
                                                    if (format.height != null && format.contentLength != null && !format.isAudio) {
                                                        val codecKey = VideoPlayerUtils.codecKeyFromMimeType(format.mimeType)
                                                        val isMp4Video = format.mimeType.contains("mp4", ignoreCase = true)
                                                        val audioSize = when {
                                                            isMp4Video && bestAacSize > 0 -> bestAacSize
                                                            !isMp4Video && bestOpusSize > 0 -> bestOpusSize
                                                            else -> bestAnyAudioSize
                                                        }
                                                        val totalSize = format.contentLength + audioSize
                                                        val key = VideoPlayerUtils.streamSizeKey(format.height, codecKey)
                                                        val currentSize = sizes[key] ?: 0L
                                                        if (totalSize > currentSize) sizes[key] = totalSize
                                                    }
                                                }
                                                sizes
                                            }
                                        } catch (e: Exception) {
                                            Log.e("VideoPlayerViewModel", "Failed to fetch stream sizes", e)
                                            null
                                        }
                                    }
                                }
                                
                                // Await both and update UI
                                val channelResult = channelDeferred.await()
                                val sizesResult = sizesDeferred.await()
                                
                                channelResult?.let { (subCount, avatarUrl) ->
                                    _uiState.value = _uiState.value.copy(
                                        channelSubscriberCount = subCount,
                                        channelAvatarUrl = avatarUrl
                                    )
                                }
                                
                                sizesResult?.let { sizes ->
                                    _uiState.value = _uiState.value.copy(streamSizes = sizes)
                                }
                            }
                        }
                    } else {
                        // Offline fallback
                        if (isOfflineAvailable) {
                            Log.d("VideoPlayerViewModel", "Using offline video for $videoId (Network fetch failed)")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = null,
                                    relatedVideos = relatedVideos,
                                    localFilePath = localFile?.absolutePath
                                )
                            }
                        } else {
                            Log.e("VideoPlayerViewModel", "Stream info is null for $videoId and no offline copy found.")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    relatedVideos = relatedVideos,
                                    error = "Failed to load video"
                                )
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("VideoPlayerViewModel", "Video info load timed out for $videoId after 30s")
                if (isOfflineAvailable) {
                     Log.d("VideoPlayerViewModel", "Ignoring timeout, playing offline video")
                     _uiState.update { it.copy(isLoading = false, error = null) }
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Load timed out. Please check your connection."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Exception loading video $videoId", e)
                
                if (isOfflineAvailable) {
                     Log.d("VideoPlayerViewModel", "Ignoring exception, playing offline video")
                     _uiState.update { it.copy(isLoading = false, error = null) }
                } else {
                    // Final fallback if everything fails
                    val downloadedVideo = videoDownloadManager.downloadedVideos.map { list -> 
                        list.find { it.video.id == videoId } 
                    }.first()

                    if (downloadedVideo != null && java.io.File(downloadedVideo.filePath).exists()) {
                        _uiState.update { 
                            it.copy(
                                streamInfo = null,
                                isLoading = false,
                                error = null,
                                localFilePath = downloadedVideo.filePath
                            )
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "An error occurred"
                            )
                        }
                    }
                }
            }
        }
    }
    
    fun switchQuality(quality: VideoQuality) {
        val streamInfo = _uiState.value.streamInfo ?: return
        // Read the user's audio language preference to preserve it during quality switches
        viewModelScope.launch {
            val audioLangPref = playerPreferences.preferredAudioLanguage.first()
            val streams = selectStreams(streamInfo, quality, audioLangPref)
            
            _uiState.value = _uiState.value.copy(
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
            viewHistory.savePlaybackPosition(
                videoId = video.id,
                position = 0L,
                duration = if (video.duration > 0) video.duration * 1000L else 0L,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg",
                channelName = video.channelName,
                channelId = video.channelId
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
            playerPreferences.setAutoplayEnabled(enabled)
            _uiState.value = _uiState.value.copy(autoplayEnabled = enabled)
        }
    }

    fun toggleLoop(enabled: Boolean) {
        EnhancedPlayerManager.getInstance().toggleLoop(enabled)
    }

    fun loadComments(videoId: String) {
        viewModelScope.launch {
            _isLoadingComments.value = true
            _commentsState.value = emptyList()
            try {
                val comments = repository.getComments(videoId)
                _commentsState.value = comments
                if (comments.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        commentCountText = "${comments.size}+"
                    )
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Error loading comments", e)
            } finally {
                _isLoadingComments.value = false
            }
        }
    }

    fun loadCommentReplies(comment: com.flow.youtube.data.model.Comment) {
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
    
    private fun selectStreams(
        streamInfo: StreamInfo,
        preferredQuality: VideoQuality,
        preferredAudioLanguage: String = "original"
    ): Triple<VideoStream?, AudioStream?, VideoQuality> {
        val audioCandidates = streamInfo.audioStreams
            .distinctBy { it.url ?: "" }
            .sortedByDescending { it.bitrate }
        
        // Select audio based on preference:
        val audioStream = when (preferredAudioLanguage) {
            "original" -> {
                // First, try to find the ORIGINAL track type (native language)
                audioCandidates.firstOrNull { stream ->
                    stream.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
                }
                // If no ORIGINAL track found, find non-DUBBED track (likely original)
                ?: audioCandidates.firstOrNull { stream ->
                    stream.audioTrackType != org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED
                }
                // Fallback to first available
                ?: audioCandidates.firstOrNull()
            }
            else -> {
                // User prefers a specific language
                audioCandidates.firstOrNull { a ->
                    val lang = a.audioLocale?.language ?: ""
                    lang.startsWith(preferredAudioLanguage, true)
                }
                // If preferred language not available, try ORIGINAL track
                ?: audioCandidates.firstOrNull { stream ->
                    stream.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
                }
                // Fallback to first available
                ?: audioCandidates.firstOrNull()
            }
        }
        
        val allVideoStreams = (streamInfo.videoStreams + streamInfo.videoOnlyStreams)
            .filterIsInstance<VideoStream>()
            .filter { 
                val mime = it.format?.mimeType
                mime?.contains("mp4") == true || mime?.contains("webm") == true
            }
        
        val videoStream = when (preferredQuality) {
            VideoQuality.AUTO -> allVideoStreams.maxByOrNull { it.height }
            else -> allVideoStreams
                .sortedBy { kotlin.math.abs(it.height - preferredQuality.height) }
                .firstOrNull()
        }
        
        val actualQuality = videoStream?.let { VideoQuality.fromHeight(it.height) } ?: VideoQuality.Q_720p
        val safeAudio = audioStream ?: streamInfo.audioStreams.firstOrNull()

        return Triple(videoStream, safeAudio, actualQuality)
    }
    
    private fun extractAvailableQualities(streamInfo: StreamInfo): List<VideoQuality> {
        val heights = (streamInfo.videoStreams + streamInfo.videoOnlyStreams)
            .filterIsInstance<VideoStream>()
            .map { it.height }
            .distinct()
            .sorted()
        
        return heights.mapNotNull { height ->
            VideoQuality.values().find { it.height == height }
        } + VideoQuality.AUTO
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
    val savedPosition: kotlinx.coroutines.flow.Flow<Long>? = null,
    val isAdaptiveMode: Boolean = false,
    val isMiniPlayer: Boolean = false,
    val isFullscreen: Boolean = false,
    val isSubscribed: Boolean = false,
    val likeState: String? = null, 
    val channelSubscriberCount: Long? = null,
    val channelAvatarUrl: String? = null,
    val chapters: List<StreamSegment> = emptyList(),
    val autoplayEnabled: Boolean = true,
    val commentCountText: String = "0",
    val streamSizes: Map<String, Long> = emptyMap(),
    val localFilePath: String? = null,
    val metadataError: String? = null,
    val dislikeCount: Long? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueTitle: String? = null,
    val hlsUrl: String? = null
)

data class SubtitleInfo(
    val url: String,
    val format: String,
    val language: String,
    val languageCode: String,
    val isAutoGenerated: Boolean
)
