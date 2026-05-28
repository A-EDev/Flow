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
        durationMs: Long
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
        }

        val userAgent = "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip"
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

        val audioSource = ProgressiveMediaSource.Factory(audioDataSourceFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(audioUri)
                    .setMimeType(MimeTypes.AUDIO_WEBM)
                    .build()
            )

        val videoSource = ProgressiveMediaSource.Factory(videoDataSourceFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(videoUri)
                    .setMimeType(MimeTypes.VIDEO_WEBM)
                    .build()
            )

        val mergedSource = MergingMediaSource(true, true, videoSource, audioSource)

        Log.d(TAG, "Created SABR MediaSource: video=$videoId, " +
            "audioItag=$audioItag, videoItag=$videoItag")

        return SabrMediaSourceResult(
            mediaSource = mergedSource,
            orchestrator = orchestrator
        )
    }
}

data class SabrMediaSourceResult(
    val mediaSource: MediaSource,
    val orchestrator: SabrOrchestrator
)
