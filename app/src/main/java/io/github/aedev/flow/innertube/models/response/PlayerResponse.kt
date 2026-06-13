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
            )
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
            val initRange: Range? = null,
            val indexRange: Range? = null,
        ) {
            val isAudio: Boolean
                get() = width == null
            val isOriginal: Boolean
                get() = audioTrack?.isAutoDubbed == null

            @Serializable
            data class AudioTrack(
                val displayName: String?,
                val id: String?,
                val isAutoDubbed: Boolean?,
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
