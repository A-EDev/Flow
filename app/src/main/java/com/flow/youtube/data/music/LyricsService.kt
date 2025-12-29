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

    data class LyricsResponse(val lyrics: String?)

    suspend fun getLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"

            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val lyricsResponse = gson.fromJson(body, LyricsResponse::class.java)
                lyricsResponse.lyrics
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
