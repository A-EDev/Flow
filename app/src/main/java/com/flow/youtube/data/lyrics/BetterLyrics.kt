// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package com.flow.youtube.data.lyrics

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service for fetching lyrics from the BetterLyrics API (lyrics-api.boidu.dev).
 * Returns structured LyricsEntry objects with word-level timestamps from TTML.
 */
object BetterLyrics {
    private const val TAG = "BetterLyrics"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private const val BASE_URL = "https://lyrics-api.boidu.dev"

    private data class TTMLResponse(
        @SerializedName("ttml") val ttml: String? = null
    )

    /**
     * Fetch lyrics by title, artist, and duration.
     * Uses TTMLParser.parseTTMLToLines() directly to preserve word-level data.
     *
     * @param title Song title
     * @param artist Artist name
     * @param duration Duration in seconds
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int
    ): Result<List<LyricsEntry>> = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")

            val url = buildString {
                append("$BASE_URL/getLyrics")
                append("?s=$encodedTitle")
                append("&a=$encodedArtist")
                if (duration > 0) {
                    append("&d=$duration")
                }
            }

            Log.d(TAG, "Fetching: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "FlowMusic/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("BetterLyrics API returned ${response.code}")
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Empty response from BetterLyrics"))
                }

                val ttml = try {
                    val ttmlResponse = gson.fromJson(body, TTMLResponse::class.java)
                    ttmlResponse?.ttml
                } catch (e: Exception) {
                    Log.d(TAG, "JSON parse failed, trying raw TTML")
                    if (body.contains("<tt") || body.contains("<p ")) body else null
                }

                if (ttml.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("No TTML in BetterLyrics response"))
                }

                val ttmlLines = try {
                    TTMLParser.parseTTMLToLines(ttml)
                } catch (e: Exception) {
                    Log.e(TAG, "TTML parse failed", e)
                    emptyList()
                }

                if (ttmlLines.isNotEmpty()) {
                    val entries = ttmlLines.map { line ->
                        LyricsEntry(
                            time = line.startTimeMs,
                            text = line.text,
                            words = line.words?.map { word ->
                                WordTimestamp(
                                    text = word.text,
                                    startTime = word.startTimeMs,
                                    endTime = word.endTimeMs
                                )
                            },
                            agent = line.agent,
                            isBackground = line.isBackground
                        )
                    }
                    Log.d(TAG, "Parsed ${entries.size} lines, ${entries.count { it.words != null }} with word sync")
                    return@withContext Result.success(entries)
                }

                Result.failure(Exception("Failed to parse BetterLyrics TTML"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "BetterLyrics fetch failed", e)
            Result.failure(e)
        }
    }
}
