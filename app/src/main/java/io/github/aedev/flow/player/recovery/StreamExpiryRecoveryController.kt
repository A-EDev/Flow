package io.github.aedev.flow.player.recovery

import io.github.aedev.flow.player.error.StreamExpiryRetryLimiter
import io.github.aedev.flow.player.error.StreamFailureContext

/**
 * Decides what the player does each time a video's streams are refused or expire and could not be
 * swapped in place: re-extract, re-extract and drop what the cache holds for the video, or stop.
 *
 * The budget belongs to one video and starts over when the player moves to another, so a long
 * playlist on a walled visitor never runs out of recoveries partway through (#921). A video that has
 * spent it, or that the player gave up on, is latched: further refusals of it are dropped rather
 * than starting a load that will fail the same way.
 */
internal class StreamExpiryRecoveryController(
    private val maxRetries: Int = MAX_STREAM_EXPIRY_RETRIES,
) {
    sealed interface Decision {
        /** Nothing to do: this video is already abandoned. */
        object Ignored : Decision

        /** Re-extract, and evict the video's cached media first once the URLs keep failing. */
        data class Reload(
            val attempt: Int,
            val limit: Int,
            val evictCache: Boolean,
        ) : Decision

        /** The retry budget is spent: surface a terminal error and stop. */
        object GiveUp : Decision
    }

    private var budgetVideoId: String? = null
    private var limiter = newLimiter()

    /** The video whose playback has been abandoned, if any. */
    var abandonedVideoId: String? = null
        private set

    fun onStreamExpired(videoId: String): Decision {
        if (abandonedVideoId == videoId) return Decision.Ignored
        if (videoId != budgetVideoId) {
            budgetVideoId = videoId
            limiter = newLimiter()
        }

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

    /** The player gave up on this video itself; no refusal of it is worth acting on. */
    fun onPlaybackAbandoned(videoId: String) {
        abandonedVideoId = videoId
    }

    /** A user-driven attempt at a video (play, retry): the budget and the latch start over. */
    fun onPlaybackRequested() {
        abandonedVideoId = null
        budgetVideoId = null
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
            // Refusals are already coalesced against the in-flight reload, so a second one is a
            // genuinely new failure however quickly it arrives.
            debounceMs = 0L,
        )

    companion object {
        const val MAX_STREAM_EXPIRY_RETRIES = 3

        // The first re-extraction assumes a stale URL; once it happens again the bytes the cache
        // holds are suspect too.
        private const val CACHE_EVICTION_FROM_ATTEMPT = 2
        private const val REASON = "stream-expired"
    }
}
