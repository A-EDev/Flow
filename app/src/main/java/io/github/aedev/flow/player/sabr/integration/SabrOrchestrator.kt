package io.github.aedev.flow.player.sabr.integration

import android.util.Log
import io.github.aedev.flow.player.sabr.core.SabrEvent
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.proto.FormatInitializationMetadata
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SabrOrchestrator(
    private val controller: SabrStreamController,
    private val bandwidthEstimateProvider: (() -> Long)? = null,
    private val audioBufferLimitBytes: Long = AUDIO_BUFFER_LIMIT_BYTES,
    private val videoBufferLimitBytes: Long = VIDEO_BUFFER_LIMIT_BYTES,
    private val reloadResolver: (suspend (SabrEvent.ReloadRequired) -> SabrStreamInfo?)? = null,
) {
    companion object {
        private const val TAG = "SabrOrchestrator"
        const val BUFFER_CAPACITY_ERROR = -6
        private const val MAX_FOLLOW_UP_ERRORS = 5
        private const val POLL_INTERVAL_MS = 250L
        private const val DEFAULT_MAX_REQUEST_GAP_MS = 8_000L
        private const val MIN_TARGET_READAHEAD_MS = 5_000L
        private const val URL_EXPIRY_MARGIN_MS = 60_000L
        private const val MAX_PLAYER_RESPONSE_RELOADS = 2
        private const val AUDIO_BUFFER_LIMIT_BYTES = 4L * 1024 * 1024
        private const val VIDEO_BUFFER_LIMIT_BYTES = 32L * 1024 * 1024
    }

    // These are conservative heap guardrails; they are not performance-tuned limits.
    val audioBuffer = SabrSegmentBuffer(audioBufferLimitBytes)
    val videoBuffer = SabrSegmentBuffer(videoBufferLimitBytes)

    private val requestWakeup = SabrRequestWakeup()
    private var scope: CoroutineScope? = null
    private var eventCollectorJob: Job? = null
    private var segmentFetchJob: Job? = null

    @Volatile
    private var poTokenRefreshJob: Deferred<PoTokenRefreshResult>? = null

    @Volatile
    private var poTokenRefreshUrgent = false

    @Volatile
    private var playerResponseReloadJob: Deferred<Boolean>? = null
    private var playerResponseReloads = 0

    private val bufferCapacityFailureReported = AtomicBoolean(false)

    private val consecutiveErrors = AtomicInteger(0)

    @Volatile
    var isRunning = false
        private set

    @Volatile
    var audioInitReceived = false
        private set

    @Volatile
    var videoInitReceived = false
        private set

    var onFormatInitialized: ((FormatInitializationMetadata) -> Unit)? = null
    var onError: ((Int, String, Boolean, Int?) -> Unit)? = null
    var onEndOfTrack: (() -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        consecutiveErrors.set(0)
        bufferCapacityFailureReported.set(false)
        audioInitReceived = false
        videoInitReceived = false
        audioBuffer.reset()
        videoBuffer.reset()

        val newScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = newScope

        eventCollectorJob =
            newScope.launch {
                controller.events.collect { event ->
                    handleEvent(event)
                }
            }

        newScope.launch(Dispatchers.IO) {
            controller.startSession()
            startFollowUpLoop()
        }
    }

    fun stop() {
        isRunning = false
        eventCollectorJob?.cancel()
        segmentFetchJob?.cancel()
        poTokenRefreshJob?.cancel()
        playerResponseReloadJob?.cancel()
        controller.abort()
        audioBuffer.signalEndOfStream()
        videoBuffer.signalEndOfStream()
        scope?.cancel()
        scope = null
    }

    fun release() {
        stop()
        controller.release()
        audioBuffer.close()
        videoBuffer.close()
    }

    fun setPlaybackRequested(requested: Boolean) {
        requestWakeup.setPlaybackRequested(requested)
    }

    fun updatePlayhead(positionMs: Long) {
        controller.updatePlayheadPosition(positionMs)
    }

    /** Background audio-only mode: tells the server to stop sending video segments. */
    fun setAudioOnly(enabled: Boolean) {
        controller.sessionState.enabledTrackTypes = if (enabled) 1 else -1
    }

    private fun handleEvent(event: SabrEvent) {
        when (event) {
            is SabrEvent.FormatInitialized -> {
                val metadata = event.metadata
                val initData = metadata.initData
                if (initData.isNotEmpty()) {
                    if (metadata.isAudio) {
                        if (!appendSegment(audioBuffer, initData, "audio")) return
                        audioInitReceived = true
                        Log.d(TAG, "Audio init received: ${metadata.mimeType} ${metadata.codecs}, ${initData.size}B")
                    } else if (metadata.isVideo) {
                        if (!appendSegment(videoBuffer, initData, "video")) return
                        videoInitReceived = true
                        Log.d(
                            TAG,
                            "Video init received: ${metadata.mimeType} ${metadata.codecs}, " +
                                "${metadata.width}x${metadata.height}, ${initData.size}B",
                        )
                    }
                }
                onFormatInitialized?.invoke(metadata)
            }

            is SabrEvent.SegmentReady -> {
                val segment = event.segment
                consecutiveErrors.set(0)
                if (segment.isAudio) {
                    appendSegment(audioBuffer, segment.data, "audio")
                } else {
                    appendSegment(videoBuffer, segment.data, "video")
                }
            }

            is SabrEvent.EndOfTrack -> {
                Log.d(TAG, "End of track")
                audioBuffer.signalEndOfStream()
                videoBuffer.signalEndOfStream()
                onEndOfTrack?.invoke()
            }

            is SabrEvent.Error -> {
                Log.e(TAG, "SABR error: code=${event.code}, msg=${event.message}, recoverable=${event.recoverable}")
                consecutiveErrors.incrementAndGet()
                if (!event.recoverable || consecutiveErrors.get() >= MAX_FOLLOW_UP_ERRORS) {
                    onError?.invoke(event.code, event.message, false, event.itag)
                } else {
                    onError?.invoke(event.code, event.message, true, event.itag)
                }
            }

            is SabrEvent.Redirect -> {
                Log.d(TAG, "Redirected to new URL")
            }

            is SabrEvent.BackoffRequired -> {
                Log.d(TAG, "Backoff: ${event.delayMs}ms")
            }

            is SabrEvent.ReloadRequired -> {
                Log.w(TAG, "Reload required: ${event.reason}")
                startPlayerResponseReload(event)
            }

            is SabrEvent.SeekDirective -> {
                Log.d(TAG, "Server seek directive: ${event.targetMs}ms")
            }

            is SabrEvent.AttestationNeeded -> {
                if (event.required) {
                    refreshPoToken(urgent = true)
                } else {
                    // A pending verdict alongside media is normal, so this must not be treated as a
                    // fault on its own. Recovering from a verdict that never resolves requires
                    // rotating the visitor identity (a re-minted token keeps the same visitorData and
                    // draws the same verdict), which is not implemented yet.
                    Log.d(TAG, "PoToken attestation is pending; waiting for the server verdict")
                }
            }
        }
    }

    private fun refreshPoToken(urgent: Boolean) {
        val existing = poTokenRefreshJob
        if (existing != null && existing.isActive) {
            // An attestation-required refresh must not be satisfied by a non-urgent mint
            // already in flight — that one can return the same cached (rejected) token.
            if (!urgent || poTokenRefreshUrgent) return
            existing.cancel()
            poTokenRefreshJob = null
        }
        poTokenRefreshUrgent = urgent
        poTokenRefreshJob =
            scope?.async(Dispatchers.IO) {
                val videoId = controller.sessionState.videoId
                val fresh =
                    try {
                        val visitorData = controller.sessionState.visitorId
                        if (visitorData.isBlank()) {
                            null
                        } else if (urgent) {
                            WebPoTokenSession.refreshForVisitorData(videoId, visitorData)
                        } else {
                            WebPoTokenSession.mintForVisitorData(videoId, visitorData)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "PoToken refresh threw: ${e.message}")
                        null
                    }
                // videoId-bound content token, same binding the session was created with
                val streamingToken = fresh?.playerRequestPoToken
                if (!streamingToken.isNullOrEmpty()) {
                    controller.sessionState.poToken = streamingToken
                    Log.w(TAG, "PoToken refreshed for $videoId (urgent=$urgent)")
                    PoTokenRefreshResult(success = true, required = urgent)
                } else if (urgent) {
                    onError?.invoke(-5, "PoToken refresh failed while attestation required", false, null)
                    PoTokenRefreshResult(success = false, required = true)
                } else {
                    PoTokenRefreshResult(success = false, required = false)
                }
            }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun startPlayerResponseReload(event: SabrEvent.ReloadRequired) {
        if (playerResponseReloadJob?.isActive == true) return
        val resolver = reloadResolver
        if (resolver == null || playerResponseReloads >= MAX_PLAYER_RESPONSE_RELOADS) {
            io.github.aedev.flow.player.error.PlayerDiagnostics.logError(
                TAG,
                "SABR reload budget spent ($playerResponseReloads/$MAX_PLAYER_RESPONSE_RELOADS) — giving up",
            )
            isRunning = false
            audioBuffer.signalEndOfStream()
            videoBuffer.signalEndOfStream()
            onError?.invoke(-2, event.reason, false, null)
            return
        }

        playerResponseReloads++
        playerResponseReloadJob =
            scope?.async(Dispatchers.IO) {
                val fresh =
                    try {
                        resolver(event)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Player response reload failed", e)
                        null
                    } ?: run {
                        io.github.aedev.flow.player.error.PlayerDiagnostics
                            .logError(TAG, "SABR reload could not resolve a fresh player response")
                        return@async false
                    }

                controller.sessionState.applyPlayerResponseReload(
                    streamingUrl = fresh.streamingUrl,
                    ustreamerConfig = fresh.ustreamerConfig,
                    poToken = fresh.poToken,
                    visitorId = fresh.visitorId,
                    cpn = fresh.cpn,
                    audioItag = fresh.audioItag,
                    audioLmt = fresh.audioLmt,
                    videoItag = fresh.videoItag,
                    videoLmt = fresh.videoLmt,
                    audioTrackId = fresh.audioTrackId,
                    audioXtags = fresh.audioXtags,
                    videoXtags = fresh.videoXtags,
                )
                Log.w(
                    TAG,
                    "Reload formats: audio=${fresh.audioItag}/${fresh.audioLmt} " +
                        "video=${fresh.videoItag}/${fresh.videoLmt} track='${fresh.audioTrackId}' " +
                        "audioXtags='${fresh.audioXtags}'",
                )
                // Clear the controller's wedge counter/reload latch now that the fresh session is
                // installed, so medialess responses seen during the reload window don't immediately
                // re-trip the non-recoverable wedge on the resumed session.
                controller.onPlayerResponseReloaded()
                Log.w(
                    TAG,
                    "Player response reloaded in place at ${controller.sessionState.playheadPositionMs}ms " +
                        "(attempt $playerResponseReloads/$MAX_PLAYER_RESPONSE_RELOADS)",
                )
                io.github.aedev.flow.player.error.PlayerDiagnostics.logWarning(
                    TAG,
                    "SABR reload applied at ${controller.sessionState.playheadPositionMs}ms " +
                        "(attempt $playerResponseReloads/$MAX_PLAYER_RESPONSE_RELOADS) — resuming session",
                )
                true
            }
    }

    private suspend fun startFollowUpLoop() {
        var lastRequestAtMs = System.currentTimeMillis()
        while (isRunning && consecutiveErrors.get() < MAX_FOLLOW_UP_ERRORS) {
            val requestGapMs =
                controller.sessionState.maxTimeSinceLastRequestMs.takeIf { it > 0 }
                    ?: DEFAULT_MAX_REQUEST_GAP_MS
            val heartbeatWaitMs = requestGapMs - (System.currentTimeMillis() - lastRequestAtMs)
            requestWakeup.awaitNext(POLL_INTERVAL_MS, heartbeatWaitMs)
            if (!isRunning) break

            val reloadJob = playerResponseReloadJob
            if (reloadJob != null) {
                val reloaded =
                    try {
                        reloadJob.await()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Player response reload wait failed", e)
                        false
                    }
                if (playerResponseReloadJob === reloadJob) playerResponseReloadJob = null
                if (!reloaded) {
                    isRunning = false
                    audioBuffer.signalEndOfStream()
                    videoBuffer.signalEndOfStream()
                    onError?.invoke(-2, "Unable to reload SABR player response", false, null)
                    break
                }
                continue
            }

            // Pace requests by the server's readahead targets instead of hammering:
            // request only when the buffered lead over the playhead drops below target,
            // with a periodic heartbeat so the server can send policy/END_OF_TRACK parts.
            val state = controller.sessionState
            val now = System.currentTimeMillis()

            // GVS URLs die at their expire= timestamp (~6h); re-extract before mid-play 403s
            val expiresAtMs = state.urlExpiresAtMs()
            if (expiresAtMs > 0 && now >= expiresAtMs - URL_EXPIRY_MARGIN_MS) {
                Log.w(TAG, "SABR URL expiring (${(expiresAtMs - now) / 1000}s left) — requesting re-extraction")
                onError?.invoke(-4, "SABR streaming URL expiring", false, null)
                break
            }

            val maxGapMs =
                state.maxTimeSinceLastRequestMs.takeIf { it > 0 }
                    ?: DEFAULT_MAX_REQUEST_GAP_MS
            val heartbeatDue = now - lastRequestAtMs >= maxGapMs
            val bufferedAheadMs = bufferedAheadMs(state)
            if (
                shouldPauseFollowUpRequest(
                    audioOnly = state.enabledTrackTypes == 1,
                    audioAtHighWater = audioBuffer.isAtHighWater,
                    videoAtHighWater = videoBuffer.isAtHighWater,
                    bufferedAheadMs = bufferedAheadMs,
                    criticalLeadMs = MIN_TARGET_READAHEAD_MS,
                    heartbeatDue = heartbeatDue,
                )
            ) {
                continue
            }
            if (!heartbeatDue && bufferedAheadMs >= targetReadaheadMs(state)) {
                continue
            }

            val refreshJob = poTokenRefreshJob
            if (refreshJob != null) {
                val refreshResult =
                    try {
                        refreshJob.await()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Distinguish our own cancellation from a superseded refresh job:
                        // an urgent re-attest cancels the non-urgent mint it replaces.
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (poTokenRefreshJob === refreshJob) poTokenRefreshJob = null
                        continue
                    } catch (e: Exception) {
                        Log.w(TAG, "PoToken refresh wait failed", e)
                        PoTokenRefreshResult(success = false, required = true)
                    }
                if (poTokenRefreshJob === refreshJob) poTokenRefreshJob = null
                if (!refreshResult.success && refreshResult.required) {
                    break
                }
            }

            try {
                positiveBandwidthEstimate(bandwidthEstimateProvider?.invoke())?.let { estimate ->
                    synchronized(state) {
                        state.estimatedBandwidthBps = estimate
                    }
                }
                controller.requestNextSegments()
                lastRequestAtMs = System.currentTimeMillis()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Follow-up request failed", e)
                consecutiveErrors.incrementAndGet()
                if (consecutiveErrors.get() >= MAX_FOLLOW_UP_ERRORS) {
                    onError?.invoke(-1, "Too many consecutive errors", false, null)
                    break
                }
                delay(1000L * consecutiveErrors.get())
            }
        }
    }

    private fun bufferedAheadMs(state: io.github.aedev.flow.player.sabr.core.SabrSessionState): Long {
        val audioEndMs =
            state.audioBufferedRanges.maxOfOrNull { it.startTimeMs + it.durationMs }
                ?: return 0L
        // Audio-only mode: video lead is frozen by design — pace by audio alone
        if (state.enabledTrackTypes == 1) return audioEndMs - state.playheadPositionMs
        val videoEndMs =
            state.videoBufferedRanges.maxOfOrNull { it.startTimeMs + it.durationMs }
                ?: return 0L
        return minOf(audioEndMs, videoEndMs) - state.playheadPositionMs
    }

    private fun targetReadaheadMs(state: io.github.aedev.flow.player.sabr.core.SabrSessionState): Long =
        minOf(state.targetAudioReadaheadMs, state.targetVideoReadaheadMs)
            .coerceAtLeast(MIN_TARGET_READAHEAD_MS)

    private fun appendSegment(
        buffer: SabrSegmentBuffer,
        data: ByteArray,
        track: String,
    ): Boolean {
        when (buffer.appendSegment(data)) {
            SegmentAppendResult.ACCEPTED -> return true
            SegmentAppendResult.CLOSED -> return false
            SegmentAppendResult.CAPACITY_EXCEEDED -> Unit
        }
        if (bufferCapacityFailureReported.compareAndSet(false, true)) {
            isRunning = false
            eventCollectorJob?.cancel()
            segmentFetchJob?.cancel()
            audioBuffer.signalEndOfStream()
            videoBuffer.signalEndOfStream()
            controller.abort()
            val failingItag =
                if (track == "audio") {
                    controller.sessionState.selectedAudioItag
                } else {
                    controller.sessionState.selectedVideoItag
                }
            onError?.invoke(
                BUFFER_CAPACITY_ERROR,
                "SABR $track buffer exceeded its ${buffer.maxBufferedBytes}-byte memory limit",
                false,
                failingItag.takeIf { it > 0 },
            )
            scope?.cancel()
            scope = null
        }
        return false
    }

    private data class PoTokenRefreshResult(
        val success: Boolean,
        val required: Boolean,
    )
}

internal fun shouldPauseFollowUpRequest(
    audioOnly: Boolean,
    audioAtHighWater: Boolean,
    videoAtHighWater: Boolean,
    bufferedAheadMs: Long,
    criticalLeadMs: Long,
    heartbeatDue: Boolean,
): Boolean {
    val buffersAtHighWater =
        if (audioOnly) {
            audioAtHighWater
        } else {
            audioAtHighWater || videoAtHighWater
        }
    return !heartbeatDue && bufferedAheadMs >= criticalLeadMs && buffersAtHighWater
}

internal fun positiveBandwidthEstimate(sample: Long?): Long? = sample?.takeIf { it > 0 }
