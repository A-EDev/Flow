package io.github.aedev.flow.data.video

import io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.stream.preferNonDrc
import java.util.Locale

/**
 * Which formats a download offers and which audio track it pairs with a video track. Pure policy:
 * no Compose, no resources, no Android UI, so every rule here is unit-testable.
 */
object DownloadStreamPolicy {
    /**
     * Codec order within one resolution for a *download*, which is deliberately not
     * [VideoCodecUtils.playbackCodecRank]: playback ranks h264 first because it is the codec every
     * decoder handles, while a download ranks vp9 first because it is the smallest file the app can
     * mux reliably, and av1 above the legacy vp8/hevc ladder.
     */
    val DOWNLOAD_CODEC_PRIORITY = mapOf("vp9" to 0, "h264" to 1, "av1" to 2, "vp8" to 3, "hevc" to 4)

    private const val UNRANKED_CODEC = 99
    private const val AUDIO_MP4 = "audio/mp4"
    private const val AUDIO_WEBM = "audio/webm"
    private val AUDIO_CONTAINERS = setOf(AUDIO_MP4, AUDIO_WEBM)
    private val VIDEO_CONTAINERS = setOf("video/mp4", "video/webm", "video/3gpp")

    /** Codecs the MP4 muxer takes only with AAC; the Matroska writer takes any audio. */
    private val AAC_ONLY_VIDEO_CODECS = setOf("h264", "hevc")

    fun videoHeight(format: Format): Int = VideoCodecUtils.qualityHeightFromFormat(format.qualityLabel, format.height ?: 0)

    fun videoCodecKey(format: Format): String = VideoCodecUtils.codecKeyFromMimeType(format.mimeType)

    fun isHdr(format: Format): Boolean = format.colorInfo?.isHdr == true

    /** "VP9 1080p", "VP9 1080p60", or with [hdrLabel] appended for an HDR format. */
    fun videoQualityLabel(
        format: Format,
        hdrLabel: String,
    ): String {
        val base =
            "${VideoCodecUtils.codecLabelFromKey(videoCodecKey(format))} " +
                VideoCodecUtils.qualityLabelWithFrameRate(videoHeight(format), format.fps ?: 0)
        return if (isHdr(format)) "$base $hdrLabel" else base
    }

    /**
     * The video ladder both download dialogs show: one entry per (resolution, codec, frame rate,
     * HDR), highest resolution first, [DOWNLOAD_CODEC_PRIORITY] within a resolution, then SDR before
     * HDR and the higher frame rate first. Formats with no URL are dropped: selecting one can only
     * fail.
     */
    fun buildDownloadVideoFormats(formats: List<Format>): List<Format> =
        formats
            .filter { !it.url.isNullOrBlank() && it.height != null && containerOf(it.mimeType) in VIDEO_CONTAINERS }
            .distinctBy { listOf(videoHeight(it), videoCodecKey(it), it.fps ?: 0, isHdr(it)) }
            .sortedWith(
                compareByDescending<Format> { videoHeight(it) }
                    .thenBy { DOWNLOAD_CODEC_PRIORITY[videoCodecKey(it)] ?: UNRANKED_CODEC }
                    .thenBy { isHdr(it) }
                    .thenByDescending { it.fps ?: 0 },
            )

    private fun audioBitrate(format: Format): Int = format.averageBitrate ?: format.bitrate

    fun audioBitrateKbps(format: Format): Int {
        val raw = format.averageBitrate?.takeIf { it > 0 } ?: format.bitrate
        return if (raw > 1000) raw / 1000 else raw.coerceAtLeast(0)
    }

    fun audioFormatLabel(
        format: Format,
        unknownLabel: String = "",
    ): String =
        when (containerOf(format.mimeType)) {
            AUDIO_WEBM -> if ("opus" in format.mimeType.lowercase()) "OPUS" else "WEBM"
            AUDIO_MP4 -> "M4A"
            else -> unknownLabel
        }

    fun audioFileExtension(format: Format): String = if (containerOf(format.mimeType) == AUDIO_WEBM) "webm" else "m4a"

    /** The container type the download service records, without the codecs parameter. */
    fun audioContainerMimeType(format: Format): String = containerOf(format.mimeType)

    fun audioLanguageLabel(format: Format): String? =
        format.audioTrack?.displayName?.takeIf { it.isNotBlank() }
            ?: audioLocale(format)?.displayLanguage?.takeIf { it.isNotBlank() }
            ?: format.audioTrack?.id?.takeIf { it.isNotBlank() }

    fun audioTrackTypeLabel(
        format: Format,
        originalLabel: String,
        dubbedLabel: String,
    ): String = if (format.isOriginal) originalLabel else dubbedLabel

    private fun audioFormatSortRank(format: Format): Int = if (containerOf(format.mimeType) == AUDIO_WEBM) 0 else 1

    /**
     * The audio formats a download can use: DRC twins dropped, URL-less and unknown containers
     * dropped, one entry per (format, bitrate, track, language, role), Opus first, then by bitrate.
     */
    fun buildDownloadAudioFormats(formats: List<Format>): List<Format> =
        formats
            .preferNonDrc()
            .filter { !it.url.isNullOrBlank() && containerOf(it.mimeType) in AUDIO_CONTAINERS }
            .distinctBy { format ->
                listOf(
                    audioFormatLabel(format),
                    audioBitrateKbps(format).toString(),
                    format.audioTrack?.id.orEmpty(),
                    audioLocale(format)?.toLanguageTag().orEmpty(),
                    format.isOriginal.toString(),
                ).joinToString("|")
            }.sortedWith(
                compareBy<Format> { audioFormatSortRank(it) }
                    .thenByDescending { audioBitrateKbps(it) }
                    .thenBy { audioLocale(it)?.displayLanguage.orEmpty() },
            )

    /**
     * The audio muxed with a video download. AAC is preferred for every video codec so the file can
     * be an MP4; Opus is taken only when the chosen language has no AAC and the video codec is one
     * the Matroska writer muxes, since the MP4 muxer rejects Opus next to h264/hevc.
     */
    fun pickCompatibleAudioForVideo(
        videoCodecKey: String,
        allAudio: List<Format>,
        preferredLang: String?,
    ): Format? {
        if (allAudio.isEmpty()) return null

        val langFilteredAudio =
            if (!preferredLang.isNullOrEmpty() && preferredLang != "original") {
                val langMatches =
                    allAudio.filter {
                        val locale = audioLocale(it)
                        locale?.language.equals(preferredLang, ignoreCase = true) ||
                            locale?.toLanguageTag().equals(preferredLang, ignoreCase = true)
                    }
                langMatches.ifEmpty { allAudio }
            } else {
                allAudio.filter { it.isOriginal }.ifEmpty { allAudio }
            }

        val aac = langFilteredAudio.filter(::isAac).maxByOrNull(::audioBitrate)
        return if (videoCodecKey in AAC_ONLY_VIDEO_CODECS) {
            aac ?: allAudio.filter(::isAac).maxByOrNull(::audioBitrate)
        } else {
            aac ?: langFilteredAudio.maxByOrNull(::audioBitrate)
        }
    }

    private fun isAac(format: Format): Boolean = containerOf(format.mimeType) == AUDIO_MP4

    private fun containerOf(mimeType: String): String = mimeType.substringBefore(';').trim().lowercase()

    private fun audioLocale(format: Format): Locale? =
        format.audioLanguageTag
            ?.let { tag -> runCatching { Locale.forLanguageTag(tag) }.getOrNull() }
            ?.takeIf { it.language.isNotBlank() }
}
