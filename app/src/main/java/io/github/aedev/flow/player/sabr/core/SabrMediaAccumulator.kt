package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.proto.MediaHeader
import io.github.aedev.flow.player.sabr.ump.SabrMediaPayload
import io.github.aedev.flow.player.sabr.ump.UmpFrameDecoder
import java.io.ByteArrayOutputStream

internal data class AccumulatedSabrMedia(
    val header: MediaHeader,
    val isAudio: Boolean,
    val encodedData: ByteArray,
)

internal class SabrMediaAccumulator {
    companion object {
        private const val MAX_ACTIVE_SEGMENTS = 8
        const val AUDIO_LIMIT_BYTES = 4 * 1024 * 1024
        const val VIDEO_LIMIT_BYTES = 32 * 1024 * 1024
        private const val INITIAL_CAPACITY_BYTES = 64 * 1024
        private const val TOTAL_LIMIT_BYTES = AUDIO_LIMIT_BYTES + VIDEO_LIMIT_BYTES

        private val knownAudioItags =
            setOf(
                139,
                140,
                141, // AAC
                171,
                172, // Vorbis
                249,
                250,
                251, // Opus
                256,
                258, // AAC HE
                327,
                328, // AAC surround
                338, // WebM Opus surround
                380,
                381, // AC-3
            )

        fun isKnownAudioItag(itag: Int): Boolean = itag in knownAudioItags
    }

    private val headers = mutableMapOf<Int, MediaHeader>()
    private val segments = mutableMapOf<Int, ByteArrayOutputStream>()
    private val audioSegments = mutableMapOf<Int, Boolean>()
    private var audioBytes = 0L
    private var videoBytes = 0L

    val incompleteCount: Int
        @Synchronized get() = headers.size

    @Synchronized
    fun begin(
        header: MediaHeader,
        isAudio: Boolean,
    ): Boolean {
        val previous = segments.remove(header.headerId)
        val replaced = previous != null
        if (previous != null) {
            release(previous.size(), audioSegments.remove(header.headerId) == true)
            headers.remove(header.headerId)
        }
        if (headers.size >= MAX_ACTIVE_SEGMENTS) {
            throw UmpFrameDecoder.ResourceLimitExceeded(
                "More than $MAX_ACTIVE_SEGMENTS media segments are incomplete",
                itag = header.itag,
            )
        }
        headers[header.headerId] = header
        segments[header.headerId] = ByteArrayOutputStream(initialCapacity(header.contentLength))
        audioSegments[header.headerId] = isAudio
        return replaced
    }

    @Synchronized
    fun append(framePayload: ByteArray) {
        if (framePayload.isEmpty()) return
        val headerId = SabrMediaPayload.headerId(framePayload) ?: return
        val offset = SabrMediaPayload.dataOffset(framePayload)
        val incomingBytes = framePayload.size - offset
        if (incomingBytes <= 0) return
        val accumulator = segments[headerId] ?: return
        val itag = headers[headerId]?.itag
        val isAudio = audioSegments[headerId] == true
        val trackBytes = if (isAudio) audioBytes else videoBytes
        val trackLimit = if (isAudio) AUDIO_LIMIT_BYTES else VIDEO_LIMIT_BYTES
        if (incomingBytes > trackLimit - trackBytes) {
            throw UmpFrameDecoder.ResourceLimitExceeded(
                "${if (isAudio) "Audio" else "Video"} media exceeds $trackLimit encoded bytes",
                itag = itag,
            )
        }
        if (incomingBytes > TOTAL_LIMIT_BYTES - audioBytes - videoBytes) {
            throw UmpFrameDecoder.ResourceLimitExceeded(
                "Incomplete SABR media exceeds the total accumulator limit",
                itag = itag,
            )
        }
        accumulator.write(framePayload, offset, incomingBytes)
        if (isAudio) {
            audioBytes += incomingBytes
        } else {
            videoBytes += incomingBytes
        }
    }

    @Synchronized
    fun finish(headerId: Int): AccumulatedSabrMedia? {
        val header = headers.remove(headerId) ?: return null
        val accumulator =
            segments.remove(headerId) ?: run {
                audioSegments.remove(headerId)
                return null
            }
        val isAudio = audioSegments.remove(headerId) ?: false
        release(accumulator.size(), isAudio)
        return AccumulatedSabrMedia(header, isAudio, accumulator.toByteArray())
    }

    @Synchronized
    fun clear() {
        headers.clear()
        segments.clear()
        audioSegments.clear()
        audioBytes = 0L
        videoBytes = 0L
    }

    private fun release(
        bytes: Int,
        isAudio: Boolean,
    ) {
        if (isAudio) {
            audioBytes = (audioBytes - bytes).coerceAtLeast(0L)
        } else {
            videoBytes = (videoBytes - bytes).coerceAtLeast(0L)
        }
    }

    private fun initialCapacity(contentLength: Long): Int =
        if (contentLength > 0) {
            contentLength.coerceAtMost(INITIAL_CAPACITY_BYTES.toLong()).toInt()
        } else {
            INITIAL_CAPACITY_BYTES
        }
}
