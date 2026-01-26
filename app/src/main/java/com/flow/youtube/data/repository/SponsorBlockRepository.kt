package com.flow.youtube.data.repository

import com.flow.youtube.data.model.SponsorBlockSegment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorBlockRepository @Inject constructor() {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://sponsor.ajay.app/api/skipSegments"
    
    // Categories to skip
    private val categories = listOf("sponsor", "intro", "outro", "selfpromo", "interaction", "music_offtopic")

    suspend fun getSegments(videoId: String): List<SponsorBlockSegment> = withContext(Dispatchers.IO) {
        try {
            val categoriesJson = gson.toJson(categories)
            val encodedCategories = URLEncoder.encode(categoriesJson, "UTF-8")
            val url = "$baseUrl?videoID=$videoId&categories=$encodedCategories"
            
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                val listType = object : TypeToken<List<SponsorBlockSegment>>() {}.type
                return@withContext gson.fromJson(responseBody, listType)
            } else {
                // 404 means no segments found, that's fine
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }
}
