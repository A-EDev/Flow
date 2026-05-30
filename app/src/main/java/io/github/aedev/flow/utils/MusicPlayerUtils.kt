package io.github.aedev.flow.utils

import android.net.Uri
import android.util.Log
import io.github.aedev.flow.FlowApplication
import io.github.aedev.flow.data.local.MusicAudioQuality
import io.github.aedev.flow.data.local.PlayerPreferences
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
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.network.AppProxyManager
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.github.aedev.flow.utils.potoken.PoTokenGenerator
import io.github.aedev.flow.utils.potoken.PoTokenResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import kotlin.math.abs

object MusicPlayerUtils {
    private const val TAG = "MusicPlayerUtils"

    private val httpClient: OkHttpClient
        get() = AppProxyManager.applyTo(OkHttpClient.Builder())
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

    private val validationHttpClient: OkHttpClient by lazy {
        AppProxyManager.applyTo(OkHttpClient.Builder())
            .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val poTokenGenerator = PoTokenGenerator()

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        ANDROID_VR_1_43_32,      
        ANDROID_VR_1_61_48,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        IOS,
        WEB,
        WEB_CREATOR
    )

    private val FAST_DIRECT_STREAM_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        ANDROID_VR_NO_AUTH,
        IPADOS,
        MOBILE,
        IOS,
        ANDROID_CREATOR
    )

    // Request deduplication - prevents duplicate fetches for same video
    private val activeRequests = ConcurrentHashMap<String, CompletableDeferred<Result<PlaybackData>>>()
    
    private data class CachedResult(val result: Result<PlaybackData>, val expiryMs: Long)
    private val resultCache = ConcurrentHashMap<String, CachedResult>()
    private const val MAX_RESULT_CACHE_TTL_MS = 600_000L // 10 minutes
    private const val ESCALATION_WINDOW_MS = 120_000L
    
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
        cachedSignatureTimestamp = null
        io.github.aedev.flow.utils.cipher.CipherDeobfuscator.invalidateSignatureTimestamp()
    }

    fun clearPlaybackCache() {
        resultCache.clear()
        Log.d(TAG, "Cleared all cached playback results")
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
        
        var poToken: PoTokenResult? = null
        var sts: Int? = null
        val sessionId = if (isLoggedIn()) YouTube.dataSyncId else YouTube.visitorData
        fun getStsForClient(client: YouTubeClient): Int? {
            if (!client.useSignatureTimestamp) return null
            sts?.let { return it }
            sts = getSignatureTimestamp(videoId)
            Log.d(TAG, "Signature timestamp: $sts")
            return sts
        }

        fun getPoTokenForWebClient(): PoTokenResult? {
            poToken?.let { return it }
            if (sessionId == null) return null
            return try {
                poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                    ?.also {
                        poToken = it
                        Log.d(TAG, "PoToken generated successfully")
                    }
            } catch (e: Exception) {
                Log.w(TAG, "PoToken generation failed: ${e.message}")
                null
            }
        }

        var response: PlayerResponse? = null
        var usedClient: YouTubeClient? = null
        var extraction: Pair<PlayerResponse.StreamingData.Format, ResolvedUrl>? = null
        var mainPlayerResponse: PlayerResponse? = null

        val escalate = videoRefreshTimestamps[videoId]
            ?.let { System.currentTimeMillis() - it < ESCALATION_WINDOW_MS } == true
        if (escalate) {
            Log.w(TAG, "Escalating music resolve for $videoId — skipping fast direct clients (post-failure retry)")
        }

        if (!escalate) {
        Log.d(TAG, "Starting fast direct stream lookup...")
        val fastClients = (FAST_DIRECT_STREAM_CLIENTS.asSequence() + STREAM_FALLBACK_CLIENTS.asSequence())
            .distinctBy { "${it.clientName}:${it.clientVersion}:${it.clientId}" }
            .toList()

        for ((index, client) in fastClients.withIndex()) {
            if (client.loginRequired && !isLoggedIn()) {
                Log.d(TAG, "Skipping ${client.clientName} - requires login")
                continue
            }

            Log.d(TAG, "Trying direct client ${index + 1}/${fastClients.size}: ${client.clientName}")

            try {
                val clientSts = getStsForClient(client)
                val clientPoToken = if (client.useWebPoTokens) getPoTokenForWebClient()?.playerRequestPoToken else null
                val fallbackResponse = YouTube.player(videoId, playlistId, client, clientSts, clientPoToken, localeOverride = YouTubeLocale.EXTRACTION).getOrNull()

                if (fallbackResponse?.playabilityStatus?.status == "OK") {
                    val result = tryExtract(
                        response = fallbackResponse,
                        client = client,
                        videoId = videoId,
                        validate = true,
                        requireDirectUrl = true,
                        allowCipherFallback = false,
                        allowNewPipeFallback = false,
                        allowStreamInfoFallback = false
                    )

                    if (result != null) {
                        response = fallbackResponse
                        extraction = result
                        usedClient = client
                        Log.i(TAG, "Direct stream success with ${client.clientName}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Client ${client.clientName} threw exception: ${e.message}")
            }
        }
        }

        if (usedClient == null) {
            Log.d(TAG, "Trying MAIN_CLIENT after direct clients: ${MAIN_CLIENT.clientName}")
            val mainSts = getStsForClient(MAIN_CLIENT)
            val mainPoToken = if (MAIN_CLIENT.useWebPoTokens) getPoTokenForWebClient()?.playerRequestPoToken else null
            mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, mainSts, mainPoToken, localeOverride = YouTubeLocale.EXTRACTION).getOrNull()

            if (mainPlayerResponse?.playabilityStatus?.status == "OK") {
                extraction = tryExtract(
                    response = mainPlayerResponse,
                    client = MAIN_CLIENT,
                    videoId = videoId,
                    validate = false,
                    requireDirectUrl = true,
                    allowCipherFallback = false,
                    allowNewPipeFallback = false,
                    allowStreamInfoFallback = false
                )
                if (extraction != null) {
                    response = mainPlayerResponse
                    usedClient = MAIN_CLIENT
                    Log.i(TAG, "MAIN_CLIENT direct URL success")
                } else {
                    Log.d(TAG, "MAIN_CLIENT has no direct playable audio URL; skipping cipher on fast path")
                }
            }
        }

        if (usedClient == null) {
            Log.d(TAG, "No direct stream URL resolved; using slow cipher/NewPipe rescue path...")

            if (mainPlayerResponse?.playabilityStatus?.status == "OK") {
                extraction = tryExtract(
                    response = mainPlayerResponse,
                    client = MAIN_CLIENT,
                    videoId = videoId,
                    validate = false,
                    requireDirectUrl = false,
                    allowCipherFallback = true,
                    allowNewPipeFallback = true,
                    allowStreamInfoFallback = true
                )
                if (extraction != null) {
                    response = mainPlayerResponse
                    usedClient = MAIN_CLIENT
                    Log.i(TAG, "Slow rescue success with ${MAIN_CLIENT.clientName}")
                }
            }

            if (usedClient == null) {
                for ((index, client) in STREAM_FALLBACK_CLIENTS.withIndex()) {
                    if (client.loginRequired && !isLoggedIn()) {
                        continue
                    }

                    try {
                        val clientSts = getStsForClient(client)
                        val clientPoToken = if (client.useWebPoTokens) getPoTokenForWebClient()?.playerRequestPoToken else null
                        val fallbackResponse = YouTube.player(videoId, playlistId, client, clientSts, clientPoToken, localeOverride = YouTubeLocale.EXTRACTION).getOrNull()

                        if (fallbackResponse?.playabilityStatus?.status == "OK") {
                            val result = tryExtract(
                                response = fallbackResponse,
                                client = client,
                                videoId = videoId,
                                validate = index != STREAM_FALLBACK_CLIENTS.lastIndex,
                                requireDirectUrl = false,
                                allowCipherFallback = true,
                                allowNewPipeFallback = true,
                                allowStreamInfoFallback = index == STREAM_FALLBACK_CLIENTS.lastIndex
                            )

                            if (result != null) {
                                response = fallbackResponse
                                extraction = result
                                usedClient = client
                                Log.i(TAG, "Slow fallback success with ${client.clientName}")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Slow fallback ${client.clientName} threw exception: ${e.message}")
                    }
                }
            }
        }

        if (response == null || extraction == null || usedClient == null) {
            throw IOException("Failed to resolve stream for $videoId after trying all clients")
        }

        val (format, resolved) = extraction
        val rawStreamUrl = resolved.url

        val streamUrl = if (resolved.needsNTransform) {
            try {
                var transformedUrl = NewPipeExtractor.deobfuscateThrottling(videoId, rawStreamUrl)
                    ?.takeIf { it != rawStreamUrl }
                    ?: CipherDeobfuscator.transformNParamInUrl(rawStreamUrl).takeIf { it != rawStreamUrl }
                    ?: io.github.aedev.flow.utils.cipher.PipePipeNsigDecoder.deobfuscateUrl(rawStreamUrl)
                    ?: rawStreamUrl
                if (usedClient.useWebPoTokens) {
                    val streamingPoToken = getPoTokenForWebClient()?.streamingDataPoToken
                    if (streamingPoToken != null && !transformedUrl.contains("pot=")) {
                        val separator = if ("?" in transformedUrl) "&" else "?"
                        transformedUrl = "${transformedUrl}${separator}pot=${Uri.encode(streamingPoToken)}"
                    }
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
        validate: Boolean = true,
        requireDirectUrl: Boolean = false,
        allowCipherFallback: Boolean = true,
        allowNewPipeFallback: Boolean = true,
        allowStreamInfoFallback: Boolean = true
    ): Pair<PlayerResponse.StreamingData.Format, ResolvedUrl>? {
        if (response?.playabilityStatus?.status != "OK") return null

        val format = findBestAudioFormat(response, requireDirectUrl) ?: return null

        val resolved = findUrlOrNull(
            format = format,
            videoId = videoId,
            playerResponse = response,
            allowCipherFallback = allowCipherFallback,
            allowNewPipeFallback = allowNewPipeFallback,
            allowStreamInfoFallback = allowStreamInfoFallback
        )
        if (resolved == null) {
            Log.d(TAG, "Could not find stream URL for format ${format.itag}")
            return null
        }

        val needsValidation = validate &&
            !client.clientName.startsWith("ANDROID") &&
            client.clientName != "IOS"
        if (needsValidation && !checkUrl(resolved.url, client.userAgent)) {
            Log.d(TAG, "URL validation failed for ${client.clientName}")
            return null
        }

        return Pair(format, resolved)
    }

    private data class ResolvedUrl(val url: String, val needsNTransform: Boolean)

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        allowCipherFallback: Boolean,
        allowNewPipeFallback: Boolean,
        allowStreamInfoFallback: Boolean
    ): ResolvedUrl? {
        if (!format.url.isNullOrEmpty()) {
            Log.d(TAG, "URL obtained from format directly")
            return ResolvedUrl(format.url, needsNTransform = true)
        }

        val signatureCipher = format.signatureCipher ?: format.cipher
        if (allowCipherFallback && !signatureCipher.isNullOrEmpty()) {
            Log.d(TAG, "Format has signatureCipher, using CipherDeobfuscator")
            val deobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (deobfuscatedUrl != null) {
                Log.d(TAG, "URL obtained via CipherDeobfuscator")
                return ResolvedUrl(deobfuscatedUrl, needsNTransform = true)
            }
        }

        if (allowNewPipeFallback) {
            val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
            if (deobfuscatedUrl != null) {
                Log.d(TAG, "URL obtained via NewPipe")
                return ResolvedUrl(deobfuscatedUrl, needsNTransform = false)
            }
        }

        if (allowStreamInfoFallback) {
            val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
            if (streamUrls.isNotEmpty()) {
                val exactMatch = streamUrls.find { it.first == format.itag }?.second
                if (exactMatch != null) {
                    Log.d(TAG, "URL obtained from StreamInfo (exact itag match)")
                    return ResolvedUrl(exactMatch, needsNTransform = false)
                }

                val audioStream = streamUrls.find { urlPair ->
                    playerResponse.streamingData?.adaptiveFormats?.any {
                        it.itag == urlPair.first && it.mimeType.startsWith("audio/")
                    } == true
                }?.second

                if (audioStream != null) {
                    Log.d(TAG, "Audio stream URL obtained from StreamInfo")
                    return ResolvedUrl(audioStream, needsNTransform = false)
                }
            }
        }

        Log.w(TAG, "Failed to get stream URL for format ${format.itag}")
        return null
    }

    private fun findBestAudioFormat(
        response: PlayerResponse,
        requireDirectUrl: Boolean = false
    ): PlayerResponse.StreamingData.Format? {
        val adaptiveFormats = response.streamingData?.adaptiveFormats ?: emptyList()
        
        val audioFormats = adaptiveFormats.filter { format ->
            format.mimeType.startsWith("audio/") &&
                format.audioTrack?.isAutoDubbed != true &&
                (!requireDirectUrl || !format.url.isNullOrEmpty())
        }
        
        if (audioFormats.isEmpty()) {
            Log.d(TAG, "No audio formats found")
            return null
        }

        val playerPreferences = PlayerPreferences(FlowApplication.appContext)
        val preferredAudioLanguage = runBlocking {
            playerPreferences.preferredAudioLanguage.first()
        }
        val preferredMusicAudioQuality = runBlocking {
            playerPreferences.musicAudioQuality.first()
        }
        val preferredFormats = preferredAudioFormats(audioFormats, preferredAudioLanguage)
        
        val bestFormat = selectPreferredMusicFormat(preferredFormats, preferredMusicAudioQuality)
        
        Log.d(TAG, "Selected format: ${bestFormat?.mimeType}, bitrate: ${bestFormat?.bitrate}")
        return bestFormat
    }

    private fun selectPreferredMusicFormat(
        formats: List<PlayerResponse.StreamingData.Format>,
        preferredMusicAudioQuality: MusicAudioQuality
    ): PlayerResponse.StreamingData.Format? {
        if (formats.isEmpty()) return null

        val formatsWithKnownBitrate = formats.filter { it.audioBitrate() > 0 }.ifEmpty { formats }
        return when (preferredMusicAudioQuality) {
            MusicAudioQuality.AUTO,
            MusicAudioQuality.HIGH -> formatsWithKnownBitrate.maxByOrNull { it.audioQualityScore() }
            MusicAudioQuality.MEDIUM -> formatsWithKnownBitrate.minByOrNull { abs(it.audioBitrate() - MEDIUM_BITRATE_TARGET) }
            MusicAudioQuality.LOW -> formatsWithKnownBitrate.minByOrNull { it.audioBitrate() }
        }
    }

    private fun PlayerResponse.StreamingData.Format.audioQualityScore(): Int {
        return audioBitrate() + if (mimeType.contains("webm")) 10_240 else 0
    }

    private fun PlayerResponse.StreamingData.Format.audioBitrate(): Int {
        return averageBitrate?.takeIf { it > 0 } ?: bitrate
    }

    private fun preferredAudioFormats(
        formats: List<PlayerResponse.StreamingData.Format>,
        preferredAudioLanguage: String
    ): List<PlayerResponse.StreamingData.Format> {
        val normalizedPreference = preferredAudioLanguage.trim().lowercase(Locale.ROOT)

        if (normalizedPreference.isBlank() || normalizedPreference == "original") {
            val originals = formats.filter { it.isOriginal }
            if (originals.isNotEmpty()) return originals
            return formats
        }

        val languageMatches = formats.filter { format ->
            val trackId = format.audioTrack?.id.orEmpty()
            val displayName = format.audioTrack?.displayName.orEmpty()
            trackId.equals(normalizedPreference, ignoreCase = true) ||
                trackId.startsWith(normalizedPreference, ignoreCase = true) ||
                displayName.contains(normalizedPreference, ignoreCase = true)
        }
        if (languageMatches.isNotEmpty()) return languageMatches

        val originals = formats.filter { it.isOriginal }
        if (originals.isNotEmpty()) return originals

        return formats
    }

    private const val MEDIUM_BITRATE_TARGET = 128_000

    private fun checkUrl(url: String, userAgent: String): Boolean {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .head()
            YouTube.cookie?.let { reqBuilder.header("Cookie", it) }
            validationHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.d(TAG, "URL validation failed: ${e.message}")
            false
        }
    }

    private fun getSignatureTimestamp(videoId: String): Int? {
        cachedSignatureTimestamp?.let {
            Log.d(TAG, "Returning cached session signature timestamp: $it")
            return it
        }

        try {
            val npSts = NewPipeExtractor.getSignatureTimestamp(videoId)
                .onSuccess { Log.d(TAG, "Signature timestamp from NewPipeExtractor: $it") }
                .onFailure { Log.w(TAG, "Failed to get signature timestamp from NewPipe: ${it.message}") }
                .getOrNull()
            if (npSts != null) {
                cachedSignatureTimestamp = npSts
                return npSts
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting signature timestamp from NewPipe", e)
        }

        return try {
            io.github.aedev.flow.utils.cipher.CipherDeobfuscator.getSignatureTimestamp()
                ?.also {
                    Log.d(TAG, "Signature timestamp obtained from CipherDeobfuscator: $it")
                    cachedSignatureTimestamp = it
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get signature timestamp from CipherDeobfuscator: ${e.message}")
            null
        }
    }
}
