package com.flow.youtube.utils

import android.util.Log
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.YouTubeClient
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.flow.youtube.innertube.models.YouTubeClient.Companion.IPADOS
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.flow.youtube.innertube.models.YouTubeClient.Companion.MOBILE
import com.flow.youtube.innertube.models.YouTubeClient.Companion.TVHTML5
import com.flow.youtube.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.flow.youtube.innertube.models.YouTubeClient.Companion.IOS
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID
import com.flow.youtube.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object MusicPlayerUtils {
    private const val TAG = "MusicPlayerUtils"
    
    private val httpClient: OkHttpClient
        get() = OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .build()
    
    private var cachedSignatureTimestamp: Int? = null
    private var timestampLastUpdated: Long = 0
    private const val TIMESTAMP_TTL = 24 * 60 * 60 * 1000L // 24 hours

    private val fetchMutex = Mutex()
    private val activeFetches = mutableMapOf<String, Deferred<Result<PlaybackData>>>()

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

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null
    ): Result<PlaybackData> = coroutineScope {
        val deferred = fetchMutex.withLock {
            activeFetches[videoId]?.let { return@withLock it }
            
            async(Dispatchers.IO) {
                innerPlayerResponseForPlayback(videoId, playlistId)
            }.also {
                activeFetches[videoId] = it
            }
        }

        try {
            deferred.await()
        } finally {
            fetchMutex.withLock {
                activeFetches.remove(videoId)
            }
        }
    }

    private suspend fun innerPlayerResponseForPlayback(
        videoId: String,
        playlistId: String? = null
    ): Result<PlaybackData> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Fetching playback for $videoId (logged in: ${isLoggedIn()})")
            
            val sts = getSignatureTimestamp()
            Log.d(TAG, "Using signature timestamp: $sts")

            val batch1 = listOf(ANDROID_VR_1_61_48, WEB_REMIX, TVHTML5, ANDROID_VR_1_43_32)
            
            val batch2 = listOf(IOS, ANDROID_CREATOR, IPADOS, MOBILE)

            val batch3 = listOf(WEB, WEB_CREATOR, ANDROID, TVHTML5_SIMPLY_EMBEDDED_PLAYER)

            val batches = listOf(batch1, batch2, batch3)
            
            var successResult: Triple<YouTubeClient, PlayerResponse, Pair<PlayerResponse.StreamingData.Format, String>>? = null

            for ((index, batch) in batches.withIndex()) {
                Log.d(TAG, "Starting Batch ${index + 1} with ${batch.size} clients...")
                
                successResult = fetchInParallel(batch, videoId, playlistId, sts)
                
                if (successResult != null) {
                    Log.i(TAG, "Batch ${index + 1} Winner: ${successResult.first.clientName}")
                    break
                } else {
                    Log.w(TAG, "Batch ${index + 1} failed. Moving to next...")
                }
            }           
            var playerResponse: PlayerResponse? = null
            var usedClient: YouTubeClient? = null
            var format: PlayerResponse.StreamingData.Format? = null
            var streamUrl: String? = null

            if (successResult != null) {
                usedClient = successResult.first
                playerResponse = successResult.second
                format = successResult.third.first
                streamUrl = successResult.third.second
            }
            
            val playbackTracking = if (usedClient != null && usedClient != WEB_REMIX && playerResponse != null) {
                Log.d(TAG, "Fetching playbackTracking from WEB_REMIX for history sync")
                YouTube.player(videoId, playlistId, WEB_REMIX, sts)
                    .getOrNull()?.playbackTracking ?: playerResponse.playbackTracking
            } else {
                playerResponse?.playbackTracking
            }

            if (format == null || streamUrl == null || playerResponse == null) {
                Log.w(TAG, "All InnerTube Batches failed. Trying NewPipe fallback...")
                
                val newPipeUrl = try {
                    com.flow.youtube.data.music.YouTubeMusicService.getAudioUrl(videoId)
                } catch (e: Exception) {
                    Log.e(TAG, "NewPipe extractor crashed", e)
                    null
                }

                if (newPipeUrl != null) {
                    Log.i(TAG, "NewPipe Fallback Successful! Total time: ${System.currentTimeMillis() - startTime}ms")
                    return@runCatching PlaybackData(
                        audioConfig = null,
                        videoDetails = PlayerResponse.VideoDetails(
                            videoId = videoId,
                            title = "Resolved via NewPipe",
                            author = "Unknown Artist",
                            channelId = "",
                            lengthSeconds = "0",
                            musicVideoType = null,
                            viewCount = null,
                            thumbnail = com.flow.youtube.innertube.models.Thumbnails(emptyList())
                        ),
                        playbackTracking = null,
                        format = PlayerResponse.StreamingData.Format(
                            itag = 140,
                            url = newPipeUrl,
                            mimeType = "audio/mp4",
                            bitrate = 128000,
                            width = null,
                            height = null,
                            contentLength = null,
                            quality = "medium",
                            fps = null,
                            qualityLabel = null,
                            averageBitrate = 128000,
                            audioQuality = "AUDIO_QUALITY_MEDIUM",
                            approxDurationMs = null,
                            audioSampleRate = 44100,
                            audioChannels = 2,
                            loudnessDb = null,
                            lastModified = null,
                            signatureCipher = null,
                            audioTrack = null
                        ),
                        streamUrl = newPipeUrl,
                        streamExpiresInSeconds = 21600,
                        usedClient = YouTubeClient.WEB
                    )
                }

                throw Exception("No suitable audio format found after trying all clients and NewPipe")
            }

            Log.i(TAG, "InnerTube Success! Client: ${usedClient!!.clientName}. Total time: ${System.currentTimeMillis() - startTime}ms")
            
            PlaybackData(
                audioConfig = playerResponse?.playerConfig?.audioConfig,
                videoDetails = playerResponse?.videoDetails,
                playbackTracking = playbackTracking,
                format = format!!,
                streamUrl = streamUrl!!,
                streamExpiresInSeconds = playerResponse!!.streamingData?.expiresInSeconds ?: 3600,
                usedClient = usedClient!!
            )
        }
    }

    private suspend fun fetchInParallel(
        clients: List<YouTubeClient>,
        videoId: String,
        playlistId: String?,
        sts: Int?
    ): Triple<YouTubeClient, PlayerResponse, Pair<PlayerResponse.StreamingData.Format, String>>? = coroutineScope {
        
        val validClients = clients.filter { !it.loginRequired || isLoggedIn() }
        
        if (validClients.isEmpty()) return@coroutineScope null

        val resultChannel = Channel<Triple<YouTubeClient, PlayerResponse, Pair<PlayerResponse.StreamingData.Format, String>>?>()
        val job = this.coroutineContext

        validClients.forEach { client ->
            launch {
                try {
                    val response = YouTube.player(videoId, playlistId, client, sts).getOrNull()
                    
                    val result = tryExtract(response, client)
                    if (result != null) {
                        resultChannel.send(Triple(client, response!!, result))
                    } else {
                        resultChannel.send(null)
                    }
                } catch (e: Exception) {
                    resultChannel.send(null)
                }
            }
        }

        var failures = 0
        while (failures < validClients.size) {
            val result = resultChannel.receive()
            if (result != null) {
                job.cancelChildren() 
                return@coroutineScope result
            }
            failures++
        }
        
        return@coroutineScope null
    }


    private fun tryExtractNoValidation(response: PlayerResponse?, client: YouTubeClient): Pair<PlayerResponse.StreamingData.Format, String>? {
        if (response?.playabilityStatus?.status != "OK") {
            Log.d(TAG, "Client ${client.clientName} failed with status: ${response?.playabilityStatus?.status}")
            return null
        }
        
        val format = findBestAudioFormat(response) ?: return null
        val url = format.url ?: return null
        
        val len = format.contentLength ?: 10000000
        val streamUrl = "$url&range=0-$len"
        Log.d(TAG, "Found format (no validation): ${format.mimeType} via ${client.clientName}")
        return Pair(format, streamUrl)
    }

    private fun tryExtract(response: PlayerResponse?, client: YouTubeClient): Pair<PlayerResponse.StreamingData.Format, String>? {
        if (response?.playabilityStatus?.status != "OK") {
            Log.d(TAG, "Client ${client.clientName} failed with status: ${response?.playabilityStatus?.status}")
            return null
        }
        
        val format = findBestAudioFormat(response) ?: return null
        val url = format.url ?: return null
        
        if (!checkUrl(url, client.userAgent)) {
            Log.w(TAG, "Format found but URL failed validation with ${client.clientName}")
            return null
        }

        val len = format.contentLength ?: 10000000
        val streamUrl = "$url&range=0-$len"
        Log.d(TAG, "Found valid format: ${format.mimeType} via ${client.clientName}")
        return Pair(format, streamUrl)
    }
    

    private fun findBestAudioFormat(response: PlayerResponse): PlayerResponse.StreamingData.Format? {
        val allFormats = (response.streamingData?.adaptiveFormats ?: emptyList()) + 
                         (response.streamingData?.formats ?: emptyList())
        
        var bestFormat = allFormats
            .filter { it.mimeType.startsWith("audio/") }
            .maxByOrNull { it.bitrate }
        
        if (bestFormat == null) {
            bestFormat = allFormats
                .filter { it.mimeType.startsWith("video/") }
                .minByOrNull { it.bitrate } 
        }
        
        return bestFormat
    }

    private fun checkUrl(url: String, userAgent: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .head()
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful.also { response.close() }
        } catch (e: Exception) {
            false
        }
    }

    private fun getSignatureTimestamp(): Int? {
        if (cachedSignatureTimestamp != null && (System.currentTimeMillis() - timestampLastUpdated < TIMESTAMP_TTL)) {
            return cachedSignatureTimestamp
        }

        return try {
            val request = Request.Builder()
                .url("https://www.youtube.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: return null
            
            val matcher = Pattern.compile("\"jsUrl\":\"([^\"]+)\"").matcher(html)
            if (matcher.find()) {
                val jsPath = matcher.group(1)
                val jsUrl = "https://www.youtube.com$jsPath"
                
                val jsRequest = Request.Builder().url(jsUrl).build()
                val jsResponse = httpClient.newCall(jsRequest).execute()
                val jsContent = jsResponse.body?.string() ?: return null
                
                val stsMatcher = Pattern.compile("signatureTimestamp:(\\d+)").matcher(jsContent)
                if (stsMatcher.find()) {
                    val sts = stsMatcher.group(1)?.toIntOrNull()
                    if (sts != null) {
                        cachedSignatureTimestamp = sts
                        timestampLastUpdated = System.currentTimeMillis()
                        Log.i(TAG, "Refreshed Signature Timestamp: $sts")
                        return sts
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch signature timestamp", e)
            null
        }
    }
}
