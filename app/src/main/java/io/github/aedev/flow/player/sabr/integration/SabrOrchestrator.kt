package io.github.aedev.flow.player.sabr.integration

import android.util.Log
import io.github.aedev.flow.player.sabr.core.SabrEvent
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.proto.FormatInitializationMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SabrOrchestrator(
    private val controller: SabrStreamController
) {
    companion object {
        private const val TAG = "SabrOrchestrator"
        private const val MAX_FOLLOW_UP_ERRORS = 5
    }

    val audioBuffer = SabrSegmentBuffer()
    val videoBuffer = SabrSegmentBuffer()

    private var scope: CoroutineScope? = null
    private var eventCollectorJob: Job? = null
    private var segmentFetchJob: Job? = null
    private var consecutiveErrors = 0

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
    var onError: ((Int, String, Boolean) -> Unit)? = null
    var onEndOfTrack: (() -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        consecutiveErrors = 0
        audioInitReceived = false
        videoInitReceived = false
        audioBuffer.reset()
        videoBuffer.reset()

        val newScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = newScope

        eventCollectorJob = newScope.launch {
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

    fun updatePlayhead(positionMs: Long) {
        controller.updatePlayheadPosition(positionMs)
    }

    private fun handleEvent(event: SabrEvent) {
        when (event) {
            is SabrEvent.FormatInitialized -> {
                val metadata = event.metadata
                val initData = metadata.initData
                if (initData.isNotEmpty()) {
                    if (metadata.isAudio) {
                        audioBuffer.appendSegment(initData)
                        audioInitReceived = true
                        Log.d(TAG, "Audio init received: ${metadata.mimeType} ${metadata.codecs}, ${initData.size}B")
                    } else if (metadata.isVideo) {
                        videoBuffer.appendSegment(initData)
                        videoInitReceived = true
                        Log.d(TAG, "Video init received: ${metadata.mimeType} ${metadata.codecs}, ${metadata.width}x${metadata.height}, ${initData.size}B")
                    }
                }
                onFormatInitialized?.invoke(metadata)
            }

            is SabrEvent.SegmentReady -> {
                val segment = event.segment
                consecutiveErrors = 0
                if (segment.isAudio) {
                    audioBuffer.appendSegment(segment.data)
                } else {
                    videoBuffer.appendSegment(segment.data)
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
                consecutiveErrors++
                if (!event.recoverable || consecutiveErrors >= MAX_FOLLOW_UP_ERRORS) {
                    onError?.invoke(event.code, event.message, false)
                } else {
                    onError?.invoke(event.code, event.message, true)
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
                isRunning = false
                audioBuffer.signalEndOfStream()
                videoBuffer.signalEndOfStream()
                onError?.invoke(-2, event.reason, false)
            }

            is SabrEvent.SeekDirective -> {
                Log.d(TAG, "Server seek directive: ${event.targetMs}ms")
            }
        }
    }

    private suspend fun startFollowUpLoop() {
        while (isRunning && consecutiveErrors < MAX_FOLLOW_UP_ERRORS) {
            delay(100)
            if (!isRunning) break
            try {
                controller.requestNextSegments()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Follow-up request failed", e)
                consecutiveErrors++
                if (consecutiveErrors >= MAX_FOLLOW_UP_ERRORS) {
                    onError?.invoke(-1, "Too many consecutive errors", false)
                    break
                }
                delay(1000L * consecutiveErrors)
            }
        }
    }
}
