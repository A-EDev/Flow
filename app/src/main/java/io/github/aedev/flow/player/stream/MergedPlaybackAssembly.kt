package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.SabrRoutingPolicy
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

/** The playable shape of one video once both extraction stacks have been folded together. */
data class MergedPlayback(
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>,
    val availableQualities: List<VideoQuality>,
    val selectedVideoStream: VideoStream?,
    val selectedAudioStream: AudioStream?,
    val subtitles: List<SubtitlesStream>,
    val chapters: List<StreamSegment>,
    val streamSizes: Map<String, Long>,
    val innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format>,
    val innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format>,
    val hlsUrl: String?,
    val dashManifestUrl: String?,
    val isLiveType: Boolean,
    val isLiveStream: Boolean,
    val hasPlayableContent: Boolean,
    val localFilePath: String?,
    val sabrInfo: SabrStreamInfo?,
    val preferSabr: Boolean,
    val preferredQuality: VideoQuality,
    val preferredCodecKey: String,
) {
    val isAdaptiveMode: Boolean get() = preferredQuality == VideoQuality.AUTO
}

/**
 * Folds a NewPipe [StreamInfo] and an InnerTube extraction of the same video into the single set of
 * streams, qualities, captions and manifest URLs playback runs on.
 *
 * Pure: no network, no preferences, no player. Everything it needs is an argument, which is what
 * makes the merge order, the quality choice and the SABR routing decision testable on their own.
 */
object MergedPlaybackAssembly {
    private const val TAG = "MergedPlaybackAssembly"

    fun assemble(
        streamInfo: StreamInfo,
        innerTubeResult: InnerTubeVideoStreamExtractor.VideoExtractionResult?,
        preferredQuality: VideoQuality,
        preferredAudioLanguage: String,
        preferredCodecKey: String,
        escalateToSabr: Boolean,
        localFilePath: String?,
    ): MergedPlayback {
        val liveFromInnerTube =
            innerTubeResult?.isLive == true &&
                (!innerTubeResult.liveHlsUrl.isNullOrEmpty() || !innerTubeResult.liveDashUrl.isNullOrEmpty())

        val innerTubeVideoStreams =
            innerTubeResult?.let { InnerTubeStreamBridge.convertVideoFormats(it.videoFormats) } ?: emptyList()
        val innerTubeAudioStreams =
            innerTubeResult?.let { InnerTubeStreamBridge.convertAudioFormats(it.audioFormats) } ?: emptyList()

        val extractorVideoStreams =
            (streamInfo.videoStreams + streamInfo.videoOnlyStreams)
                .filterIsInstance<VideoStream>()
        val effectiveVideoStreams: List<VideoStream> =
            StreamMergeUtils.mergeVideoStreams(extractorVideoStreams, innerTubeVideoStreams)
        val effectiveAudioStreams: List<AudioStream> =
            StreamMergeUtils.mergeAudioStreams(streamInfo.audioStreams, innerTubeAudioStreams)
        val downloadStreamSizes =
            StreamSizeEstimator.merge(
                StreamSizeEstimator.fromExtractorStreams(
                    effectiveVideoStreams,
                    effectiveAudioStreams,
                    streamInfo.duration,
                ),
                StreamSizeEstimator.fromInnerTubeFormats(
                    innerTubeResult?.videoFormats.orEmpty(),
                    innerTubeResult?.audioFormats.orEmpty(),
                    streamInfo.duration * 1000L,
                ),
            )

        logChosenStack(
            streamInfo,
            innerTubeResult,
            extractorVideoStreams,
            innerTubeVideoStreams,
            innerTubeAudioStreams,
            effectiveVideoStreams,
        )

        val availableQualities = VideoQualityOptions.availableQualities(effectiveVideoStreams)
        val selectedStreams =
            ServicePlaybackStreamSelector.selectStreams(
                videoCandidates = effectiveVideoStreams,
                audioCandidatesAll = effectiveAudioStreams,
                preferredQuality = preferredQuality,
                preferredAudioLanguage = preferredAudioLanguage,
                preferredCodecKey = preferredCodecKey,
            )

        val captionStreams =
            innerTubeResult
                ?.playerResponse
                ?.let { CaptionTrackResolver.resolve(it) }
                .orEmpty()
        val mergedSubtitleStreams =
            StreamProcessor.processSubtitleStreams(streamInfo.subtitles.orEmpty() + captionStreams)

        val liveType =
            streamInfo.streamType == StreamType.LIVE_STREAM ||
                streamInfo.streamType == StreamType.POST_LIVE_STREAM ||
                innerTubeResult?.isLive == true
        val liveHlsUrl =
            streamInfo.hlsUrl?.takeIf { liveType && it.isNotEmpty() }
                ?: innerTubeResult?.liveHlsUrl?.takeIf { liveFromInnerTube }
        val effectiveDashUrl =
            streamInfo.dashMpdUrl?.takeIf { it.isNotEmpty() }
                ?: innerTubeResult?.liveDashUrl?.takeIf { liveFromInnerTube }

        val resolvedSabrInfo = innerTubeResult?.sabrInfo
        val directMaxHeightForSabr =
            effectiveVideoStreams.maxOfOrNull { VideoCodecUtils.qualityHeightFromStream(it) } ?: 0
        // Prefer SABR for playback only when it actually beats the best direct/NewPipe stream we
        // already have (or a 403-expiry escalation forces it), so a working direct ladder is never
        // swapped for a SABR session needlessly.
        val preferSabrForPlayback =
            resolvedSabrInfo != null &&
                SabrRoutingPolicy.shouldPreferSabr(
                    escalateToSabr,
                    resolvedSabrInfo.videoHeight,
                    directMaxHeightForSabr,
                )
        if (resolvedSabrInfo != null) {
            Log.d(
                TAG,
                "SABR available: audioItag=${resolvedSabrInfo.audioItag}, " +
                    "videoItag=${resolvedSabrInfo.videoItag}, " +
                    "sabrHeight=${resolvedSabrInfo.videoHeight}, " +
                    "directMax=$directMaxHeightForSabr, prefer=$preferSabrForPlayback",
            )
        }

        return MergedPlayback(
            videoStreams = effectiveVideoStreams,
            audioStreams = effectiveAudioStreams,
            availableQualities = availableQualities,
            selectedVideoStream = selectedStreams.first,
            selectedAudioStream = selectedStreams.second,
            subtitles = mergedSubtitleStreams,
            chapters = streamInfo.streamSegments ?: emptyList(),
            streamSizes = downloadStreamSizes,
            innerTubeVideoFormats = innerTubeResult?.videoFormats ?: emptyList(),
            innerTubeAudioFormats = innerTubeResult?.audioFormats ?: emptyList(),
            hlsUrl = liveHlsUrl,
            dashManifestUrl = effectiveDashUrl,
            isLiveType = liveType,
            isLiveStream = streamInfo.streamType == StreamType.LIVE_STREAM || innerTubeResult?.isLive == true,
            hasPlayableContent =
                effectiveVideoStreams.isNotEmpty() ||
                    !liveHlsUrl.isNullOrEmpty() ||
                    !effectiveDashUrl.isNullOrEmpty() ||
                    localFilePath != null ||
                    resolvedSabrInfo != null,
            localFilePath = localFilePath,
            sabrInfo = resolvedSabrInfo,
            preferSabr = preferSabrForPlayback,
            preferredQuality = preferredQuality,
            preferredCodecKey = preferredCodecKey,
        )
    }

    /**
     * Re-picks the streams for [quality] from a result that is already on screen, over the InnerTube
     * formats the screen kept from the load.
     *
     * The InnerTube streams lead the merge here and trail it in [assemble]: the merge de-duplicates
     * by URL, so a format both stacks produced resolves to the InnerTube stream object on a quality
     * switch and to the extractor's on the initial load.
     */
    fun selectQualityStreams(
        streamInfo: StreamInfo,
        innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format>,
        innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format>,
        quality: VideoQuality,
        preferredAudioLanguage: String,
        preferredCodecKey: String,
    ): Pair<VideoStream?, AudioStream?> {
        val innerTubeVideoStreams = InnerTubeStreamBridge.convertVideoFormats(innerTubeVideoFormats)
        val innerTubeAudioStreams = InnerTubeStreamBridge.convertAudioFormats(innerTubeAudioFormats)
        val effectiveVideo =
            StreamMergeUtils.mergeVideoStreams(
                innerTubeVideoStreams,
                (streamInfo.videoStreams + streamInfo.videoOnlyStreams).filterIsInstance<VideoStream>(),
            )
        val effectiveAudio: List<AudioStream> =
            StreamMergeUtils.mergeAudioStreams(innerTubeAudioStreams, streamInfo.audioStreams)
        return ServicePlaybackStreamSelector.selectStreams(
            videoCandidates = effectiveVideo,
            audioCandidatesAll = effectiveAudio,
            preferredQuality = quality,
            preferredAudioLanguage = preferredAudioLanguage,
            preferredCodecKey = preferredCodecKey,
        )
    }

    private fun logChosenStack(
        streamInfo: StreamInfo,
        innerTubeResult: InnerTubeVideoStreamExtractor.VideoExtractionResult?,
        extractorVideoStreams: List<VideoStream>,
        innerTubeVideoStreams: List<VideoStream>,
        innerTubeAudioStreams: List<AudioStream>,
        effectiveVideoStreams: List<VideoStream>,
    ) {
        if (extractorVideoStreams.isNotEmpty()) {
            Log.i(
                TAG,
                "Using NewPipe streams: ${extractorVideoStreams.size} video, " +
                    "${streamInfo.audioStreams.size} audio (merged=${effectiveVideoStreams.size})",
            )
        } else if (innerTubeVideoStreams.isNotEmpty()) {
            Log.i(
                TAG,
                "Using InnerTube streams: ${innerTubeVideoStreams.size} video, " +
                    "${innerTubeAudioStreams.size} audio (client=${innerTubeResult?.usedClient?.clientName})",
            )
        } else {
            Log.d(TAG, "No direct-URL streams; relying on manifests/SABR (sabr=${innerTubeResult?.sabrInfo != null})")
        }
    }
}
