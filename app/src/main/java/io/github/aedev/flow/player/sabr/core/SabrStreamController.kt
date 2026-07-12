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
import io.github.aedev.flow.player.sabr.proto.SabrContextSendingPolicy
import io.github.aedev.flow.player.sabr.proto.SabrError
import io.github.aedev.flow.player.sabr.proto.SabrRedirect
import io.github.aedev.flow.player.sabr.proto.SabrSeek
import io.github.aedev.flow.player.sabr.proto.StreamProtectionStatus
import io.github.aedev.flow.player.sabr.ump.UmpFrame
import io.github.aedev.flow.player.sabr.ump.UmpFrameDecoder
import io.github.aedev.flow.player.sabr.ump.UmpPartType
import io.github.aedev.flow.player.sabr.ump.SabrMediaPayload
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
    data class ReloadRequired(val reason: String, val reloadToken: String? = null) : SabrEvent()
    data class SeekDirective(val targetMs: Long) : SabrEvent()
    // required=false: grace window, refresh PoToken in background; true: media already cut
    data class AttestationNeeded(val required: Boolean) : SabrEvent()
}

class SabrStreamController(
    private val dataSource: SabrDataSource,
    val sessionState: SabrSessionState = SabrSessionState()
) {
    companion object {
        private const val TAG = "SabrStreamCtrl"
        private const val READ_BUFFER_SIZE = 16384
        private const val MAX_MEDIALESS_RESPONSES = 3
        private const val NEAR_END_TOLERANCE_MS = 2_000L

        /**
         * Appends the GVS session query params the SABR server expects: `alr=yes` and `cpn` (content-playback nonce) are added
         * only when not already present so an existing value from the player response is never
         * overridden, while `rn` (request number) is refreshed on every request.
         */
        internal fun buildRequestUrl(baseUrl: String, cpn: String, requestSequence: Int): String {
            val fragmentIndex = baseUrl.indexOf('#')
            val fragment = if (fragmentIndex >= 0) baseUrl.substring(fragmentIndex) else ""
            val withoutFragment = if (fragmentIndex >= 0) baseUrl.substring(0, fragmentIndex) else baseUrl
            val queryIndex = withoutFragment.indexOf('?')
            val path = if (queryIndex >= 0) withoutFragment.substring(0, queryIndex) else withoutFragment
            val params = if (queryIndex >= 0) {
                withoutFragment.substring(queryIndex + 1)
                    .split('&')
                    .filter(String::isNotEmpty)
                    .filterNot { it.substringBefore('=') == "rn" }
                    .toMutableList()
            } else {
                mutableListOf()
            }
            if (params.none { it.substringBefore('=') == "alr" }) params += "alr=yes"
            if (cpn.isNotEmpty() && params.none { it.substringBefore('=') == "cpn" }) {
                params += "cpn=$cpn"
            }
            params += "rn=$requestSequence"
            return "$path?${params.joinToString("&")}$fragment"
        }

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

    @Volatile
    private var sawMediaInResponse = false
    private var consecutiveMedialessResponses = 0
    private var attestationRetried = false
    private var malformedPartsInResponse = 0

    // A RELOAD_PLAYER_RESPONSE is a control signal: the server stops serving media until we
    // re-fetch /player and resume on the fresh session. Those medialess responses must NOT count
    // toward the wedge, or the 3-strike non-recoverable escalation kills the session before the
    // async reload can install the new URL/config (the RELOAD_PLAYER_RESPONSE loop root cause).
    @Volatile
    private var reloadPending = false

    suspend fun startSession() {
        aborted = false
        sessionState.requestSequence = 0
        consecutiveMedialessResponses = 0
        attestationRetried = false
        sawMediaInResponse = false
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

    /**
     * Called by the orchestrator once a RELOAD_PLAYER_RESPONSE has been serviced and the fresh
     * player response (URL/config/token) is installed in [sessionState]. Clears the wedge counter
     * and reload latch so the resumed session starts with a clean slate — otherwise medialess
     * strikes accumulated during the reload window would immediately trip the non-recoverable wedge.
     */
    fun onPlayerResponseReloaded() {
        consecutiveMedialessResponses = 0
        reloadPending = false
        sawMediaInResponse = false
        attestationRetried = false
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
            try {
                discardIncompleteMedia("new response")
                frameDecoder.reset()
                malformedPartsInResponse = 0
                val url = buildRequestUrl(
                    sessionState.effectiveUrl,
                    sessionState.cpn,
                    sessionState.requestSequence
                )
                sawMediaInResponse = false
                // The WEB GVS endpoint identifies the session from the URL params + cpn + the
                // StreamerContext PoToken, not an X-Goog-Visitor-Id header.
                val stream = dataSource.open(url, requestBody)
                readAndProcessStream(stream)
                discardIncompleteMedia("response ended")
                trackMedialessResponses()
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

    // Empty responses are legal (policy-only), but repeated ones while we still need
    // media mean the session is wedged (e.g. stale buffered ranges) — escalate.
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun trackMedialessResponses() {
        if (sawMediaInResponse) {
            consecutiveMedialessResponses = 0
            return
        }
        if (reloadPending) return
        if (isBufferedToEnd()) return
        consecutiveMedialessResponses++
        if (consecutiveMedialessResponses >= MAX_MEDIALESS_RESPONSES) {
            Log.w(TAG, "No media in $consecutiveMedialessResponses consecutive responses — session wedged")
            io.github.aedev.flow.player.error.PlayerDiagnostics.logError(
                TAG, "SABR wedged: no media in $consecutiveMedialessResponses responses (reload loop)")
            _events.emit(SabrEvent.Error(
                code = -3,
                message = "No media in $consecutiveMedialessResponses consecutive responses",
                recoverable = false
            ))
        }
    }

    private fun isBufferedToEnd(): Boolean {
        val durationMs = sessionState.durationMs
        if (durationMs <= 0) return false
        val audioEnd = sessionState.audioBufferedRanges.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L
        if (sessionState.enabledTrackTypes == 1) return audioEnd >= durationMs - NEAR_END_TOLERANCE_MS
        val videoEnd = sessionState.videoBufferedRanges.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L
        return minOf(audioEnd, videoEnd) >= durationMs - NEAR_END_TOLERANCE_MS
    }

    private suspend fun readAndProcessStream(stream: InputStream) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (coroutineContext.isActive && !aborted) {
            val bytesRead = stream.read(buffer)
            if (bytesRead == -1) break

            frameDecoder.feed(buffer, 0, bytesRead)

            while (frameDecoder.hasNext()) {
                val frame = frameDecoder.next()
                try {
                    dispatchFrame(frame)
                } catch (error: Exception) {
                    malformedPartsInResponse++
                    Log.w(
                        TAG,
                        "Ignoring malformed ${UmpPartType.nameOf(frame.type)} part " +
                            "(${frame.payload.size}B): ${error.message}"
                    )
                }
            }
        }
    }

    private fun discardIncompleteMedia(reason: String) {
        if (activeHeaders.isEmpty() && segmentAccumulators.isEmpty()) return
        Log.w(TAG, "Discarding ${activeHeaders.size} incomplete media segment(s): $reason")
        activeHeaders.clear()
        segmentAccumulators.clear()
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
            UmpPartType.SABR_CONTEXT_SENDING_POLICY -> handleContextSendingPolicy(frame)
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
        if (activeHeaders.put(header.headerId, header) != null) {
            Log.w(TAG, "Replacing incomplete media header id=${header.headerId}")
        }
        segmentAccumulators[header.headerId] = ByteArrayOutputStream(
            if (header.contentLength > 0) header.contentLength.coerceAtMost(2_000_000).toInt()
            else 65536
        )
        Log.v(TAG, "MediaHeader: id=${header.headerId}, itag=${header.itag}, " +
            "seq=${header.sequenceNumber}, time=${header.timeRangeStartMs}ms")
    }

    private fun handleMedia(frame: UmpFrame) {
        if (frame.payload.isEmpty()) return

        val headerId = SabrMediaPayload.headerId(frame.payload) ?: return
        val mediaData = frame.payload.copyOfRange(
            SabrMediaPayload.dataOffset(frame.payload),
            frame.payload.size
        )
        segmentAccumulators[headerId]?.write(mediaData)
    }

    private suspend fun handleMediaEnd(frame: UmpFrame) {
        val headerId = SabrMediaPayload.headerId(frame.payload) ?: return

        val header = activeHeaders.remove(headerId) ?: return
        val accumulator = segmentAccumulators.remove(headerId) ?: return
        val encodedData = accumulator.toByteArray()

        if (header.contentLength > 0 && encodedData.size.toLong() != header.contentLength) {
            Log.w(TAG, "Segment length mismatch: itag=${header.itag}, seq=${header.sequenceNumber}, " +
                "expected=${header.contentLength}, got=${encodedData.size} — dropping")
            return
        }

        val data = try {
            SabrMediaDecoder.decode(header.compressionType, encodedData)
        } catch (error: Exception) {
            Log.w(TAG, "Could not decode SABR segment: itag=${header.itag}, " +
                "seq=${header.sequenceNumber}, compression=${header.compressionType}", error)
            return
        }

        if (header.itag != sessionState.selectedAudioItag &&
            header.itag != sessionState.selectedVideoItag
        ) {
            Log.v(TAG, "Ignoring unselected SABR itag=${header.itag}, seq=${header.sequenceNumber}")
            return
        }

        sawMediaInResponse = true
        attestationRetried = false
        // Re-sent segments would corrupt the append-only byte pipe — drop duplicates
        if (!sessionState.markSegmentConsumed(header.itag, header.sequenceNumber, header.isInitSegment)) {
            Log.v(TAG, "Duplicate segment dropped: itag=${header.itag}, seq=${header.sequenceNumber}, init=${header.isInitSegment}")
            return
        }

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

        // Init segments are not part of the media timeline — never report them as buffered
        if (!header.isInitSegment) {
            val formatId = FormatId(header.itag, header.lmt)
            val range = FormatBufferedRange(
                formatId = formatId,
                startTimeMs = header.timeRangeStartMs,
                durationMs = header.durationMs,
                startSequence = header.sequenceNumber,
                endSequence = header.sequenceNumber
            )
            sessionState.addBufferedRange(isAudio, range)
        }

        Log.d(TAG, "Segment complete: itag=${header.itag}, seq=${header.sequenceNumber}, " +
            "init=${header.isInitSegment}, ${if (isAudio) "audio" else "video"}, " +
            "size=${data.size}, time=${header.timeRangeStartMs}ms")

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
        sessionState.playheadPositionMs = seek.seekTargetMs
        _events.emit(SabrEvent.SeekDirective(seek.seekTargetMs))
    }

    private fun handleContextUpdate(frame: UmpFrame) {
        val update = SabrContextUpdate.decode(frame.payload)
        sessionState.updateFromContextUpdate(update)
        Log.v(TAG, "ContextUpdate: type=${update.type}, ${update.value.size}B, sendByDefault=${update.sendByDefault}")
    }

    private fun handleContextSendingPolicy(frame: UmpFrame) {
        val policy = SabrContextSendingPolicy.decode(frame.payload)
        sessionState.updateFromContextSendingPolicy(policy)
        Log.v(
            TAG,
            "ContextSendingPolicy: start=${policy.startTypes}, " +
                "stop=${policy.stopTypes}, discard=${policy.discardTypes}"
        )
    }

    private suspend fun handleProtectionStatus(frame: UmpFrame) {
        val status = StreamProtectionStatus.decode(frame.payload)
        Log.d(TAG, "ProtectionStatus: status=${status.status}, maxRetries=${status.maxRetries}")
        when (status.status) {
            StreamProtectionStatus.STATUS_ATTESTATION_PENDING -> {
                Log.w(TAG, "Stream protection: attestation pending (PoToken being verified)")
                _events.emit(SabrEvent.AttestationNeeded(required = false))
            }
            StreamProtectionStatus.STATUS_ATTESTATION_REQUIRED -> {
                // Media is cut. Try one in-session token refresh before tearing down.
                if (!attestationRetried) {
                    attestationRetried = true
                    Log.w(TAG, "Stream protection: attestation required — attempting PoToken refresh")
                    _events.emit(SabrEvent.AttestationNeeded(required = true))
                } else {
                    _events.emit(SabrEvent.ReloadRequired("Stream protection: attestation required"))
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun handleReloadRequired(frame: UmpFrame) {
        // ReloadPlayerResponse { reload_playback_params = 1 { token = 1 } }
        val token = try {
            var t: String? = null
            io.github.aedev.flow.player.sabr.proto.ProtobufReader(frame.payload).forEachField { field ->
                if (field.fieldNumber == 1) {
                    io.github.aedev.flow.player.sabr.proto.ProtobufReader(field.asBytes()).forEachField { inner ->
                        if (inner.fieldNumber == 1) t = inner.asString()
                    }
                }
            }
            t
        } catch (e: Exception) {
            null
        }
        sessionState.reloadToken = token
        reloadPending = true
        Log.w(TAG, "Server demands player reload (token=${token != null})")
        io.github.aedev.flow.player.error.PlayerDiagnostics.logWarning(
            TAG, "SABR: server demands player reload (token=${token != null})")
        _events.emit(SabrEvent.ReloadRequired("Server requested player response reload", token))
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
