package io.github.aedev.flow.player.stream

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.AttestationPlatform
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.sabr.SabrRoutingPolicy
import io.github.aedev.flow.player.sabr.core.SabrCpn
import io.github.aedev.flow.player.sabr.integration.SabrClientIdentity
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.github.aedev.flow.utils.cipher.PipePipeNsigDecoder
import io.github.aedev.flow.utils.potoken.PoTokenResult
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object InnerTubeVideoStreamExtractor {
    private const val TAG = "InnerTubeVideoExtractor"
    private const val PER_CLIENT_TIMEOUT_MS = 6000L
    private const val WEB_PLAYER_TIMEOUT_MS = 10000L

    // How long a confirmed winner will wait for the streaming pot before shipping its
    // URLs pot-less. The mint starts alongside the ladder, so a warm session is ready well before
    // any winner and pays nothing here; this only bounds the slow tail (cold WebView, the
    // low-trust re-attest loop) so first frame is never held open by BotGuard. A pot-less ship is
    // the pre-fix behavior: the URLs still start instantly, and if GVS cuts them ~60s in the
    // existing 403 -> reload path re-extracts with the (by then cached) token and recovers.
    private const val STREAM_POT_ATTACH_GRACE_MS = 2500L
    private val N_PARAM_REGEX = Regex("""(?:^|[?&])n=([^&]+)""")
    private val POT_PARAM_REGEX = Regex("""[?&]pot=""")
    private val extractionCoalescer =
        InFlightRequestCoalescer<ExtractionKey, VideoExtractionResult?>(
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

    private data class ExtractionKey(
        val videoId: String,
        val forceSabr: Boolean,
    )

    // The token-free direct client. VISIONOS alone: it is the only client that still serves direct
    // adaptive URLs GVS will honour for the whole video without a PO Token, and it does so without
    // an `n` parameter, so first frame costs neither an attestation nor an nsig decode.
    private val FAST_CLIENTS: List<YouTubeClient> =
        listOf(
            YouTubeClient.VISIONOS,
        )

    private val BOT_RESISTANT_CLIENTS: List<YouTubeClient> =
        listOf(
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        )

    /**
     * Direct clients GVS now cuts off after ~60s of media without a PO Token Flow cannot mint
     * (DroidGuard). Kept below the attested SABR path rather than deleted: they still answer, still
     * carry a full ladder, and are the difference between degraded playback and none when both
     * VISIONOS and SABR are unavailable. Never promote these above [SABR_CLIENTS].
     */
    private val GATED_FALLBACK_CLIENTS: List<YouTubeClient> =
        listOf(
            YouTubeClient.ANDROID_VR_1_61_48,
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.ANDROID_VR_1_65_10,
            YouTubeClient.ANDROID_VR_1_43_32,
        )

    // Last-resort token-free clients, tried after everything else
    private val LAST_RESORT_CLIENTS: List<YouTubeClient> =
        listOf(
            YouTubeClient.MOBILE,
            YouTubeClient.ANDROID_CREATOR,
        )

    // MWEB first: it carries every dubbed audio track and is web-family, so its GVS token is one
    // Flow's own BotGuard session can mint.
    private val SABR_CLIENTS: List<YouTubeClient> =
        listOf(
            YouTubeClient.MWEB,
            YouTubeClient.WEB,
        )

    private val LIVE_MANIFEST_CLIENTS: List<YouTubeClient> =
        listOf(
            YouTubeClient.VISIONOS,
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.ANDROID_VR_1_61_48,
        )

    data class VideoExtractionResult(
        val videoFormats: List<PlayerResponse.StreamingData.Format>,
        val audioFormats: List<PlayerResponse.StreamingData.Format>,
        val playerResponse: PlayerResponse,
        val usedClient: YouTubeClient,
        val sabrInfo: SabrStreamInfo?,
        val isLive: Boolean = false,
        val liveHlsUrl: String? = null,
        val liveDashUrl: String? = null,
    )

    @OptIn(UnstableApi::class)
    suspend fun extract(
        videoId: String,
        forceSabr: Boolean = false,
    ): VideoExtractionResult? {
        val key = ExtractionKey(videoId, forceSabr)
        return extractionCoalescer.run(key) {
            selectStreams(videoId, forceSabr)
        }
    }

    private suspend fun selectStreams(
        videoId: String,
        forceSabr: Boolean,
    ): VideoExtractionResult? =
        withContext(Dispatchers.IO) {
            Log.w(TAG, "Extraction start for $videoId (forceSabr=$forceSabr)")
            PlayerDiagnostics.logWarning(TAG, "extract start $videoId forceSabr=$forceSabr")
            val failureReasons = mutableListOf<String>()
            val liveDetected = booleanArrayOf(false)

            if (forceSabr) {
                trySabrClients(videoId, failureReasons)?.let {
                    Log.w(TAG, "Extraction OK for $videoId via ${it.usedClient.clientName} (mode=SABR/forced)")
                    PlayerDiagnostics.logWarning(TAG, "extract OK $videoId mode=SABR/forced via ${it.usedClient.clientName}")
                    return@withContext it
                }
                Log.e(TAG, "Forced SABR extraction failed for $videoId. Reasons: ${failureReasons.joinToString(" | ")}")
                PlayerDiagnostics.logError(TAG, "forced SABR FAILED $videoId: ${failureReasons.joinToString(" | ")}")
                return@withContext null
            }

            // 1) Fast path: token-free clients with direct URLs
            tryDirectClients(videoId, FAST_CLIENTS, failureReasons, liveDetected = liveDetected)?.let { direct ->
                val result = maybeUpgradeToSabr(videoId, direct, failureReasons)
                Log.w(TAG, "Extraction OK for $videoId via ${result.usedClient.clientName} (mode=${resultMode(result)})")
                PlayerDiagnostics.logWarning(TAG, "extract OK $videoId via ${result.usedClient.clientName} mode=${resultMode(result)}")
                return@withContext result
            }

            if (liveDetected[0]) {
                tryLiveClients(videoId, failureReasons)?.let {
                    Log.w(TAG, "Live manifest for $videoId via ${it.usedClient.clientName} (live-clients)")
                    return@withContext it
                }
            }

            // 2) Durable path: web client + BotGuard PoToken + SABR. Ranked above every remaining
            // direct client because it is the only other path whose token Flow can actually mint —
            // the clients below it are all served unattested and can be cut off mid-playback.
            trySabrClients(videoId, failureReasons)?.let {
                Log.w(TAG, "Extraction OK for $videoId via ${it.usedClient.clientName} (mode=SABR)")
                PlayerDiagnostics.logWarning(TAG, "extract OK $videoId mode=SABR (durable) via ${it.usedClient.clientName}")
                return@withContext if (liveDetected[0] && !it.isLive) it.copy(isLive = true) else it
            }

            tryDirectClients(videoId, BOT_RESISTANT_CLIENTS, failureReasons, liveDetected = liveDetected)?.let { direct ->
                val result = maybeUpgradeToSabr(videoId, direct, failureReasons)
                Log.w(TAG, "Extraction OK for $videoId via ${result.usedClient.clientName} (mode=${resultMode(result)})")
                return@withContext result
            }

            // 3) Gated direct clients. Playable, but GVS stops serving them ~60s in, so they rank
            // below anything attested and are only reached when the paths above are unavailable.
            tryDirectClients(videoId, GATED_FALLBACK_CLIENTS, failureReasons, liveDetected = liveDetected)?.let { direct ->
                val result = maybeUpgradeToSabr(videoId, direct, failureReasons)
                Log.w(TAG, "Extraction OK for $videoId via ${result.usedClient.clientName} (mode=${resultMode(result)}/gated)")
                PlayerDiagnostics.logWarning(
                    TAG,
                    "extract OK $videoId via ${result.usedClient.clientName} mode=${resultMode(result)}/GATED — " +
                        "unattested, GVS may stop serving ~60s in",
                )
                return@withContext result
            }

            // 4) Last resort: remaining token-free clients
            tryDirectClients(videoId, LAST_RESORT_CLIENTS, failureReasons, allowUntransformedN = true, liveDetected = liveDetected)?.let {
                Log.w(TAG, "Extraction OK for $videoId via ${it.usedClient.clientName} (mode=DIRECT/last-resort)")
                PlayerDiagnostics.logWarning(TAG, "extract OK $videoId via ${it.usedClient.clientName} mode=DIRECT/last-resort")
                return@withContext if (liveDetected[0] && !it.isLive) it.copy(isLive = true) else it
            }

            Log.e(TAG, "All clients failed for $videoId (forceSabr=$forceSabr). Reasons: ${failureReasons.joinToString(" | ")}")
            PlayerDiagnostics.logError(TAG, "ALL clients failed $videoId: ${failureReasons.joinToString(" | ")}")
            null
        }

    private fun resultMode(r: VideoExtractionResult): String =
        when {
            r.isLive -> "LIVE"
            r.sabrInfo != null -> "SABR-upgrade"
            else -> "DIRECT"
        }

    /**
     * Guarded SABR upgrade. YouTube increasingly serves the high rungs of the ladder as
     * SABR-only (no direct URL), so a token-free client can succeed while only exposing e.g.
     * 360p — leaving the user stuck low with "no other quality". When the direct ladder is
     * quality-incomplete ([SabrRoutingPolicy.shouldAttemptSabrUpgrade]), try the
     * WEB+PoToken+SABR path and prefer it ONLY if it beats the direct ceiling. If SABR is
     * unavailable (bot-walled, no PoToken) the original direct result is returned unchanged, so
     * a video that plays today can never regress.
     */
    private suspend fun maybeUpgradeToSabr(
        videoId: String,
        direct: VideoExtractionResult,
        failureReasons: MutableList<String>,
    ): VideoExtractionResult {
        if (direct.isLive || direct.sabrInfo != null) return direct

        val directMaxHeight = direct.videoFormats.maxOfOrNull { it.height ?: 0 } ?: 0
        if (!SabrRoutingPolicy.shouldAttemptSabrUpgrade(directMaxHeight)) return direct

        Log.w(
            TAG,
            "Direct ladder for $videoId capped at ${directMaxHeight}p (< ${SabrRoutingPolicy.QUALITY_UPGRADE_FLOOR}p); attempting SABR upgrade",
        )
        val sabr = trySabrClients(videoId, failureReasons) ?: return direct
        val sabrHeight = sabr.sabrInfo?.videoHeight ?: 0
        return if (sabr.sabrInfo != null && sabrHeight > directMaxHeight) {
            Log.w(TAG, "Upgraded $videoId: ${directMaxHeight}p direct → ${sabrHeight}p SABR")
            sabr
        } else {
            direct
        }
    }

    /**
     * @param client the client that opened the session being reloaded. A reload must stay on it —
     *   re-resolving an MWEB session as WEB would contradict the player response the server is
     *   already tracking — so this deliberately does not walk [SABR_CLIENTS].
     */
    suspend fun resolveSabrDownload(
        videoId: String,
        targetHeight: Int = 0,
        preferredCodec: String? = null,
        cpn: String = SabrCpn.generate(),
        reloadToken: String? = null,
        client: YouTubeClient = YouTubeClient.WEB,
    ): SabrStreamInfo? =
        withContext(Dispatchers.IO) {
            val failureReasons = mutableListOf<String>()
            tryWebSabr(
                videoId = videoId,
                failureReasons = failureReasons,
                targetHeight = targetHeight,
                preferredCodec = preferredCodec,
                cpn = cpn,
                reloadToken = reloadToken,
                client = client,
            )?.sabrInfo.also { sabrInfo ->
                if (sabrInfo == null) {
                    Log.w(TAG, "SABR download resolve failed for $videoId: ${failureReasons.joinToString(" | ")}")
                }
            }
        }

    @OptIn(UnstableApi::class)
    private suspend fun tryDirectClients(
        videoId: String,
        clients: List<YouTubeClient>,
        failureReasons: MutableList<String>,
        allowUntransformedN: Boolean = false,
        liveDetected: BooleanArray? = null,
    ): VideoExtractionResult? =
        coroutineScope {
            val sts: Int? =
                if (clients.any { it.useSignatureTimestamp }) {
                    NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                } else {
                    null
                }

            // Attested player requests yield direct URLs that survive GVS enforcement; unattested
            // ones get cut off roughly a minute in (served briefly, then 403 once the buffer drains).
            val mint = async { WebPoTokenSession.mintBounded(videoId) }
            var mintLogged = false

            suspend fun awaitMint(): PoTokenResult? {
                val result = mint.await()
                if (!mintLogged) {
                    mintLogged = true
                    if (result == null) {
                        Log.w(TAG, "Direct clients for $videoId running without a PoToken (mint unavailable in time)")
                        PlayerDiagnostics.logWarning(
                            TAG,
                            "fast-path UNATTESTED $videoId (no PoToken in time) — direct URLs typically 403 ~60s in",
                        )
                    } else {
                        PlayerDiagnostics.logWarning(
                            TAG,
                            "fast-path attested $videoId (playerPot len=${result.playerRequestPoToken.length}, " +
                                "streamingPot len=${result.streamingDataPoToken.length})",
                        )
                    }
                }
                return result
            }

            for (client in clients) {
                try {
                    Log.d(TAG, "Trying ${client.clientName} v${client.clientVersion}")

                    // Only web-family clients get the BotGuard token. Injecting it into an
                    // ANDROID_VR/IOS /player request makes YT reject it outright (non-OK / empty
                    // streamingData), and stamping it on their URLs is worse than sending nothing:
                    // it is a claim GVS validates and refuses. Those clients are usable only for as
                    // long as YouTube serves them unattested.
                    val webAttested = client.attestation == AttestationPlatform.WEB
                    val clientPoToken = if (webAttested) awaitMint()?.playerRequestPoToken else null

                    val playerResponse =
                        withTimeoutOrNull(PER_CLIENT_TIMEOUT_MS) {
                            // Force en-US extraction locale so the response is deterministic across regions.
                            // Route video extraction to www.youtube.com (not the music host): the main site
                            // serves usable ANDROID_VR direct adaptive formats that survive GVS enforcement,
                            // instead of the SABR-only responses the music endpoint returns for these clients.
                            YouTube
                                .player(
                                    videoId,
                                    client = client,
                                    signatureTimestamp = if (client.useSignatureTimestamp) sts else null,
                                    poToken = clientPoToken,
                                    localeOverride = YouTubeLocale.EXTRACTION,
                                    apiUrl = YouTubeClient.API_URL_YOUTUBE,
                                ).getOrNull()
                        }

                    if (playerResponse == null) {
                        failureReasons.add("${client.clientName}: timeout or null response")
                        PlayerDiagnostics.logWarning(
                            TAG,
                            "skip ${client.clientName} v${client.clientVersion}: null/timeout pot=${clientPoToken != null}",
                        )
                        continue
                    }

                    val status = playerResponse.playabilityStatus.status
                    if (status != "OK") {
                        val reason = playerResponse.playabilityStatus.reason
                        val tag = if (isBotWall(reason)) "BOT_WALL" else "status=$status"
                        failureReasons.add("${client.clientName}: $tag, reason=$reason")
                        Log.w(TAG, "${client.clientName}: $tag, reason=$reason")
                        PlayerDiagnostics.logWarning(
                            TAG,
                            "skip ${client.clientName} v${client.clientVersion}: $tag reason=$reason pot=${clientPoToken != null}",
                        )
                        continue
                    }

                    if (playerResponse.isLiveNow()) {
                        // A genuine live manifest wins outright. Live playback never consumes the
                        // mint, so cancel it rather than letting coroutineScope wait it out.
                        playerResponse.toLiveResultOrNull(client)?.let {
                            mint.cancel()
                            return@coroutineScope it
                        }

                        if (playerResponse.videoDetails?.isLive == true) {
                            liveDetected?.set(0, true)
                            failureReasons.add("${client.clientName}: live but no hls/dash manifest")
                            PlayerDiagnostics.logWarning(
                                TAG,
                                "skip ${client.clientName} v${client.clientVersion}: live (broadcasting), no hls/dash pot=${clientPoToken != null}",
                            )
                            continue
                        }
                        PlayerDiagnostics.logWarning(
                            TAG,
                            "${client.clientName} v${client.clientVersion}: live-flag but VOD (post-live-DVR/premiere) — using adaptive formats",
                        )
                    }

                    val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats
                    if (adaptiveFormats.isNullOrEmpty()) {
                        failureReasons.add("${client.clientName}: no adaptive formats")
                        PlayerDiagnostics.logWarning(
                            TAG,
                            "skip ${client.clientName} v${client.clientVersion}: no adaptive formats pot=${clientPoToken != null}",
                        )
                        continue
                    }

                    primeRemoteNsigIfNeeded(videoId, adaptiveFormats)

                    val formatsWithUrl = adaptiveFormats.mapNotNull { it.toPlayableFormat(videoId, allowUntransformedN) }
                    // Capability probe : logs, per client,
                    // how many adaptive formats carried a direct URL vs how many resolved to a playable
                    // stream, plus whether the response is SABR-capable. Makes "why was this client skipped"
                    // visible in the in-app diagnostics instead of only when every client fails.
                    val rawUrlCount = adaptiveFormats.count { !it.url.isNullOrEmpty() }
                    val sabrPresent = !playerResponse.streamingData?.serverAbrStreamingUrl.isNullOrEmpty()
                    PlayerDiagnostics.logWarning(
                        TAG,
                        "probe ${client.clientName} v${client.clientVersion}: adaptive=${adaptiveFormats.size} " +
                            "hasUrl=$rawUrlCount resolvable=${formatsWithUrl.size} sabr=$sabrPresent pot=${clientPoToken != null}",
                    )
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
                    Log.i(
                        TAG,
                        "Success with ${client.clientName}: ${videoFormats.size} video (${heights.joinToString()}p), ${audioFormats.size} audio (direct URLs)",
                    )

                    val urlPot =
                        if (webAttested) {
                            withTimeoutOrNull(STREAM_POT_ATTACH_GRACE_MS) { awaitMint() }?.streamingDataPoToken
                        } else {
                            null
                        }
                    if (urlPot == null && webAttested) {
                        PlayerDiagnostics.logWarning(
                            TAG,
                            "${client.clientName} winner for $videoId shipping pot-less (mint not ready in " +
                                "${STREAM_POT_ATTACH_GRACE_MS}ms) — direct URLs may 403 ~60s in",
                        )
                    }

                    mint.cancel()

                    return@coroutineScope VideoExtractionResult(
                        videoFormats = if (urlPot != null) videoFormats.map { it.withUrlPoToken(urlPot) } else videoFormats,
                        audioFormats = if (urlPot != null) audioFormats.map { it.withUrlPoToken(urlPot) } else audioFormats,
                        playerResponse = playerResponse,
                        usedClient = client,
                        sabrInfo = null,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failureReasons.add("${client.clientName}: exception=${e.javaClass.simpleName}: ${e.message}")
                    Log.w(TAG, "${client.clientName} failed: ${e.message}")
                    PlayerDiagnostics.logWarning(
                        TAG,
                        "skip ${client.clientName} v${client.clientVersion}: exception=${e.javaClass.simpleName}: ${e.message}",
                    )
                }
            }
            mint.cancel()
            null
        }

    /**
     * Walks [SABR_CLIENTS] until one resolves a SABR session, so a bot wall on WEB alone does not
     * end the durable path.
     */
    private suspend fun trySabrClients(
        videoId: String,
        failureReasons: MutableList<String>,
        targetHeight: Int = 0,
        preferredCodec: String? = null,
        cpn: String = SabrCpn.generate(),
        reloadToken: String? = null,
    ): VideoExtractionResult? {
        for (client in SABR_CLIENTS) {
            tryWebSabr(
                videoId = videoId,
                failureReasons = failureReasons,
                targetHeight = targetHeight,
                preferredCodec = preferredCodec,
                cpn = cpn,
                reloadToken = reloadToken,
                client = client,
            )?.let { return it }
        }
        return null
    }

    /**
     * A web-like [client] + a WebView BotGuard PoToken + forced en-US locale. Produces a SABR
     * session (the response is SABR-only). Returns null when no visitorData / PoToken is available.
     */
    private suspend fun tryWebSabr(
        videoId: String,
        failureReasons: MutableList<String>,
        targetHeight: Int = 0,
        preferredCodec: String? = null,
        cpn: String = SabrCpn.generate(),
        reloadToken: String? = null,
        client: YouTubeClient = YouTubeClient.WEB,
    ): VideoExtractionResult? {
        val label = client.clientName
        try {
            val visitorData = WebPoTokenSession.sessionVisitorData()
            if (visitorData.isNullOrEmpty()) {
                failureReasons.add("$label: no visitorData")
                Log.w(TAG, "$label+SABR: no visitorData available")
                return null
            }
            val poToken = WebPoTokenSession.mintForVisitorData(videoId, visitorData)
            if (poToken == null) {
                failureReasons.add("$label: PoToken unavailable (WebView missing/broken?)")
                Log.w(TAG, "$label+SABR: PoToken mint returned null (WebView missing/broken?)")
                return null
            }
            val sts =
                NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                    ?: CipherDeobfuscator.ensureSignatureTimestamp()

            val playerResponse =
                withTimeoutOrNull(WEB_PLAYER_TIMEOUT_MS) {
                    YouTube
                        .playerWeb(
                            videoId = videoId,
                            signatureTimestamp = sts,
                            poToken = poToken.playerRequestPoToken,
                            visitorData = visitorData,
                            locale = YouTubeLocale.EXTRACTION,
                            cpn = cpn,
                            reloadToken = reloadToken,
                            client = client,
                        ).getOrNull()
                }
            if (playerResponse == null) {
                failureReasons.add("$label: timeout or null response")
                Log.w(TAG, "$label+SABR: player request timeout/null")
                return null
            }

            val status = playerResponse.playabilityStatus.status
            if (status != "OK") {
                val reason = playerResponse.playabilityStatus.reason
                val tag = if (isBotWall(reason)) "BOT_WALL" else "status=$status"
                failureReasons.add("$label: $tag, reason=$reason")
                Log.w(TAG, "$label: $tag, reason=$reason")
                return null
            }

            // StreamerContext.po_token carries the videoId-bound content token, which is what GVS
            // accepts here in practice.
            //
            // The visitor-bound token used to draw sabr.media_serving_enforcement_id_error, and the
            // reason is now understood: it was minted against the whole visitorData blob rather than
            // the 11-char visitor ID GVS actually binds to (see [VisitorId]). That binding is fixed,
            // so the streaming token is a candidate here too — but this path is the durable fallback
            // and the videoId-bound token is the one measured to work, so switching wants an
            // on-device A/B rather than an assumption.
            val resolved =
                if (targetHeight > 0) {
                    SabrUrlResolver.resolveForQuality(
                        playerResponse,
                        targetHeight = targetHeight,
                        preferredCodec = preferredCodec,
                        injectedPoToken = poToken.playerRequestPoToken,
                        injectedVisitorData = visitorData,
                    )
                } else {
                    SabrUrlResolver.resolve(
                        playerResponse,
                        preferredCodec = preferredCodec,
                        injectedPoToken = poToken.playerRequestPoToken,
                        injectedVisitorData = visitorData,
                    )
                }
            if (resolved == null) {
                failureReasons.add("$label: SABR resolve failed (no serverAbrStreamingUrl / formats)")
                Log.w(TAG, "$label+SABR: resolve failed — no serverAbrStreamingUrl/formats (pot/ustreamer present?)")
                return null
            }

            val withTransformedN =
                try {
                    val transformedUrl =
                        transformNParamInUrlOrNull(
                            videoId = videoId,
                            rawUrl = resolved.streamingUrl,
                            label = "SABR",
                        )
                    if (transformedUrl == null) {
                        Log.w(TAG, "$label+SABR: n-transform unavailable; using the server SABR endpoint unchanged")
                        resolved.copy(cpn = cpn)
                    } else {
                        resolved.copy(streamingUrl = transformedUrl, cpn = cpn)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "$label+SABR: n-transform threw; using the server SABR endpoint unchanged: ${e.message}")
                    resolved.copy(cpn = cpn)
                }

            // Carried on the session so the GVS streaming request and any later reload report the
            // same client that minted this response.
            val sabrInfo =
                withTransformedN.copy(
                    clientNameId = SabrClientIdentity.clientNameId(client),
                    clientVersion = client.clientVersion,
                    clientUserAgent = client.userAgent,
                )

            val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats.orEmpty()
            val videoFormats = adaptiveFormats.filter { !it.isAudio && it.height != null }
            val audioFormats = adaptiveFormats.filter { it.isAudio }

            val heights = videoFormats.mapNotNull { it.height }.distinct().sorted()
            Log.w(
                TAG,
                "$label+PoToken (SABR) resolved: ${videoFormats.size} video (${heights.joinToString()}p), " +
                    "${audioFormats.size} audio, sabr=true",
            )

            return VideoExtractionResult(
                videoFormats = videoFormats,
                audioFormats = audioFormats,
                playerResponse = playerResponse,
                usedClient = client,
                sabrInfo = sabrInfo,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failureReasons.add("$label: exception=${e.javaClass.simpleName}: ${e.message}")
            Log.w(TAG, "$label+SABR failed: ${e.message}")
            return null
        }
    }

    private suspend fun tryLiveClients(
        videoId: String,
        failureReasons: MutableList<String>,
    ): VideoExtractionResult? {
        val sts: Int? =
            if (LIVE_MANIFEST_CLIENTS.any { it.useSignatureTimestamp }) {
                NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
            } else {
                null
            }

        for (client in LIVE_MANIFEST_CLIENTS) {
            try {
                val playerResponse =
                    withTimeoutOrNull(PER_CLIENT_TIMEOUT_MS) {
                        YouTube
                            .player(
                                videoId,
                                client = client,
                                signatureTimestamp = if (client.useSignatureTimestamp) sts else null,
                                localeOverride = YouTubeLocale.EXTRACTION,
                                apiUrl = YouTubeClient.API_URL_YOUTUBE,
                            ).getOrNull()
                    }
                if (playerResponse == null) {
                    failureReasons.add("${client.clientName}(live): timeout or null response")
                    continue
                }
                if (playerResponse.playabilityStatus.status != "OK") {
                    failureReasons.add("${client.clientName}(live): status=${playerResponse.playabilityStatus.status}")
                    continue
                }
                playerResponse.toLiveResultOrNull(client)?.let {
                    Log.w(TAG, "Live manifest via ${client.clientName} (hls=${it.liveHlsUrl != null}, dash=${it.liveDashUrl != null})")
                    return it
                }
                failureReasons.add("${client.clientName}(live): no hls/dash manifest")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureReasons.add("${client.clientName}(live): exception=${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return null
    }

    private fun PlayerResponse.isLiveNow(): Boolean =
        LiveDetectionRules.isLiveNow(
            isLive = videoDetails?.isLive,
            isPostLiveDvr = videoDetails?.isPostLiveDvr,
            hasLiveStreamability = playabilityStatus.liveStreamability != null,
            hasHlsManifest = !streamingData?.hlsManifestUrl.isNullOrBlank(),
            hasAdaptiveFormats = !streamingData?.adaptiveFormats.isNullOrEmpty(),
        )

    private fun PlayerResponse.toLiveResultOrNull(client: YouTubeClient): VideoExtractionResult? {
        val hls = streamingData?.hlsManifestUrl?.takeIf { it.isNotBlank() }
        val dash = streamingData?.dashManifestUrl?.takeIf { it.isNotBlank() }
        if (hls == null && dash == null) return null
        return VideoExtractionResult(
            videoFormats = emptyList(),
            audioFormats = emptyList(),
            playerResponse = this,
            usedClient = client,
            sabrInfo = null,
            isLive = true,
            liveHlsUrl = hls,
            liveDashUrl = dash,
        )
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
            val resolved =
                try {
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

    /**
     * GVS's non-SABR stream protection: the streaming (visitor-bound) PoToken rides on the
     * direct URL as the `pot` query param. Appended after the n-transform so the transform
     * never sees or reorders it.
     */
    private fun PlayerResponse.StreamingData.Format.withUrlPoToken(pot: String): PlayerResponse.StreamingData.Format {
        val rawUrl = url ?: return this
        if (POT_PARAM_REGEX.containsMatchIn(rawUrl)) return this
        val separator = if ("?" in rawUrl) "&" else "?"
        return copy(url = "$rawUrl${separator}pot=${Uri.encode(pot)}")
    }

    private suspend fun PlayerResponse.StreamingData.Format.withPlayableUrl(
        videoId: String,
        allowUntransformedN: Boolean,
    ): PlayerResponse.StreamingData.Format? {
        val rawUrl = url ?: return this
        val transformed = transformNParamInUrlOrNull(videoId, rawUrl, "itag=$itag")
        return when {
            transformed != null -> {
                copy(url = transformed)
            }

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
            val transformed =
                localNTransformOrNull(videoId, rawUrl, rawN)
                    ?: PipePipeNsigDecoder
                        .deobfuscateUrl(rawUrl)
                        ?.takeIf { isNParameterTransformed(rawN, it) }
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

    /** NewPipe's decoder, then the in-app cipher. Null when neither could transform [rawN]. */
    private suspend fun localNTransformOrNull(
        videoId: String,
        rawUrl: String,
        rawN: String,
    ): String? {
        NewPipeExtractor
            .deobfuscateThrottling(videoId, rawUrl)
            ?.takeIf { isNParameterTransformed(rawN, it) }
            ?.let { return it }
        return CipherDeobfuscator
            .transformNParamInUrl(rawUrl)
            .takeIf { isNParameterTransformed(rawN, it) }
    }

    /**
     * Batch-primes the remote n decoder, but only once both local decoders have failed on a
     * representative URL.
     *
     * The remote decoder is the third fallback in [transformNParamInUrlOrNull], yet priming it
     * unconditionally put a third-party round trip in front of *every* extraction — the dominant
     * cost of the InnerTube leg on a cold start. Probing locally first keeps that round trip off
     * the path to first frame whenever a local decoder can do the job, while still collapsing the
     * whole ladder into one request when it genuinely is needed. The probe is not wasted work:
     * both local decoders cache their player script, so it warms exactly what the per-format loop
     * below is about to ask for.
     */
    private suspend fun primeRemoteNsigIfNeeded(
        videoId: String,
        adaptiveFormats: List<PlayerResponse.StreamingData.Format>,
    ) {
        val urls = adaptiveFormats.mapNotNull { it.url }
        val sample =
            urls.firstNotNullOfOrNull { url ->
                extractNParameter(url)?.let { url to it }
            } ?: return
        if (localNTransformOrNull(videoId, sample.first, sample.second) != null) return
        Log.w(TAG, "Local n-decoders failed for $videoId; priming remote decoder for ${urls.size} formats")
        PipePipeNsigDecoder.prefetch(urls)
    }

    private fun isNParameterTransformed(
        rawN: String,
        candidateUrl: String,
    ): Boolean {
        if (candidateUrl.isBlank()) return false
        val candidateN = extractNParameter(candidateUrl) ?: return candidateUrl != rawN
        return candidateN != rawN
    }

    private fun extractNParameter(url: String): String? =
        try {
            Uri.parse(url).getQueryParameter("n")
        } catch (_: Exception) {
            N_PARAM_REGEX.find(url)?.groupValues?.getOrNull(1)
        }
}
