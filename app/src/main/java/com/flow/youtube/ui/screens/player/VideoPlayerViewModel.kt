package com.flow.youtube.ui.screens.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.*
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.*

class VideoPlayerViewModel(
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()
    
    private var viewHistory: ViewHistory? = null
    private var subscriptionRepository: SubscriptionRepository? = null
    private var likedVideosRepository: LikedVideosRepository? = null
    
    fun initialize(context: Context) {
        viewHistory = ViewHistory.getInstance(context)
        subscriptionRepository = SubscriptionRepository.getInstance(context)
        likedVideosRepository = LikedVideosRepository.getInstance(context)
    }
    
    fun initializeViewHistory(context: Context) {
        viewHistory = ViewHistory.getInstance(context)
        subscriptionRepository = SubscriptionRepository.getInstance(context)
        likedVideosRepository = LikedVideosRepository.getInstance(context)
    }
    
    fun loadVideoInfo(videoId: String, preferredQuality: VideoQuality = VideoQuality.AUTO) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val streamInfo = repository.getVideoStreamInfo(videoId)
                
                if (streamInfo != null) {
                    val relatedVideos = repository.getRelatedVideos(videoId)
                    val availableQualities = extractAvailableQualities(streamInfo)
                    
                    // For AUTO mode, start with 360p for fast loading, will scale up dynamically
                    val initialQuality = if (preferredQuality == VideoQuality.AUTO) {
                        VideoQuality.Q_360p
                    } else {
                        preferredQuality
                    }
                    
                    val selectedStreams = selectStreams(streamInfo, initialQuality)
                    val subtitles = extractSubtitles(streamInfo)
                    
                    // Load saved playback position
                    val savedPosition = viewHistory?.getPlaybackPosition(videoId)
                    
                    _uiState.value = _uiState.value.copy(
                        streamInfo = streamInfo,
                        relatedVideos = relatedVideos,
                        videoStream = selectedStreams.first,
                        audioStream = selectedStreams.second,
                        availableQualities = availableQualities,
                        selectedQuality = selectedStreams.third,
                        subtitles = subtitles,
                        isLoading = false,
                        savedPosition = savedPosition,
                        isAdaptiveMode = preferredQuality == VideoQuality.AUTO
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load video"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An error occurred"
                )
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
    
    fun scaleUpQuality() {
        // Called when buffer is healthy - scale up to next quality level
        if (!_uiState.value.isAdaptiveMode) return
        
        val currentQuality = _uiState.value.selectedQuality
        val availableQualities = _uiState.value.availableQualities
            .filter { it != VideoQuality.AUTO }
            .sortedBy { it.height }
        
        val currentIndex = availableQualities.indexOf(currentQuality)
        if (currentIndex != -1 && currentIndex < availableQualities.size - 1) {
            val nextQuality = availableQualities[currentIndex + 1]
            switchQuality(nextQuality)
        }
    }
    
    fun scaleDownQuality() {
        // Called when buffering - scale down to lower quality
        if (!_uiState.value.isAdaptiveMode) return
        
        val currentQuality = _uiState.value.selectedQuality
        val availableQualities = _uiState.value.availableQualities
            .filter { it != VideoQuality.AUTO }
            .sortedBy { it.height }
        
        val currentIndex = availableQualities.indexOf(currentQuality)
        if (currentIndex > 0) {
            val lowerQuality = availableQualities[currentIndex - 1]
            switchQuality(lowerQuality)
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
            viewHistory?.savePlaybackPosition(
                videoId = videoId,
                position = position,
                duration = duration,
                title = title,
                thumbnailUrl = thumbnailUrl,
                channelName = channelName,
                channelId = channelId
            )
        }
    }
    
    fun toggleSubscription(channelId: String, channelName: String, channelThumbnail: String) {
        viewModelScope.launch {
            val isSubscribed = subscriptionRepository?.isSubscribed(channelId)?.first() ?: false
            if (isSubscribed) {
                subscriptionRepository?.unsubscribe(channelId)
                _uiState.value = _uiState.value.copy(isSubscribed = false)
            } else {
                subscriptionRepository?.subscribe(
                    ChannelSubscription(
                        channelId = channelId,
                        channelName = channelName,
                        channelThumbnail = channelThumbnail
                    )
                )
                _uiState.value = _uiState.value.copy(isSubscribed = true)
            }
        }
    }
    
    fun likeVideo(videoId: String, title: String, thumbnail: String, channelName: String) {
        viewModelScope.launch {
            likedVideosRepository?.likeVideo(
                LikedVideoInfo(
                    videoId = videoId,
                    title = title,
                    thumbnail = thumbnail,
                    channelName = channelName
                )
            )
            _uiState.value = _uiState.value.copy(likeState = "LIKED")
        }
    }
    
    fun dislikeVideo(videoId: String) {
        viewModelScope.launch {
            likedVideosRepository?.dislikeVideo(videoId)
            _uiState.value = _uiState.value.copy(likeState = "DISLIKED")
        }
    }
    
    fun removeLikeState(videoId: String) {
        viewModelScope.launch {
            likedVideosRepository?.removeLikeState(videoId)
            _uiState.value = _uiState.value.copy(likeState = null)
        }
    }
    
    fun loadSubscriptionAndLikeState(channelId: String, videoId: String) {
        viewModelScope.launch {
            subscriptionRepository?.isSubscribed(channelId)?.collect { isSubscribed ->
                _uiState.value = _uiState.value.copy(isSubscribed = isSubscribed)
            }
        }
        viewModelScope.launch {
            likedVideosRepository?.getLikeState(videoId)?.collect { likeState ->
                _uiState.value = _uiState.value.copy(likeState = likeState)
            }
        }
    }
    
    fun toggleSubtitles(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(subtitlesEnabled = enabled)
    }
    
    fun selectSubtitleTrack(subtitle: SubtitleInfo) {
        _uiState.value = _uiState.value.copy(selectedSubtitle = subtitle)
    }
    
    fun setMiniPlayerMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isMiniPlayer = enabled)
    }
    
    fun setFullscreen(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = enabled)
    }
    
    private fun selectStreams(
        streamInfo: StreamInfo,
        preferredQuality: VideoQuality
    ): Triple<VideoStream?, AudioStream?, VideoQuality> {
        // Get best audio stream
        val audioStream = streamInfo.audioStreams
            .maxByOrNull { it.bitrate }
        
        // Get video streams with audio if available, otherwise video-only
        val videoStreamsWithAudio = streamInfo.videoStreams
        val videoOnlyStreams = streamInfo.videoOnlyStreams
        
        val allVideoStreams = (videoStreamsWithAudio + videoOnlyStreams)
            .filterIsInstance<VideoStream>()
            .filter { 
                val mime = it.format?.mimeType
                mime?.contains("mp4") == true || mime?.contains("webm") == true
            }
        
        // Select based on quality preference
        val videoStream = when (preferredQuality) {
            VideoQuality.AUTO -> {
                // Select best quality available
                allVideoStreams.maxByOrNull { it.height }
            }
            else -> {
                // Find closest match to preferred quality
                allVideoStreams
                    .sortedBy { kotlin.math.abs(it.height - preferredQuality.height) }
                    .firstOrNull()
            }
        }
        
        val actualQuality = videoStream?.let { VideoQuality.fromHeight(it.height) } ?: VideoQuality.Q_720p
        
        return Triple(videoStream, audioStream, actualQuality)
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
    val likeState: String? = null // LIKED, DISLIKED, or null
)

data class SubtitleInfo(
    val url: String,
    val format: String,
    val language: String,
    val languageCode: String,
    val isAutoGenerated: Boolean
)
