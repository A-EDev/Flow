package io.github.aedev.flow.player.stream

import android.net.Uri
import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.github.aedev.flow.utils.cipher.PipePipeNsigDecoder
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object InnerTubeVideoStreamExtractor {
    private const val TAG = "InnerTubeVideoExtractor"
    private const val PER_CLIENT_TIMEOUT_MS = 6000L
    private const val WEB_PLAYER_TIMEOUT_MS = 10000L
    private val N_PARAM_REGEX = Regex("""(?:^|[?&])n=([^&]+)""")

    //* Fast, token-free clients tried first. They return direct adaptive URLs (played via normal DASH/progressive) when not bot-walled
     
    private val FAST_CLIENTS: List<YouTubeClient> = listOf(
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.IPADOS,
        YouTubeClient.IOS,
    )

    private val BOT_RESISTANT_CLIENTS: List<YouTubeClient> = listOf(
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
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


    suspend fun extract(videoId: String, forceSabr: Boolean = false): VideoExtractionResult? = withContext(Dispatchers.IO) {
        Log.w(TAG, "Extraction start for $videoId (forceSabr=$forceSabr)")
        val failureReasons = mutableListOf<String>()

        // 1) Fast path: token-free clients with direct URLs
        if (!forceSabr) {
            tryDirectClients(videoId, FAST_CLIENTS, failureReasons)?.let {
                Log.w(TAG, "Extraction OK for $videoId via ${it.usedClient.clientName} (mode=DIRECT)")
                return@withContext it
            }
        }

        tryDirectClients(videoId, BOT_RESISTANT_CLIENTS, failureReasons)?.let {
            Log.w(TAG, "Extraction OK for $videoId via ${it.usedClient.clientName} (mode=DIRECT/embedded)")
            return@withContext it
        }

        // 2) Durable path: WEB + BotGuard PoToken + SABR. Survives the LOGIN_REQUIRED bot wall
        tryWebSabr(videoId, failureReasons)?.let {
            Log.w(TAG, "Extraction OK for $videoId via WEB (mode=SABR)")
            return@withContext it
        }

        // 3) Last resort: remaining token-free clients
        tryDirectClients(videoId, LAST_RESORT_CLIENTS, failureReasons, allowUntransformedN = true)?.let {
            Log.w(TAG, "Extraction OK for $videoId via ${it.usedClient.clientName} (mode=DIRECT/last-resort)")
            return@withContext it
        }

        Log.e(TAG, "All clients failed for $videoId (forceSabr=$forceSabr). Reasons: ${failureReasons.joinToString(" | ")}")
        null
    }

    private suspend fun tryDirectClients(
        videoId: String,
        clients: List<YouTubeClient>,
        failureReasons: MutableList<String>,
        allowUntransformedN: Boolean = false,
    ): VideoExtractionResult? {
        val sts: Int? = if (clients.any { it.useSignatureTimestamp }) {
            NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
        } else null

        for (client in clients) {
            try {
                Log.d(TAG, "Trying ${client.clientName} v${client.clientVersion}")

                val playerResponse = withTimeoutOrNull(PER_CLIENT_TIMEOUT_MS) {
                    // Force en-US extraction locale so the response is deterministic across regions.
                    YouTube.player(
                        videoId,
                        client = client,
                        signatureTimestamp = if (client.useSignatureTimestamp) sts else null,
                        localeOverride = YouTubeLocale.EXTRACTION,
                    ).getOrNull()
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

                PipePipeNsigDecoder.prefetch(adaptiveFormats.mapNotNull { it.url })

                val formatsWithUrl = adaptiveFormats.mapNotNull { it.toPlayableFormat(videoId, allowUntransformedN) }
                if (formatsWithUrl.isEmpty()) {
                    failureReasons.add("${client.clientName}: ${adaptiveFormats.size} formats, none resolvable (SABR-only)")
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
                Log.w(TAG, "WEB+SABR: no visitorData available")
                return null
            }
            val poToken = WebPoTokenSession.mint(videoId)
            if (poToken == null) {
                failureReasons.add("WEB: PoToken unavailable (WebView missing/broken?)")
                Log.w(TAG, "WEB+SABR: PoToken mint returned null (WebView missing/broken?)")
                return null
            }
            val sts = NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                ?: CipherDeobfuscator.ensureSignatureTimestamp()

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
                Log.w(TAG, "WEB+SABR: player request timeout/null")
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
                Log.w(TAG, "WEB+SABR: resolve failed — no serverAbrStreamingUrl/formats (pot/ustreamer present?)")
                return null
            }

            val sabrInfo = try {
                val transformedUrl = transformNParamInUrlOrNull(
                    videoId = videoId,
                    rawUrl = resolved.streamingUrl,
                    label = "SABR"
                )
                if (transformedUrl == null) {
                    failureReasons.add("WEB: SABR URL n-transform failed")
                    Log.w(TAG, "WEB+SABR: refusing SABR URL with untransformed n parameter")
                    return null
                }
                if (transformedUrl != resolved.streamingUrl) resolved.copy(streamingUrl = transformedUrl) else resolved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureReasons.add("WEB: SABR URL n-transform exception=${e.javaClass.simpleName}: ${e.message}")
                Log.w(TAG, "WEB+SABR: SABR URL n-transform threw: ${e.message}")
                return null
            }

            val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats.orEmpty()
            val videoFormats = adaptiveFormats.filter { !it.isAudio && it.height != null }
            val audioFormats = adaptiveFormats.filter { it.isAudio }

            val heights = videoFormats.mapNotNull { it.height }.distinct().sorted()
            Log.w(TAG, "WEB+PoToken (SABR) resolved: ${videoFormats.size} video (${heights.joinToString()}p), ${audioFormats.size} audio, sabr=true")

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
    
    private suspend fun PlayerResponse.StreamingData.Format.toPlayableFormat(
        videoId: String,
        allowUntransformedN: Boolean,
    ): PlayerResponse.StreamingData.Format? {
        if (!url.isNullOrEmpty()) return withPlayableUrl(videoId, allowUntransformedN)
        if (!signatureCipher.isNullOrEmpty() || !cipher.isNullOrEmpty()) {
            val resolved = try {
                NewPipeExtractor.getStreamUrl(this, videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "cipher resolve failed for $videoId itag=$itag: ${e.message}")
                null
            }
            return if (!resolved.isNullOrEmpty()) copy(url = resolved).withPlayableUrl(videoId, allowUntransformedN) else null
        }
        return null
    }

    private suspend fun PlayerResponse.StreamingData.Format.withPlayableUrl(
        videoId: String,
        allowUntransformedN: Boolean,
    ): PlayerResponse.StreamingData.Format? {
        val rawUrl = url ?: return this
        val transformed = transformNParamInUrlOrNull(videoId, rawUrl, "itag=$itag")
        return when {
            transformed != null -> copy(url = transformed)
            allowUntransformedN -> {
                Log.w(TAG, "Using untransformed n URL as last-resort fallback for $videoId itag=$itag; playback may throttle")
                this
            }
            else -> {
                Log.w(TAG, "Rejecting untransformed n URL for $videoId itag=$itag; direct playback would likely throttle")
                null
            }
        }
    }

    private suspend fun transformNParamInUrlOrNull(
        videoId: String,
        rawUrl: String,
        label: String,
    ): String? {
        val rawN = extractNParameter(rawUrl) ?: return rawUrl
        return try {
            var transformed: String? = NewPipeExtractor.deobfuscateThrottling(videoId, rawUrl)
                ?.takeIf { isNParameterTransformed(rawN, it) }
            if (transformed == null) {
                transformed = CipherDeobfuscator.transformNParamInUrl(rawUrl)
                    .takeIf { isNParameterTransformed(rawN, it) }
            }
            if (transformed == null) {
                transformed = PipePipeNsigDecoder.deobfuscateUrl(rawUrl)
                    ?.takeIf { isNParameterTransformed(rawN, it) }
            }
            if (transformed != null) {
                Log.d(TAG, "Applied n-transform for $videoId $label")
            }
            transformed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "n-transform failed for $videoId $label: ${e.message}")
            null
        }
    }

    private fun isNParameterTransformed(rawN: String, candidateUrl: String): Boolean {
        if (candidateUrl.isBlank()) return false
        val candidateN = extractNParameter(candidateUrl) ?: return candidateUrl != rawN
        return candidateN != rawN
    }

    private fun extractNParameter(url: String): String? {
        return try {
            Uri.parse(url).getQueryParameter("n")
        } catch (_: Exception) {
            N_PARAM_REGEX.find(url)?.groupValues?.getOrNull(1)
        }
    }
}
