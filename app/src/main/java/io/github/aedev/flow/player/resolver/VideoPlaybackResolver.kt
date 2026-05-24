package io.github.aedev.flow.player.resolver

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.config.PlayerConfig
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Specialized resolver for video playback that handles different stream types
 * including generating local DASH manifests to avoid YouTube throttling.
 * 
 * YouTube throttles progressive (direct URL) streams to ~50-100 KB/s, which causes
 * buffering even on fast connections. The solution is to convert progressive URLs
 * into DASH manifests, which are not throttled and allow adaptive bitrate switching.
 * 
 * Priority order:
 * 1. YouTube DASH manifest URL (streamInfo.dashMpdUrl) - Best for adaptive playback
 * 2. Generated DASH manifest from progressive streams - Avoids throttling
 * 3. Direct progressive stream - Last resort, may buffer
 */
class VideoPlaybackResolver(
    private val dashDataSourceFactory: DataSource.Factory,
    private val progressiveDataSourceFactory: DataSource.Factory,
    private val liveDashDataSourceFactory: DataSource.Factory = dashDataSourceFactory,
    private val liveHlsDataSourceFactory: DataSource.Factory = progressiveDataSourceFactory
) {
    companion object {
        private const val TAG = "VideoPlaybackResolver"
        private const val PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 15.0
    }

    fun resolve(
        videoStreams: List<VideoStream>,
        audioStream: AudioStream?,
        dashManifestUrl: String?,
        hlsUrl: String?,
        durationSeconds: Long
    ): MediaSource? {
        Log.d(TAG, "Resolving playback: ${videoStreams.size} video streams, audio=${audioStream != null}, dash=${!dashManifestUrl.isNullOrEmpty()}, hls=${!hlsUrl.isNullOrEmpty()}, duration=${durationSeconds}s")
        
        if (!hlsUrl.isNullOrEmpty() && !dashManifestUrl.isNullOrEmpty()) {
            try {
                Log.d(TAG, "Using YouTube DASH manifest for live DVR playback: ${dashManifestUrl.take(80)}...")

                val liveDashItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(dashManifestUrl)
                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                    .setLiveConfiguration(
                        androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(PlayerConfig.LIVE_EDGE_GAP_MS)
                            .build()
                    )
                    .build()

                return androidx.media3.exoplayer.dash.DashMediaSource.Factory(liveDashDataSourceFactory)
                    .createMediaSource(liveDashItem)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build live DASH media source, falling back to HLS", e)
            }
        }

        if (!hlsUrl.isNullOrEmpty()) {
            try {
                Log.d(TAG, "Using YouTube HLS manifest for live playback: ${hlsUrl.take(80)}...")

                val liveItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(hlsUrl)
                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    .setLiveConfiguration(
                        androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(PlayerConfig.LIVE_EDGE_GAP_MS)
                            .build()
                    )
                    .build()

                return androidx.media3.exoplayer.hls.HlsMediaSource.Factory(liveHlsDataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setPlaylistTrackerFactory { dataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory ->
                        DefaultHlsPlaylistTracker(
                            dataSourceFactory,
                            loadErrorHandlingPolicy,
                            playlistParserFactory,
                            PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT
                        )
                    }
                    .createMediaSource(liveItem)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build live HLS media source", e)
            }
        }
        
        // If only 1 video stream is passed, the user selected a specific quality
        // In this case, DON'T use YouTube's DASH URL (which has all qualities) - use the specific stream
        val useSpecificStream = videoStreams.size == 1
        
        // 2. Priority: YouTube's native DASH manifest (only for adaptive/auto mode with multiple streams)
        if (!dashManifestUrl.isNullOrEmpty() && !useSpecificStream) {
            try {
                Log.d(TAG, "Using YouTube DASH manifest for adaptive playback: ${dashManifestUrl.take(80)}...")
                val dashItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(dashManifestUrl)
                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                    .build()
                return androidx.media3.exoplayer.dash.DashMediaSource.Factory(dashDataSourceFactory)
                    .createMediaSource(dashItem)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to use YouTube DASH manifest, trying generated manifests", e)
            }
        } else if (useSpecificStream) {
            Log.d(TAG, "User selected specific quality (${videoStreams.firstOrNull()?.let(VideoCodecUtils::qualityHeightFromStream)}p) - bypassing YouTube DASH URL")
        }

        // 3. Generate DASH manifests from progressive streams (NewPipe approach)
        // This avoids YouTube's progressive throttling (~50-100 KB/s limit)
        val videoSource = createVideoSource(
            videoStreams = videoStreams,
            durationSeconds = durationSeconds,
            preferMuxed = audioStream == null
        )
        val audioSource = createAudioSource(audioStream, durationSeconds)
        
        return when {
            videoSource != null && audioSource != null -> {
                Log.d(TAG, "Created MergingMediaSource with video + audio (sync adjusted)")
                MergingMediaSource(true, true, videoSource, audioSource)
            }
            videoSource != null -> {
                Log.d(TAG, "Using video source only (no separate audio)")
                videoSource
            }
            audioSource != null -> {
                Log.d(TAG, "Using audio source only")
                audioSource
            }
            else -> {
                Log.e(TAG, "Failed to create any media source!")
                null
            }
        }
    }

    /**
     * Create video source, preferring generated DASH manifests over raw progressive
     */
    private fun createVideoSource(
        videoStreams: List<VideoStream>,
        durationSeconds: Long,
        preferMuxed: Boolean
    ): MediaSource? {
        if (videoStreams.isEmpty()) return null
        
        // Sort by quality and prefer video-only streams (they can use DASH manifests)
        val sortedStreams = videoStreams.sortedWith(
            compareByDescending<VideoStream> { VideoCodecUtils.qualityHeightFromStream(it) }
                .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                .thenByDescending { it.bitrate }
        )
        
        val bestStream = if (preferMuxed) {
            sortedStreams.firstOrNull { !it.isVideoOnly } ?: sortedStreams.firstOrNull()
        } else {
            sortedStreams.firstOrNull { it.isVideoOnly } ?: sortedStreams.firstOrNull()
        }
        
        if (bestStream == null) return null
        
        return createOptimalVideoSource(bestStream, durationSeconds)
    }
    
    /**
     * Create the optimal video source based on stream type and delivery method.
     * Prioritizes DASH manifest generation to avoid YouTube throttling.
     */
    private fun createOptimalVideoSource(
        stream: VideoStream,
        durationSeconds: Long
    ): MediaSource? {
        val url = stream.content
        if (url.isNullOrEmpty()) {
            Log.w(TAG, "Video stream has null/empty URL")
            return null
        }
        
        val deliveryMethod = stream.deliveryMethod
        Log.d(TAG, "Creating video source: ${VideoCodecUtils.qualityHeightFromStream(stream)}p, delivery=${deliveryMethod}, videoOnly=${stream.isVideoOnly}")
        
        return try {
            when (deliveryMethod) {
                DeliveryMethod.DASH -> {
                    // OTF (On The Fly) streams - generate DASH manifest
                    createOtfDashSource(stream, durationSeconds)
                }
                DeliveryMethod.PROGRESSIVE_HTTP -> {
                    // Progressive streams - generate DASH manifest to avoid throttling
                    if (stream.isVideoOnly && durationSeconds > 0) {
                        createProgressiveDashSource(stream, durationSeconds)
                    } else {
                        // Fallback for non-video-only streams
                        createProgressiveSource(stream)
                    }
                }
                DeliveryMethod.HLS -> {
                    createHlsSource(stream)
                }
                else -> {
                    Log.w(TAG, "Unknown delivery method: $deliveryMethod, using progressive")
                    createProgressiveSource(stream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating video source, falling back to progressive", e)
            createProgressiveSource(stream)
        }
    }
    
    /**
     * Create audio source, preferring DASH manifests to avoid throttling
     */
    private fun createAudioSource(
        audioStream: AudioStream?,
        durationSeconds: Long
    ): MediaSource? {
        if (audioStream == null) return null
        
        val url = audioStream.content
        if (url.isNullOrEmpty()) {
            Log.w(TAG, "Audio stream has null/empty URL")
            return null
        }
        
        val deliveryMethod = audioStream.deliveryMethod
        Log.d(TAG, "Creating audio source: ${audioStream.averageBitrate}kbps, delivery=$deliveryMethod")
        
        return try {
            when (deliveryMethod) {
                DeliveryMethod.DASH -> {
                    createOtfDashSourceForAudio(audioStream, durationSeconds)
                }
                DeliveryMethod.PROGRESSIVE_HTTP -> {
                    // Generate DASH manifest for audio to avoid throttling
                    if (durationSeconds > 0 && audioStream.itagItem != null) {
                        createProgressiveDashSourceForAudio(audioStream, durationSeconds)
                    } else {
                        createProgressiveSourceForAudio(audioStream)
                    }
                }
                else -> {
                    createProgressiveSourceForAudio(audioStream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating audio source, falling back to progressive", e)
            createProgressiveSourceForAudio(audioStream)
        }
    }
    
    /**
     * Create DASH source from OTF (On The Fly) stream URL
     */
    private fun createOtfDashSource(stream: VideoStream, durationSeconds: Long): MediaSource {
        val itagItem = stream.itagItem 
            ?: throw IllegalStateException("No ItagItem for DASH stream")
            
        Log.d(TAG, "Generating OTF DASH manifest for ${VideoCodecUtils.qualityHeightFromStream(stream)}p video")
        val manifestString = ManifestGenerator.generateOtfManifest(stream, itagItem, durationSeconds)
        
        return if (manifestString != null) {
            MediaSourceBuilder.buildDashSource(
                dashDataSourceFactory, 
                manifestString, 
                Uri.parse(stream.content)
            )
        } else {
            Log.w(TAG, "OTF manifest generation failed, using progressive")
            createProgressiveSource(stream)
        }
    }
    
    /**
     * Create DASH source from progressive stream URL (avoids YouTube throttling!)
     */
    private fun createProgressiveDashSource(stream: VideoStream, durationSeconds: Long): MediaSource {
        val itagItem = stream.itagItem
        
        if (itagItem != null && durationSeconds > 0) {
            Log.d(TAG, "Generating progressive DASH manifest for ${VideoCodecUtils.qualityHeightFromStream(stream)}p to avoid throttling")
            val manifestString = ManifestGenerator.generateProgressiveManifest(stream, itagItem, durationSeconds)
            
            if (manifestString != null) {
                return MediaSourceBuilder.buildDashSource(
                    dashDataSourceFactory,
                    manifestString,
                    Uri.parse(stream.content)
                )
            }
        }
        
        Log.w(TAG, "Progressive DASH manifest generation failed, using raw progressive (may throttle)")
        return createProgressiveSource(stream)
    }
    
    /**
     * Create OTF DASH source for audio
     */
    private fun createOtfDashSourceForAudio(stream: AudioStream, durationSeconds: Long): MediaSource {
        val itagItem = stream.itagItem
            ?: return createProgressiveSourceForAudio(stream)
            
        Log.d(TAG, "Generating OTF DASH manifest for audio")
        val manifestString = ManifestGenerator.generateOtfManifest(stream, itagItem, durationSeconds)
        
        return if (manifestString != null) {
            MediaSourceBuilder.buildDashSource(
                dashDataSourceFactory,
                manifestString,
                Uri.parse(stream.content)
            )
        } else {
            createProgressiveSourceForAudio(stream)
        }
    }
    
    /**
     * Create DASH source from progressive audio URL
     */
    private fun createProgressiveDashSourceForAudio(stream: AudioStream, durationSeconds: Long): MediaSource {
        val itagItem = stream.itagItem
        
        if (itagItem != null && durationSeconds > 0) {
            Log.d(TAG, "Generating progressive DASH manifest for audio to avoid throttling")
            val manifestString = ManifestGenerator.generateProgressiveManifest(stream, itagItem, durationSeconds)
            
            if (manifestString != null) {
                return MediaSourceBuilder.buildDashSource(
                    dashDataSourceFactory,
                    manifestString,
                    Uri.parse(stream.content)
                )
            }
        }
        
        return createProgressiveSourceForAudio(stream)
    }
    
    /**
     * Fallback: Create standard progressive video source (may be throttled!)
     */
    private fun createProgressiveSource(stream: VideoStream): MediaSource {
        Log.d(TAG, "Creating progressive video source for ${VideoCodecUtils.qualityHeightFromStream(stream)}p (WARNING: may be throttled)")
        val item = androidx.media3.common.MediaItem.Builder()
            .setUri(stream.content)
            .setMimeType(stream.format?.mimeType ?: androidx.media3.common.MimeTypes.VIDEO_MP4)
            .build()
        return androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(progressiveDataSourceFactory)
            .createMediaSource(item)
    }
    
    /**
     * Fallback: Create standard progressive audio source
     */
    private fun createProgressiveSourceForAudio(stream: AudioStream): MediaSource {
        Log.d(TAG, "Creating progressive audio source (WARNING: may be throttled)")
        val item = androidx.media3.common.MediaItem.Builder()
            .setUri(stream.content)
            .setMimeType(stream.format?.mimeType ?: androidx.media3.common.MimeTypes.AUDIO_AAC)
            .build()
        return androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(progressiveDataSourceFactory)
            .createMediaSource(item)
    }
    
    /**
     * Create HLS source
     */
    private fun createHlsSource(stream: Stream): MediaSource {
        Log.d(TAG, "Creating HLS source")
        return MediaSourceBuilder.buildHlsSource(progressiveDataSourceFactory, Uri.parse(stream.content))
    }
}