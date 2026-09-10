package io.github.aedev.flow.ui.screens.player.state

import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.PlayerChannelMetadataPolicy
import io.github.aedev.flow.player.error.VideoErrorMapper
import io.github.aedev.flow.player.stream.ResolvedPlayback
import io.github.aedev.flow.player.stream.StreamSizeEstimator
import io.github.aedev.flow.player.stream.VideoQualityOptions
import io.github.aedev.flow.ui.screens.player.SecondaryMetadata
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/*
 * Every state transition the player screen makes while a load resolves, as pure functions over
 * VideoPlayerUiState.
 *
 * The ViewModel keeps the ordering: which side effect runs before which write, and which write is
 * skipped because the load that produced it is no longer current. Nothing here reads the clock, the
 * network, the player or the preferences — the values a transition needs are arguments, so the
 * fields each outcome writes can be asserted without a ViewModel.
 */

/** A downloaded copy is about to play: the local path replaces whatever the load had reached. */
internal fun VideoPlayerUiState.applyLocalCopyReady(
    videoId: String,
    step: ResolvedPlayback.LocalCopyReady,
): VideoPlayerUiState =
    copy(
        streamInfo = if (step.clearStreamInfo) null else streamInfo,
        localFilePath = step.localFilePath,
        localFileVideoId = videoId,
        offlineSponsorBlockSegments = step.offlineSegments,
        error = null,
        errorHint = null,
        isLoading = false,
        isUpcoming = false,
        upcomingReleaseTimeMs = null,
    )

/** Resolution failed but a downloaded copy exists: the failure never reaches the screen. */
internal fun VideoPlayerUiState.applyLocalCopyAfterFailure(): VideoPlayerUiState = copy(isLoading = false, error = null, errorHint = null)

/** A local copy is already playing and only the surrounding metadata was still missing. */
internal fun VideoPlayerUiState.applyOfflineFallback(step: ResolvedPlayback.OfflineFallback): VideoPlayerUiState =
    copy(
        isLoading = false,
        error = null,
        errorHint = null,
        relatedVideos = step.relatedVideos,
        localFilePath = step.localFilePath,
        offlineSponsorBlockSegments = step.offlineSegments,
        isUpcoming = false,
        upcomingReleaseTimeMs = null,
    )

/** The merged NewPipe + InnerTube result: the screen's streams, qualities, chapters and manifests. */
internal fun VideoPlayerUiState.applyMergedPlayback(
    videoId: String,
    step: ResolvedPlayback.Merged,
): VideoPlayerUiState {
    val streams = step.streams
    return copy(
        streamInfo = step.streamInfo,
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
}

/**
 * A VOD that only InnerTube could resolve.
 *
 * This is the screen's second stream writer, and it writes a different set of fields from
 * [applyMergedPlayback]: `chapters`, `offlineSponsorBlockSegments`, `localFilePath` and
 * `localFileVideoId` are deliberately left as the load left them, because the InnerTube path never
 * had them to begin with.
 */
internal fun VideoPlayerUiState.applyVodStreams(
    relatedVideos: List<Video>,
    videoStream: VideoStream?,
    audioStream: AudioStream?,
    availableQualities: List<VideoQuality>,
    savedPositionMs: Long,
    isAdaptiveMode: Boolean,
    autoplayEnabled: Boolean,
    innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format>,
    innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format>,
    streamSizes: Map<String, Long>,
): VideoPlayerUiState =
    copy(
        streamInfo = null,
        relatedVideos = relatedVideos,
        videoStream = videoStream,
        audioStream = audioStream,
        availableQualities = availableQualities,
        selectedQuality = VideoQualityOptions.qualityOf(videoStream),
        isLoading = false,
        error = null,
        errorHint = null,
        savedPosition = savedPositionMs,
        isAdaptiveMode = isAdaptiveMode,
        autoplayEnabled = autoplayEnabled,
        isLive = false,
        isUpcoming = false,
        upcomingReleaseTimeMs = null,
        innerTubeVideoFormats = innerTubeVideoFormats,
        innerTubeAudioFormats = innerTubeAudioFormats,
        streamSizes = streamSizes,
    )

/** A live stream whose manifest only InnerTube produced. */
internal fun VideoPlayerUiState.applyLiveStreams(
    relatedVideos: List<Video>,
    hlsUrl: String?,
): VideoPlayerUiState =
    copy(
        streamInfo = null,
        relatedVideos = relatedVideos,
        isLoading = false,
        error = null,
        errorHint = null,
        hlsUrl = hlsUrl,
        isLive = true,
        isUpcoming = false,
        upcomingReleaseTimeMs = null,
        innerTubeVideoFormats = emptyList(),
        innerTubeAudioFormats = emptyList(),
    )

/** The InnerTube VOD path threw: the related lane it had already gathered survives the error. */
internal fun VideoPlayerUiState.applyVodFailure(
    relatedVideos: List<Video>,
    videoError: VideoErrorMapper.VideoError,
): VideoPlayerUiState =
    copy(
        isLoading = false,
        relatedVideos = relatedVideos,
        error = videoError.message,
        errorHint = videoError.hint,
    )

/** Nothing resolved. A null [relatedVideos] leaves the lane the screen is already showing alone. */
internal fun VideoPlayerUiState.applyPlaybackFailure(
    relatedVideos: List<Video>?,
    videoError: VideoErrorMapper.VideoError,
): VideoPlayerUiState =
    copy(
        isLoading = false,
        relatedVideos = relatedVideos ?: this.relatedVideos,
        error = videoError.message,
        errorHint = videoError.hint,
    )

/**
 * The channel avatar and subscriber count.
 *
 * The avatar is chosen twice: once between what the fetch returned and what the load embedded, and
 * again between that winner and the avatar the cached video itself carries, so a cached avatar is
 * never replaced by a worse one. A result for a video the screen has moved on from changes nothing.
 */
internal fun VideoPlayerUiState.applyChannelMetadata(result: SecondaryMetadata.Channel): VideoPlayerUiState {
    val fetchedOrEmbedded =
        PlayerChannelMetadataPolicy.selectAvatarUrl(
            fetchedAvatarUrl = result.fetchedAvatarUrl,
            embeddedAvatarUrl = result.embeddedAvatarUrl,
            currentAvatarUrl = channelAvatarUrl,
        )
    val cached = cachedVideo
    if (cached?.id != result.videoId) return this

    val selectedAvatar =
        PlayerChannelMetadataPolicy.selectAvatarUrl(
            fetchedAvatarUrl = fetchedOrEmbedded,
            embeddedAvatarUrl = cached.channelThumbnailUrl,
            currentAvatarUrl = channelAvatarUrl,
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

    return copy(
        cachedVideo = updatedCached,
        channelAvatarUrl = selectedAvatar,
        channelSubscriberCount = result.subscriberCount ?: channelSubscriberCount,
    )
}

/** The related lane, dropped when the screen has already moved to another video. */
internal fun VideoPlayerUiState.applyRelatedVideos(
    videoId: String,
    videos: List<Video>,
): VideoPlayerUiState =
    if (cachedVideo?.id != videoId && streamInfo?.id != videoId) {
        this
    } else {
        copy(relatedVideos = videos)
    }

/**
 * Late NewPipe metadata folded over an InnerTube-only load.
 *
 * Its streams join the download dialog's list, so their sizes have to join the map the dialog looks
 * them up in rather than replace it.
 */
internal fun VideoPlayerUiState.applyEnrichedMetadata(result: SecondaryMetadata.Enriched): VideoPlayerUiState {
    val streamInfo = result.streamInfo
    return copy(
        cachedVideo = result.video,
        streamInfo = streamInfo,
        relatedVideos = result.relatedVideos.ifEmpty { relatedVideos },
        chapters = streamInfo.streamSegments ?: chapters,
        streamSizes =
            StreamSizeEstimator.merge(
                streamSizes,
                StreamSizeEstimator.fromExtractorStreams(
                    (streamInfo.videoStreams + streamInfo.videoOnlyStreams).filterIsInstance<VideoStream>(),
                    streamInfo.audioStreams,
                    streamInfo.duration,
                ),
            ),
    )
}

/** The live watch refresh: title, channel, counts and the avatar it resolved. */
internal fun VideoPlayerUiState.applyLiveWatchMetadata(result: SecondaryMetadata.LiveWatch): VideoPlayerUiState =
    copy(
        cachedVideo = result.video,
        channelAvatarUrl = result.channelAvatarUrl ?: channelAvatarUrl,
        channelSubscriberCount = result.subscriberCount ?: channelSubscriberCount,
    )

/**
 * The video the session identity and the media notification are armed from once NewPipe's metadata
 * lands, or null when it carried no usable title and the screen keeps what it had.
 */
internal fun VideoPlayerUiState.primaryMetadataVideo(
    videoId: String,
    streamInfo: StreamInfo,
): Video? {
    val realTitle = streamInfo.name?.takeIf { it.isNotBlank() } ?: return null
    val currentCached = cachedVideo
    return blankVideo(videoId, currentCached).copy(
        title = realTitle,
        channelName = streamInfo.uploaderName?.takeIf { it.isNotBlank() } ?: currentCached?.channelName ?: "",
        channelId =
            currentCached?.channelId?.takeIf { it.isNotBlank() }
                ?: streamInfo.uploaderUrl?.split("/")?.last() ?: "",
        thumbnailUrl =
            streamInfo.thumbnails
                ?.maxByOrNull { it.height }
                ?.url
                ?.takeIf { it.isNotBlank() }
                ?: currentCached?.thumbnailUrl ?: "",
        duration = streamInfo.duration.toInt().takeIf { it > 0 } ?: (currentCached?.duration ?: 0),
    )
}

/** The learning signal a resolved load emits: tags and description, not a title-only stub. */
internal fun neuroSignalVideo(
    videoId: String,
    streamInfo: StreamInfo,
): Video =
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

/** The video the live watch refresh starts from when the merged result turned out to be live. */
internal fun VideoPlayerUiState.liveWatchFallbackVideo(
    videoId: String,
    streamInfo: StreamInfo,
): Video =
    Video(
        id = videoId,
        title = streamInfo.name ?: cachedVideo?.title ?: "Live",
        channelName = streamInfo.uploaderName ?: cachedVideo?.channelName ?: "",
        channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: cachedVideo?.channelId ?: "",
        thumbnailUrl =
            streamInfo.thumbnails.maxByOrNull { it.height }?.url
                ?: cachedVideo?.thumbnailUrl
                ?: ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, null),
        duration = 0,
        viewCount = streamInfo.viewCount,
        uploadDate = "",
        description = streamInfo.description?.content ?: cachedVideo?.description ?: "",
        isLive = true,
    )

/**
 * The richest [Video] the screen holds for [videoId], or null when it holds none.
 *
 * Engine signals are fed from this rather than from the title-only stub a card hands over, so a
 * like or a watch recorded here carries the tags, description and duration the load resolved.
 */
internal fun VideoPlayerUiState.richVideoFor(videoId: String): Video? =
    cachedVideo?.takeIf { it.id == videoId }
        ?: streamInfo?.takeIf { it.id == videoId }?.let { info ->
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

/** The quality the user picked, and the streams that choice resolved to. */
internal fun VideoPlayerUiState.applySelectedQuality(
    quality: VideoQuality,
    videoStream: VideoStream?,
    audioStream: AudioStream?,
): VideoPlayerUiState =
    copy(
        videoStream = videoStream,
        audioStream = audioStream,
        selectedQuality = VideoQualityOptions.qualityOf(videoStream),
        isAdaptiveMode = quality == VideoQuality.AUTO,
    )

/** The identity a load enriches when the screen holds nothing for the video yet. */
internal fun blankVideo(
    videoId: String,
    cached: Video?,
): Video =
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
