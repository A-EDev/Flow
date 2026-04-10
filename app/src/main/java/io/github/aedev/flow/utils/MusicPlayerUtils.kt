package io.github.aedev.flow.utils

import android.net.Uri
import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.IOS
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.IPADOS
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.MOBILE
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.TVHTML5
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.WEB
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.WEB_REMIX
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.github.aedev.flow.utils.potoken.PoTokenGenerator
import io.github.aedev.flow.utils.potoken.PoTokenResult
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

    private val poTokenGenerator = PoTokenGenerator()

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
    
    private data class CachedResult(val result: Result<PlaybackData>, val expiryMs: Long)
    private val resultCache = ConcurrentHashMap<String, CachedResult>()
    private const val MAX_RESULT_CACHE_TTL_MS = 600_000L // 10 minutes
    
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
            if (System.currentTimeMillis() < cached.expiryMs && cached.result.isSuccess) {
                Log.d(TAG, "Returning cached result for $videoId (expires in ${cached.expiryMs - System.currentTimeMillis()}ms)")
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
                val expiresInSec = result.getOrNull()?.streamExpiresInSeconds ?: 300
                val ttlMs = minOf(expiresInSec * 1000L - 60_000L, MAX_RESULT_CACHE_TTL_MS).coerceAtLeast(30_000L)
                resultCache[videoId] = CachedResult(result, System.currentTimeMillis() + ttlMs)
                Log.d(TAG, "Cached result for $videoId, TTL=${ttlMs / 1000}s")
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

        // Generate PoToken for WEB clients
        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn()) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                if (poToken != null) Log.d(TAG, "PoToken generated successfully")
            } catch (e: Exception) {
                Log.w(TAG, "PoToken generation failed: ${e.message}")
            }
        }

        var response: PlayerResponse? = null
        var usedClient: YouTubeClient? = null
        var extraction: Pair<PlayerResponse.StreamingData.Format, String>? = null
        var mainPlayerResponse: PlayerResponse? = null

        Log.d(TAG, "Trying MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, sts, poToken?.playerRequestPoToken).getOrNull()
        
        if (mainPlayerResponse?.playabilityStatus?.status == "OK") {
            extraction = tryExtract(mainPlayerResponse, MAIN_CLIENT, videoId, validate = false)
            if (extraction != null) {
                response = mainPlayerResponse
                usedClient = MAIN_CLIENT
                Log.i(TAG, "MAIN_CLIENT success")
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
                    val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                    val fallbackResponse = YouTube.player(videoId, playlistId, client, sts, clientPoToken).getOrNull()
                    
                    if (fallbackResponse?.playabilityStatus?.status == "OK") {
                        val skipValidation = index == STREAM_FALLBACK_CLIENTS.size - 1
                        val result = tryExtract(fallbackResponse, client, videoId, validate = !skipValidation)
                        
                        if (result != null) {
                            response = fallbackResponse
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

        val (format, rawStreamUrl) = extraction

        // Apply n-transform and append pot= for web clients
        val needsNTransform = usedClient.useWebPoTokens ||
            usedClient.clientName in setOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")
        val streamUrl = if (needsNTransform) {
            try {
                var transformedUrl = CipherDeobfuscator.transformNParamInUrl(rawStreamUrl)
                if (poToken?.streamingDataPoToken != null) {
                    val separator = if ("?" in transformedUrl) "&" else "?"
                    transformedUrl = "${transformedUrl}${separator}pot=${Uri.encode(poToken.streamingDataPoToken)}"
                }
                transformedUrl
            } catch (e: Exception) {
                Log.w(TAG, "N-transform/pot failed, using raw URL: ${e.message}")
                rawStreamUrl
            }
        } else {
            rawStreamUrl
        }

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

    private suspend fun tryExtract(
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
        
        val needsValidation = validate &&
            !client.clientName.startsWith("ANDROID") &&
            client.clientName != "IOS"
        if (needsValidation && !checkUrl(url, client.userAgent)) {
            Log.d(TAG, "URL validation failed for ${client.clientName}")
            return null
        }

        return Pair(format, url)
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse
    ): String? {
        // 1. Direct URL from format
        if (!format.url.isNullOrEmpty()) {
            Log.d(TAG, "URL obtained from format directly")
            return format.url
        }

        // 2. SignatureCipher deobfuscation via CipherDeobfuscator
        val signatureCipher = format.signatureCipher
        if (!signatureCipher.isNullOrEmpty()) {
            Log.d(TAG, "Format has signatureCipher, using CipherDeobfuscator")
            val deobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (deobfuscatedUrl != null) {
                Log.d(TAG, "URL obtained via CipherDeobfuscator")
                return deobfuscatedUrl
            }
        }

        // 3. NewPipe deobfuscation
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Log.d(TAG, "URL obtained via NewPipe")
            return deobfuscatedUrl
        }

        // 4. StreamInfo fallback
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
        
        val audioFormats = adaptiveFormats.filter { format ->
            format.mimeType.startsWith("audio/") &&
                format.audioTrack?.isAutoDubbed != true 
        }
        
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
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .head()
            YouTube.cookie?.let { reqBuilder.header("Cookie", it) }
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) return true
            }
        } catch (e: Exception) {
            // HEAD might not be supported, try GET
        }

        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-100")
                .get()
            YouTube.cookie?.let { reqBuilder.header("Cookie", it) }
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
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
