package com.flow.youtube.utils

import android.util.Log
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.YouTubeClient
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.flow.youtube.innertube.models.YouTubeClient.Companion.IOS
import com.flow.youtube.innertube.models.YouTubeClient.Companion.IPADOS
import com.flow.youtube.innertube.models.YouTubeClient.Companion.MOBILE
import com.flow.youtube.innertube.models.YouTubeClient.Companion.TVHTML5
import com.flow.youtube.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.flow.youtube.innertube.models.response.PlayerResponse
import com.flow.youtube.innertube.pages.NewPipeExtractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object MusicPlayerUtils {
    private const val TAG = "MusicPlayerUtils"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX
    
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        TVHTML5,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        WEB,
        WEB_CREATOR
    )

    // Request deduplication - prevents duplicate fetches for same video
    private val activeRequests = ConcurrentHashMap<String, CompletableDeferred<Result<PlaybackData>>>()
    
    // Result cache - keeps completed results for short-term reuse
    private data class CachedResult(val result: Result<PlaybackData>, val timestamp: Long)
    private val resultCache = ConcurrentHashMap<String, CachedResult>()
    private const val RESULT_CACHE_TTL_MS = 30_000L // Cache successful results for 30 seconds
    
    private val videoRefreshTimestamps = ConcurrentHashMap<String, Long>()

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val usedClient: YouTubeClient
    )

    private fun isLoggedIn(): Boolean = YouTube.cookie != null

    fun forceRefreshForVideo(videoId: String) {
        Log.d(TAG, "Force refresh requested for $videoId")
        videoRefreshTimestamps[videoId] = System.currentTimeMillis()
        activeRequests.remove(videoId)
        resultCache.remove(videoId)
    }

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null
    ): Result<PlaybackData> = withContext(Dispatchers.IO) {
        val cached = resultCache[videoId]
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.timestamp
            if (age < RESULT_CACHE_TTL_MS && cached.result.isSuccess) {
                Log.d(TAG, "Returning cached result for $videoId (age: ${age}ms)")
                return@withContext cached.result
            } else {
                resultCache.remove(videoId)
            }
        }
        
        val existingRequest = activeRequests[videoId]
        if (existingRequest != null && existingRequest.isActive) {
            Log.d(TAG, "Reusing existing request for $videoId")
            return@withContext existingRequest.await()
        }
        
        val deferred = CompletableDeferred<Result<PlaybackData>>()
        val previousRequest = activeRequests.putIfAbsent(videoId, deferred)
        
        if (previousRequest != null && previousRequest.isActive) {
            Log.d(TAG, "Another thread started request for $videoId, waiting...")
            return@withContext previousRequest.await()
        }
        
        try {
            val result = fetchPlaybackData(videoId, playlistId)
            deferred.complete(result)
            
            if (result.isSuccess) {
                resultCache[videoId] = CachedResult(result, System.currentTimeMillis())
            }
            
            result
        } catch (e: Exception) {
            val failure = Result.failure<PlaybackData>(e)
            deferred.complete(failure)
            failure
        } finally {
            activeRequests.remove(videoId, deferred)
        }
    }
    
    private suspend fun fetchPlaybackData(
        videoId: String,
        playlistId: String?
    ): Result<PlaybackData> = runCatching {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Fetching playback for $videoId (logged in: ${isLoggedIn()})")
        
        val sts = getSignatureTimestamp(videoId)
        Log.d(TAG, "Signature timestamp: $sts")
        
        var response: PlayerResponse? = null
        var usedClient: YouTubeClient? = null
        var extraction: Pair<PlayerResponse.StreamingData.Format, String>? = null
        var mainPlayerResponse: PlayerResponse? = null

        Log.d(TAG, "Trying MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, sts).getOrNull()
        
        if (mainPlayerResponse?.playabilityStatus?.status == "OK") {
            val enhancedResponse = YouTube.newPipePlayer(videoId, mainPlayerResponse)
            val responseToUse = enhancedResponse ?: mainPlayerResponse
            
            extraction = tryExtract(responseToUse, MAIN_CLIENT, videoId)
            if (extraction != null) {
                response = responseToUse
                usedClient = MAIN_CLIENT
                Log.i(TAG, "MAIN_CLIENT success with NewPipe enhancement")
            }
        }

        if (usedClient == null) {
            Log.d(TAG, "MAIN_CLIENT failed or invalid. Starting fallback...")
            
            for ((index, client) in STREAM_FALLBACK_CLIENTS.withIndex()) {
                if (client.loginRequired && !isLoggedIn()) {
                    Log.d(TAG, "Skipping ${client.clientName} - requires login")
                    continue
                }
                
                Log.d(TAG, "Trying fallback ${index + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")
                
                try {
                    val fallbackResponse = YouTube.player(videoId, playlistId, client, sts).getOrNull()
                    
                    if (fallbackResponse?.playabilityStatus?.status == "OK") {
                        val enhancedResponse = YouTube.newPipePlayer(videoId, fallbackResponse)
                        val responseToUse = enhancedResponse ?: fallbackResponse
                        
                        val skipValidation = index == STREAM_FALLBACK_CLIENTS.size - 1
                        val result = tryExtract(responseToUse, client, videoId, validate = !skipValidation)
                        
                        if (result != null) {
                            response = responseToUse
                            extraction = result
                            usedClient = client
                            Log.i(TAG, "Fallback success with ${client.clientName}")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Client ${client.clientName} threw exception: ${e.message}")
                }
            }
        }

        if (response == null || extraction == null || usedClient == null) {
            throw IOException("Failed to resolve stream for $videoId after trying all clients")
        }

        val (format, streamUrl) = extraction

        val playbackTracking = if (usedClient != MAIN_CLIENT && mainPlayerResponse != null) {
            mainPlayerResponse.playbackTracking ?: response.playbackTracking
        } else {
            response.playbackTracking
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        Log.i(TAG, "Playback resolved in ${elapsedMs}ms via ${usedClient.clientName}")

        PlaybackData(
            audioConfig = mainPlayerResponse?.playerConfig?.audioConfig ?: response.playerConfig?.audioConfig,
            videoDetails = mainPlayerResponse?.videoDetails ?: response.videoDetails,
            playbackTracking = playbackTracking,
            format = format,
            streamUrl = streamUrl,
            streamExpiresInSeconds = response.streamingData?.expiresInSeconds ?: 21600,
            usedClient = usedClient
        )
    }

    private fun tryExtract(
        response: PlayerResponse?, 
        client: YouTubeClient,
        videoId: String,
        validate: Boolean = true
    ): Pair<PlayerResponse.StreamingData.Format, String>? {
        if (response?.playabilityStatus?.status != "OK") return null
        
        val format = findBestAudioFormat(response) ?: return null
        
        val url = findUrlOrNull(format, videoId, response)
        if (url == null) {
            Log.d(TAG, "Could not find stream URL for format ${format.itag}")
            return null
        }
        
        if (validate && !checkUrl(url, client.userAgent)) {
            Log.d(TAG, "URL validation failed for ${client.clientName}")
            return null
        }

        val streamUrl = if (format.contentLength != null) {
            "$url&range=0-${format.contentLength}"
        } else {
            url
        }
        
        return Pair(format, streamUrl)
    }

    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse
    ): String? {
        if (!format.url.isNullOrEmpty()) {
            Log.d(TAG, "Using URL from format directly")
            return format.url
        }

        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Log.d(TAG, "URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val exactMatch = streamUrls.find { it.first == format.itag }?.second
            if (exactMatch != null) {
                Log.d(TAG, "URL obtained from StreamInfo (exact itag match)")
                return exactMatch
            }

            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.mimeType.startsWith("audio/")
                } == true
            }?.second

            if (audioStream != null) {
                Log.d(TAG, "Audio stream URL obtained from StreamInfo")
                return audioStream
            }
        }

        Log.w(TAG, "Failed to get stream URL for format ${format.itag}")
        return null
    }

    private fun findBestAudioFormat(response: PlayerResponse): PlayerResponse.StreamingData.Format? {
        val adaptiveFormats = response.streamingData?.adaptiveFormats ?: emptyList()
        
        val audioFormats = adaptiveFormats.filter { it.mimeType.startsWith("audio/") }
        
        if (audioFormats.isEmpty()) {
            Log.d(TAG, "No audio formats found")
            return null
        }
        
        val bestFormat = audioFormats.maxByOrNull { format ->
            format.bitrate + (if (format.mimeType.contains("webm")) 10240 else 0)
        }
        
        Log.d(TAG, "Selected format: ${bestFormat?.mimeType}, bitrate: ${bestFormat?.bitrate}")
        return bestFormat
    }

    private fun checkUrl(url: String, userAgent: String): Boolean {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .head()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) return true
            }
        } catch (e: Exception) {
            // HEAD might not be supported, try GET
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-100")
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 206
            }
        } catch (e: Exception) {
            Log.d(TAG, "URL validation failed: ${e.message}")
            false
        }
    }

    private fun getSignatureTimestamp(videoId: String): Int? {
        return try {
            NewPipeExtractor.getSignatureTimestamp(videoId)
                .onSuccess { Log.d(TAG, "Signature timestamp: $it") }
                .onFailure { Log.w(TAG, "Failed to get signature timestamp: ${it.message}") }
                .getOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting signature timestamp", e)
            null
        }
    }
}
