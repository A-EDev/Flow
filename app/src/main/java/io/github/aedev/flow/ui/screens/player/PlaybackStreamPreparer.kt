package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.CaptionTrackResolver
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.ResolvedPlayback
import io.github.aedev.flow.player.stream.ServicePlaybackStreamSelector
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.StreamSizeEstimator
import io.github.aedev.flow.player.stream.VideoQualityOptions
import io.github.aedev.flow.ui.screens.player.state.blankVideo
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Turns one InnerTube extraction into the values the player screen plays from: the identity the
 * session and the media notification are armed with, the Media3-shaped streams and captions, and the
 * quality and audio track the load's preferences select.
 *
 * Pure — no network, no player, no preferences and no screen state. The ViewModel owns what happens
 * around the result: it applies the state reducers, reads the resume position, and hands the streams
 * to [PlaybackPreparer] in the order playback needs them.
 */
internal class PlaybackStreamPreparer {
    /** The identity every InnerTube result carries, merged over what the screen already knew. */
    data class StreamIdentity(
        val enrichedVideo: Video,
        val title: String,
        val channel: String,
        val thumbnail: String,
        val channelId: String,
        val embeddedAvatarUrls: List<String>,
    )

    data class VodStreams(
        val identity: StreamIdentity,
        val durationSeconds: Long,
        val videoStreams: List<VideoStream>,
        val audioStreams: List<AudioStream>,
        val availableQualities: List<VideoQuality>,
        val videoStream: VideoStream?,
        val audioStream: AudioStream?,
        val subtitles: List<SubtitlesStream>,
        val isAdaptiveMode: Boolean,
        val streamSizes: Map<String, Long>,
    )

    data class LiveStreams(
        val identity: StreamIdentity,
        val hlsUrl: String?,
        val dashManifestUrl: String?,
        val subtitles: List<SubtitlesStream>,
    )

    fun assembleVod(
        videoId: String,
        cached: Video?,
        step: ResolvedPlayback.VodFromInnerTube,
    ): VodStreams {
        val result = step.result
        val details = result.playerResponse.videoDetails
        val durationSeconds =
            details?.lengthSeconds?.toLongOrNull()?.takeIf { it > 0 }
                ?: cached?.duration?.toLong()?.takeIf { it > 0 }
                ?: 0L
        val videoStreams = InnerTubeStreamBridge.convertVideoFormats(result.videoFormats)
        val audioStreams = InnerTubeStreamBridge.convertAudioFormats(result.audioFormats)
        val selected =
            ServicePlaybackStreamSelector.selectStreams(
                videoCandidates = videoStreams,
                audioCandidatesAll = audioStreams,
                preferredQuality = step.preferredQuality,
                preferredAudioLanguage = step.preferredAudioLanguage,
                preferredCodecKey = step.preferredCodecKey,
            )
        return VodStreams(
            identity = identity(videoId, cached, result, fallbackTitle = "", durationSeconds = durationSeconds),
            durationSeconds = durationSeconds,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            availableQualities = VideoQualityOptions.availableQualities(videoStreams),
            videoStream = selected.first,
            audioStream = selected.second,
            subtitles = captionStreams(result),
            isAdaptiveMode = step.preferredQuality == VideoQuality.AUTO,
            streamSizes =
                StreamSizeEstimator.fromInnerTubeFormats(
                    result.videoFormats,
                    result.audioFormats,
                    durationSeconds * 1000L,
                ),
        )
    }

    fun assembleLive(
        videoId: String,
        cached: Video?,
        result: InnerTubeVideoStreamExtractor.VideoExtractionResult,
    ): LiveStreams =
        LiveStreams(
            identity = identity(videoId, cached, result, fallbackTitle = "Live", durationSeconds = 0L),
            hlsUrl = result.liveHlsUrl,
            dashManifestUrl = result.liveDashUrl,
            subtitles = captionStreams(result),
        )

    private fun identity(
        videoId: String,
        cached: Video?,
        result: InnerTubeVideoStreamExtractor.VideoExtractionResult,
        fallbackTitle: String,
        durationSeconds: Long,
    ): StreamIdentity {
        val details = result.playerResponse.videoDetails
        val title = details?.title?.takeIf { it.isNotBlank() } ?: cached?.title ?: fallbackTitle
        val channel = details?.author?.takeIf { it.isNotBlank() } ?: cached?.channelName ?: ""
        val channelId = details?.channelId?.takeIf { it.isNotBlank() } ?: cached?.channelId ?: ""
        val thumbnail =
            details
                ?.thumbnail
                ?.thumbnails
                ?.maxByOrNull { it.height ?: 0 }
                ?.url
                ?: cached?.thumbnailUrl ?: ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, null)
        return StreamIdentity(
            enrichedVideo =
                blankVideo(videoId, cached).copy(
                    title = title,
                    channelName = channel,
                    channelId = channelId,
                    thumbnailUrl = thumbnail,
                    duration = durationSeconds.toInt(),
                ),
            title = title,
            channel = channel,
            thumbnail = thumbnail,
            channelId = channelId,
            embeddedAvatarUrls = listOfNotNull(cached?.channelThumbnailUrl) + cached?.channelThumbnailUrls.orEmpty(),
        )
    }

    private fun captionStreams(result: InnerTubeVideoStreamExtractor.VideoExtractionResult): List<SubtitlesStream> =
        StreamProcessor.processSubtitleStreams(CaptionTrackResolver.resolve(result.playerResponse))
}
