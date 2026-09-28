package io.github.aedev.flow.data.video.downloader.resolve

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.data.video.DefaultDownloadSelection
import io.github.aedev.flow.data.video.DownloadStreamPolicy
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.VideoExtractionResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The streams one run of a download fetches. */
data class ResolvedStreams(
    val video: Format?,
    val audio: Format,
    val durationMs: Long,
)

sealed interface ResolveOutcome {
    data class Resolved(
        val streams: ResolvedStreams,
    ) : ResolveOutcome

    /** Nothing downloadable: the video is gone, blocked, or offers no stream this request can use. */
    data object Unavailable : ResolveOutcome

    /** Only Opus audio exists, which the MP4 writer does not take. */
    data object NoCompatibleAudio : ResolveOutcome
}

/**
 * Turns a [DownloadRequest] into streams with fresh URLs, through the same InnerTube client ladder
 * playback uses (coalesced with it, so a download of the video on screen costs no second request).
 */
@Singleton
class DownloadStreamResolver
    @Inject
    constructor(
        private val preferences: PlayerPreferences,
    ) {
        suspend fun resolve(
            request: DownloadRequest,
            avoidItags: Set<Int> = emptySet(),
        ): ResolveOutcome {
            val result = InnerTubeVideoStreamExtractor.extract(request.videoId) ?: return ResolveOutcome.Unavailable
            if (result.isLive) return ResolveOutcome.Unavailable
            return select(request, result, avoidItags, defaults())
        }

        private suspend fun defaults(): SelectionDefaults =
            SelectionDefaults(
                height = preferences.defaultDownloadQuality.first().height,
                codec =
                    preferences.defaultDownloadCodec
                        .first()
                        .takeIf { it != VideoCodec.AUTO }
                        ?.codecKey,
                language = preferences.preferredAudioLanguage.first(),
            )

        internal data class SelectionDefaults(
            val height: Int,
            val codec: String?,
            val language: String?,
        )

        internal companion object {
            /** The pure choice, separate from extraction so every rule is unit tested. */
            fun select(
                request: DownloadRequest,
                result: VideoExtractionResult,
                avoidItags: Set<Int>,
                defaults: SelectionDefaults,
            ): ResolveOutcome {
                val audioFormats =
                    DownloadStreamPolicy
                        .buildDownloadAudioFormats(result.audioFormats)
                        .filterNot { it.itag in avoidItags }
                val language = request.audioLanguage ?: defaults.language
                val durationMs = result.durationMs()

                val audio =
                    request.audioItag
                        ?.let { itag -> audioFormats.firstOrNull { it.itag == itag } }
                        ?.takeIf { DownloadStreamPolicy.isAacFormat(it) }
                        ?: request.audioTrackId?.let { id ->
                            DownloadStreamPolicy.pickAacAudio(audioFormats.filter { it.audioTrack?.id == id }, language)
                        }
                        ?: DownloadStreamPolicy.pickAacAudio(audioFormats, language)
                        ?: return if (result.audioFormats.any { !it.url.isNullOrBlank() }) {
                            ResolveOutcome.NoCompatibleAudio
                        } else {
                            ResolveOutcome.Unavailable
                        }

                if (request.wantsAudioOnly) {
                    return ResolveOutcome.Resolved(ResolvedStreams(video = null, audio = audio, durationMs = durationMs))
                }

                val videoFormats =
                    DownloadStreamPolicy
                        .buildDownloadVideoFormats(result.videoFormats)
                        .filterNot { it.itag in avoidItags }
                request.videoItag
                    ?.let { itag -> videoFormats.firstOrNull { it.itag == itag } }
                    ?.let { return ResolveOutcome.Resolved(ResolvedStreams(it, audio, durationMs)) }

                val height =
                    DefaultDownloadSelection.pickHeight(
                        videoFormats.map(DownloadStreamPolicy::videoHeight),
                        request.targetHeight ?: defaults.height,
                    ) ?: return ResolveOutcome.Unavailable
                val atHeight = videoFormats.filter { DownloadStreamPolicy.videoHeight(it) == height }
                val codec =
                    DefaultDownloadSelection
                        .rankCodecs(atHeight.map(DownloadStreamPolicy::videoCodecKey), request.videoCodec ?: defaults.codec)
                        .firstOrNull() ?: return ResolveOutcome.Unavailable
                val video = atHeight.first { DownloadStreamPolicy.videoCodecKey(it) == codec }
                return ResolveOutcome.Resolved(ResolvedStreams(video, audio, durationMs))
            }

            private fun VideoExtractionResult.durationMs(): Long =
                (audioFormats + videoFormats).firstNotNullOfOrNull { it.approxDurationMs?.toLongOrNull() }
                    ?: playerResponse.videoDetails
                        ?.lengthSeconds
                        ?.toLongOrNull()
                        ?.times(1000L) ?: 0L
        }
    }
