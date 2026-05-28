package io.github.aedev.flow.ui.components

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.player.stream.AudioStreamSelector
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import javax.inject.Inject

/**
 * Lightweight singleton event bus for feed-visible state changes.
 * Emitted by QuickActionsViewModel, observed by HomeViewModel / ShortsViewModel
 * to instantly strip blocked/disliked content from the cached feed.
 */
object FeedInvalidationBus {
    sealed class Event {
        data class ChannelBlocked(val channelId: String) : Event()
        data class NotInterested(val videoId: String, val channelId: String) : Event()
        data class MarkedWatched(val videoId: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun emit(event: Event) { _events.tryEmit(event) }
}

@HiltViewModel
class QuickActionsViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerPreferences: io.github.aedev.flow.data.local.PlayerPreferences,
    private val videoDownloadManager: VideoDownloadManager,
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

    val downloadedVideoIds = videoDownloadManager.allDownloads
        .map { list ->
            list.filter { it.overallStatus == DownloadItemStatus.COMPLETED && it.items.isNotEmpty() }
                .map { it.download.videoId }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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
                    val resolvedThumbnail = channelThumbnail
                        .takeUnless { ThumbnailUrlResolver.isYoutubeVideoThumbnail(it) }
                        ?.takeIf { it.isNotBlank() }
                        ?: withContext(Dispatchers.IO) {
                            repository.fetchChannelAvatarById(channelId)
                        }
                    subscriptionRepository.subscribe(
                        ChannelSubscription(
                            channelId = channelId,
                            channelName = channelName,
                            channelThumbnail = resolvedThumbnail,
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
     * Block the channel of a video — the channel will never appear in the feed again.
     * Uses FlowNeuroEngine.blockChannel to persist the block and scrub any existing
     * channel score, mirroring the "Blocked Channels" UI in User Preferences.
     */
    fun blockChannel(video: Video) {
        if (video.channelId.isBlank()) return
        viewModelScope.launch {
            try {
                FlowNeuroEngine.blockChannel(context, video.channelId)
                FeedInvalidationBus.emit(FeedInvalidationBus.Event.ChannelBlocked(video.channelId))
                Toast.makeText(
                    context,
                    context.getString(io.github.aedev.flow.R.string.channel_blocked_toast, video.channelName),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
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
                FeedInvalidationBus.emit(
                    FeedInvalidationBus.Event.NotInterested(video.id, video.channelId)
                )
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
                    InteractionType.WATCHED,
                    percentWatched = 1.0f
                )
                
                val durationMs = if (video.duration > 0) video.duration * 1000L else 1000L
                val thumbnailUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg"
                io.github.aedev.flow.data.local.ViewHistory.getInstance(context).savePlaybackPosition(
                    videoId = video.id,
                    position = durationMs,
                    duration = durationMs,
                    title = video.title,
                    thumbnailUrl = thumbnailUrl,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    isMusic = false,
                    isShort = video.isShort
                )

                _watchedVideoIds.update { it + video.id }
                FeedInvalidationBus.emit(FeedInvalidationBus.Event.MarkedWatched(video.id))
                Toast.makeText(
                    context,
                    context.getString(io.github.aedev.flow.R.string.mark_as_watched_toast),
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
                    InteractionType.LIKED,
                    percentWatched = 0f
                )
                Toast.makeText(
                    context,
                    context.getString(io.github.aedev.flow.R.string.i_like_this_toast),
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
        io.github.aedev.flow.player.EnhancedPlayerManager.getInstance().addVideoToQueueNext(video)
    }

    /**
     * Append [video] to the end of the current queue.
     */
    fun addVideoToQueue(video: Video) {
        io.github.aedev.flow.player.EnhancedPlayerManager.getInstance().addVideoToQueue(video)
    }

    fun downloadVideo(video: Video) {
        viewModelScope.launch {
            try {
                io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils.promptStoragePermissionIfNeeded(context)

                val targetQuality = playerPreferences.defaultDownloadQuality.first()
                val targetHeight = targetQuality.height

                Toast.makeText(context, "Fetching download links...", Toast.LENGTH_SHORT).show()

                // Try innertube extraction first (HD+ quality, direct URLs)
                val innerTubeResult = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(8000L) {
                        io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.extract(video.id)
                    }
                }

                if (innerTubeResult != null && innerTubeResult.videoFormats.isNotEmpty() && innerTubeResult.audioFormats.isNotEmpty()) {
                    downloadFromInnerTube(video, innerTubeResult, targetHeight)
                    return@launch
                }

                // Fall back to NewPipe StreamInfo
                val streamInfo = withContext(Dispatchers.IO) {
                    repository.getVideoStreamInfo(video.id)
                }

                if (streamInfo != null) {
                    val videoStreams = io.github.aedev.flow.player.stream.InnerTubeStreamBridge.convertVideoFormats(
                        innerTubeResult?.videoFormats ?: emptyList()
                    ).ifEmpty {
                        (streamInfo.videoStreams + (streamInfo.videoOnlyStreams ?: emptyList()))
                            .filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
                    }
                    val audioStreams: List<org.schabi.newpipe.extractor.stream.AudioStream> =
                        io.github.aedev.flow.player.stream.InnerTubeStreamBridge.convertAudioFormats(
                            innerTubeResult?.audioFormats ?: emptyList()
                        ).ifEmpty { streamInfo.audioStreams ?: emptyList() }

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

                    fun qualityHeight(s: org.schabi.newpipe.extractor.stream.VideoStream): Int {
                        return QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(s))
                    }

                    fun List<org.schabi.newpipe.extractor.stream.VideoStream>.bestForTarget():
                            org.schabi.newpipe.extractor.stream.VideoStream? {
                        if (isEmpty()) return null
                        if (targetHeight == 0) return maxByOrNull { qualityHeight(it) }
                        return filter { qualityHeight(it) <= targetHeight }.maxByOrNull { qualityHeight(it) }
                            ?: minByOrNull { qualityHeight(it) }
                    }

                    val videoOnlyStreams = videoStreams.filter { it.isVideoOnly }
                    val combinedStreams = videoStreams.filter { !it.isVideoOnly }

                    val bestMp4VideoOnly  = videoOnlyStreams.filter { isMp4Video(it) }.bestForTarget()
                    val bestVp9VideoOnly  = videoOnlyStreams.filter { isVp9Video(it) }.bestForTarget()
                    val bestCombined      = combinedStreams.bestForTarget()
                    val preferredAudioLanguage = playerPreferences.preferredAudioLanguage.first()

                    val allAudio = audioStreams

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
                                qualityHeight(bestMp4VideoOnly) > (bestCombined?.let(::qualityHeight) ?: 0) -> {
                            selectedStream = bestMp4VideoOnly
                            val aacAudio = AudioStreamSelector.selectPreferredAudioStream(
                                streams = allAudio,
                                preferredAudioLanguage = preferredAudioLanguage,
                                compatibilityFilter = ::isAacCompatible
                            )
                            audioUrl = aacAudio?.content ?: aacAudio?.url
                            videoCodec = null
                        }
                        bestCombined != null -> {
                            selectedStream = bestCombined
                            audioUrl = null
                            videoCodec = null
                        }
                        bestVp9VideoOnly != null &&
                                (qualityHeight(bestVp9VideoOnly) > (bestMp4VideoOnly?.let(::qualityHeight) ?: 0)) -> {
                            selectedStream = bestVp9VideoOnly
                            val opusAudio = AudioStreamSelector.selectPreferredAudioStream(
                                streams = allAudio,
                                preferredAudioLanguage = preferredAudioLanguage,
                                compatibilityFilter = ::isOpusCompatible
                            )
                            audioUrl = opusAudio?.content ?: opusAudio?.url
                            videoCodec = "vp9"
                        }
                        bestMp4VideoOnly != null -> {
                            selectedStream = bestMp4VideoOnly
                            val aacAudio = AudioStreamSelector.selectPreferredAudioStream(
                                streams = allAudio,
                                preferredAudioLanguage = preferredAudioLanguage,
                                compatibilityFilter = ::isAacCompatible
                            )
                            audioUrl = aacAudio?.content ?: aacAudio?.url
                            videoCodec = null
                        }
                        else -> {
                            selectedStream = null
                            audioUrl = null
                            videoCodec = null
                        }
                    }

                    val videoUrl = selectedStream?.content ?: selectedStream?.url

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

                    if (selectedStream != null && videoUrl != null) {
                        io.github.aedev.flow.data.video.downloader.FlowDownloadService.startDownload(
                            context = context,
                            video = fullVideo,
                            url = videoUrl,
                            quality = "${qualityHeight(selectedStream)}p",
                            audioUrl = audioUrl,
                            videoCodec = videoCodec
                        )
                        Toast.makeText(context, "Download started: ${fullVideo.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        trySabrDownload(fullVideo, targetHeight)
                    }
                } else {
                    trySabrDownload(video, 0)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadFromInnerTube(
        video: Video,
        result: io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.VideoExtractionResult,
        targetHeight: Int
    ) {
        val videoFormats = result.videoFormats.filter { it.url != null && it.height != null }
        val audioFormats = result.audioFormats.filter { it.url != null }

        val bestVideo = if (targetHeight == 0) {
            videoFormats.maxByOrNull { (it.height ?: 0) * 10000 + it.bitrate }
        } else {
            videoFormats.filter { (it.height ?: 0) <= targetHeight }
                .maxByOrNull { (it.height ?: 0) * 10000 + it.bitrate }
                ?: videoFormats.minByOrNull { it.height ?: Int.MAX_VALUE }
        }

        if (bestVideo == null) return

        val isMp4 = bestVideo.mimeType.contains("mp4", ignoreCase = true)
        val bestAudio = if (isMp4) {
            audioFormats.filter { it.mimeType.contains("mp4", ignoreCase = true) }
                .maxByOrNull { it.bitrate }
                ?: audioFormats.maxByOrNull { it.bitrate }
        } else {
            audioFormats.filter { it.mimeType.contains("webm", ignoreCase = true) }
                .maxByOrNull { it.bitrate }
                ?: audioFormats.maxByOrNull { it.bitrate }
        }

        val videoCodec = when {
            bestVideo.mimeType.contains("vp9", true) || bestVideo.mimeType.contains("vp09", true) -> "vp9"
            bestVideo.mimeType.contains("av01", true) -> "av1"
            else -> null
        }

        io.github.aedev.flow.data.video.downloader.FlowDownloadService.startDownload(
            context = context,
            video = video,
            url = bestVideo.url!!,
            quality = "${bestVideo.height}p",
            audioUrl = bestAudio?.url,
            videoCodec = videoCodec
        )
        Toast.makeText(context, "Download started: ${video.title}", Toast.LENGTH_SHORT).show()
    }

    private fun trySabrDownload(video: Video, targetHeight: Int) {
        viewModelScope.launch {
            try {
                Toast.makeText(context, "Trying SABR download...", Toast.LENGTH_SHORT).show()
                val sabrInfo = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(8000L) {
                        val playerResponse = YouTube.player(video.id, client = YouTubeClient.ANDROID)
                            .getOrNull() ?: return@withTimeoutOrNull null
                        if (targetHeight > 0) {
                            SabrUrlResolver.resolveForQuality(playerResponse, targetHeight)
                        } else {
                            SabrUrlResolver.resolve(playerResponse)
                        }
                    }
                }

                if (sabrInfo != null) {
                    val codecHint = if (sabrInfo.videoItag in listOf(313, 271, 308, 248, 303, 247, 302, 244, 243, 242)) "vp9" else null
                    io.github.aedev.flow.data.video.downloader.FlowDownloadService.startSabrDownload(
                        context = context,
                        video = video,
                        quality = "${targetHeight.takeIf { it > 0 } ?: "best"}p",
                        sabrStreamingUrl = sabrInfo.streamingUrl,
                        audioItag = sabrInfo.audioItag,
                        audioLmt = sabrInfo.audioLmt,
                        videoItag = sabrInfo.videoItag,
                        videoLmt = sabrInfo.videoLmt,
                        durationMs = sabrInfo.durationMs,
                        videoCodec = codecHint
                    )
                    Toast.makeText(context, "SABR download started: ${video.title}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No download source available", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "SABR download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
