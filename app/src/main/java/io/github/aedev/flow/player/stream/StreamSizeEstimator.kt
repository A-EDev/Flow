package io.github.aedev.flow.player.stream

import io.github.aedev.flow.innertube.models.response.PlayerResponse

object StreamSizeEstimator {
    private const val BITS_PER_BYTE = 8

    /**
     * @param fallbackDurationMs used when a format carries no `approxDurationMs` of its own
     *   (SABR responses drop it alongside `contentLength`). Pass 0 when unknown.
     */
    fun fromInnerTubeFormats(
        videoFormats: List<PlayerResponse.StreamingData.Format>,
        audioFormats: List<PlayerResponse.StreamingData.Format>,
        fallbackDurationMs: Long = 0L,
    ): Map<String, Long> {
        if (videoFormats.isEmpty()) return emptyMap()

        fun sizeOf(format: PlayerResponse.StreamingData.Format): Long {
            format.contentLength?.takeIf { it > 0L }?.let { return it }
            val durationMs = format.approxDurationMs?.toLongOrNull()?.takeIf { it > 0L } ?: fallbackDurationMs
            return estimateBytes(format.averageBitrate ?: format.bitrate, durationMs)
        }

        val audible = audioFormats.filter { it.isAudio }
        val bestMp4Audio = audible.filter { isMp4(it.mimeType) }.maxByOrNull { it.bitrate }?.let(::sizeOf) ?: 0L
        val bestAnyAudio = audible.maxByOrNull { it.bitrate }?.let(::sizeOf) ?: 0L
        // Downloads mux AAC next to every video codec and fall back to other audio only without it.
        val pairedAudio = if (bestMp4Audio > 0L) bestMp4Audio else bestAnyAudio

        val sizes = mutableMapOf<String, Long>()
        videoFormats.forEach { format ->
            if (format.isAudio) return@forEach
            val height = format.height ?: return@forEach
            val videoBytes = sizeOf(format)
            if (videoBytes <= 0L) return@forEach
            sizes.keepLargest(
                key =
                    VideoCodecUtils.streamSizeKey(
                        VideoCodecUtils.qualityHeightFromFormat(format.qualityLabel, height),
                        VideoCodecUtils.codecKeyFromMimeType(format.mimeType),
                    ),
                bytes = videoBytes + pairedAudio,
            )
        }
        return sizes
    }

    private fun MutableMap<String, Long>.keepLargest(
        key: String,
        bytes: Long,
    ) {
        if (bytes > (this[key] ?: 0L)) this[key] = bytes
    }

    private fun estimateBytes(
        bitrateBitsPerSecond: Int,
        durationMs: Long,
    ): Long =
        if (bitrateBitsPerSecond <= 0 || durationMs <= 0L) {
            0L
        } else {
            bitrateBitsPerSecond.toLong() * durationMs / (BITS_PER_BYTE * 1000L)
        }

    private fun isMp4(mimeType: String): Boolean = mimeType.contains("mp4", ignoreCase = true)
}
