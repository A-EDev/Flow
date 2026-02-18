package com.flow.youtube.data.repository

import com.flow.youtube.data.model.SponsorBlockSegment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorBlockRepository @Inject constructor() {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://sponsor.ajay.app/api/skipSegments"

    // Categories to fetch
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

    /**
     * Submit a new SponsorBlock segment.
     * Uses query parameters as required by the SponsorBlock API.
     * @return true if the submission was accepted (HTTP 200), false otherwise.
     */
    suspend fun submitSegment(
        videoId: String,
        startTime: Float,
        endTime: Float,
        category: String,
        userId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val uuid = java.util.UUID.randomUUID().toString().replace("-", "")
            val duration = (endTime - startTime)
            val submitUrl = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl()
                .newBuilder()
                .addQueryParameter("videoID", videoId)
                .addQueryParameter("startTime", startTime.toString())
                .addQueryParameter("endTime", endTime.toString())
                .addQueryParameter("category", category)
                .addQueryParameter("userID", userId)
                .addQueryParameter("userAgent", "FlowYouTube/1.0")
                .addQueryParameter("UUID", uuid)
                .addQueryParameter("duration", duration.toString())
                .build()

            val request = Request.Builder()
                .url(submitUrl)
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
