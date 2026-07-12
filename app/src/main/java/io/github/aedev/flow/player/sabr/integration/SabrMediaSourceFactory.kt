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
import io.github.aedev.flow.player.sabr.core.SabrCpn
import io.github.aedev.flow.player.sabr.network.SabrDataSource
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.VideoCodecUtils

@UnstableApi
object SabrMediaSourceFactory {
    private const val TAG = "SabrMediaSrcFactory"

    fun create(
        info: SabrStreamInfo,
        videoId: String,
        durationMs: Long,
        startPositionMs: Long = 0L
    ): SabrMediaSourceResult {
        val sessionState = SabrSessionState().apply {
            this.streamingUrl = info.streamingUrl
            this.videoId = videoId
            this.selectedAudioItag = info.audioItag
            this.selectedAudioLmt = info.audioLmt
            this.selectedVideoItag = info.videoItag
            this.selectedVideoLmt = info.videoLmt
            this.audioTrackId = info.audioTrackId
            // stickyResolution = the user's explicit pick (0 in auto); selectedVideoHeight is
            // the actual chosen format height, used to floor sticky_resolution so auto mode
            // still asks the server for full quality instead of dropping to 360p.
            this.stickyResolution = info.targetHeight
            this.selectedVideoHeight = info.videoHeight
            this.playheadPositionMs = startPositionMs
            this.poToken = info.poToken
            this.visitorId = info.visitorId
            this.ustreamerConfig = info.ustreamerConfig
            this.durationMs = durationMs
            this.clientNameId = WEB_CLIENT_NAME_ID
            this.clientVersion = io.github.aedev.flow.innertube.models.YouTubeClient.WEB.clientVersion
            // A real WEB streamer_context carries no OS fields — the browser client reports
            // clientName/version + hl/gl only. Sending osName/osVersion made our streaming
            // request fingerprint-inconsistent with the player response, a RELOAD_PLAYER_RESPONSE
            // trigger.
            this.osName = ""
            this.osVersion = ""
            this.cpn = info.cpn.ifEmpty(SabrCpn::generate)
        }
        if (startPositionMs > 0) sessionState.lastSeekAtMs = System.currentTimeMillis()

        // WEB user-agent so the GVS/SABR request matches the WEB-minted PoToken
        val userAgent = io.github.aedev.flow.innertube.models.YouTubeClient.USER_AGENT_WEB
        val dataSource = SabrDataSource(userAgent)
        val controller = SabrStreamController(dataSource, sessionState)
        val reloadHeight = info.targetHeight.takeIf { it > 0 } ?: info.videoHeight
        val reloadCodec = VideoCodecUtils.codecKeyFromMimeType(info.videoMimeType)
        // Keep the content-playback nonce stable across reloads — a fresh cpn each reload reads
        // as a new playback session and re-triggers the server's RELOAD_PLAYER_RESPONSE demand.
        val sessionCpn = sessionState.cpn
        val orchestrator = SabrOrchestrator(controller) { event ->
            InnerTubeVideoStreamExtractor.resolveSabrDownload(
                videoId = videoId,
                targetHeight = reloadHeight,
                preferredCodec = reloadCodec,
                reloadToken = event.reloadToken,
                cpn = sessionCpn.ifEmpty(SabrCpn::generate),
            )
        }

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
        containerMimeType(info.audioMimeType, isAudio = true)?.let { audioItemBuilder.setMimeType(it) }
        val videoItemBuilder = MediaItem.Builder().setUri(videoUri)
        containerMimeType(info.videoMimeType, isAudio = false)?.let { videoItemBuilder.setMimeType(it) }

        val audioSource = ProgressiveMediaSource.Factory(audioDataSourceFactory)
            .createMediaSource(audioItemBuilder.build())

        val videoSource = ProgressiveMediaSource.Factory(videoDataSourceFactory)
            .createMediaSource(videoItemBuilder.build())

        val mergedSource = MergingMediaSource(true, true, videoSource, audioSource)

        Log.d(TAG, "Created SABR MediaSource: video=$videoId, " +
            "audioItag=${info.audioItag} (${info.audioMimeType}), videoItag=${info.videoItag} (${info.videoMimeType}), " +
            "startPos=${startPositionMs}ms")

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
