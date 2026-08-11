package io.github.aedev.flow.player.stream

import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.player.quality.QualityManager
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

/**
 * Picks the video and audio stream for playback started from the service layer — the autoplay and
 * preload path, which resolves streams without the player UI being involved.
 *
 * TODO: the audio half duplicates [AudioStreamSelector.selectPreferredAudioStream], which matches
 * more broadly (language tag and track name, not just `audioLocale.language`) and picks by
 * `averageBitrate` rather than relying on a pre-sort. Consolidating changes which dubbed track this
 * path selects, so it is a behaviour change with its own device pass, not part of the extraction
 * that created this file.
 */
object ServicePlaybackStreamSelector {
    fun selectStreams(
        videoCandidates: List<VideoStream>,
        audioCandidatesAll: List<AudioStream>,
        preferredQuality: VideoQuality,
        preferredAudioLanguage: String,
        preferredCodecKey: String = "auto",
    ): Pair<VideoStream?, AudioStream?> {
        val audioCandidates =
            audioCandidatesAll
                .distinctBy { it.url ?: it.content }
                .sortedByDescending { it.bitrate }

        val audioStream =
            when (preferredAudioLanguage) {
                "original", "" -> {
                    audioCandidates.firstOrNull {
                        it.audioTrackType == AudioTrackType.ORIGINAL
                    } ?: audioCandidates.firstOrNull {
                        it.audioTrackType != AudioTrackType.DUBBED
                    } ?: audioCandidates.firstOrNull()
                }

                else -> {
                    audioCandidates.firstOrNull { audio ->
                        val lang = audio.audioLocale?.language ?: ""
                        lang.startsWith(preferredAudioLanguage, ignoreCase = true)
                    } ?: audioCandidates.firstOrNull {
                        it.audioTrackType == AudioTrackType.ORIGINAL
                    } ?: audioCandidates.firstOrNull()
                }
            }

        val videoStreams =
            videoCandidates
                .filter {
                    val mime = it.format?.mimeType
                    mime?.contains("mp4", ignoreCase = true) == true ||
                        mime?.contains("webm", ignoreCase = true) == true
                }

        val selectedVideoStream =
            when (preferredQuality) {
                // AUTO leaves the choice to adaptive track selection at playback time.
                VideoQuality.AUTO -> {
                    null
                }

                else -> {
                    videoStreams
                        .sortedWith(
                            compareBy<VideoStream> {
                                abs(
                                    QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) -
                                        preferredQuality.height,
                                )
                            }.thenBy { VideoCodecUtils.codecRankWithPreference(it, preferredCodecKey) }
                                .thenByDescending { it.bitrate },
                        ).firstOrNull()
                }
            }
        val videoStream =
            if (audioStream == null && selectedVideoStream == null) {
                // Nothing to pair a video-only stream with, so prefer a muxed one that carries audio.
                videoStreams
                    .sortedWith(
                        compareBy<VideoStream> { if (it.isVideoOnly) 1 else 0 }
                            .thenByDescending { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) }
                            .thenBy { VideoCodecUtils.codecRankWithPreference(it, preferredCodecKey) }
                            .thenByDescending { it.bitrate },
                    ).firstOrNull()
            } else {
                selectedVideoStream
            }
        return videoStream to audioStream
    }
}
