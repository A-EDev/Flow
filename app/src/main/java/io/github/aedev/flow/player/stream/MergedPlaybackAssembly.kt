package io.github.aedev.flow.player.stream

import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamSegment
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
    val storyboard: List<StoryboardLevel>,
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

    /**
     * Re-picks the streams for [quality] from a result that is already on screen, over the InnerTube
     * formats the screen kept from the load.
     *
     * The InnerTube streams lead the merge here and trail it in [assemble]: the merge de-duplicates
     * by URL, so a format both stacks produced resolves to the InnerTube stream object on a quality
     * switch and to the extractor's on the initial load.
     *
     * [streamInfo] is optional because a load InnerTube resolved on its own never has one, and
     * refusing to pick a quality without it is what left the quality menu inert on that path.
     */
    fun selectQualityStreams(
        streamInfo: StreamInfo?,
        innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format>,
        innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format>,
        quality: VideoQuality,
        preferredAudioLanguage: String,
        preferredCodecKey: String,
    ): Pair<VideoStream?, AudioStream?> {
        val innerTubeVideoStreams = InnerTubeStreamBridge.convertVideoFormats(innerTubeVideoFormats)
        val innerTubeAudioStreams = InnerTubeStreamBridge.convertAudioFormats(innerTubeAudioFormats)
        val extractorVideo =
            streamInfo
                ?.let { (it.videoStreams + it.videoOnlyStreams).filterIsInstance<VideoStream>() }
                .orEmpty()
        val effectiveVideo = StreamMergeUtils.mergeVideoStreams(innerTubeVideoStreams, extractorVideo)
        val effectiveAudio: List<AudioStream> =
            StreamMergeUtils.mergeAudioStreams(innerTubeAudioStreams, streamInfo?.audioStreams.orEmpty())
        return ServicePlaybackStreamSelector.selectStreams(
            videoCandidates = effectiveVideo,
            audioCandidatesAll = effectiveAudio,
            preferredQuality = quality,
            preferredAudioLanguage = preferredAudioLanguage,
            preferredCodecKey = preferredCodecKey,
        )
    }
}
