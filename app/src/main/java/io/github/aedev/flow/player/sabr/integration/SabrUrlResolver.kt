package io.github.aedev.flow.player.sabr.integration

import android.net.Uri
import android.util.Log
import io.github.aedev.flow.innertube.models.response.PlayerResponse

data class SabrStreamInfo(
    val streamingUrl: String,
    val audioItag: Int,
    val audioLmt: Long,
    val videoItag: Int,
    val videoLmt: Long,
    val durationMs: Long,
    val poToken: String = "",
    val visitorId: String = "",
    val ustreamerConfig: ByteArray = ByteArray(0),
    val audioMimeType: String = "",
    val videoMimeType: String = ""
)

object SabrUrlResolver {
    private const val TAG = "SabrUrlResolver"

    private val PREFERRED_AUDIO_ITAGS = listOf(251, 250, 249, 140, 141)

    private val PREFERRED_VIDEO_ITAGS_BY_HEIGHT = mapOf(
        2160 to listOf(313, 271),
        1440 to listOf(308, 271),
        1080 to listOf(248, 303, 299),
        720  to listOf(247, 302, 298),
        480  to listOf(244, 218),
        360  to listOf(243, 134),
        240  to listOf(242, 133)
    )

    /**
     * Resolve a SABR session from a player response.
     *
     * @param injectedPoToken the GVS/streaming PoToken (base64) to send in StreamerContext.po_token.
     *   Preferred over any `pot` query param on the URL (which is the non-SABR mechanism).
     * @param injectedVisitorData the session-bound visitorData, must match the one used to mint the token.
     */
    fun resolve(
        playerResponse: PlayerResponse,
        injectedPoToken: String? = null,
        injectedVisitorData: String? = null
    ): SabrStreamInfo? {
        val streamingData = playerResponse.streamingData ?: return null
        val sabrUrl = streamingData.serverAbrStreamingUrl
        if (sabrUrl.isNullOrEmpty()) {
            Log.d(TAG, "No serverAbrStreamingUrl in player response")
            return null
        }
        val poToken = injectedPoToken?.takeIf { it.isNotEmpty() }
            ?: queryParameter(sabrUrl, "pot").orEmpty()
        val visitorId = injectedVisitorData?.takeIf { it.isNotEmpty() }
            ?: playerResponse.responseContext.visitorData.orEmpty()
        if (poToken.isEmpty() || visitorId.isEmpty()) {
            Log.d(TAG, "Skipping SABR: missing token context (pot=${poToken.isNotEmpty()}, visitor=${visitorId.isNotEmpty()})")
            return null
        }

        val adaptiveFormats = streamingData.adaptiveFormats
        if (adaptiveFormats.isEmpty()) {
            Log.w(TAG, "No adaptive formats available")
            return null
        }

        val audioFormats = adaptiveFormats.filter { it.isAudio }
        val videoFormats = adaptiveFormats.filter { !it.isAudio }

        val selectedAudio = selectBestAudio(audioFormats)
        val selectedVideo = selectBestVideo(videoFormats)

        if (selectedAudio == null || selectedVideo == null) {
            Log.w(TAG, "Could not select audio/video format: audio=${selectedAudio != null}, video=${selectedVideo != null}")
            return null
        }

        val durationMs = selectedVideo.approxDurationMs?.toLongOrNull()
            ?: selectedAudio.approxDurationMs?.toLongOrNull()
            ?: (playerResponse.videoDetails?.lengthSeconds?.toLongOrNull()?.let { it * 1000L })
            ?: 0L

        Log.d(TAG, "Resolved SABR: audioItag=${selectedAudio.itag}, videoItag=${selectedVideo.itag}, " +
            "video=${selectedVideo.width}x${selectedVideo.height}, duration=${durationMs}ms, ustreamer=${extractUstreamerConfig(playerResponse).size}B")

        return SabrStreamInfo(
            streamingUrl = sabrUrl,
            audioItag = selectedAudio.itag,
            audioLmt = selectedAudio.lastModified ?: 0L,
            videoItag = selectedVideo.itag,
            videoLmt = selectedVideo.lastModified ?: 0L,
            durationMs = durationMs,
            poToken = poToken,
            visitorId = visitorId,
            ustreamerConfig = extractUstreamerConfig(playerResponse),
            audioMimeType = selectedAudio.mimeType,
            videoMimeType = selectedVideo.mimeType
        )
    }

    fun resolveForQuality(
        playerResponse: PlayerResponse,
        targetHeight: Int,
        injectedPoToken: String? = null,
        injectedVisitorData: String? = null
    ): SabrStreamInfo? {
        val streamingData = playerResponse.streamingData ?: return null
        val sabrUrl = streamingData.serverAbrStreamingUrl ?: return null
        val poToken = injectedPoToken?.takeIf { it.isNotEmpty() }
            ?: queryParameter(sabrUrl, "pot").orEmpty()
        val visitorId = injectedVisitorData?.takeIf { it.isNotEmpty() }
            ?: playerResponse.responseContext.visitorData.orEmpty()
        if (poToken.isEmpty() || visitorId.isEmpty()) {
            Log.d(TAG, "Skipping SABR quality resolve: missing token context")
            return null
        }

        val adaptiveFormats = streamingData.adaptiveFormats
        val audioFormats = adaptiveFormats.filter { it.isAudio }
        val videoFormats = adaptiveFormats.filter { !it.isAudio }

        val selectedAudio = selectBestAudio(audioFormats) ?: return null
        val selectedVideo = selectVideoForHeight(videoFormats, targetHeight) ?: return null

        val durationMs = selectedVideo.approxDurationMs?.toLongOrNull()
            ?: selectedAudio.approxDurationMs?.toLongOrNull()
            ?: (playerResponse.videoDetails?.lengthSeconds?.toLongOrNull()?.let { it * 1000L })
            ?: 0L

        return SabrStreamInfo(
            streamingUrl = sabrUrl,
            audioItag = selectedAudio.itag,
            audioLmt = selectedAudio.lastModified ?: 0L,
            videoItag = selectedVideo.itag,
            videoLmt = selectedVideo.lastModified ?: 0L,
            durationMs = durationMs,
            poToken = poToken,
            visitorId = visitorId,
            ustreamerConfig = extractUstreamerConfig(playerResponse),
            audioMimeType = selectedAudio.mimeType,
            videoMimeType = selectedVideo.mimeType
        )
    }

    // Decode the base64 `videoPlaybackUstreamerConfig` required by the SABR POST body. 
    private fun extractUstreamerConfig(playerResponse: PlayerResponse): ByteArray {
        val b64 = playerResponse.playerConfig
            ?.mediaCommonConfig
            ?.mediaUstreamerRequestConfig
            ?.videoPlaybackUstreamerConfig
            ?.takeIf { it.isNotEmpty() }
            ?: return ByteArray(0)
        return try {
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode videoPlaybackUstreamerConfig: ${e.message}")
            ByteArray(0)
        }
    }

    private fun queryParameter(url: String, name: String): String? {
        return try {
            Uri.parse(url).getQueryParameter(name)
        } catch (e: Exception) {
            null
        }
    }

    private fun selectBestAudio(
        audioFormats: List<PlayerResponse.StreamingData.Format>
    ): PlayerResponse.StreamingData.Format? {
        for (preferredItag in PREFERRED_AUDIO_ITAGS) {
            audioFormats.find { it.itag == preferredItag }?.let { return it }
        }
        val webmAudio = audioFormats
            .filter { it.mimeType.contains("webm", ignoreCase = true) }
            .maxByOrNull { it.bitrate }
        if (webmAudio != null) return webmAudio

        return audioFormats.maxByOrNull { it.bitrate }
    }

    private fun selectBestVideo(
        videoFormats: List<PlayerResponse.StreamingData.Format>
    ): PlayerResponse.StreamingData.Format? {
        val webmVideos = videoFormats.filter { it.mimeType.contains("webm", ignoreCase = true) }

        val best = webmVideos.maxByOrNull { (it.height ?: 0) * 10000 + it.bitrate }
        if (best != null) return best

        return videoFormats.maxByOrNull { (it.height ?: 0) * 10000 + it.bitrate }
    }

    private fun selectVideoForHeight(
        videoFormats: List<PlayerResponse.StreamingData.Format>,
        targetHeight: Int
    ): PlayerResponse.StreamingData.Format? {
        val preferredItags = PREFERRED_VIDEO_ITAGS_BY_HEIGHT[targetHeight]
        if (preferredItags != null) {
            for (itag in preferredItags) {
                videoFormats.find { it.itag == itag }?.let { return it }
            }
        }

        val webmAtHeight = videoFormats
            .filter { it.mimeType.contains("webm", ignoreCase = true) && it.height == targetHeight }
            .maxByOrNull { it.bitrate }
        if (webmAtHeight != null) return webmAtHeight

        val anyAtHeight = videoFormats
            .filter { it.height == targetHeight }
            .maxByOrNull { it.bitrate }
        if (anyAtHeight != null) return anyAtHeight

        return videoFormats
            .sortedBy { kotlin.math.abs((it.height ?: 0) - targetHeight) }
            .firstOrNull()
    }
}
