package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object InnerTubeVideoStreamExtractor {
    private const val TAG = "InnerTubeVideoExtractor"
    private const val PER_CLIENT_TIMEOUT_MS = 6000L
    private const val WEB_PLAYER_TIMEOUT_MS = 10000L

    //* Fast, token-free clients tried first. They return direct adaptive URLs (played via normal DASH/progressive) when not bot-walled
     
    private val FAST_CLIENTS: List<YouTubeClient> = listOf(
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.IPADOS,
        YouTubeClient.IOS,
    )

    // Last-resort token-free clients tried after the durable WEB+SABR path
    private val LAST_RESORT_CLIENTS: List<YouTubeClient> = listOf(
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
        Log.d(TAG, "Starting extraction for $videoId")
        val failureReasons = mutableListOf<String>()

        // 1) Fast path: token-free clients with direct URLs
        tryDirectClients(videoId, FAST_CLIENTS, failureReasons)?.let { return@withContext it }

        // 2) Durable path: WEB + BotGuard PoToken + SABR. Survives the LOGIN_REQUIRED bot wall
        tryWebSabr(videoId, failureReasons)?.let { return@withContext it }

        // 3) Last resort: remaining token-free clients
        tryDirectClients(videoId, LAST_RESORT_CLIENTS, failureReasons)?.let { return@withContext it }

        Log.e(TAG, "All clients failed for $videoId. Reasons: ${failureReasons.joinToString(" | ")}")
        null
    }

    private suspend fun tryDirectClients(
        videoId: String,
        clients: List<YouTubeClient>,
        failureReasons: MutableList<String>,
    ): VideoExtractionResult? {
        for (client in clients) {
            try {
                Log.d(TAG, "Trying ${client.clientName} v${client.clientVersion}")

                val playerResponse = withTimeoutOrNull(PER_CLIENT_TIMEOUT_MS) {
                    // Force en-US extraction locale so the response is deterministic across regions.
                    YouTube.player(videoId, client = client, localeOverride = YouTubeLocale.EXTRACTION).getOrNull()
                }

                if (playerResponse == null) {
                    failureReasons.add("${client.clientName}: timeout or null response")
                    continue
                }

                val status = playerResponse.playabilityStatus.status
                if (status != "OK") {
                    val reason = playerResponse.playabilityStatus.reason
                    val tag = if (isBotWall(reason)) "BOT_WALL" else "status=$status"
                    failureReasons.add("${client.clientName}: $tag, reason=$reason")
                    Log.w(TAG, "${client.clientName}: $tag, reason=$reason")
                    continue
                }

                val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats
                if (adaptiveFormats.isNullOrEmpty()) {
                    failureReasons.add("${client.clientName}: no adaptive formats")
                    continue
                }

                val formatsWithUrl = adaptiveFormats
                    .filter { !it.url.isNullOrEmpty() }
                    .map { it.withPlayableUrl(videoId) }
                if (formatsWithUrl.isEmpty()) {
                    failureReasons.add("${client.clientName}: ${adaptiveFormats.size} formats, none with direct URLs (SABR-only/cipher)")
                    continue
                }

                val videoFormats = formatsWithUrl.filter { !it.isAudio && it.height != null && it.width != null }
                val audioFormats = formatsWithUrl.filter { it.isAudio }
                if (videoFormats.isEmpty()) {
                    failureReasons.add("${client.clientName}: no video formats with direct URLs")
                    continue
                }
                if (audioFormats.isEmpty()) {
                    failureReasons.add("${client.clientName}: no audio formats with direct URLs")
                    continue
                }

                val heights = videoFormats.mapNotNull { it.height }.distinct().sorted()
                Log.i(TAG, "Success with ${client.clientName}: ${videoFormats.size} video (${heights.joinToString()}p), ${audioFormats.size} audio (direct URLs)")

                return VideoExtractionResult(
                    videoFormats = videoFormats,
                    audioFormats = audioFormats,
                    playerResponse = playerResponse,
                    usedClient = client,
                    sabrInfo = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureReasons.add("${client.clientName}: exception=${e.javaClass.simpleName}: ${e.message}")
                Log.w(TAG, "${client.clientName} failed: ${e.message}")
            }
        }
        return null
    }

    /**
     * WEB client + a WebView BotGuard PoToken + forced en-US locale.
     * Produces a SABR session (the response is SABR-only). Returns null when no visitorData / PoToken is available
     */
    private suspend fun tryWebSabr(
        videoId: String,
        failureReasons: MutableList<String>,
    ): VideoExtractionResult? {
        try {
            val visitorData = WebPoTokenSession.sessionVisitorData()
            if (visitorData.isNullOrEmpty()) {
                failureReasons.add("WEB: no visitorData")
                return null
            }
            val poToken = WebPoTokenSession.mint(videoId)
            if (poToken == null) {
                failureReasons.add("WEB: PoToken unavailable (WebView missing/broken?)")
                return null
            }
            val sts = CipherDeobfuscator.ensureSignatureTimestamp()

            val playerResponse = withTimeoutOrNull(WEB_PLAYER_TIMEOUT_MS) {
                YouTube.playerWeb(
                    videoId = videoId,
                    signatureTimestamp = sts,
                    poToken = poToken.playerRequestPoToken,
                    visitorData = visitorData,
                    locale = YouTubeLocale.EXTRACTION,
                ).getOrNull()
            }
            if (playerResponse == null) {
                failureReasons.add("WEB: timeout or null response")
                return null
            }

            val status = playerResponse.playabilityStatus.status
            if (status != "OK") {
                val reason = playerResponse.playabilityStatus.reason
                val tag = if (isBotWall(reason)) "BOT_WALL" else "status=$status"
                failureReasons.add("WEB: $tag, reason=$reason")
                Log.w(TAG, "WEB: $tag, reason=$reason")
                return null
            }

            val resolved = SabrUrlResolver.resolve(
                playerResponse,
                injectedPoToken = poToken.streamingDataPoToken,
                injectedVisitorData = visitorData,
            )
            if (resolved == null) {
                failureReasons.add("WEB: SABR resolve failed (no serverAbrStreamingUrl / formats)")
                return null
            }

            val sabrInfo = try {
                val transformed = CipherDeobfuscator.transformNParamInUrl(resolved.streamingUrl)
                if (transformed != resolved.streamingUrl) resolved.copy(streamingUrl = transformed) else resolved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                resolved
            }

            val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats.orEmpty()
            val videoFormats = adaptiveFormats.filter { !it.isAudio && it.height != null }
            val audioFormats = adaptiveFormats.filter { it.isAudio }

            val heights = videoFormats.mapNotNull { it.height }.distinct().sorted()
            Log.i(TAG, "Success with WEB+PoToken (SABR): ${videoFormats.size} video (${heights.joinToString()}p), ${audioFormats.size} audio, sabr=true")

            return VideoExtractionResult(
                videoFormats = videoFormats,
                audioFormats = audioFormats,
                playerResponse = playerResponse,
                usedClient = YouTubeClient.WEB,
                sabrInfo = sabrInfo,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failureReasons.add("WEB: exception=${e.javaClass.simpleName}: ${e.message}")
            Log.w(TAG, "WEB+SABR failed: ${e.message}")
            return null
        }
    }

    private fun isBotWall(reason: String?): Boolean {
        if (reason == null) return false
        return reason.contains("Sign in to confirm", ignoreCase = true) ||
            reason.contains("confirm you", ignoreCase = true) ||
            reason.contains("not a bot", ignoreCase = true) ||
            reason.contains("Inicia sesión", ignoreCase = true) // localized "sign in"
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "n-transform failed for $videoId itag=$itag: ${e.message}")
            this
        }
    }
}
