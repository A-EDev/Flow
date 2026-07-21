package io.github.aedev.flow.innertube.models.response

import io.github.aedev.flow.innertube.models.ResponseContext
import io.github.aedev.flow.innertube.models.Thumbnails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PlayerResponse with [io.github.aedev.flow.innertube.models.YouTubeClient.WEB_REMIX] client
 */
@Serializable
data class PlayerResponse(
    val responseContext: ResponseContext,
    val playabilityStatus: PlayabilityStatus,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
    val captions: Captions? = null,
    @SerialName("playbackTracking")
    val playbackTracking: PlaybackTracking?,
) {
    @Serializable
    data class Captions(
        val playerCaptionsTracklistRenderer: PlayerCaptionsTracklistRenderer? = null,
    ) {
        @Serializable
        data class PlayerCaptionsTracklistRenderer(
            val captionTracks: List<CaptionTrack>? = null,
            val translationLanguages: List<TranslationLanguage>? = null,
        )

        @Serializable
        data class CaptionTrack(
            val baseUrl: String? = null,
            val name: Text? = null,
            val languageCode: String? = null,
            val kind: String? = null,            // "asr" = auto-generated
            val isTranslatable: Boolean? = null,
        )

        @Serializable
        data class TranslationLanguage(
            val languageCode: String? = null,
            val languageName: Text? = null,
        )

        @Serializable
        data class Text(
            val simpleText: String? = null,
            val runs: List<Run>? = null,
        ) {
            @Serializable
            data class Run(val text: String? = null)

            val text: String? get() = simpleText ?: runs?.joinToString("") { it.text.orEmpty() }
        }
    }

    @Serializable
    data class PlayabilityStatus(
        val status: String,
        val reason: String?,
        val liveStreamability: LiveStreamability? = null,
    ) {
        @Serializable
        data class LiveStreamability(
            val liveStreamabilityRenderer: LiveStreamabilityRenderer? = null,
        ) {
            @Serializable
            data class LiveStreamabilityRenderer(
                val videoId: String? = null,
                val offlineSlate: OfflineSlate? = null,
            ) {
                @Serializable
                data class OfflineSlate(
                    val liveStreamOfflineSlateRenderer: LiveStreamOfflineSlateRenderer? = null,
                ) {
                    @Serializable
                    data class LiveStreamOfflineSlateRenderer(
                        val scheduledStartTime: String? = null,
                    )
                }
            }
        }
    }

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig? = null,
        val mediaCommonConfig: MediaCommonConfig? = null,
    ) {
        @Serializable
        data class AudioConfig(
            val loudnessDb: Double?,
            val perceptualLoudnessDb: Double?,
        )

        @Serializable
        data class MediaCommonConfig(
            val mediaUstreamerRequestConfig: MediaUstreamerRequestConfig? = null,
        ) {
            @Serializable
            data class MediaUstreamerRequestConfig(
                val videoPlaybackUstreamerConfig: String? = null,
            )
        }
    }

    @Serializable
    data class StreamingData(
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format> = emptyList(),
        val expiresInSeconds: Int = 0,
        val serverAbrStreamingUrl: String? = null,
        val hlsManifestUrl: String? = null,
        val dashManifestUrl: String? = null,
    ) {
        @Serializable
        data class Format(
            val itag: Int,
            val url: String?,
            val mimeType: String,
            val bitrate: Int,
            val width: Int?,
            val height: Int?,
            val contentLength: Long?,
            val quality: String,
            val fps: Int?,
            val qualityLabel: String?,
            val averageBitrate: Int?,
            val audioQuality: String?,
            val approxDurationMs: String?,
            val audioSampleRate: Int?,
            val audioChannels: Int?,
            val loudnessDb: Double?,
            val lastModified: Long?,
            val signatureCipher: String?,
            val cipher: String? = null,
            val audioTrack: AudioTrack? = null,
            val isDrc: Boolean? = null,
            val xtags: String? = null,
            val trackAbsoluteLoudnessLkfs: Double? = null,
            val initRange: Range? = null,
            val indexRange: Range? = null,
        ) {
            val isAudio: Boolean
                get() = width == null

            /**
             * Dynamic-range-compressed variant. YouTube ships DRC and non-DRC copies of the same
             * itag+track (identical bitrate to within a few bytes/s), so bitrate alone cannot tell
             * them apart — without this flag the DRC copy wins a bitrate sort and the user silently
             * gets loudness-flattened audio.
             */
            val isDynamicRangeCompressed: Boolean
                get() = isDrc == true || audioContentTags["drc"] == "1"

            /**
             * YouTube's authoritative audio role, decoded from `xtags` (e.g. acont=original vs
             * acont=dubbed). Preferred over the audioTrack id suffix, which is only a convention.
             */
            val isOriginal: Boolean
                get() {
                    audioContentTags["acont"]?.let { return it.equals("original", ignoreCase = true) }
                    val track = audioTrack ?: return true
                    if (track.isAutoDubbed == true) return false
                    track.audioIsDefault?.let { return it }
                    track.displayName?.let { if (it.contains("original", ignoreCase = true)) return true }
                    val id = track.id ?: return true
                    return id.substringAfterLast('.', missingDelimiterValue = "4") == "4"
                }

            val audioLanguageTag: String?
                get() = audioTrack?.id
                    ?.substringBeforeLast('.', missingDelimiterValue = "")
                    ?.takeIf { it.isNotBlank() }
                    ?: audioContentTags["lang"]

            private val audioContentTags: Map<String, String>
                get() = xtags?.takeIf { it.isNotBlank() }?.let(AudioXTags::decode).orEmpty()

            @Serializable
            data class AudioTrack(
                val displayName: String?,
                val id: String?,
                val isAutoDubbed: Boolean? = null,
                val audioIsDefault: Boolean? = null,
            )

            @Serializable
            data class Range(
                val start: String? = null,
                val end: String? = null,
            )
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String,
        val title: String?,
        val author: String?,
        val channelId: String,
        val lengthSeconds: String = "0",
        val musicVideoType: String? = null,
        val viewCount: String? = null,
        val thumbnail: Thumbnails? = null,
        val isLive: Boolean? = null,
        val isLiveContent: Boolean? = null,
        val isLiveDvrEnabled: Boolean? = null,
        val isPostLiveDvr: Boolean? = null,
    )

    @Serializable
    data class PlaybackTracking(
        @SerialName("videostatsPlaybackUrl")
        val videostatsPlaybackUrl: VideostatsPlaybackUrl?,
        @SerialName("videostatsWatchtimeUrl")
        val videostatsWatchtimeUrl: VideostatsWatchtimeUrl?,
        @SerialName("atrUrl")
        val atrUrl: AtrUrl?,
    ) {
        @Serializable
        data class VideostatsPlaybackUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )
        @Serializable
        data class VideostatsWatchtimeUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )
        @Serializable
        data class AtrUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )
    }
}
