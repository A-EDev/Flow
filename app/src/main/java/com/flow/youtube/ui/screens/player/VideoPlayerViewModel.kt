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
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.YouTubeClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import org.schabi.newpipe.extractor.stream.*
import com.flow.youtube.data.video.VideoDownloadManager
import com.flow.youtube.data.video.DownloadedVideo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

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
    
    fun initializeViewHistory(context: Context) {
        // Handled by Hilt
    }
    
    fun loadVideoInfo(videoId: String, isWifi: Boolean = true) {
        val currentState = _uiState.value
        Log.d("VideoPlayerViewModel", "loadVideoInfo: Request=$videoId. Current=${currentState.streamInfo?.id}, IsLoading=${currentState.isLoading}")

        // Don't reload if already loaded the same video successfully
        if (currentState.streamInfo?.id == videoId && !currentState.isLoading && currentState.error == null) {
            Log.d("VideoPlayerViewModel", "Video $videoId already loaded successfully. Skipping.")
            return
        }
        
        // If loading the same video, skip. If loading a different video, proceed (and effectively restart/override)
        if (currentState.isLoading && currentState.streamInfo?.id == videoId) {
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
            relatedVideos = emptyList()
        )
        
        viewModelScope.launch {
            Log.d("VideoPlayerViewModel", "Starting loadVideoInfo for $videoId")
            try {
                kotlinx.coroutines.withTimeout(30_000) {
                    // Determine preferred quality from preferences
                    val preferredQuality = if (isWifi) {
                        playerPreferences.defaultQualityWifi.first()
                    } else {
                        playerPreferences.defaultQualityCellular.first()
                    }
                    
                    Log.d("VideoPlayerViewModel", "Loading video $videoId with preferred quality: ${preferredQuality.label} (isWifi=$isWifi)")
                    
                    // Check if video is downloaded
                    val downloadedVideo = videoDownloadManager.downloadedVideos.map { list -> 
                        list.find { it.video.id == videoId } 
                    }.first()

                    val streamInfoDeferred = async { 
                        var info: StreamInfo? = null
                        var attempt = 0
                        val maxAttempts = 3
                        while (info == null && attempt < maxAttempts) {
                            try {
                                attempt++
                                info = repository.getVideoStreamInfo(videoId)
                                
                                if (info == null) { 
                                     if (attempt < maxAttempts) {
                                         Log.w("VideoPlayerViewModel", "Stream info fetch failed (attempt $attempt), retrying in ${attempt * 500}ms...")
                                         delay(attempt * 500L) // Exponential backoff: 500ms, 1000ms
                                     }
                                }
                            } catch (e: Exception) {
                                Log.e("VideoPlayerViewModel", "Failed to load stream info (attempt $attempt)", e)
                                if (attempt < maxAttempts) {
                                    delay(attempt * 500L)
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
                        
                        // For AUTO mode, start with 360p for fast loading, will scale up dynamically
                        val initialQuality = if (preferredQuality == VideoQuality.AUTO) {
                            VideoQuality.Q_360p
                        } else {
                            preferredQuality
                        }
                        
                        val selectedStreams = selectStreams(streamInfo, initialQuality)
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
                            localFilePath = localFilePath
                        )

                        // Fetch channel info asynchronously
                        viewModelScope.launch {
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

                                        _uiState.value = _uiState.value.copy(
                                            channelSubscriberCount = subCount,
                                            channelAvatarUrl = avatarUrl
                                        )
                                    }
                                }
                            } catch (e: Exception) { 
                                Log.e("VideoPlayerViewModel", "Failed to fetch channel info", e)
                                _uiState.value = _uiState.value.copy(metadataError = "Channel info failed")
                            }
                        }

                        // Fetch stream sizes
                        viewModelScope.launch {
                            try {
                                val playerResult = YouTube.player(videoId, client = YouTubeClient.MOBILE)
                                playerResult.onSuccess { playerResponse ->
                                    val sizes = mutableMapOf<Int, Long>()
                                    val bestAudioSize = playerResponse.streamingData?.adaptiveFormats
                                        ?.filter { it.isAudio }?.maxByOrNull { it.bitrate }?.contentLength ?: 0L
                                    
                                    playerResponse.streamingData?.formats?.forEach { format ->
                                        if (format.height != null && format.contentLength != null) {
                                            sizes[format.height] = format.contentLength
                                        }
                                    }
                                    playerResponse.streamingData?.adaptiveFormats?.forEach { format ->
                                        if (format.height != null && format.contentLength != null && !format.isAudio) {
                                            val totalSize = format.contentLength + bestAudioSize
                                            val currentSize = sizes[format.height] ?: 0L
                                            if (totalSize > currentSize) sizes[format.height] = totalSize
                                        }
                                    }
                                    _uiState.value = _uiState.value.copy(streamSizes = sizes)
                                }.onFailure { e ->
                                    Log.e("VideoPlayerViewModel", "Failed to fetch stream sizes", e)
                                    _uiState.value = _uiState.value.copy(metadataError = "Resolving stream sizes failed")
                                }
                            } catch (e: Exception) { }
                        }
                    } else {
                        // Offline fallback
                        if (downloadedVideo != null && java.io.File(downloadedVideo.filePath).exists()) {
                            Log.d("VideoPlayerViewModel", "Using offline video for $videoId")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = null,
                                relatedVideos = relatedVideos,
                                localFilePath = downloadedVideo.filePath
                            )
                        } else {
                            Log.e("VideoPlayerViewModel", "Stream info is null for $videoId and no offline copy found.")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                relatedVideos = relatedVideos,
                                error = "Failed to load video"
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("VideoPlayerViewModel", "Video info load timed out for $videoId after 30s")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Load timed out. Please check your connection."
                )
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Exception loading video $videoId", e)
                // Final fallback if everything fails
                val downloadedVideo = videoDownloadManager.downloadedVideos.map { list -> 
                    list.find { it.video.id == videoId } 
                }.first()

                if (downloadedVideo != null && java.io.File(downloadedVideo.filePath).exists()) {
                    _uiState.value = _uiState.value.copy(
                        streamInfo = null,
                        isLoading = false,
                        error = null,
                        localFilePath = downloadedVideo.filePath
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "An error occurred"
                    )
                }
            }
        }
    }
    
    fun switchQuality(quality: VideoQuality) {
        val streamInfo = _uiState.value.streamInfo ?: return
        val streams = selectStreams(streamInfo, quality)
        
        _uiState.value = _uiState.value.copy(
            videoStream = streams.first,
            audioStream = streams.second,
            selectedQuality = streams.third,
            isAdaptiveMode = quality == VideoQuality.AUTO
        )
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
    
    private fun selectStreams(
        streamInfo: StreamInfo,
        preferredQuality: VideoQuality
    ): Triple<VideoStream?, AudioStream?, VideoQuality> {
        val audioCandidates = streamInfo.audioStreams
            .distinctBy { it.url ?: "" }
            .sortedByDescending { it.bitrate }

        val audioStream = audioCandidates.firstOrNull { a ->
            val lang = a.audioLocale?.language ?: ""
            lang.startsWith("en", true)
        } ?: audioCandidates.firstOrNull()
        
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
}

data class VideoPlayerUiState(
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
    val streamSizes: Map<Int, Long> = emptyMap(),
    val localFilePath: String? = null,
    val metadataError: String? = null
)

data class SubtitleInfo(
    val url: String,
    val format: String,
    val language: String,
    val languageCode: String,
    val isAutoGenerated: Boolean
)
