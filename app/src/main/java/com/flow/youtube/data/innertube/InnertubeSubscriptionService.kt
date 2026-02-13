package com.flow.youtube.data.innertube

import android.util.Log
import com.flow.youtube.data.model.Video
import com.flow.youtube.utils.PerformanceDispatcher
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Service for fetching YouTube subscriptions using InnerTube API directly.
 * Optimized for speed: parallel fetches, low timeouts, progressive loading.
 */
object InnertubeSubscriptionService {
    private const val TAG = "InnertubeSubs"
    private const val BASE_URL = "https://www.youtube.com/youtubei/v1/browse"
    // Standard Android client key
    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" 
    private const val CHUNK_SIZE = 10 // Fetch 10 channels at a time
    private const val TIMEOUT_MS = 5000L // 5s timeout per request
    private const val MAX_RESULTS_PER_CHANNEL = 5 // Latest 5 videos per channel

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Fetch latest videos from subscribed channels using InnerTube.
     * Returns a Flow for progressive loading (emit results as they arrive).
     * @param channelIds List of subscribed channel IDs.
     * @param maxTotal Maximum total videos to return.
     */
    fun fetchSubscriptionVideos(channelIds: List<String>, maxTotal: Int = 200): Flow<List<Video>> = flow {
        if (channelIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val allVideos = mutableListOf<Video>()
        val processedChannels = mutableSetOf<String>()

        // Chunk channels for parallelism
        val chunks = channelIds.chunked(CHUNK_SIZE)

        supervisorScope {
            val deferredResults = chunks.map { chunk ->
                async(PerformanceDispatcher.networkIO) {
                    fetchChunkVideos(chunk, processedChannels)
                }
            }

            // Emit results progressively as chunks complete
            for (deferred in deferredResults) {
                try {
                    val chunkVideos = withTimeoutOrNull(TIMEOUT_MS * 2) { deferred.await() } ?: emptyList()
                    allVideos.addAll(chunkVideos)
                    
                    // Sort by upload date (latest first) and limit total
                    val sorted = allVideos.sortedByDescending { parseUploadDate(it.uploadDate) }.take(maxTotal)
                    emit(sorted.toList()) // Emit current state
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Chunk failed: ${e.message}")
                }
            }
        }

        // Final emit with all results (just to be sure)
        val finalSorted = allVideos.sortedByDescending { parseUploadDate(it.uploadDate) }.take(maxTotal)
        emit(finalSorted)
    }

    /**
     * Fetch videos for a chunk of channels.
     */
    private suspend fun fetchChunkVideos(chunk: List<String>, processed: MutableSet<String>): List<Video> {
        val videos = mutableListOf<Video>()
        
        // Parallel fetch within the chunk as well for maximum speed
        supervisorScope {
            val fetches = chunk.map { channelId ->
                async(Dispatchers.IO) {
                    if (!processed.contains(channelId)) {
                        try {
                            val channelVideos = fetchChannelVideos(channelId)
                            synchronized(processed) {
                                processed.add(channelId)
                            }
                            channelVideos
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch channel $channelId: ${e.message}")
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
            }
            
            fetches.forEach { 
                videos.addAll(it.await().take(MAX_RESULTS_PER_CHANNEL)) 
            }
        }
        
        return videos
    }

    /**
     * Fetch latest videos for a single channel using InnerTube.
     */
    private fun fetchChannelVideos(channelId: String): List<Video> {
        val requestBody = """
        {
            "context": {
                "client": {
                    "clientName": "ANDROID",
                    "clientVersion": "17.31.35",
                    "androidSdkVersion": 30
                },
                "user": {"lockedSafetyMode": false}
            },
            "browseId": "$channelId",
            "params": "EgZ2aWRlb3M%3D"
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("$BASE_URL?key=$API_KEY")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        // This is blocking, but called within Dispatchers.IO
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

        val responseBody = response.body?.string() ?: return emptyList()
        val json = gson.fromJson(responseBody, JsonObject::class.java)
        return parseVideosFromResponse(json, channelId)
    }

    /**
     * Parse video items from InnerTube JSON response.
     */
    private fun parseVideosFromResponse(json: JsonObject, channelId: String): List<Video> {
        val videos = mutableListOf<Video>()
        
        try {
            // Traverse the JSON to find video items
            // This path can be brittle, so we use safe calls
            val tabs = json.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnBrowseResultsRenderer")
                ?.getAsJsonArray("tabs")
            
            var videoTab: JsonObject? = null
            
            if (tabs != null) {
                // Find the "Videos" tab
                for (tab in tabs) {
                    val tabObj = tab.asJsonObject
                    val title = tabObj.getAsJsonObject("tabRenderer")?.get("title")?.asString
                    if (title == "Videos" || tabObj.getAsJsonObject("tabRenderer")?.get("selected")?.asBoolean == true) {
                        videoTab = tabObj
                        break
                    }
                }
                // Fallback to index 1 if not found by name (usually Home is 0, Videos is 1)
                 if (videoTab == null && tabs.size() > 1) {
                    videoTab = tabs.get(1).asJsonObject
                }
            }

            val contents = videoTab
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("itemSectionRenderer")
                ?.getAsJsonArray("contents") 
                
                // Sometimes it's gridRenderer instead of itemSectionRenderer for videos
                ?: videoTab
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("richGridRenderer")
                ?.getAsJsonArray("contents")

            if (contents == null) return emptyList()

            for (content in contents) {
                val item = content.asJsonObject
                
                // Handle grid items (richItemRenderer)
                val videoRenderer = if (item.has("richItemRenderer")) {
                    item.getAsJsonObject("richItemRenderer")?.getAsJsonObject("content")?.getAsJsonObject("videoRenderer")
                } else {
                    item.getAsJsonObject("videoRenderer")
                }
                
                if (videoRenderer != null) {
                     val videoId = videoRenderer.get("videoId")?.asString ?: continue
                    
                    val title = videoRenderer.getAsJsonObject("title")
                        ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: ""
                        
                    val thumbnail = videoRenderer.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: ""
                        
                    val duration = parseDuration(videoRenderer.getAsJsonObject("lengthText")?.get("simpleText")?.asString)
                    
                    val viewCount = videoRenderer.getAsJsonObject("viewCountText")?.get("simpleText")?.asString ?: "0"
                    val uploadDate = videoRenderer.getAsJsonObject("publishedTimeText")?.get("simpleText")?.asString ?: ""
                     
                    // Short video check (often vertical aspect ratio or explicitly marked, but simple heuristic for now)
                    // We can also check if navigationEndpoint is 'reel' but here we just get videos.
                    
                    videos.add(Video(
                        id = videoId,
                        title = title,
                        channelName = "", // We know the channel ID, client can fill name if needed, or we fetch it
                        channelId = channelId,
                        thumbnailUrl = thumbnail,
                        duration = duration,
                        viewCount = parseViewCount(viewCount),
                        uploadDate = uploadDate,
                        channelThumbnailUrl = "" // Optimization: don't fetch channel avatar for every video list to save bandwidth
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response for $channelId: ${e.message}")
        }
        
        return videos
    }

    // Helper: Parse duration string (e.g., "3:45") to seconds
    private fun parseDuration(durationStr: String?): Int {
        if (durationStr.isNullOrEmpty()) return 0
        val parts = durationStr.trim().split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    // Helper: Parse view count string
    private fun parseViewCount(viewStr: String?): Long {
        if (viewStr == null) return 0L
        if (viewStr.contains("No views")) return 0L
        
        // Handle "1.2M views", "10K views", etc. or "1,234 views"
        val clean = viewStr.split(" ").firstOrNull() ?: return 0L
        
        return try {
            val lastChar = clean.last()
            val numberPart = clean.dropLast(1).replace(",", "").toFloatOrNull() ?: return 0L
            
            when (lastChar.uppercaseChar()) {
                'K' -> (numberPart * 1000).toLong()
                'M' -> (numberPart * 1000000).toLong()
                'B' -> (numberPart * 1000000000).toLong()
                else -> clean.replace(",", "").toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    // Helper: Parse upload date for sorting (simple heuristic)
    private fun parseUploadDate(dateStr: String): Long {
        val now = System.currentTimeMillis()
        val s = dateStr.lowercase()
        return when {
            s.contains("second") -> now - (extractNum(s) * 1000)
            s.contains("minute") -> now - (extractNum(s) * 60000)
            s.contains("hour") -> now - (extractNum(s) * 3600000)
            s.contains("day") -> now - (extractNum(s) * 86400000)
            s.contains("week") -> now - (extractNum(s) * 604800000)
            s.contains("month") -> now - (extractNum(s) * 2592000000L)
            s.contains("year") -> now - (extractNum(s) * 31536000000L)
            else -> now
        }
    }
    
    private fun extractNum(s: String): Long {
        return s.filter { it.isDigit() }.toLongOrNull() ?: 1L
    }
}
