package io.github.aedev.flow.utils.potoken

import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
        return withContext(Dispatchers.IO) {
            try {
                // getWebClientPoToken internally runBlocking-bridges to the WebView main thread.
                generator.getWebClientPoToken(videoId, vd)
            } catch (e: Exception) {
                Log.w(TAG, "PoToken mint failed for $videoId: ${e.message}")
                null
            }
        }
    }

    // Pre-warm the BotGuard session at app start so the first real extraction is fast.
    suspend fun prewarm() {
        try {
            sessionVisitorData()
        } catch (e: Exception) {
            Log.w(TAG, "prewarm failed: ${e.message}")
        }
    }
}
