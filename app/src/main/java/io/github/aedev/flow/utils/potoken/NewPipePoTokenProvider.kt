package io.github.aedev.flow.utils.potoken

import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult as ExtractorPoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

object NewPipePoTokenProvider : PoTokenProvider {
    private const val TAG = "NewPipePoTokenProvider"

    private val poTokenGenerator = PoTokenGenerator()
    private val visitorDataLock = Any()
    private var webPoTokenVisitorData: String? = null

    override fun getWebClientPoToken(videoId: String): ExtractorPoTokenResult? {
        val visitorData = ensureVisitorData() ?: return null
        val poTokenResult = try {
            poTokenGenerator.getWebClientPoToken(videoId, visitorData)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate extractor poToken for $videoId: ${e.message}", e)
            null
        } ?: return null

        return ExtractorPoTokenResult(
            visitorData,
            poTokenResult.playerRequestPoToken,
            poTokenResult.streamingDataPoToken
        )
    }

    override fun getWebEmbedClientPoToken(videoId: String): ExtractorPoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String): ExtractorPoTokenResult? = null

    override fun getIosClientPoToken(videoId: String): ExtractorPoTokenResult? = null

    private fun ensureVisitorData(): String? {
        synchronized(visitorDataLock) {
            webPoTokenVisitorData?.takeIf { it.isNotBlank() }?.let { return it }

            return runCatching {
                val requestInfo = InnertubeClientRequestInfo.ofWebClient()
                requestInfo.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
                YoutubeParsingHelper.getVisitorDataFromInnertube(
                    requestInfo,
                    NewPipe.getPreferredLocalization(),
                    NewPipe.getPreferredContentCountry(),
                    YoutubeParsingHelper.getYouTubeHeaders(),
                    YoutubeParsingHelper.YOUTUBEI_V1_URL,
                    null,
                    false
                )
            }.onFailure { e ->
                Log.w(TAG, "Failed to fetch extractor visitor data: ${e.message}", e)
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.also { webPoTokenVisitorData = it }
        }
    }
}
