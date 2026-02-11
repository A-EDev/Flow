package com.flow.youtube.data.music

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

object LyricsService {
    private val client = OkHttpClient()
    private val gson = Gson()

    data class LyricsResponse(
        val id: Long? = null,
        val plainLyrics: String?,
        val syncedLyrics: String?,
        val instrumental: Boolean?,
        val duration: Double? = null
    )

    suspend fun getLyrics(artist: String, title: String, duration: Int? = null): LyricsResponse? = withContext(Dispatchers.IO) {
        val queries = listOf(
            artist to title, // Standard
            "" to title, // Title only (some have artist in title)
            "" to "$artist - $title" // Combined search
        )

        for ((qArtist, qTitle) in queries) {
            val result = tryFetch(qArtist, qTitle, duration)
            if (result?.syncedLyrics != null) return@withContext result
            
            // If we have plain lyrics, we'll keep them as fallback if we don't find synced ones later
            if (result != null && result.plainLyrics != null) {
                // Continue searching for synced ones, but keep this one as a potential return
                val searchResult = trySearch(qArtist, qTitle, duration)
                if (searchResult?.syncedLyrics != null) return@withContext searchResult
                return@withContext result 
            }
        }
        
        return@withContext null
    }

    private fun tryFetch(artist: String, title: String, duration: Int?): LyricsResponse? {
        try {
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            
            val url = if (artist.isNotEmpty()) {
                if (duration != null && duration > 0) {
                    "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle&duration=$duration"
                } else {
                    "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"
                }
            } else {
                // LRCLib /get requires artist_name. If empty, skip to search.
                return null
            }

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    return gson.fromJson(body, LyricsResponse::class.java)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun trySearch(artist: String, title: String, duration: Int?): LyricsResponse? {
        try {
            val q = if (artist.isNotEmpty()) "$artist $title" else title
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encodedQ"
            
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val searchResults = gson.fromJson(body, Array<LyricsResponse>::class.java)
                
                if (searchResults.isNotEmpty()) {
                    // 1. Try to find the one with closest duration AND synced lyrics
                    if (duration != null && duration > 0) {
                        val closestSynced = searchResults
                            .filter { it.syncedLyrics != null }
                            .minByOrNull { Math.abs((it.duration ?: 0.0) - duration) }
                        
                        if (closestSynced != null && Math.abs((closestSynced.duration ?: 0.0) - duration) < 5) {
                            return closestSynced
                        }
                    }
                    
                    // 2. Just find the first one with synced lyrics
                    val firstSynced = searchResults.firstOrNull { it.syncedLyrics != null }
                    if (firstSynced != null) return firstSynced
                    
                    // 3. Fallback to first result regardless of synced status
                    return searchResults[0]
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
