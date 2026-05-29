package io.github.aedev.flow.player.sabr.integration

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.network.SabrDataSource

@UnstableApi
object SabrMediaSourceFactory {
    private const val TAG = "SabrMediaSrcFactory"

    fun create(
        streamingUrl: String,
        videoId: String,
        audioItag: Int,
        audioLmt: Long,
        videoItag: Int,
        videoLmt: Long,
        poToken: String,
        visitorId: String,
        ustreamerConfig: ByteArray,
        durationMs: Long,
        audioMimeType: String = "",
        videoMimeType: String = ""
    ): SabrMediaSourceResult {
        val sessionState = SabrSessionState().apply {
            this.streamingUrl = streamingUrl
            this.videoId = videoId
            this.selectedAudioItag = audioItag
            this.selectedAudioLmt = audioLmt
            this.selectedVideoItag = videoItag
            this.selectedVideoLmt = videoLmt
            this.poToken = poToken
            this.visitorId = visitorId
            this.ustreamerConfig = ustreamerConfig
            this.durationMs = durationMs
            this.clientNameId = WEB_CLIENT_NAME_ID
            this.clientVersion = io.github.aedev.flow.innertube.models.YouTubeClient.WEB.clientVersion
            this.osName = "Windows"
            this.osVersion = "10.0"
        }

        // WEB user-agent so the GVS/SABR request 
        val userAgent = io.github.aedev.flow.innertube.models.YouTubeClient.USER_AGENT_WEB
        val dataSource = SabrDataSource(userAgent, visitorId.ifEmpty { null })
        val controller = SabrStreamController(dataSource, sessionState)
        val orchestrator = SabrOrchestrator(controller)

        val audioDataSourceFactory = SabrExoPlayerDataSource.Factory(
            orchestrator.audioBuffer,
            orchestrator.videoBuffer
        ).setAudio(true)

        val videoDataSourceFactory = SabrExoPlayerDataSource.Factory(
            orchestrator.audioBuffer,
            orchestrator.videoBuffer
        ).setAudio(false)

        val audioUri = Uri.parse("sabr://$videoId/audio")
        val videoUri = Uri.parse("sabr://$videoId/video")

        val audioItemBuilder = MediaItem.Builder().setUri(audioUri)
        containerMimeType(audioMimeType, isAudio = true)?.let { audioItemBuilder.setMimeType(it) }
        val videoItemBuilder = MediaItem.Builder().setUri(videoUri)
        containerMimeType(videoMimeType, isAudio = false)?.let { videoItemBuilder.setMimeType(it) }

        val audioSource = ProgressiveMediaSource.Factory(audioDataSourceFactory)
            .createMediaSource(audioItemBuilder.build())

        val videoSource = ProgressiveMediaSource.Factory(videoDataSourceFactory)
            .createMediaSource(videoItemBuilder.build())

        val mergedSource = MergingMediaSource(true, true, videoSource, audioSource)

        Log.d(TAG, "Created SABR MediaSource: video=$videoId, " +
            "audioItag=$audioItag ($audioMimeType), videoItag=$videoItag ($videoMimeType)")

        return SabrMediaSourceResult(
            mediaSource = mergedSource,
            orchestrator = orchestrator
        )
    }

    private const val WEB_CLIENT_NAME_ID = 1

    /**
     * Map a YouTube format mimeType (e.g. `audio/webm; codecs="opus"`) to an ExoPlayer
     * container MIME constant. Returns null when unknown so ExoPlayer sniffs the stream.
     */
    private fun containerMimeType(mimeType: String, isAudio: Boolean): String? {
        val mt = mimeType.lowercase()
        return when {
            mt.contains("webm") -> if (isAudio) MimeTypes.AUDIO_WEBM else MimeTypes.VIDEO_WEBM
            mt.contains("mp4") -> if (isAudio) MimeTypes.AUDIO_MP4 else MimeTypes.VIDEO_MP4
            else -> null
        }
    }
}

data class SabrMediaSourceResult(
    val mediaSource: MediaSource,
    val orchestrator: SabrOrchestrator
)
