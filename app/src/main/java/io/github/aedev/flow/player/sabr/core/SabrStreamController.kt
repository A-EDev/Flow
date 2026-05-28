package io.github.aedev.flow.player.sabr.core

import android.util.Log
import io.github.aedev.flow.player.sabr.network.SabrDataSource
import io.github.aedev.flow.player.sabr.proto.FormatBufferedRange
import io.github.aedev.flow.player.sabr.proto.FormatId
import io.github.aedev.flow.player.sabr.proto.FormatInitializationMetadata
import io.github.aedev.flow.player.sabr.proto.MediaHeader
import io.github.aedev.flow.player.sabr.proto.NextRequestPolicy
import io.github.aedev.flow.player.sabr.proto.PlaybackStartPolicy
import io.github.aedev.flow.player.sabr.proto.SabrContextUpdate
import io.github.aedev.flow.player.sabr.proto.SabrError
import io.github.aedev.flow.player.sabr.proto.SabrRedirect
import io.github.aedev.flow.player.sabr.proto.SabrSeek
import io.github.aedev.flow.player.sabr.proto.StreamProtectionStatus
import io.github.aedev.flow.player.sabr.ump.UmpFrame
import io.github.aedev.flow.player.sabr.ump.UmpFrameDecoder
import io.github.aedev.flow.player.sabr.ump.UmpPartType
import io.github.aedev.flow.player.sabr.ump.UmpVarInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

data class SabrSegment(
    val headerId: Int,
    val itag: Int,
    val videoId: String,
    val isAudio: Boolean,
    val timeRangeStartMs: Long,
    val durationMs: Long,
    val sequenceNumber: Int,
    val data: ByteArray
)

sealed class SabrEvent {
    data class SegmentReady(val segment: SabrSegment) : SabrEvent()
    data class FormatInitialized(val metadata: FormatInitializationMetadata) : SabrEvent()
    data class Redirect(val newUrl: String) : SabrEvent()
    data class Error(val code: Int, val message: String, val recoverable: Boolean) : SabrEvent()
    data class BackoffRequired(val delayMs: Long) : SabrEvent()
    object EndOfTrack : SabrEvent()
    data class ReloadRequired(val reason: String) : SabrEvent()
    data class SeekDirective(val targetMs: Long) : SabrEvent()
}

class SabrStreamController(
    private val dataSource: SabrDataSource,
    val sessionState: SabrSessionState = SabrSessionState()
) {
    companion object {
        private const val TAG = "SabrStreamCtrl"
        private const val READ_BUFFER_SIZE = 16384

        private val KNOWN_AUDIO_ITAGS = setOf(
            139, 140, 141, // AAC
            171, 172,      // Vorbis
            249, 250, 251, // Opus
            256, 258,      // AAC HE
            327, 328,      // AAC surround
            338,           // WebM Opus surround
            380, 381       // AC-3
        )
    }

    private val _events = MutableSharedFlow<SabrEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SabrEvent> = _events.asSharedFlow()

    private val frameDecoder = UmpFrameDecoder()

    private val activeHeaders = mutableMapOf<Int, MediaHeader>()
    private val segmentAccumulators = mutableMapOf<Int, ByteArrayOutputStream>()

    @Volatile
    private var aborted = false

    suspend fun startSession() {
        aborted = false
        sessionState.requestSequence = 0
        frameDecoder.reset()

        Log.d(TAG, "Starting SABR session: video=${sessionState.videoId}, " +
            "audioItag=${sessionState.selectedAudioItag}, videoItag=${sessionState.selectedVideoItag}")

        val body = SabrRequestBuilder.buildInitialRequest(sessionState)
        fetchAndProcessResponse(body)
    }

    suspend fun requestNextSegments() {
        if (aborted) return

        val now = System.currentTimeMillis()
        if (sessionState.backoffDeadlineMs > now) {
            val waitMs = sessionState.backoffDeadlineMs - now
            Log.d(TAG, "Backing off for ${waitMs}ms")
            _events.emit(SabrEvent.BackoffRequired(waitMs))
            delay(waitMs)
        }

        val body = SabrRequestBuilder.buildFollowUpRequest(sessionState)
        fetchAndProcessResponse(body)
    }

    fun updatePlayheadPosition(positionMs: Long) {
        sessionState.playheadPositionMs = positionMs
    }

    fun selectFormats(audioItag: Int, audioLmt: Long, videoItag: Int, videoLmt: Long) {
        sessionState.selectedAudioItag = audioItag
        sessionState.selectedAudioLmt = audioLmt
        sessionState.selectedVideoItag = videoItag
        sessionState.selectedVideoLmt = videoLmt
    }

    fun abort() {
        aborted = true
        dataSource.close()
    }

    fun release() {
        abort()
        frameDecoder.reset()
        activeHeaders.clear()
        segmentAccumulators.clear()
        dataSource.release()
    }

    private suspend fun fetchAndProcessResponse(requestBody: ByteArray) {
        withContext(Dispatchers.IO) {
            var stream: InputStream? = null
            try {
                stream = dataSource.open(sessionState.effectiveUrl, requestBody)
                readAndProcessStream(stream)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!aborted) {
                    Log.e(TAG, "SABR fetch error", e)
                    _events.emit(SabrEvent.Error(
                        code = -1,
                        message = e.message ?: "Unknown error",
                        recoverable = true
                    ))
                }
            } finally {
                dataSource.close()
            }
        }
    }

    private suspend fun readAndProcessStream(stream: InputStream) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (coroutineContext.isActive && !aborted) {
            val bytesRead = stream.read(buffer)
            if (bytesRead == -1) break

            frameDecoder.feed(buffer, 0, bytesRead)

            while (frameDecoder.hasNext()) {
                val frame = frameDecoder.next()
                dispatchFrame(frame)
            }
        }
    }

    private suspend fun dispatchFrame(frame: UmpFrame) {
        when (frame.type) {
            UmpPartType.MEDIA_HEADER -> handleMediaHeader(frame)
            UmpPartType.MEDIA -> handleMedia(frame)
            UmpPartType.MEDIA_END -> handleMediaEnd(frame)
            UmpPartType.FORMAT_INITIALIZATION_METADATA -> handleFormatInit(frame)
            UmpPartType.NEXT_REQUEST_POLICY -> handleNextRequestPolicy(frame)
            UmpPartType.SABR_REDIRECT -> handleRedirect(frame)
            UmpPartType.SABR_ERROR -> handleError(frame)
            UmpPartType.SABR_SEEK -> handleSeek(frame)
            UmpPartType.SABR_CONTEXT_UPDATE -> handleContextUpdate(frame)
            UmpPartType.STREAM_PROTECTION_STATUS -> handleProtectionStatus(frame)
            UmpPartType.RELOAD_PLAYER_RESPONSE -> handleReloadRequired(frame)
            UmpPartType.END_OF_TRACK -> handleEndOfTrack()
            UmpPartType.PLAYBACK_START_POLICY -> handlePlaybackStartPolicy(frame)
            else -> {
                Log.v(TAG, "Ignoring UMP part: ${UmpPartType.nameOf(frame.type)}, size=${frame.payload.size}")
            }
        }
    }

    private fun handleMediaHeader(frame: UmpFrame) {
        val header = MediaHeader.decode(frame.payload)
        activeHeaders[header.headerId] = header
        segmentAccumulators[header.headerId] = ByteArrayOutputStream(
            if (header.contentLength > 0) header.contentLength.coerceAtMost(2_000_000).toInt()
            else 65536
        )
        Log.v(TAG, "MediaHeader: id=${header.headerId}, itag=${header.itag}, " +
            "seq=${header.sequenceNumber}, time=${header.timeRangeStartMs}ms")
    }

    private fun handleMedia(frame: UmpFrame) {
        if (frame.payload.isEmpty()) return

        var pos = 0
        val firstByte = frame.payload[0].toInt() and 0xFF
        val headerIdSize = UmpVarInt.sizeOf(firstByte)
        val headerId = UmpVarInt.decode(frame.payload, 0).toInt()
        pos = headerIdSize

        val mediaData = frame.payload.copyOfRange(pos, frame.payload.size)
        segmentAccumulators[headerId]?.write(mediaData)
    }

    private suspend fun handleMediaEnd(frame: UmpFrame) {
        val headerId = if (frame.payload.isNotEmpty()) {
            UmpVarInt.decode(frame.payload, 0).toInt()
        } else {
            return
        }

        val header = activeHeaders.remove(headerId) ?: return
        val accumulator = segmentAccumulators.remove(headerId) ?: return
        val data = accumulator.toByteArray()

        val formatMeta = sessionState.formatMetadata[header.itag]
        val isAudio = formatMeta?.isAudio ?: (header.itag in KNOWN_AUDIO_ITAGS)

        val segment = SabrSegment(
            headerId = headerId,
            itag = header.itag,
            videoId = header.videoId,
            isAudio = isAudio,
            timeRangeStartMs = header.timeRangeStartMs,
            durationMs = header.durationMs,
            sequenceNumber = header.sequenceNumber,
            data = data
        )

        val formatId = FormatId(header.itag, header.lmt)
        val range = FormatBufferedRange(
            formatId = formatId,
            startTimeMs = header.timeRangeStartMs,
            durationMs = header.durationMs,
            startSequence = header.sequenceNumber,
            endSequence = header.sequenceNumber
        )
        sessionState.addBufferedRange(isAudio, range)

        Log.d(TAG, "Segment complete: itag=${header.itag}, seq=${header.sequenceNumber}, " +
            "${if (isAudio) "audio" else "video"}, size=${data.size}, time=${header.timeRangeStartMs}ms")

        _events.emit(SabrEvent.SegmentReady(segment))
    }

    private suspend fun handleFormatInit(frame: UmpFrame) {
        val metadata = FormatInitializationMetadata.decode(frame.payload)
        sessionState.storeFormatMetadata(metadata)

        Log.d(TAG, "FormatInit: itag=${metadata.formatId?.itag}, " +
            "${metadata.mimeType} ${metadata.codecs}, ${metadata.width}x${metadata.height}, " +
            "initDataSize=${metadata.initData.size}")

        _events.emit(SabrEvent.FormatInitialized(metadata))
    }

    private fun handleNextRequestPolicy(frame: UmpFrame) {
        val policy = NextRequestPolicy.decode(frame.payload)
        sessionState.updateFromNextRequestPolicy(policy)
        Log.d(TAG, "NextRequestPolicy: backoff=${policy.backoffTimeMs}ms, " +
            "cookie=${policy.playbackCookie.size}B")
    }

    private suspend fun handleRedirect(frame: UmpFrame) {
        val redirect = SabrRedirect.decode(frame.payload)
        sessionState.updateFromRedirect(redirect)
        Log.d(TAG, "Redirect: ${redirect.url.take(80)}...")
        _events.emit(SabrEvent.Redirect(redirect.url))
    }

    private suspend fun handleError(frame: UmpFrame) {
        val error = SabrError.decode(frame.payload)
        Log.e(TAG, "SABR Error: code=${error.errorCode}, msg=${error.errorMessage}, " +
            "recoverable=${error.isRecoverable}")
        _events.emit(SabrEvent.Error(error.errorCode, error.errorMessage, error.isRecoverable))
    }

    private suspend fun handleSeek(frame: UmpFrame) {
        val seek = SabrSeek.decode(frame.payload)
        Log.d(TAG, "SeekDirective: target=${seek.seekTargetMs}ms")
        _events.emit(SabrEvent.SeekDirective(seek.seekTargetMs))
    }

    private fun handleContextUpdate(frame: UmpFrame) {
        val update = SabrContextUpdate.decode(frame.payload)
        sessionState.updateFromContextUpdate(update)
        Log.v(TAG, "ContextUpdate: ${update.context.size}B")
    }

    private suspend fun handleProtectionStatus(frame: UmpFrame) {
        val status = StreamProtectionStatus.decode(frame.payload)
        Log.d(TAG, "ProtectionStatus: status=${status.status}, reason=${status.reason}")
        if (status.status == StreamProtectionStatus.STATUS_REQUIRES_RELOAD) {
            _events.emit(SabrEvent.ReloadRequired("Stream protection requires reload: ${status.reason}"))
        }
    }

    private suspend fun handleReloadRequired(frame: UmpFrame) {
        Log.w(TAG, "Server demands player reload")
        _events.emit(SabrEvent.ReloadRequired("Server requested player response reload"))
    }

    private suspend fun handleEndOfTrack() {
        Log.d(TAG, "End of track reached")
        _events.emit(SabrEvent.EndOfTrack)
    }

    private fun handlePlaybackStartPolicy(frame: UmpFrame) {
        val policy = PlaybackStartPolicy.decode(frame.payload)
        Log.d(TAG, "PlaybackStartPolicy: minBuffer=${policy.minBufferBeforePlaybackMs}ms")
    }
}
