package com.flow.youtube.ui.screens.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.*
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.InterestProfile
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.player.EnhancedPlayerManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.*

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val viewHistory: ViewHistory,
    private val subscriptionRepository: SubscriptionRepository,
    private val likedVideosRepository: LikedVideosRepository,
    private val interestProfile: InterestProfile
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context) {
        // Handled by Hilt
    }
    
    fun initializeViewHistory(context: Context) {
        // Handled by Hilt
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
                        // channelSubscriberCount will be filled below if available
                        savedPosition = savedPosition,
                        isAdaptiveMode = preferredQuality == VideoQuality.AUTO
                    )

                    // Fetch channel info (subscriber count + avatar) asynchronously
                    try {
                        val channelUrl = streamInfo.uploaderUrl ?: ""
                        if (channelUrl.isNotBlank()) {
                            val channelInfo = repository.getChannelInfo(channelUrl)
                            channelInfo?.let { ci ->
                                // Get subscriber count (numeric)
                                val subCount = try {
                                    val method = ci::class.java.methods.firstOrNull { it.name.equals("getSubscriberCount", true) }
                                    if (method != null) {
                                        (method.invoke(ci) as? Long) ?: 0L
                                    } else {
                                        // Some implementations may expose subscriberCount as a string field
                                        val textMethod = ci::class.java.methods.firstOrNull { it.name.equals("getSubscriberCountText", true) || it.name.equals("getSubscriberCountString", true) }
                                        val textVal = textMethod?.invoke(ci) as? String
                                        textVal?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
                                    }
                                } catch (ex: Exception) {
                                    0L
                                }
                                
                                // Get avatar URL from thumbnails
                                val avatarUrl = try {
                                    // NewPipe's ChannelInfo exposes getThumbnails() or getAvatars()
                                    val thumbnailsMethod = ci::class.java.methods.firstOrNull { 
                                        it.name.equals("getThumbnails", true) || it.name.equals("getAvatars", true)
                                    }
                                    val thumbnails = thumbnailsMethod?.invoke(ci) as? List<*>
                                    
                                    // Extract URL from Image objects
                                    thumbnails?.firstOrNull()?.let { img ->
                                        val urlMethod = img::class.java.methods.firstOrNull { it.name.equals("getUrl", true) }
                                        urlMethod?.invoke(img) as? String
                                    } ?: ""
                                } catch (ex: Exception) {
                                    Log.w("VideoPlayerViewModel", "Could not extract avatar URL", ex)
                                    ""
                                }

                                _uiState.value = _uiState.value.copy(
                                    channelSubscriberCount = subCount,
                                    channelAvatarUrl = avatarUrl
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("VideoPlayerViewModel", "Could not fetch channel info", e)
                        // ignore best-effort
                    }
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
            
            // Update interest profile to learn from watch behavior
            if (duration > 0) {
                interestProfile?.recordWatch(
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
                
                // Learn from subscription - strong signal
                interestProfile?.recordSubscription(channelId, channelName)
            }
        }
    }
    
    fun likeVideo(videoId: String, title: String, thumbnail: String, channelName: String, channelId: String = "") {
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
            
            // Learn from like - strong positive signal
            interestProfile?.recordLike(title, channelId, channelName)
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
        // Find index in the stream's subtitles and instruct the player manager
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
    
    private fun selectStreams(
        streamInfo: StreamInfo,
        preferredQuality: VideoQuality
    ): Triple<VideoStream?, AudioStream?, VideoQuality> {
        // Dedupe audio streams by url and prefer highest bitrate
        val audioCandidates = streamInfo.audioStreams
            .distinctBy { it.url ?: "" }
            .sortedByDescending { it.bitrate }

        // Preferred audio: try to match the stream's original language (fallback to English, then highest bitrate)
        val originalLang = streamInfo.uploaderName?.let { null } // extractor may not provide language; keep null for now

        val audioStream = audioCandidates.firstOrNull { a ->
            // prefer explicit locale matches
            val lang = a.audioLocale?.language ?: ""
            if (!originalLang.isNullOrBlank() && !lang.isNullOrBlank()) {
                lang.equals(originalLang, true)
            } else false
        } ?: audioCandidates.firstOrNull { a ->
            // prefer english if present
            val lang = a.audioLocale?.language ?: ""
            lang?.startsWith("en", true) == true
        } ?: audioCandidates.firstOrNull()
        
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

        // Make manager aware of available streams (so EnhancedPlayerManager can prefer adaptive formats)
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
    val likeState: String? = null, // LIKED, DISLIKED, or null
    val channelSubscriberCount: Long? = null,
    val channelAvatarUrl: String? = null
)




data class SubtitleInfo(
    val url: String,
    val format: String,
    val language: String,
    val languageCode: String,
    val isAutoGenerated: Boolean
)
