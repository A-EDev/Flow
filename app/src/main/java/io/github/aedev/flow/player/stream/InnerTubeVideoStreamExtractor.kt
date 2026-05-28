package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object InnerTubeVideoStreamExtractor {
    private const val TAG = "InnerTubeVideoExtractor"
    private const val PER_CLIENT_TIMEOUT_MS = 6000L

    private val VIDEO_STREAM_CLIENTS: List<YouTubeClient> = listOf(
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.IPADOS,
        YouTubeClient.IOS,
        YouTubeClient.MOBILE,
        YouTubeClient.ANDROID_CREATOR,
    )

    data class VideoExtractionResult(
        val videoFormats: List<PlayerResponse.StreamingData.Format>,
        val audioFormats: List<PlayerResponse.StreamingData.Format>,
        val playerResponse: PlayerResponse,
        val usedClient: YouTubeClient,
        val sabrInfo: SabrStreamInfo?,
    )

    suspend fun extract(videoId: String): VideoExtractionResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting extraction for $videoId with ${VIDEO_STREAM_CLIENTS.size} clients")

        val failureReasons = mutableListOf<String>()

        for ((index, client) in VIDEO_STREAM_CLIENTS.withIndex()) {
            try {
                Log.d(TAG, "Trying client ${index + 1}/${VIDEO_STREAM_CLIENTS.size}: ${client.clientName} v${client.clientVersion}")

                val playerResponse = withTimeoutOrNull(PER_CLIENT_TIMEOUT_MS) {
                    YouTube.player(videoId, client = client).getOrNull()
                }

                if (playerResponse == null) {
                    val reason = "${client.clientName}: timeout or null response"
                    Log.w(TAG, reason)
                    failureReasons.add(reason)
                    continue
                }

                if (playerResponse.playabilityStatus.status != "OK") {
                    val reason = "${client.clientName}: status=${playerResponse.playabilityStatus.status}, reason=${playerResponse.playabilityStatus.reason}"
                    Log.w(TAG, reason)
                    failureReasons.add(reason)
                    continue
                }

                val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats
                if (adaptiveFormats.isNullOrEmpty()) {
                    val reason = "${client.clientName}: no adaptive formats in response"
                    Log.w(TAG, reason)
                    failureReasons.add(reason)
                    continue
                }

                val formatsWithUrl = adaptiveFormats
                    .filter { !it.url.isNullOrEmpty() }
                    .map { it.withPlayableUrl(videoId) }
                if (formatsWithUrl.isEmpty()) {
                    val reason = "${client.clientName}: ${adaptiveFormats.size} adaptive formats but none have direct URLs (cipher-only)"
                    Log.w(TAG, reason)
                    failureReasons.add(reason)
                    continue
                }

                val videoFormats = formatsWithUrl.filter { !it.isAudio && it.height != null && it.width != null }
                val audioFormats = formatsWithUrl.filter { it.isAudio }

                if (videoFormats.isEmpty()) {
                    val reason = "${client.clientName}: no video formats with direct URLs (${formatsWithUrl.size} total formats)"
                    Log.w(TAG, reason)
                    failureReasons.add(reason)
                    continue
                }
                if (audioFormats.isEmpty()) {
                    val reason = "${client.clientName}: no audio formats with direct URLs"
                    Log.w(TAG, reason)
                    failureReasons.add(reason)
                    continue
                }

                val sabrInfo = try {
                    SabrUrlResolver.resolve(playerResponse)
                } catch (e: Exception) {
                    Log.d(TAG, "SABR resolution failed: ${e.message}")
                    null
                }

                val heights = videoFormats.mapNotNull { it.height }.distinct().sorted()
                Log.i(TAG, "Success with ${client.clientName}: ${videoFormats.size} video (${heights.joinToString()}p), ${audioFormats.size} audio, sabr=${sabrInfo != null}")

                return@withContext VideoExtractionResult(
                    videoFormats = videoFormats,
                    audioFormats = audioFormats,
                    playerResponse = playerResponse,
                    usedClient = client,
                    sabrInfo = sabrInfo,
                )
            } catch (e: Exception) {
                val reason = "${client.clientName}: exception=${e.javaClass.simpleName}: ${e.message}"
                Log.w(TAG, reason)
                failureReasons.add(reason)
            }
        }

        Log.e(TAG, "All ${VIDEO_STREAM_CLIENTS.size} clients failed for $videoId. Reasons: ${failureReasons.joinToString(" | ")}")
        null
    }

    private suspend fun PlayerResponse.StreamingData.Format.withPlayableUrl(
        videoId: String
    ): PlayerResponse.StreamingData.Format {
        val rawUrl = url ?: return this
        if (!rawUrl.contains("n=")) return this

        return try {
            val transformedUrl = CipherDeobfuscator.transformNParamInUrl(rawUrl)
            if (transformedUrl != rawUrl) {
                Log.d(TAG, "Applied n-transform for $videoId itag=$itag")
            }
            copy(url = transformedUrl)
        } catch (e: Exception) {
            Log.w(TAG, "n-transform failed for $videoId itag=$itag: ${e.message}")
            this
        }
    }
}
