package io.github.aedev.flow.player.stream

import io.github.aedev.flow.player.quality.QualityManager
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Single source of truth for merging video/audio stream lists coming from the two
 * extraction backends (native InnerTube + NewPipe). Used by both [EnhancedPlayerManager]
 * (engine-side queue/autoplay advances) and [VideoPlayerViewModel] (initial load)
 */
object StreamMergeUtils {

    fun mergeVideoStreams(
        primary: List<VideoStream>,
        fallback: List<VideoStream>
    ): List<VideoStream> {
        return (primary + fallback)
            .filter { it.getContent().isNotBlank() }
            .distinctBy { stream ->
                val url = stream.getContent()
                if (url.isNotBlank()) url else "${VideoCodecUtils.qualityHeightFromStream(stream)}_${VideoCodecUtils.codecKeyFromStream(stream)}_${stream.bitrate}"
            }
            .sortedWith(
                compareByDescending<VideoStream> { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) }
                    .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                    .thenByDescending { it.bitrate }
            )
    }

    fun mergeAudioStreams(
        primary: List<AudioStream>,
        fallback: List<AudioStream>
    ): List<AudioStream> {
        return (primary + fallback)
            .filter { it.getContent().isNotBlank() }
            .distinctBy { stream ->
                listOf(
                    stream.getContent(),
                    stream.format?.mimeType.orEmpty(),
                    stream.audioTrackId.orEmpty(),
                    stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate
                ).joinToString("|")
            }
            .sortedByDescending { it.averageBitrate.takeIf { bitrate -> bitrate > 0 } ?: it.bitrate }
    }
}
