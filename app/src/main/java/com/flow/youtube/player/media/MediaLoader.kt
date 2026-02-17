package com.flow.youtube.player.media

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.flow.youtube.player.cache.PlayerCacheManager
import com.flow.youtube.player.config.PlayerConfig
import com.flow.youtube.player.resolver.VideoPlaybackResolver
import com.flow.youtube.player.state.EnhancedPlayerState
import com.flow.youtube.player.surface.SurfaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File

/**
 * Handles media loading and resolution.
 */
@UnstableApi
class MediaLoader(
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>,
    private val cacheManager: PlayerCacheManager?,
    private val surfaceManager: SurfaceManager?
) {
    companion object {
        private const val TAG = "MediaLoader"
    }
    
    /**
     * Load media with video and audio streams.
     * 
     * @param player ExoPlayer instance
     * @param context Application context
     * @param videoStream Video stream to load (can be null for audio-only)
     * @param audioStream Audio stream to load
     * @param availableVideoStreams All available video streams for fallback
     * @param currentVideoStream Current video stream reference
     * @param dashManifestUrl Optional DASH manifest URL
     * @param durationSeconds Duration in seconds
     * @param preservePosition Position to seek to after loading
     * @param localFilePath Optional local file path for offline playback
     * @param currentDurationSeconds Fallback duration from stream info
     */
    fun loadMedia(
        player: ExoPlayer?,
        context: Context?,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        availableVideoStreams: List<VideoStream>,
        currentVideoStream: VideoStream?,
        dashManifestUrl: String?,
        durationSeconds: Long,
        currentDurationSeconds: Long,
        preservePosition: Long? = null,
        localFilePath: String? = null
    ): Boolean {
        val finalDuration = when {
            durationSeconds > 0 -> durationSeconds
            currentDurationSeconds > 0 -> currentDurationSeconds
            else -> 0L
        }

        player?.let { exoPlayer ->
            try {
                // Reattach surface before loading
                reattachSurface(exoPlayer)

                Log.d(TAG, "Preparing media: video=${videoStream?.height ?: -1}p surfaceReady=${surfaceManager?.isSurfaceReady}")
                
                val ctx = context ?: throw IllegalStateException("Context not initialized")
                val dataSourceFactory = cacheManager?.getDataSourceFactory()
                    ?: DefaultDataSource.Factory(ctx)
                
                if (surfaceManager?.isSurfaceReady != true && localFilePath == null) {
                    Log.w(TAG, "Surface not ready yet, preparing media and waiting for attach")
                }
                
                Log.d(TAG, "Resolving media with VideoPlaybackResolver for duration ${finalDuration}s")
                
                val mediaSource = createMediaSource(
                    dataSourceFactory = dataSourceFactory,
                    videoStream = videoStream,
                    audioStream = audioStream,
                    availableVideoStreams = availableVideoStreams,
                    currentVideoStream = currentVideoStream,
                    dashManifestUrl = dashManifestUrl,
                    finalDuration = finalDuration,
                    localFilePath = localFilePath
                )
                
                if (mediaSource != null) {
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    stateFlow.value = stateFlow.value.copy(isPrepared = true)
                    
                    if (preservePosition != null && preservePosition > 0) {
                        exoPlayer.seekTo(preservePosition)
                        Log.d(TAG, "Seeking to preserved position: ${preservePosition}ms")
                    }
                    
                    exoPlayer.playWhenReady = true
                    Log.d(TAG, "Media loaded successfully via VideoPlaybackResolver")
                    return true
                } else {
                    Log.e(TAG, "Failed to resolve media source - streams invalid")
                    stateFlow.value = stateFlow.value.copy(error = "Failed to load media: Invalid streams")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media", e)
                stateFlow.value = stateFlow.value.copy(error = "Failed to load media: ${e.message}")
                return false
            }
        }
        return false
    }
    
    private fun reattachSurface(player: ExoPlayer) {
        surfaceManager?.getSurfaceHolder()?.let { holder ->
            val surface = holder.surface
            if (surface != null && surface.isValid) {
                player.setVideoSurface(surface)
                Log.d(TAG, "Reattached surface before media load")
            }
        }
    }
    
    private fun createMediaSource(
        dataSourceFactory: DataSource.Factory,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        availableVideoStreams: List<VideoStream>,
        currentVideoStream: VideoStream?,
        dashManifestUrl: String?,
        finalDuration: Long,
        localFilePath: String?
    ): MediaSource? {
        return if (localFilePath != null) {
            ProgressiveMediaSource.Factory(cacheManager?.getProgressiveDataSourceFactory() ?: dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(File(localFilePath))))
        } else {
            val audio = audioStream ?: return null
            val resolver = VideoPlaybackResolver(
                cacheManager?.getDashDataSourceFactory() ?: dataSourceFactory,
                cacheManager?.getProgressiveDataSourceFactory() ?: dataSourceFactory
            )
            
            val selectedStreams = if (videoStream != null) {
                listOf(videoStream)
            } else if (!dashManifestUrl.isNullOrEmpty() && availableVideoStreams.size > 1) {
                availableVideoStreams
            } else {
                listOfNotNull(currentVideoStream ?: availableVideoStreams.firstOrNull())
            }
            Log.d(TAG, "Passing ${selectedStreams.size} stream(s) to resolver: ${selectedStreams.map { "${it.height}p" }}")
            resolver.resolve(selectedStreams, audio, dashManifestUrl, finalDuration)
        }
    }
}
