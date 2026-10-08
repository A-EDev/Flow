package io.github.aedev.flow.player.stream

import android.os.SystemClock
import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.utils.cipher.PipePipeNsigDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A video's streams from [YouTubeClient.TV_TIZEN], the source that keeps serving a visitor GVS has
 * walled on the app clients (#921). Every format it returns is signed, so the signatures and `n`
 * parameters are solved by the remote decoder against the player whose timestamp the request
 * carried, and an answer with a pre-roll is held until GVS will serve it ([PrerollWait]). Null when
 * any step is unavailable; callers fall back to what they did before.
 */
internal object TizenStreamResolver {
    private const val TAG = "TizenStreams"
    private const val PLAYER_TIMEOUT_MS = 8_000L

    suspend fun resolve(videoId: String): InnerTubeVideoStreamExtractor.VideoExtractionResult? =
        withContext(Dispatchers.IO) {
            val signatureTimestamp = PipePipeNsigDecoder.signatureTimestamp() ?: return@withContext fail(videoId, "no signature timestamp")
            val response =
                withTimeoutOrNull(PLAYER_TIMEOUT_MS) {
                    YouTube
                        .player(
                            videoId,
                            client = YouTubeClient.TV_TIZEN,
                            signatureTimestamp = signatureTimestamp,
                            localeOverride = YouTubeLocale.EXTRACTION,
                            apiUrl = YouTubeClient.API_URL_YOUTUBE,
                        ).getOrNull()
                } ?: return@withContext fail(videoId, "no /player answer")
            val answeredAtMs = SystemClock.elapsedRealtime()
            if (response.playabilityStatus.status != "OK") {
                return@withContext fail(videoId, "status=${response.playabilityStatus.status} reason=${response.playabilityStatus.reason}")
            }
            val formats = response.streamingData?.adaptiveFormats.orEmpty()
            val playable = playableFormats(formats)
            val video = playable.filter { !it.isAudio && it.height != null && it.width != null }
            val audio = playable.filter { it.isAudio }
            if (video.isEmpty() || audio.isEmpty()) {
                return@withContext fail(videoId, "${playable.size}/${formats.size} formats solved")
            }
            val prerollMs = PrerollWait.of(response.adPlacements)
            val waitMs = prerollMs - (SystemClock.elapsedRealtime() - answeredAtMs)
            val message =
                "TV_TIZEN streams for $videoId: ${video.size} video, ${audio.size} audio" +
                    if (prerollMs > 0) ", pre-roll wait ${waitMs.coerceAtLeast(0)}ms" else ""
            Log.w(TAG, message)
            PlayerDiagnostics.logWarning(TAG, message)
            if (waitMs > 0) delay(waitMs)
            InnerTubeVideoStreamExtractor.VideoExtractionResult(
                videoFormats = video,
                audioFormats = audio,
                playerResponse = response,
                usedClient = YouTubeClient.TV_TIZEN,
                sabrInfo = null,
            )
        }

    private suspend fun playableFormats(formats: List<PlayerResponse.StreamingData.Format>): List<PlayerResponse.StreamingData.Format> {
        val ciphers = formats.associateWith { SignatureCipher.parse(it.signatureCipher ?: it.cipher) }
        val solved = PipePipeNsigDecoder.decodeSignatures(ciphers.values.mapNotNull { it?.signature })
        val signed =
            formats.mapNotNull { format ->
                val cipher = ciphers[format] ?: return@mapNotNull format.takeIf { !it.url.isNullOrEmpty() }
                solved[cipher.signature]?.let { format.copy(url = cipher.signedUrl(it)) }
            }
        PipePipeNsigDecoder.prefetch(signed.mapNotNull { it.url })
        return signed.mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            if (PipePipeNsigDecoder.rawN(url) == null) return@mapNotNull format
            PipePipeNsigDecoder.deobfuscateUrl(url)?.let { format.copy(url = it) }
        }
    }

    private fun fail(
        videoId: String,
        why: String,
    ): InnerTubeVideoStreamExtractor.VideoExtractionResult? {
        Log.w(TAG, "TV_TIZEN unavailable for $videoId: $why")
        PlayerDiagnostics.logWarning(TAG, "TV_TIZEN unavailable for $videoId: $why")
        return null
    }
}
