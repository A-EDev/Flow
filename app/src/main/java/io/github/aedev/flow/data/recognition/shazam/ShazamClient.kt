/*
 * Shazam discovery client. Recognition protocol/mapping ported from Metrolist's
 * :shazamkit (GPL-3.0), which credits MusicRecognizer by Aleksey Saenko.
 */
package io.github.aedev.flow.data.recognition.shazam

import io.github.aedev.flow.data.recognition.RecognitionResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Posts a Shazam DejaVu signature to the Android discovery endpoint and maps the match. */
@Singleton
class ShazamClient @Inject constructor(
    okHttpClient: OkHttpClient
) {
    private val client by lazy {
        HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            expectSuccess = false
        }
    }

    private val requestMutex = Mutex()
    private var lastRequestTime = 0L
    private val resultCache = ConcurrentHashMap<String, CachedResult>()

    suspend fun recognize(signature: String, sampleDurationMs: Long): Result<RecognitionResult> {
        val cacheKey = signature.hashCode().toString()
        getCached(cacheKey)?.let { return Result.success(it) }

        var lastError: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val result = requestMutex.withLock {
                    enforceRateLimit()
                    performRecognition(signature, sampleDurationMs)
                }
                cache(cacheKey, result)
                return Result.success(result)
            } catch (e: Exception) {
                lastError = e
                val rateLimited = e.message?.contains("429") == true ||
                    e.message?.contains("Too many requests", ignoreCase = true) == true
                if (rateLimited && attempt < MAX_RETRIES - 1) {
                    delay(INITIAL_RETRY_DELAY_MS * (1L shl attempt))
                } else {
                    return Result.failure(e)
                }
            }
        }
        return Result.failure(lastError ?: Exception("Recognition failed"))
    }

    private suspend fun performRecognition(signature: String, sampleDurationMs: Long): RecognitionResult {
        val timestamp = System.currentTimeMillis() / 1000
        val uuid1 = UUID.randomUUID().toString().uppercase()
        val uuid2 = UUID.randomUUID().toString()

        val request = ShazamRequestJson(
            geolocation = ShazamRequestJson.Geolocation(
                altitude = Random.nextDouble() * 400 + 100,
                latitude = Random.nextDouble() * 180 - 90,
                longitude = Random.nextDouble() * 360 - 180
            ),
            signature = ShazamRequestJson.Signature(
                samplems = sampleDurationMs,
                timestamp = timestamp,
                uri = signature
            ),
            timestamp = timestamp,
            timezone = TIMEZONES.random()
        )

        val response = client.post("https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2") {
            parameter("sync", "true")
            parameter("webv3", "true")
            parameter("sampling", "true")
            parameter("connected", "")
            parameter("shazamapiversion", "v3")
            parameter("sharehub", "true")
            parameter("video", "v3")
            header("User-Agent", USER_AGENTS.random())
            header("Content-Language", "en_US")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            throw when (response.status.value) {
                429 -> Exception("Too many requests")
                404 -> Exception("No match found")
                in 500..599 -> Exception("Shazam service temporarily unavailable")
                else -> Exception("Recognition failed (error ${response.status.value})")
            }
        }

        return response.body<ShazamResponseJson>().toRecognitionResult()
            ?: throw Exception("No match found")
    }

    private suspend fun enforceRateLimit() {
        val since = System.currentTimeMillis() - lastRequestTime
        if (since < MIN_REQUEST_INTERVAL_MS) delay(MIN_REQUEST_INTERVAL_MS - since)
        lastRequestTime = System.currentTimeMillis()
    }

    private fun getCached(key: String): RecognitionResult? {
        val cached = resultCache[key] ?: return null
        if (System.currentTimeMillis() - cached.timestamp > CACHE_DURATION_MS) {
            resultCache.remove(key)
            return null
        }
        return cached.result
    }

    private fun cache(key: String, result: RecognitionResult) {
        resultCache[key] = CachedResult(System.currentTimeMillis(), result)
    }

    private fun ShazamResponseJson.toRecognitionResult(): RecognitionResult? {
        val track = this.track ?: return null

        val metadata = track.sections?.find { it?.type == "SONG" }?.metadata
        val album = metadata?.find { it?.title == "Album" }?.text
        val label = metadata?.find { it?.title == "Label" }?.text
        val releaseDate = metadata?.find { it?.title == "Released" }?.text
        val lyrics = track.sections?.find { it?.type == "LYRICS" }?.text

        val appleMusicUrl = track.hub?.options
            ?.firstOrNull { it?.providername?.contains("apple", ignoreCase = true) == true }
            ?.actions?.firstOrNull()?.uri
        val spotifyUrl = track.hub?.providers
            ?.find { it?.caption?.contains("spotify", ignoreCase = true) == true }
            ?.actions?.firstOrNull()?.uri
        val youtubeUri = track.hub?.options
            ?.find { it?.type?.contains("video", ignoreCase = true) == true }
            ?.actions?.firstOrNull()?.uri
        val youtubeVideoId = youtubeUri?.let { uri ->
            uri.substringAfterLast("v=", "").takeIf { it.isNotEmpty() }
                ?: uri.substringAfterLast("/", "").takeIf { it.isNotEmpty() && it.length == 11 }
        }

        return RecognitionResult(
            trackId = track.key ?: tagid ?: "",
            title = track.title ?: "",
            artist = track.subtitle ?: "",
            album = album,
            coverArtUrl = track.images?.coverart,
            coverArtHqUrl = track.images?.coverarthq,
            genre = track.genres?.primary,
            releaseDate = releaseDate,
            label = label,
            lyrics = lyrics,
            shazamUrl = track.url,
            appleMusicUrl = appleMusicUrl,
            spotifyUrl = spotifyUrl,
            isrc = track.isrc,
            youtubeVideoId = youtubeVideoId
        )
    }

    private data class CachedResult(val timestamp: Long, val result: RecognitionResult)

    private companion object {
        const val MIN_REQUEST_INTERVAL_MS = 1000L
        const val MAX_RETRIES = 3
        const val INITIAL_RETRY_DELAY_MS = 2000L
        const val CACHE_DURATION_MS = 300_000L

        val USER_AGENTS = listOf(
            "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
            "Dalvik/1.6.0 (Linux; U; Android 4.4.2; SM-T210 Build/KOT49H)",
            "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
            "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
            "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)"
        )
        val TIMEZONES = listOf(
            "Europe/Paris", "Europe/London", "America/New_York",
            "America/Los_Angeles", "Asia/Tokyo", "Asia/Dubai"
        )
    }
}
