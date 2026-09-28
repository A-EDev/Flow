package io.github.aedev.flow.player.stream

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.VideoExtractionResult

/** The video's length from its `/player` response, or null when the response does not say. */
internal fun VideoExtractionResult.durationMs(): Long? =
    playerResponse.videoDetails
        ?.lengthSeconds
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?.times(1_000L)

/** Formats that carry a direct URL; the rest need a cipher step nothing downstream performs. */
internal fun VideoExtractionResult.playableVideoFormats() = videoFormats.filter { !it.url.isNullOrBlank() }

internal fun VideoExtractionResult.playableAudioFormats() = audioFormats.filter { !it.url.isNullOrBlank() }

/**
 * Drop a DRC format when its normal twin is present. YouTube ships both at bitrates that differ
 * by a handful of bytes/s, so any downstream "highest bitrate wins" pick would otherwise land on
 * the loudness-flattened copy. Order is preserved so default-track selection is unaffected.
 */
internal fun List<PlayerResponse.StreamingData.Format>.preferNonDrc(): List<PlayerResponse.StreamingData.Format> {
    val normalTwins =
        filterNot { it.isDynamicRangeCompressed }
            .mapTo(mutableSetOf()) { it.itag to it.audioTrack?.id }
    if (normalTwins.isEmpty()) return this
    return filterNot {
        it.isDynamicRangeCompressed && (it.itag to it.audioTrack?.id) in normalTwins
    }
}
