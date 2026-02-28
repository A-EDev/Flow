package com.flow.youtube.ui.components

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.ChannelSubscription
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QuickActionsViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val subscriptionRepository = SubscriptionRepository.getInstance(context)

    val watchLaterIds = playlistRepository.getWatchLaterIdsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** In-memory set of video IDs manually marked as watched this session */
    private val _watchedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val watchedVideoIds = _watchedVideoIds.asStateFlow()

    /** Per-video subscription state cache: channelId -> Boolean */
    private val _subscribedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val subscribedChannelIds = _subscribedChannelIds.asStateFlow()

    fun loadSubscriptionState(channelId: String) {
        viewModelScope.launch {
            subscriptionRepository.isSubscribed(channelId).collect { subscribed ->
                if (subscribed) {
                    _subscribedChannelIds.update { it + channelId }
                } else {
                    _subscribedChannelIds.update { it - channelId }
                }
            }
        }
    }

    fun toggleSubscription(channelId: String, channelName: String, channelThumbnail: String) {
        viewModelScope.launch {
            try {
                val isCurrentlySubscribed = _subscribedChannelIds.value.contains(channelId)
                if (isCurrentlySubscribed) {
                    subscriptionRepository.unsubscribe(channelId)
                    _subscribedChannelIds.update { it - channelId }
                    Toast.makeText(context, "Unsubscribed from $channelName", Toast.LENGTH_SHORT).show()
                } else {
                    subscriptionRepository.subscribe(
                        ChannelSubscription(
                            channelId = channelId,
                            channelName = channelName,
                            channelThumbnail = channelThumbnail,
                            subscribedAt = System.currentTimeMillis()
                        )
                    )
                    _subscribedChannelIds.update { it + channelId }
                    Toast.makeText(context, "Subscribed to $channelName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleWatchLater(video: Video) {
        viewModelScope.launch {
            try {
                android.util.Log.d("QuickActionsViewModel", "Toggling Watch Later for video: ${video.id}")
                val isInWatchLater = playlistRepository.isInWatchLater(video.id)
                android.util.Log.d("QuickActionsViewModel", "Is currently in Watch Later: $isInWatchLater")
                
                if (isInWatchLater) {
                    playlistRepository.removeFromWatchLater(video.id)
                    android.util.Log.d("QuickActionsViewModel", "Removed from Watch Later")
                    Toast.makeText(context, "Removed from Watch Later", Toast.LENGTH_SHORT).show()
                } else {
                    playlistRepository.addToWatchLater(video)
                    android.util.Log.d("QuickActionsViewModel", "Added to Watch Later")
                    Toast.makeText(context, "Added to Watch Later", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("QuickActionsViewModel", "Error toggling Watch Later", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Mark a video as "Not Interested" - this strongly penalizes the video's topics
     * and channel in the FlowNeuroEngine, making similar content much less likely to appear.
     */
    fun markNotInterested(video: Video) {
        viewModelScope.launch {
            try {
                FlowNeuroEngine.markNotInterested(context, video)
                Toast.makeText(
                    context, 
                    "Got it! You'll see less content like this.", 
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Mark a video as "Watched" - signals a positive WATCHED interaction to FlowNeuroEngine,
     * boosting the video's topics and channel in recommendations. Useful for quick-starting
     * the algorithm without replaying the whole video.
     */
    fun markAsWatched(video: Video) {
        viewModelScope.launch {
            try {
                FlowNeuroEngine.onVideoInteraction(
                    context,
                    video,
                    FlowNeuroEngine.InteractionType.WATCHED,
                    percentWatched = 1.0f
                )
                
                val durationMs = if (video.duration > 0) video.duration * 1000L else 1000L
                val thumbnailUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg"
                com.flow.youtube.data.local.ViewHistory.getInstance(context).savePlaybackPosition(
                    videoId = video.id,
                    position = durationMs,
                    duration = durationMs,
                    title = video.title,
                    thumbnailUrl = thumbnailUrl,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    isMusic = false
                )

                _watchedVideoIds.update { it + video.id }
                Toast.makeText(
                    context,
                    context.getString(com.flow.youtube.R.string.mark_as_watched_toast),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Mark a video as "I like this" - signals a positive LIKED interaction to FlowNeuroEngine,
     * boosting the video's topics and channel. Helps users seed the algorithm with content
     * they enjoy without watching the full video in Flow.
     */
    fun markAsInteresting(video: Video) {
        viewModelScope.launch {
            try {
                FlowNeuroEngine.onVideoInteraction(
                    context,
                    video,
                    FlowNeuroEngine.InteractionType.LIKED,
                    percentWatched = 0f
                )
                Toast.makeText(
                    context,
                    context.getString(com.flow.youtube.R.string.i_like_this_toast),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Insert [video] immediately after the current position (Play Next).
     */
    fun playVideoNext(video: Video) {
        com.flow.youtube.player.EnhancedPlayerManager.getInstance().addVideoToQueueNext(video)
    }

    /**
     * Append [video] to the end of the current queue.
     */
    fun addVideoToQueue(video: Video) {
        com.flow.youtube.player.EnhancedPlayerManager.getInstance().addVideoToQueue(video)
    }

    fun downloadVideo(video: Video) {
        viewModelScope.launch {
            try {
                com.flow.youtube.ui.screens.player.util.VideoPlayerUtils.promptStoragePermissionIfNeeded(context)

                Toast.makeText(context, "Fetching download links...", Toast.LENGTH_SHORT).show()
                val streamInfo = withContext(Dispatchers.IO) {
                    repository.getVideoStreamInfo(video.id)
                }

                if (streamInfo != null) {
                    // ── Video-stream selection ────────────────────────────────────────────
                    val combinedStreams = streamInfo.videoStreams
                        ?.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                        ?: emptyList()
                    val videoOnlyStreams = streamInfo.videoOnlyStreams
                        ?.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                        ?: emptyList()

                    fun isMp4Video(s: org.schabi.newpipe.extractor.stream.VideoStream): Boolean {
                        val mime  = (s.format?.mimeType ?: "").lowercase()
                        val fname = (s.format?.name ?: "").lowercase()
                        return mime.contains("mp4") || fname.contains("mpeg") || fname.contains("mp4")
                    }

                    fun isVp9Video(s: org.schabi.newpipe.extractor.stream.VideoStream): Boolean {
                        val mime  = (s.format?.mimeType ?: "").lowercase()
                        val fname = (s.format?.name ?: "").lowercase()
                        return mime.contains("vp9") || mime.contains("vp09") ||
                               fname.contains("vp9") || fname.contains("webm")
                    }

                    val bestMp4VideoOnly  = videoOnlyStreams.filter { isMp4Video(it) }.maxByOrNull { it.height }
                    val bestVp9VideoOnly  = videoOnlyStreams.filter { isVp9Video(it) }.maxByOrNull { it.height }
                    val bestAnyVideoOnly  = videoOnlyStreams.maxByOrNull { it.height }
                    val bestCombined      = combinedStreams.maxByOrNull { it.height }

                    // ── Audio selection (codec-aware) ────────────────────────────────────
                    val allAudio = streamInfo.audioStreams ?: emptyList()

                    fun isAacCompatible(a: org.schabi.newpipe.extractor.stream.AudioStream): Boolean {
                        val mime  = (a.format?.mimeType ?: "").lowercase()
                        val fname = (a.format?.name ?: "").lowercase()
                        if (fname.contains("opus") || fname.contains("vorbis") ||
                            mime.contains("opus") || mime.contains("vorbis") ||
                            fname.contains("webm") || mime.contains("webm")) return false
                        return true
                    }

                    fun isOpusCompatible(a: org.schabi.newpipe.extractor.stream.AudioStream): Boolean {
                        val mime  = (a.format?.mimeType ?: "").lowercase()
                        val fname = (a.format?.name ?: "").lowercase()
                        return fname.contains("webm") || mime.contains("audio/webm") ||
                               fname.contains("opus") || mime.contains("opus")
                    }

                    val selectedStream: org.schabi.newpipe.extractor.stream.VideoStream?
                    val audioUrl: String?
                    val videoCodec: String?

                    when {
                        bestMp4VideoOnly != null &&
                                bestMp4VideoOnly.height > (bestCombined?.height ?: 0) -> {
                            selectedStream = bestMp4VideoOnly
                            val aacAudio = allAudio.filter { isAacCompatible(it) }.maxByOrNull { it.averageBitrate }
                                ?: allAudio.filter { isAacCompatible(it) }.maxByOrNull { it.averageBitrate }
                            audioUrl = aacAudio?.content ?: aacAudio?.url
                            videoCodec = null
                        }
                        bestCombined != null -> {
                            selectedStream = bestCombined
                            audioUrl = null
                            videoCodec = null
                        }
                        bestVp9VideoOnly != null &&
                                (bestVp9VideoOnly.height > (bestMp4VideoOnly?.height ?: 0)) -> {
                            selectedStream = bestVp9VideoOnly
                            val opusAudio = allAudio.filter { isOpusCompatible(it) }.maxByOrNull { it.averageBitrate }
                                ?: allAudio.maxByOrNull { it.averageBitrate }
                            audioUrl = opusAudio?.content ?: opusAudio?.url
                            videoCodec = "vp9"
                        }
                        bestMp4VideoOnly != null -> {
                            selectedStream = bestMp4VideoOnly
                            val aacAudio = allAudio.filter { isAacCompatible(it) }.maxByOrNull { it.averageBitrate }
                            audioUrl = aacAudio?.content ?: aacAudio?.url
                            videoCodec = null
                        }
                        else -> {
                            selectedStream = null
                            audioUrl = null
                            videoCodec = null
                        }
                    }

                    // NewPipe DASH VideoStreams store the real URL in .content; .url may be null.
                    val videoUrl = selectedStream?.content ?: selectedStream?.url
                    if (selectedStream != null && videoUrl != null) {
                        val fullVideo = Video(
                            id = video.id,
                            title = video.title.ifBlank { streamInfo.name ?: "Unknown" },
                            channelName = video.channelName.ifBlank { streamInfo.uploaderName ?: "" },
                            channelId = video.channelId.ifBlank { streamInfo.uploaderUrl?.substringAfterLast("/") ?: "local" },
                            thumbnailUrl = video.thumbnailUrl.ifBlank { streamInfo.thumbnails?.maxByOrNull { it.height }?.url ?: "" },
                            duration = if (video.duration > 0) video.duration else streamInfo.duration.toInt(),
                            viewCount = video.viewCount,
                            uploadDate = video.uploadDate,
                            description = video.description.ifBlank { streamInfo.description?.content ?: "" }
                        )

                        com.flow.youtube.data.video.downloader.FlowDownloadService.startDownload(
                            context = context,
                            video = fullVideo,
                            url = videoUrl,
                            quality = "${selectedStream.height}p",
                            audioUrl = audioUrl,
                            videoCodec = videoCodec
                        )
                        Toast.makeText(context, "Download started: ${fullVideo.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No suitable stream found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Failed to fetch video info", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
