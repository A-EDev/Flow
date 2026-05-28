package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
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

        for ((index, client) in VIDEO_STREAM_CLIENTS.withIndex()) {
            try {
                Log.d(TAG, "Trying client ${index + 1}/${VIDEO_STREAM_CLIENTS.size}: ${client.clientName} v${client.clientVersion}")

                val playerResponse = withTimeoutOrNull(PER_CLIENT_TIMEOUT_MS) {
                    YouTube.player(videoId, client = client).getOrNull()
                }

                if (playerResponse == null) {
                    Log.d(TAG, "${client.clientName}: no response")
                    continue
                }

                if (playerResponse.playabilityStatus.status != "OK") {
                    Log.d(TAG, "${client.clientName}: status=${playerResponse.playabilityStatus.status}, reason=${playerResponse.playabilityStatus.reason}")
                    continue
                }

                val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats
                if (adaptiveFormats.isNullOrEmpty()) {
                    Log.d(TAG, "${client.clientName}: no adaptive formats")
                    continue
                }

                val formatsWithUrl = adaptiveFormats.filter { !it.url.isNullOrEmpty() }
                if (formatsWithUrl.isEmpty()) {
                    Log.d(TAG, "${client.clientName}: adaptive formats have no direct URLs (cipher-only)")
                    continue
                }

                val videoFormats = formatsWithUrl.filter { !it.isAudio && it.height != null && it.width != null }
                val audioFormats = formatsWithUrl.filter { it.isAudio }

                if (videoFormats.isEmpty()) {
                    Log.d(TAG, "${client.clientName}: no video formats with direct URLs")
                    continue
                }
                if (audioFormats.isEmpty()) {
                    Log.d(TAG, "${client.clientName}: no audio formats with direct URLs")
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
                Log.w(TAG, "${client.clientName} failed: ${e.message}")
            }
        }

        Log.w(TAG, "All clients failed for $videoId")
        null
    }
}
