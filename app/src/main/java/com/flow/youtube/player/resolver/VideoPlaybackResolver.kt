package com.flow.youtube.player.resolver

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Specialized resolver for video playback that handles different stream types
 * including generating local DASH manifests to avoid YouTube throttling.
 */
class VideoPlaybackResolver(
    private val dashDataSourceFactory: DataSource.Factory,
    private val progressiveDataSourceFactory: DataSource.Factory
) {
    companion object {
        private const val TAG = "VideoPlaybackResolver"
    }

    fun resolve(
        videoStream: VideoStream?, // Revert to single stream for progressive fallback
        audioStream: AudioStream?,
        dashManifestUrl: String?, // New: Prioritize DASH manifest if available
        durationSeconds: Long
    ): MediaSource? {
        // 1. Priority: Official DASH Manifest (Seamless Switching, No Lag)
        if (!dashManifestUrl.isNullOrEmpty()) {
            return try {
                androidx.media3.exoplayer.dash.DashMediaSource.Factory(dashDataSourceFactory)
                    .createMediaSource(androidx.media3.common.MediaItem.fromUri(Uri.parse(dashManifestUrl)))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create DASH source from URL, falling back", e)
                null
            }
        }

        // 2. Fallback: Progressive / Generated DASH
        val sources = mutableListOf<MediaSource>()

        if (videoStream != null) {
            val source = createMediaSource(videoStream, durationSeconds)
            if (source != null) {
                sources.add(source)
            }
        }

        if (audioStream != null) {
            val source = createMediaSource(audioStream, durationSeconds)
            if (source != null) {
                sources.add(source)
            }
        }

        return when {
            sources.isEmpty() -> null
            sources.size == 1 -> sources[0]
            else -> MergingMediaSource(true, *sources.toTypedArray())
        }
    }

    private fun createMediaSource(
        stream: Stream,
        durationSeconds: Long
    ): MediaSource? {
        val url = stream.content
        if (url.isNullOrEmpty()) return null

        return try {
            when (stream.deliveryMethod) {
                DeliveryMethod.DASH -> createDashSource(stream, durationSeconds)
                DeliveryMethod.PROGRESSIVE_HTTP -> createProgressiveSource(stream, durationSeconds)
                DeliveryMethod.HLS -> createHlsSource(stream)
                else -> createStandardProgressiveSource(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream, falling back to progressive", e)
            createStandardProgressiveSource(stream)
        }
    }

    private fun createDashSource(stream: Stream, durationSeconds: Long): MediaSource {
        val itagItem = stream.itagItem 
            ?: throw IllegalStateException("No ItagItem for DASH stream")
            
        val manifestString = ManifestGenerator.generateOtfManifest(stream, itagItem, durationSeconds)
        
        return if (manifestString != null) {
            MediaSourceBuilder.buildDashSource(
                dashDataSourceFactory, 
                manifestString, 
                Uri.parse(stream.content)
            )
        } else {
             // Fallback
             createStandardProgressiveSource(stream)
        }
    }

    @Suppress("DEPRECATION")
    private fun createProgressiveSource(stream: Stream, durationSeconds: Long): MediaSource {
        val itagItem = stream.itagItem
        
        // Try generating DASH manifest for progressive stream (better seeking/buffering)
        if (itagItem != null) {
            // Only use this for video-only streams or audio streams (separated)
            val isSeparated = (stream is VideoStream && stream.isVideoOnly) || (stream is AudioStream)
            
            if (isSeparated) {
                val manifestString = ManifestGenerator.generateProgressiveManifest(stream, itagItem, durationSeconds)
                
                if (manifestString != null) {
                    return MediaSourceBuilder.buildDashSource(
                        dashDataSourceFactory, // Use DASH factory for generated manifests
                        manifestString,
                        Uri.parse(stream.content)
                    )
                }
            }
        }
        
        return createStandardProgressiveSource(stream)
    }

    private fun createHlsSource(stream: Stream): MediaSource {
        return MediaSourceBuilder.buildHlsSource(progressiveDataSourceFactory, Uri.parse(stream.content))
    }

    private fun createStandardProgressiveSource(stream: Stream): MediaSource {
        return MediaSourceBuilder.buildProgressiveSource(progressiveDataSourceFactory, Uri.parse(stream.content))
    }
}