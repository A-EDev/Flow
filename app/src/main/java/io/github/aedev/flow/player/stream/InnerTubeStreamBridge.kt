package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import java.util.Locale

object InnerTubeStreamBridge {
    private const val TAG = "InnerTubeStreamBridge"

    fun convertVideoFormats(
        formats: List<PlayerResponse.StreamingData.Format>
    ): List<VideoStream> {
        return formats.mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            val height = format.height ?: return@mapNotNull null
            val mediaFormat = mapVideoMimeToMediaFormat(format.mimeType) ?: return@mapNotNull null
            val resolutionLabel = format.qualityLabel
                ?.takeIf { it.isNotBlank() }
                ?: "${height}p"

            try {
                VideoStream.Builder()
                    .setId(format.itag.toString())
                    .setItagItem(buildItagItem(format, isAudio = false))
                    .setContent(url, true)
                    .setMediaFormat(mediaFormat)
                    .setResolution(resolutionLabel)
                    .setIsVideoOnly(true)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build VideoStream for itag=${format.itag}: ${e.message}")
                null
            }
        }
    }

    fun convertAudioFormats(
        formats: List<PlayerResponse.StreamingData.Format>
    ): List<AudioStream> {
        return formats.preferNonDrc().mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            val mediaFormat = mapAudioMimeToMediaFormat(format.mimeType) ?: return@mapNotNull null
            val bitrate = format.averageBitrate ?: format.bitrate

            try {
                AudioStream.Builder()
                    .setId(format.itag.toString())
                    .setItagItem(buildItagItem(format, isAudio = true))
                    .setContent(url, true)
                    .setMediaFormat(mediaFormat)
                    .setAverageBitrate(bitrate)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .applyAudioTrackMetadata(format)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build AudioStream for itag=${format.itag}: ${e.message}")
                null
            }
        }
    }

    /**
     * Drop a DRC format when its normal twin is present. YouTube ships both at bitrates that differ
     * by a handful of bytes/s, so any downstream "highest bitrate wins" pick would otherwise land on
     * the loudness-flattened copy. Order is preserved so default-track selection is unaffected.
     */
    private fun List<PlayerResponse.StreamingData.Format>.preferNonDrc():
        List<PlayerResponse.StreamingData.Format> {
        val normalTwins = filterNot { it.isDynamicRangeCompressed }
            .mapTo(mutableSetOf()) { it.itag to it.audioTrack?.id }
        if (normalTwins.isEmpty()) return this
        return filterNot {
            it.isDynamicRangeCompressed && (it.itag to it.audioTrack?.id) in normalTwins
        }
    }

    private fun AudioStream.Builder.applyAudioTrackMetadata(
        format: PlayerResponse.StreamingData.Format
    ): AudioStream.Builder {
        setAudioTrackType(if (format.isOriginal) AudioTrackType.ORIGINAL else AudioTrackType.DUBBED)
        format.audioTrack?.let { track ->
            track.id?.takeIf { it.isNotBlank() }?.let { setAudioTrackId(it) }
            track.displayName?.takeIf { it.isNotBlank() }?.let { setAudioTrackName(it) }
        }
        format.audioLanguageTag?.let { tag ->
            runCatching { Locale.forLanguageTag(tag) }
                .getOrNull()
                ?.takeIf { it.language.isNotBlank() }
                ?.let { setAudioLocale(it) }
        }
        return this
    }

    private fun mapVideoMimeToMediaFormat(mimeType: String): MediaFormat? {
        val mime = mimeType.lowercase()
        return when {
            mime.startsWith("video/mp4") -> MediaFormat.MPEG_4
            mime.startsWith("video/webm") -> MediaFormat.WEBM
            mime.startsWith("video/3gpp") -> MediaFormat.v3GPP
            else -> {
                Log.d(TAG, "Unknown video MIME: $mimeType")
                null
            }
        }
    }

    private fun mapAudioMimeToMediaFormat(mimeType: String): MediaFormat? {
        val mime = mimeType.lowercase()
        return when {
            mime.startsWith("audio/mp4") -> MediaFormat.M4A
            mime.startsWith("audio/webm") && mime.contains("opus") -> MediaFormat.WEBMA_OPUS
            mime.startsWith("audio/webm") -> MediaFormat.WEBMA
            else -> {
                Log.d(TAG, "Unknown audio MIME: $mimeType")
                null
            }
        }
    }

    private fun buildItagItem(
        format: PlayerResponse.StreamingData.Format,
        isAudio: Boolean
    ): ItagItem? {
        val item = try {
            ItagItem.getItag(format.itag)
        } catch (e: Exception) {
            Log.d(TAG, "No NewPipe ItagItem metadata for itag=${format.itag}: ${e.message}")
            return null
        }

        VideoCodecUtils.codecStringFromMimeType(format.mimeType)
            .takeIf { it.isNotBlank() }
            ?.let { item.codec = it }
        item.bitrate = format.averageBitrate ?: format.bitrate
        format.contentLength?.let { item.contentLength = it }
        format.approxDurationMs?.toLongOrNull()?.let { item.approxDurationMs = it }
        format.lastModified?.let { item.lastModified = it }
        format.initRange?.let { range ->
            range.start?.toIntOrNull()?.let { item.initStart = it }
            range.end?.toIntOrNull()?.let { item.initEnd = it }
        }
        format.indexRange?.let { range ->
            range.start?.toIntOrNull()?.let { item.indexStart = it }
            range.end?.toIntOrNull()?.let { item.indexEnd = it }
        }

        if (isAudio) {
            format.audioChannels?.let { item.audioChannels = it }
            format.audioSampleRate?.let { item.sampleRate = it }
        } else {
            format.width?.let { item.width = it }
            format.height?.let { item.height = it }
            format.fps?.let { item.fps = it }
            item.quality = format.quality
        }
        return item
    }
}
