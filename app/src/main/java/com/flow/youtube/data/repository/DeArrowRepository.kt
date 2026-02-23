package com.flow.youtube.data.repository

import android.util.LruCache
import com.flow.youtube.data.model.DeArrowContent
import com.flow.youtube.data.model.DeArrowResult
import com.flow.youtube.data.model.DeArrowThumbnail
import com.flow.youtube.data.model.DeArrowTitle
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches DeArrow branding data from the SponsorBlock API.
 *
 * DeArrow replaces clickbait titles and thumbnails with community-submitted,
 * more accurate alternatives. See https://dearrow.ajay.app/
 *
 * Results are cached in-memory (LRU, 200 entries) to prevent redundant network calls.
 */
object DeArrowRepository {

    private val client = OkHttpClient()
    private val gson = Gson()

    private const val BRANDING_BASE_URL = "https://sponsor.ajay.app/api/branding"
    private const val THUMBNAIL_BASE_URL = "https://dearrow-thumb.ajay.app/api/v1/getThumbnail"

    /**
     * LRU cache mapping videoId -> DeArrowResult (null = fetched but no useful data).
     * Capacity: 200 entries ≈ a few home page loads.
     */
    private val cache = LruCache<String, Optional<DeArrowResult>>(200)

    /** Wrapper to distinguish "cache miss" from "cached null" */
    private class Optional<T>(val value: T?)

    /**
     * Returns the DeArrow result for [videoId], or null if:
     *  - DeArrow has no data for this video
     *  - The network request failed
     *
     * Results are cached so the same video is only fetched once per session.
     */
    suspend fun getDeArrowResult(videoId: String): DeArrowResult? = withContext(Dispatchers.IO) {
        // Cache hit
        cache.get(videoId)?.let { return@withContext it.value }

        val result = try {
            val request = Request.Builder()
                .url("$BRANDING_BASE_URL/$videoId")
                .header("User-Agent", "FlowYouTube/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                null
            } else {
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    null
                } else {
                    parseResponse(body, videoId)
                }
            }
        } catch (e: Exception) {
            null
        }

        cache.put(videoId, Optional(result))
        result
    }

    private fun parseResponse(json: String, videoId: String): DeArrowResult? {
        return try {
            val mapType = object : TypeToken<Map<String, JsonObject>>() {}.type
            val map: Map<String, JsonObject> = gson.fromJson(json, mapType)
            val videoObject = map[videoId] ?: return null

            val content = gson.fromJson(videoObject, DeArrowContent::class.java)
            extractResult(content, videoId)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Picks the best title and thumbnail from [content]:
     * - Prefers locked entries (manually verified) over voted ones
     * - Ignores entries with negative votes (downvoted)
     * - Ignores "original" entries (these just mean "keep as-is")
     */
    private fun extractResult(content: DeArrowContent, videoId: String): DeArrowResult? {
        val title = content.titles
            .filter { !it.original && (it.votes >= 0 || it.locked) }
            .maxByOrNull { if (it.locked) Int.MAX_VALUE else it.votes }
            ?.title

        val bestThumb = content.thumbnails
            .filter { !it.original && (it.votes >= 0 || it.locked) }
            .maxByOrNull { if (it.locked) Int.MAX_VALUE else it.votes }

        val thumbnailUrl = when {
            bestThumb?.thumbnail != null -> bestThumb.thumbnail
            bestThumb?.timestamp != null ->
                "$THUMBNAIL_BASE_URL?videoID=$videoId&time=${bestThumb.timestamp}"
            else -> null
        }

        return if (title == null && thumbnailUrl == null) null
        else DeArrowResult(title = title, thumbnailUrl = thumbnailUrl)
    }

    /** Removes a cached entry, forcing a fresh fetch next time. */
    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    /** Clears the entire in-memory cache. */
    fun clearCache() {
        cache.evictAll()
    }
}
