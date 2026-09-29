package io.github.aedev.flow.ui.screens.player

import android.util.Log
import io.github.aedev.flow.player.error.StreamExpiryRetryLimiter
import io.github.aedev.flow.player.error.StreamFailureContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Decides what the player screen does each time the playing streams turn out to be expired
 * (a 403/410 on a URL that worked a moment ago): re-extract, re-extract and drop what the cache
 * holds for the video, or stop asking.
 *
 * Counting is delegated to [StreamExpiryRetryLimiter] — the same limiter the player's error handler
 * uses — keyed by video id, so a new video always starts from a fresh budget. A video that has run
 * out, or whose playback the player itself abandoned, is latched: further expiry events for it are
 * dropped rather than starting a load that will fail the same way.
 */
internal class StreamExpiryRecoveryController(
    private val maxRetries: Int = MAX_STREAM_EXPIRY_RETRIES,
) {
    sealed interface Decision {
        /** Nothing to do: this video is already abandoned. */
        object Ignored : Decision

        /** Re-extract, and evict the video's cached media first once the URLs keep expiring. */
        data class Reload(
            val attempt: Int,
            val limit: Int,
            val evictCache: Boolean,
        ) : Decision

        /** The retry budget is spent: surface a terminal error and stop. */
        object GiveUp : Decision
    }

    private var limiter = newLimiter()

    /**
     * Turns every expiry event on [events] into one decision, on [scope].
     *
     * An event that arrives while a load is already in flight is dropped: that load *is* the
     * recovery, and starting a second one would re-extract the same URLs twice. [onReload] and
     * [onGiveUp] run on the collecting coroutine, one at a time, in arrival order.
     */
    fun collectExpiryEvents(
        scope: CoroutineScope,
        events: Flow<Unit>,
        videoIdInPlayback: () -> String?,
        isLoadInFlight: () -> Boolean,
        onReload: suspend (String, Decision.Reload) -> Unit,
        onGiveUp: suspend (String) -> Unit,
    ): Job =
        events
            .onEach {
                val videoId = videoIdInPlayback() ?: return@onEach
                if (isLoadInFlight()) {
                    Log.d(TAG, "Stream expiry for $videoId coalesced — a stream load is already in flight")
                    return@onEach
                }
                when (val decision = onStreamExpired(videoId)) {
                    is Decision.Ignored -> {
                        Log.d(TAG, "Ignoring stream expiry for abandoned playback $videoId")
                    }

                    is Decision.GiveUp -> {
                        Log.e(TAG, "Stream expiry retry limit reached for $videoId — giving up")
                        onGiveUp(videoId)
                    }

                    is Decision.Reload -> {
                        Log.w(
                            TAG,
                            "Stream expired — re-fetching streams for $videoId " +
                                "(attempt ${decision.attempt}/${decision.limit})",
                        )
                        onReload(videoId, decision)
                    }
                }
            }.launchIn(scope)

    /** The video whose playback has been abandoned, if any. */
    var abandonedVideoId: String? = null
        private set

    fun onStreamExpired(videoId: String): Decision {
        if (abandonedVideoId == videoId) return Decision.Ignored

        return when (val decision = limiter.record(StreamFailureContext(reason = REASON, url = videoId))) {
            is StreamExpiryRetryLimiter.Decision.Retry -> {
                Decision.Reload(
                    attempt = decision.attempt,
                    limit = decision.limit,
                    evictCache = decision.attempt >= CACHE_EVICTION_FROM_ATTEMPT,
                )
            }

            is StreamExpiryRetryLimiter.Decision.GiveUp -> {
                abandonedVideoId = videoId
                Decision.GiveUp
            }

            StreamExpiryRetryLimiter.Decision.AlreadyAbandoned -> {
                abandonedVideoId = videoId
                Decision.Ignored
            }

            StreamExpiryRetryLimiter.Decision.Debounced -> {
                Decision.Ignored
            }
        }
    }

    /** The player gave up on this video itself; no expiry event for it is worth acting on. */
    fun onPlaybackAbandoned(videoId: String) {
        abandonedVideoId = videoId
    }

    /** A user-driven attempt at [videoId] (play, retry): the budget and both latches start over. */
    fun onPlaybackRequested() {
        abandonedVideoId = null
        limiter = newLimiter()
    }

    /** Loading a different video releases the latch the abandoned one holds. */
    fun onLoadStarted(videoId: String) {
        if (abandonedVideoId != null && abandonedVideoId != videoId) {
            abandonedVideoId = null
        }
    }

    private fun newLimiter() =
        StreamExpiryRetryLimiter(
            maxConsecutiveFailures = maxRetries,
            // Expiry events are already coalesced against the in-flight load, so a second one
            // is a genuinely new failure however quickly it arrives.
            debounceMs = 0L,
        )

    companion object {
        private const val TAG = "StreamExpiryRecovery"

        const val MAX_STREAM_EXPIRY_RETRIES = 3

        // The first re-extraction assumes a stale URL; once it happens again the bytes the cache
        // holds are suspect too.
        private const val CACHE_EVICTION_FROM_ATTEMPT = 2
        private const val REASON = "stream-expired"
    }
}
