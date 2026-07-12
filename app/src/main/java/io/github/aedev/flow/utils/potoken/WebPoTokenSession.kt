package io.github.aedev.flow.utils.potoken

import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single source of truth for the WEB BotGuard PoToken session used by the **native video
 * stream extractor** (separate from [NewPipePoTokenProvider], which serves the NewPipe path).
 */
object WebPoTokenSession {
    private const val TAG = "WebPoTokenSession"

    private val generator = PoTokenGenerator()
    private val visitorMutex = Mutex()

    suspend fun sessionVisitorData(): String? {
        YouTube.visitorData?.takeIf { it.isNotBlank() }?.let { return it }
        return visitorMutex.withLock {
            YouTube.visitorData?.takeIf { it.isNotBlank() }?.let { return it }
            val fetched = YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() }
            if (fetched != null) {
                YouTube.visitorData = fetched
                Log.d(TAG, "Fetched session visitorData for WEB PoToken")
            } else {
                Log.w(TAG, "Could not obtain visitorData for WEB PoToken")
            }
            fetched
        }
    }

    /**
     * Mint the player + streaming PoToken pair for [videoId], bound to the session visitorData.
     * Returns null if a visitorData is unavailable or the WebView/BotGuard path is unusable

     */
    suspend fun mint(videoId: String): PoTokenResult? {
        val vd = sessionVisitorData() ?: return null
        return mintForVisitorData(videoId, vd)
    }

    // Mint with a bounded wait for the fast extraction path.
    suspend fun mintBounded(videoId: String, maxWaitMs: Long = 10_000L): PoTokenResult? {
        return withTimeoutOrNull(maxWaitMs) {
            val vd = sessionVisitorData() ?: return@withTimeoutOrNull null
            try {
                generator.getWebClientPoTokenSuspend(videoId, vd)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bounded PoToken mint failed for $videoId: ${e.message}")
                null
            }
        }
    }

    /** Mint against the exact visitor identity carried by the corresponding player response. */
    suspend fun mintForVisitorData(videoId: String, visitorData: String): PoTokenResult? {
        return mintForVisitorData(videoId, visitorData, forceRefresh = false)
    }

    /** Re-run attestation and replace the cached streaming token after a protection boundary. */
    suspend fun refreshForVisitorData(videoId: String, visitorData: String): PoTokenResult? {
        return mintForVisitorData(videoId, visitorData, forceRefresh = true)
    }

    private suspend fun mintForVisitorData(
        videoId: String,
        visitorData: String,
        forceRefresh: Boolean
    ): PoTokenResult? {
        if (visitorData.isBlank()) return null
        return withTimeoutOrNull(90_000L) {
            try {
                generator.getWebClientPoTokenSuspend(videoId, visitorData, forceRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "PoToken mint failed for $videoId: ${e.message}")
                null
            }
        }
    }

    // Pre-warm the BotGuard session at app start so the first real extraction is fast.
    suspend fun prewarm() {
        try {
            val visitorData = sessionVisitorData() ?: return
            withContext(Dispatchers.IO) {
                generator.prewarmWebClient(visitorData)
            }
        } catch (e: Exception) {
            Log.w(TAG, "prewarm failed: ${e.message}")
        }
    }
}
