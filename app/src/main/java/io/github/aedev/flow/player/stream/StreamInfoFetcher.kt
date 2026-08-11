package io.github.aedev.flow.player.stream

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Fetches NewPipe `StreamInfo` for playback, retrying transient extraction failures. */
object StreamInfoFetcher {
    private const val TAG = "StreamInfoFetcher"
    private const val ATTEMPTS = 3
    private const val TIMEOUT_MS = 12_000L

    /**
     * The second attempt swaps to the `youtu.be` short form: the two URL shapes take different
     * extractor paths, so one can succeed where the other fails on the same video.
     */
    suspend fun fetchForPlayback(videoId: String): StreamInfo? =
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            repeat(ATTEMPTS) { attempt ->
                val info =
                    try {
                        val url =
                            if (attempt == 1) {
                                "https://youtu.be/$videoId"
                            } else {
                                "https://www.youtube.com/watch?v=$videoId"
                            }
                        withTimeoutOrNull(TIMEOUT_MS) {
                            StreamInfo.getInfo(ServiceList.YouTube, url)
                        }
                    } catch (e: Exception) {
                        lastError = e
                        null
                    }
                if (info != null) return@withContext info
                if (attempt < 2) delay((attempt + 1) * 300L)
            }
            Log.e(TAG, "Failed to fetch stream info for $videoId", lastError)
            null
        }
}
