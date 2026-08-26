package io.github.aedev.flow.player.stream

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

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
        val bestWebmAudio = audible.filterNot { isMp4(it.mimeType) }.maxByOrNull { it.bitrate }?.let(::sizeOf) ?: 0L
        val bestAnyAudio = audible.maxByOrNull { it.bitrate }?.let(::sizeOf) ?: 0L

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
                bytes = videoBytes + muxedAudioBytes(isMp4(format.mimeType), bestMp4Audio, bestWebmAudio, bestAnyAudio),
            )
        }
        return sizes
    }

    /**
     * @param durationSeconds the video's duration, used only for streams whose itag carries neither
     *   a content length nor an approximate duration. Pass 0 when unknown.
     */
    fun fromExtractorStreams(
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        durationSeconds: Long = 0L,
    ): Map<String, Long> {
        if (videoStreams.isEmpty()) return emptyMap()
        val fallbackDurationMs = durationSeconds.coerceAtLeast(0L) * 1000L

        fun sizeOf(
            itag: ItagItem?,
            bitrate: Int,
        ): Long {
            itag?.contentLength?.takeIf { it > 0L }?.let { return it }
            val durationMs = itag?.approxDurationMs?.takeIf { it > 0L } ?: fallbackDurationMs
            return estimateBytes(bitrate, durationMs)
        }

        val bestMp4Audio =
            audioStreams
                .filter { isMp4(audioMimeType(it)) }
                .maxByOrNull { audioBitrate(it) }
                ?.let { sizeOf(it.itagItem, audioBitrate(it)) } ?: 0L
        val bestWebmAudio =
            audioStreams
                .filterNot { isMp4(audioMimeType(it)) }
                .maxByOrNull { audioBitrate(it) }
                ?.let { sizeOf(it.itagItem, audioBitrate(it)) } ?: 0L
        val bestAnyAudio =
            audioStreams
                .maxByOrNull { audioBitrate(it) }
                ?.let { sizeOf(it.itagItem, audioBitrate(it)) } ?: 0L

        val sizes = mutableMapOf<String, Long>()
        videoStreams.forEach { stream ->
            val videoBytes = sizeOf(stream.itagItem, stream.bitrate)
            if (videoBytes <= 0L) return@forEach
            val codecKey = VideoCodecUtils.codecKeyFromStream(stream)
            // A muxed stream already carries its audio; only a video-only stream needs one added.
            val audioBytes =
                if (stream.isVideoOnly) {
                    muxedAudioBytes(codecKey == "h264" || codecKey == "hevc", bestMp4Audio, bestWebmAudio, bestAnyAudio)
                } else {
                    0L
                }
            sizes.keepLargest(
                key = VideoCodecUtils.streamSizeKey(VideoCodecUtils.qualityHeightFromStream(stream), codecKey),
                bytes = videoBytes + audioBytes,
            )
        }
        return sizes
    }

    /** Combines per-source maps, keeping the largest estimate for each `(resolution, codec)` pair. */
    fun merge(vararg sources: Map<String, Long>): Map<String, Long> {
        val merged = mutableMapOf<String, Long>()
        sources.forEach { source -> source.forEach { (key, bytes) -> merged.keepLargest(key, bytes) } }
        return merged
    }

    private fun muxedAudioBytes(
        isMp4Video: Boolean,
        bestMp4Audio: Long,
        bestWebmAudio: Long,
        bestAnyAudio: Long,
    ): Long =
        when {
            isMp4Video && bestMp4Audio > 0L -> bestMp4Audio
            !isMp4Video && bestWebmAudio > 0L -> bestWebmAudio
            else -> bestAnyAudio
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

    private fun audioMimeType(stream: AudioStream): String = stream.format?.mimeType.orEmpty()

    private fun audioBitrate(stream: AudioStream): Int = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate
}
