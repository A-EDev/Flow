package io.github.aedev.flow.data.transcript

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TranscriptRepository"
private const val CACHE_SIZE = 4

/**
 * Reads a caption track as a transcript.
 *
 * The track URL comes from what the player already resolved for subtitles, so opening a transcript
 * costs one request for a few KB of text and nothing at all the second time.
 */
@Singleton
class TranscriptRepository
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
    ) {
        private val cache = LruCache<String, List<TranscriptCue>>(CACHE_SIZE)

        suspend fun cues(trackUrl: String): List<TranscriptCue> =
            withContext(Dispatchers.IO) {
                if (trackUrl.isBlank()) return@withContext emptyList()
                cache.get(trackUrl)?.let { return@withContext it }
                val body =
                    try {
                        httpClient.newCall(Request.Builder().url(trackUrl).build()).execute().use { response ->
                            if (!response.isSuccessful) {
                                Log.w(TAG, "Caption track returned HTTP ${response.code}")
                                return@withContext emptyList()
                            }
                            response.body?.string().orEmpty()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Caption track fetch failed: ${e.message}")
                        return@withContext emptyList()
                    }
                VideoTranscript.parse(body).also { cues ->
                    if (cues.isNotEmpty()) cache.put(trackUrl, cues)
                }
            }
    }
