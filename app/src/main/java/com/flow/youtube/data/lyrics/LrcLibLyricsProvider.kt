package com.flow.youtube.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import java.net.URLEncoder

/**
 * LyricsProvider implementation wrapping the LrcLib API.
 * LrcLib provides standard LRC synced lyrics (line-level only, no word sync).
 */
class LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"
    
    private val client = OkHttpClient()
    private val gson = Gson()
    
    private data class LrcLibResponse(
        val id: Long? = null,
        val plainLyrics: String?,
        val syncedLyrics: String?,
        val instrumental: Boolean?,
        val duration: Double? = null
    )

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int
    ): Result<List<LyricsEntry>> = withContext(Dispatchers.IO) {
        try {
            val result = tryFetch(artist, title, duration)
                ?: trySearch(artist, title, duration)
                ?: tryFetch("", title, duration)
                ?: trySearch("", "$artist $title", duration)
            
            if (result != null) {
                val syncedLrc = result.syncedLyrics
                val plainLrc = result.plainLyrics
                
                if (syncedLrc != null) {
                    val entries = LyricsUtils.parseLyrics(syncedLrc)
                    if (entries.isNotEmpty()) {
                        return@withContext Result.success(entries)
                    }
                }
                
                if (plainLrc != null) {
                    // Plain text: single entry with no sync
                    val entries = listOf(LyricsEntry(time = 0L, text = plainLrc))
                    return@withContext Result.success(entries)
                }
                
                Result.failure(Exception("No lyrics content in response"))
            } else {
                Result.failure(Exception("No lyrics found on LrcLib"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun tryFetch(artist: String, title: String, duration: Int?): LrcLibResponse? {
        try {
            if (artist.isEmpty()) return null
            
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            
            val url = if (duration != null && duration > 0) {
                "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle&duration=$duration"
            } else {
                "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"
            }

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    return gson.fromJson(body, LrcLibResponse::class.java)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LrcLibProvider", "Fetch failed", e)
        }
        return null
    }
    
    private fun trySearch(artist: String, title: String, duration: Int?): LrcLibResponse? {
        try {
            val q = if (artist.isNotEmpty()) "$artist $title" else title
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encodedQ"
            
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val results = gson.fromJson(body, Array<LrcLibResponse>::class.java)
                
                if (results.isNotEmpty()) {
                    if (duration != null && duration > 0) {
                        val closestSynced = results
                            .filter { it.syncedLyrics != null }
                            .minByOrNull { Math.abs((it.duration ?: 0.0) - duration) }
                        
                        if (closestSynced != null && Math.abs((closestSynced.duration ?: 0.0) - duration) < 5) {
                            return closestSynced
                        }
                    }
                    
                    return results.firstOrNull { it.syncedLyrics != null } ?: results[0]
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LrcLibProvider", "Search failed", e)
        }
        return null
    }
}
