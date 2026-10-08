package io.github.aedev.flow.player.recovery

import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Reloads the playing video's streams when they were refused and could not be swapped in place.
 *
 * Owned by the player rather than the screen: background playback, queue advances and a closed
 * player screen all need it, and while the screen owned it a refusal with no screen to hear it
 * left playback stopped at the minute for good (#921). Every decision is logged at W, because the
 * W/E lines are what a user's diagnostics report carries.
 *
 * Main-thread confined, like the manager that owns it.
 */
internal class StreamDenialReloader(
    private val scope: CoroutineScope,
    private val currentVideoId: () -> String?,
    private val positionMs: () -> Long,
    private val playDownload: suspend (videoId: String, startPositionMs: Long) -> Boolean,
    private val extract: suspend (videoId: String) -> InnerTubeVideoStreamExtractor.VideoExtractionResult?,
    private val prepare: suspend (videoId: String, InnerTubeVideoStreamExtractor.VideoExtractionResult, startPositionMs: Long) -> Boolean,
    private val evictCache: suspend () -> Unit,
    private val abandon: (videoId: String) -> Unit,
    private val log: (String) -> Unit,
) {
    private val budget = StreamExpiryRecoveryController()
    private var job: Job? = null

    /** The video being reloaded right now, if any. */
    var reloadingVideoId: String? = null
        private set

    fun onStreamsDenied() {
        val videoId =
            currentVideoId() ?: run {
                log("stream denied with no video playing; nothing to reload")
                return
            }
        if (job?.isActive == true && reloadingVideoId == videoId) {
            log("stream denied for $videoId while its reload is running; coalesced")
            return
        }
        when (val decision = budget.onStreamExpired(videoId)) {
            StreamExpiryRecoveryController.Decision.Ignored -> {
                log("stream denied for $videoId after playback was given up; ignored")
            }

            StreamExpiryRecoveryController.Decision.GiveUp -> {
                log("stream denied for $videoId and the reload budget is spent; giving up")
                abandon(videoId)
            }

            is StreamExpiryRecoveryController.Decision.Reload -> {
                reload(videoId, decision)
            }
        }
    }

    fun onPlaybackRequested() = budget.onPlaybackRequested()

    fun onLoadStarted(videoId: String) = budget.onLoadStarted(videoId)

    fun isReloading(videoId: String?): Boolean = videoId != null && job?.isActive == true && reloadingVideoId == videoId

    fun hasGivenUpOn(videoId: String?): Boolean = videoId != null && budget.abandonedVideoId == videoId

    private fun reload(
        videoId: String,
        decision: StreamExpiryRecoveryController.Decision.Reload,
    ) {
        val startPositionMs = positionMs().coerceAtLeast(0L)
        log("stream denied for $videoId; reloading at ${startPositionMs}ms (attempt ${decision.attempt}/${decision.limit})")
        reloadingVideoId = videoId
        job =
            scope.launch {
                var failed = false
                try {
                    // A download finished meanwhile beats fetching streams that were just refused.
                    if (playDownload(videoId, startPositionMs)) {
                        log("reload of $videoId plays its download instead")
                        return@launch
                    }
                    if (decision.evictCache) evictCache()
                    val extraction = extract(videoId)
                    if (currentVideoId() != videoId) return@launch
                    failed = extraction == null || !prepare(videoId, extraction, startPositionMs)
                    if (failed) log("reload of $videoId found no playable streams")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed = true
                    log("reload of $videoId failed: ${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    reloadingVideoId = null
                }
                // A reload that found nothing spends its attempt like any other refusal.
                if (failed && currentVideoId() == videoId) onStreamsDenied()
            }
    }
}
