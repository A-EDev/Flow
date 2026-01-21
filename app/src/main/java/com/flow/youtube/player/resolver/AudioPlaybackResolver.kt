package com.flow.youtube.player.resolver

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DataSource
import com.flow.youtube.player.datasource.YouTubeHttpDataSource

/**
 * Specialized resolver for audio-only playback.
 *
 * Based on NewPipe's AudioPlaybackResolver implementation.
 */
class AudioPlaybackResolver(
    private val dashDataSourceFactory: DataSource.Factory,
    private val progressiveDataSourceFactory: DataSource.Factory,
    private val hlsDataSourceFactory: DataSource.Factory
) : PlaybackResolver {

    override fun resolve(mediaItem: MediaItem, streamInfo: Any): MediaSource? {
        val audioUrl = extractAudioUrl(streamInfo) ?: return null

        val enhancedMediaItem = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaMetadata(mediaItem.mediaMetadata)
            .build()

        return when {
            isHlsUrl(audioUrl) -> createHlsAudioSource(enhancedMediaItem)
            isDashUrl(audioUrl) -> createDashAudioSource(enhancedMediaItem)
            else -> createProgressiveAudioSource(enhancedMediaItem)
        }
    }

    override fun canHandle(streamInfo: Any): Boolean {
        return isAudioOnly(streamInfo) && hasAudioStream(streamInfo)
    }

    override fun getPriority(): Int = 50 // Lower priority than video resolver

    private fun createHlsAudioSource(mediaItem: MediaItem): MediaSource {
        return HlsMediaSource.Factory(hlsDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
    }

    private fun createDashAudioSource(mediaItem: MediaItem): MediaSource {
        return DashMediaSource.Factory(dashDataSourceFactory)
            .createMediaSource(mediaItem)
    }

    private fun createProgressiveAudioSource(mediaItem: MediaItem): MediaSource {
        return ProgressiveMediaSource.Factory(progressiveDataSourceFactory)
            .createMediaSource(mediaItem)
    }

    // Helper methods
    private fun isAudioOnly(streamInfo: Any): Boolean {
        // Implementation would check if this is audio-only content
        return false // Placeholder
    }

    private fun hasAudioStream(streamInfo: Any): Boolean {
        // Implementation would check for available audio streams
        return true // Placeholder
    }

    private fun extractAudioUrl(streamInfo: Any): Uri? {
        // Implementation would extract audio URL from stream info
        return null // Placeholder
    }

    private fun isHlsUrl(uri: Uri): Boolean {
        return uri.lastPathSegment?.endsWith(".m3u8") == true ||
               uri.toString().contains("m3u8")
    }

    private fun isDashUrl(uri: Uri): Boolean {
        return uri.lastPathSegment?.endsWith(".mpd") == true ||
               uri.toString().contains(".mpd")
    }
}