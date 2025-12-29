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
        val plainLyrics: String?,
        val syncedLyrics: String?,
        val instrumental: Boolean?
    )

    suspend fun getLyrics(artist: String, title: String, duration: Int? = null): LyricsResponse? = withContext(Dispatchers.IO) {
        try {
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            
            // Try to get lyrics with metadata for better accuracy
            var url = "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"
            if (duration != null) {
                url += "&duration=$duration"
            }

            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    return@withContext gson.fromJson(body, LyricsResponse::class.java)
                }
            }
            
            // If direct get fails, try search
            val searchUrl = "https://lrclib.net/api/search?q=$encodedArtist+$encodedTitle"
            val searchRequest = Request.Builder().url(searchUrl).build()
            
            client.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val searchResults = gson.fromJson(body, Array<LyricsResponse>::class.java)
                if (searchResults.isNotEmpty()) {
                    return@withContext searchResults[0]
                }
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
