// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package com.flow.youtube.data.lyrics

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Service for fetching lyrics from the SimpMusic API.
 * Returns structured LyricsEntry objects.
 * Prioritizes richSyncLyrics (word-by-word JSON) > syncedLyrics (LRC) > plainLyrics.
 *
 * API endpoint: https://api-lyrics.simpmusic.org/v1/{videoId}
 * Response: {"type":"success","data":[{...lyrics data...}]}
 */
object SimpMusicLyrics {
    private const val TAG = "SimpMusicLyrics"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"

    /**
     * Rich sync JSON item structure from SimpMusic API.
     */
    private data class RichSyncItem(
        val ts: Double = 0.0,
        val te: Double = 0.0,
        val l: List<RichSyncWord>? = null,
        val x: String? = null
    )

    private data class RichSyncWord(
        val c: String = "",
        val o: Double = 0.0
    )

    /**
     * Fetch lyrics by video ID and return structured entries.
     */
    suspend fun getLyrics(videoId: String, duration: Int? = null): Result<List<LyricsEntry>> = withContext(Dispatchers.IO) {
        try {
            val url = BASE_URL + videoId
            Log.d(TAG, "Fetching: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SimpMusicLyrics/1.0")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("SimpMusic API returned ${response.code}")
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Empty response from SimpMusic"))
                }

                val entries = parseSimpMusicResponse(body, duration)
                if (entries != null && entries.isNotEmpty()) {
                    Log.d(TAG, "Got ${entries.size} lines, ${entries.count { it.words != null }} with word sync")
                    Result.success(entries)
                } else {
                    Result.failure(Exception("No lyrics found in SimpMusic response"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SimpMusic fetch failed", e)
            Result.failure(e)
        }
    }

    private fun parseSimpMusicResponse(json: String, duration: Int?): List<LyricsEntry>? {
        try {
            val root = JsonParser.parseString(json)

            val lyricsArray = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    val type = obj.get("type")?.asString
                    if (type != null && type != "success") {
                        Log.d(TAG, "API returned type=$type")
                        return null
                    }
                    obj.getAsJsonArray("data") ?: return null
                }
                else -> return null
            }

            if (lyricsArray.size() == 0) return null

            var bestMatch: com.google.gson.JsonObject? = null
            var bestDurationDiff = Double.MAX_VALUE

            for (element in lyricsArray) {
                val lyricObj = element.asJsonObject

                if (duration != null && duration > 0) {
                    val lyricDuration = lyricObj.get("durationSeconds")?.asInt
                        ?: lyricObj.get("duration")?.asInt
                        ?: 0
                    val diff = abs(lyricDuration - duration).toDouble()
                    if (diff <= 10 && diff < bestDurationDiff) {
                        bestDurationDiff = diff
                        bestMatch = lyricObj
                    }
                } else {
                    bestMatch = lyricObj
                    break
                }
            }

            if (bestMatch == null) bestMatch = lyricsArray[0].asJsonObject

            val richSyncStr = bestMatch?.get("richSyncLyrics")?.asString
            if (!richSyncStr.isNullOrBlank()) {
                val richEntries = parseRichSyncJson(richSyncStr)
                if (richEntries != null && richEntries.isNotEmpty()) {
                    Log.d(TAG, "Using richSyncLyrics (${richEntries.size} lines)")
                    return richEntries
                }
            }

            val syncedStr = bestMatch?.get("syncedLyrics")?.asString
            if (!syncedStr.isNullOrBlank()) {
                val entries = LyricsUtils.parseLyrics(syncedStr)
                if (entries.isNotEmpty()) {
                    Log.d(TAG, "Using syncedLyrics (${entries.size} lines)")
                    return entries
                }
            }

            val plainStr = bestMatch?.get("plainLyrics")?.asString
                ?: bestMatch?.get("plainLyric")?.asString
            if (!plainStr.isNullOrBlank()) {
                Log.d(TAG, "Using plainLyrics")
                return listOf(LyricsEntry(time = 0L, text = plainStr))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
        }
        return null
    }

    /**
     * Parse SimpMusic's richSyncLyrics JSON format into LyricsEntry with word timestamps.
     *
     * Format: [{"ts": 1.23, "te": 5.67, "l": [{"c": "word", "o": 0.0}, ...], "x": "full line"}, ...]
     */
    private fun parseRichSyncJson(richSyncJson: String): List<LyricsEntry>? {
        return try {
            val type = object : TypeToken<List<RichSyncItem>>() {}.type
            val items: List<RichSyncItem> = gson.fromJson(richSyncJson, type)

            items.mapNotNull { item ->
                val lineText = item.x ?: item.l?.joinToString(" ") { it.c.trim() } ?: return@mapNotNull null
                if (lineText.isBlank()) return@mapNotNull null

                val lineStartMs = (item.ts * 1000).toLong()

                val wordTimestamps = item.l?.mapNotNull { word ->
                    val trimmed = word.c.trim()
                    if (trimmed.isBlank()) return@mapNotNull null
                    val wordStartMs = ((item.ts + word.o) * 1000).toLong()
                    WordTimestamp(
                        text = trimmed,
                        startTime = wordStartMs,
                        endTime = (item.te * 1000).toLong()
                    )
                }

                val refinedWords = wordTimestamps?.mapIndexed { index, wt ->
                    val nextStart = if (index < wordTimestamps.size - 1) {
                        wordTimestamps[index + 1].startTime
                    } else {
                        (item.te * 1000).toLong()
                    }
                    wt.copy(endTime = nextStart)
                }

                LyricsEntry(
                    time = lineStartMs,
                    text = lineText.trim(),
                    words = refinedWords?.takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse richSync JSON", e)
            null
        }
    }
}
