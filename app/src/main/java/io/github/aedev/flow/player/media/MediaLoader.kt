package io.github.aedev.flow.player.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import io.github.aedev.flow.player.cache.PlayerCacheManager
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.resolver.VideoPlaybackResolver
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceFactory
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceResult
import io.github.aedev.flow.player.sabr.integration.SabrOrchestrator
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.surface.SurfaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.util.Locale

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

        internal fun subtitleTrackId(index: Int): String = "flow-subtitle-$index"
    }

    private var activeSabrOrchestrator: SabrOrchestrator? = null
    private var lastSourceWasSabr = false
    var onSabrFallbackNeeded: (() -> Unit)? = null
    
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
     * @param audioOnly When true, never selects video streams or video manifests.
     */
    fun loadMedia(
        player: ExoPlayer?,
        context: Context?,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        availableVideoStreams: List<VideoStream>,
        currentVideoStream: VideoStream?,
        dashManifestUrl: String?,
        hlsUrl: String?,
        isLiveStream: Boolean = false,
        durationSeconds: Long,
        currentDurationSeconds: Long,
        preservePosition: Long? = null,
        localFilePath: String? = null,
        audioOnly: Boolean = false,
        subtitleStreams: List<SubtitlesStream> = emptyList(),
        sabrInfo: SabrStreamInfo? = null,
        sabrVideoId: String? = null,
        sabrPreferred: Boolean = false,
        innerTubeVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        innerTubeAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList()
    ): Boolean {
        val finalDuration = when {
            durationSeconds > 0 -> durationSeconds
            currentDurationSeconds > 0 -> currentDurationSeconds
            else -> 0L
        }

        player?.let { exoPlayer ->
            try {
                // Reattach surface before loading
                if (!audioOnly) {
                    reattachSurface(exoPlayer)
                }

                Log.d(TAG, "Preparing media: video=${videoStream?.let(VideoCodecUtils::qualityHeightFromStream) ?: -1}p audioOnly=$audioOnly surfaceReady=${surfaceManager?.isSurfaceReady}")

                val ctx = context ?: throw IllegalStateException("Context not initialized")
                val dataSourceFactory = cacheManager?.getDataSourceFactory()
                    ?: DefaultDataSource.Factory(ctx)

                if (!audioOnly && surfaceManager?.isSurfaceReady != true && localFilePath == null) {
                    Log.w(TAG, "Surface not ready yet, preparing media and waiting for attach")
                }

                Log.d(TAG, "Resolving media with VideoPlaybackResolver for duration ${finalDuration}s")

                lastSourceWasSabr = false
                val mediaSource = createMediaSource(
                    dataSourceFactory = dataSourceFactory,
                    videoStream = videoStream,
                    audioStream = audioStream,
                    availableVideoStreams = availableVideoStreams,
                    currentVideoStream = currentVideoStream,
                    dashManifestUrl = dashManifestUrl,
                    hlsUrl = hlsUrl,
                    isLiveStream = isLiveStream,
                    finalDuration = finalDuration,
                    localFilePath = localFilePath,
                    audioOnly = audioOnly,
                    subtitleStreams = subtitleStreams,
                    sabrInfo = sabrInfo,
                    sabrVideoId = sabrVideoId,
                    sabrPreferred = sabrPreferred,
                    startPositionMs = preservePosition ?: 0L,
                    innerTubeVideoFormats = innerTubeVideoFormats,
                    innerTubeAudioFormats = innerTubeAudioFormats
                )

                if (mediaSource != null) {
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    stateFlow.value = stateFlow.value.copy(isPrepared = true)

                    // SABR sessions already start fetching at the position; seeking the
                    // unseekable progressive pipe would restart extraction.
                    if (preservePosition != null && preservePosition > 0 && !lastSourceWasSabr) {
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
    
    fun buildPreloadMediaSource(
        context: Context?,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        availableVideoStreams: List<VideoStream>,
        dashManifestUrl: String?,
        durationSeconds: Long,
        subtitleStreams: List<SubtitlesStream> = emptyList()
    ): MediaSource? {
        val ctx = context ?: return null
        val dataSourceFactory = cacheManager?.getDataSourceFactory() ?: DefaultDataSource.Factory(ctx)
        return try {
            createMediaSource(
                dataSourceFactory = dataSourceFactory,
                videoStream = videoStream,
                audioStream = audioStream,
                availableVideoStreams = availableVideoStreams,
                currentVideoStream = videoStream,
                dashManifestUrl = dashManifestUrl,
                hlsUrl = null,
                isLiveStream = false,
                finalDuration = durationSeconds,
                localFilePath = null,
                audioOnly = false,
                subtitleStreams = subtitleStreams
            )
        } catch (e: Exception) {
            Log.w(TAG, "buildPreloadMediaSource failed", e)
            null
        }
    }

    private fun reattachSurface(player: ExoPlayer) {
        surfaceManager?.let { sm ->
            val holder = sm.getSurfaceHolder()
            if (holder?.surface?.isValid == true) {
                sm.attachVideoSurface(holder, player, forceAttach = false)
            }
        }
    }
    
    fun releaseSabr() {
        activeSabrOrchestrator?.release()
        activeSabrOrchestrator = null
    }

    fun getActiveSabrOrchestrator(): SabrOrchestrator? = activeSabrOrchestrator

    private fun createMediaSource(
        dataSourceFactory: DataSource.Factory,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        availableVideoStreams: List<VideoStream>,
        currentVideoStream: VideoStream?,
        dashManifestUrl: String?,
        hlsUrl: String?,
        isLiveStream: Boolean,
        finalDuration: Long,
        localFilePath: String?,
        audioOnly: Boolean,
        subtitleStreams: List<SubtitlesStream>,
        sabrInfo: SabrStreamInfo? = null,
        sabrVideoId: String? = null,
        sabrPreferred: Boolean = false,
        startPositionMs: Long = 0L,
        innerTubeVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        innerTubeAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList()
    ): MediaSource? {
        val sabrAvailable = sabrInfo != null && sabrInfo.streamingUrl.isNotEmpty() &&
            sabrVideoId != null && sabrInfo.audioItag > 0 && sabrInfo.videoItag > 0

        if (sabrAvailable && sabrPreferred) {
            createSabrMediaSource(sabrInfo!!, sabrVideoId!!, finalDuration, startPositionMs)
                ?.let { return mergeSubtitleSourcesIfNeeded(it, subtitleStreams, dataSourceFactory) }
        }

        val mediaSource = if (localFilePath != null) {
            val localUri = if (localFilePath.startsWith("content://")) {
                android.net.Uri.parse(localFilePath)
            } else {
                android.net.Uri.fromFile(File(localFilePath))
            }
            ProgressiveMediaSource.Factory(cacheManager?.getProgressiveDataSourceFactory() ?: dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(localUri))
        } else {
            val resolver = VideoPlaybackResolver(
                cacheManager?.getDashDataSourceFactory() ?: dataSourceFactory,
                cacheManager?.getProgressiveDataSourceFactory() ?: dataSourceFactory,
                cacheManager?.getLiveDashDataSourceFactory()
                    ?: cacheManager?.getDashDataSourceFactory()
                    ?: dataSourceFactory,
                cacheManager?.getLiveHlsDataSourceFactory()
                    ?: cacheManager?.getHlsDataSourceFactory()
                    ?: dataSourceFactory
            )

            val selectedStreams = if (audioOnly) {
                emptyList()
            } else if (videoStream != null) {
                listOf(videoStream)
            } else if (!dashManifestUrl.isNullOrEmpty() && availableVideoStreams.size > 1) {
                availableVideoStreams
            } else {
                listOfNotNull(currentVideoStream ?: availableVideoStreams.firstOrNull())
            }
            Log.d(TAG, "Passing ${selectedStreams.size} stream(s) to resolver: ${selectedStreams.map { "${VideoCodecUtils.qualityHeightFromStream(it)}p" }}")
            resolver.resolve(
                selectedStreams,
                audioStream,
                dashManifestUrl = if (audioOnly) null else dashManifestUrl,
                hlsUrl = if (audioOnly) null else hlsUrl,
                durationSeconds = finalDuration,
                isLiveStream = isLiveStream && !audioOnly
            )
        }

        if (mediaSource == null && sabrAvailable && !sabrPreferred) {
            Log.w(TAG, "No playable extractor streams — falling back to native SABR session")
            createSabrMediaSource(sabrInfo!!, sabrVideoId!!, finalDuration, startPositionMs)
                ?.let { return mergeSubtitleSourcesIfNeeded(it, subtitleStreams, dataSourceFactory) }
        }

        return mergeSubtitleSourcesIfNeeded(mediaSource, subtitleStreams, dataSourceFactory)
    }

    private fun createSabrMediaSource(
        info: SabrStreamInfo,
        videoId: String,
        finalDuration: Long,
        startPositionMs: Long
    ): MediaSource? {
        return try {
            releaseSabr()
            val durationMs = info.durationMs.takeIf { it > 0 } ?: (finalDuration * 1000L)
            val result = SabrMediaSourceFactory.create(
                info = info,
                videoId = videoId,
                durationMs = durationMs,
                startPositionMs = startPositionMs
            )
            activeSabrOrchestrator = result.orchestrator
            result.orchestrator.onError = { _, msg, recoverable ->
                if (!recoverable) {
                    Log.w(TAG, "SABR non-recoverable error: $msg — triggering fallback")
                    onSabrFallbackNeeded?.invoke()
                }
            }
            result.orchestrator.start()
            lastSourceWasSabr = true
            Log.d(TAG, "Using SABR MediaSource for $videoId (startPos=${startPositionMs}ms)")
            result.mediaSource
        } catch (e: Exception) {
            Log.w(TAG, "SABR MediaSource creation failed, falling back to DASH/Progressive", e)
            releaseSabr()
            null
        }
    }

    private fun mergeSubtitleSourcesIfNeeded(
        mediaSource: MediaSource?,
        subtitleStreams: List<SubtitlesStream>,
        dataSourceFactory: DataSource.Factory
    ): MediaSource? {
        if (mediaSource == null || subtitleStreams.isEmpty()) return mediaSource

        val subtitleSources = subtitleStreams.mapIndexedNotNull { index, subtitleStream ->
            val subtitleUrl = subtitleStream.getContent().takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val language = subtitleStream.languageTag ?: subtitleStream.locale?.toLanguageTag()
            val label = subtitleStream.displayLanguageName ?: language ?: "Unknown"
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                .setMimeType(resolveSubtitleMimeType(subtitleStream))
                .setLanguage(language)
                .setLabel(if (subtitleStream.isAutoGenerated) "$label (Auto)" else label)
                .setSelectionFlags(0)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .setId(subtitleTrackId(index))
                .build()

            SingleSampleMediaSource.Factory(dataSourceFactory)
                .setTreatLoadErrorsAsEndOfStream(true)
                .createMediaSource(subtitleConfig, C.TIME_UNSET)
        }

        if (subtitleSources.isEmpty()) return mediaSource

        Log.d(TAG, "Merged ${subtitleSources.size} subtitle source(s)")
        return MergingMediaSource(
            true,
            true,
            mediaSource,
            *subtitleSources.toTypedArray()
        )
    }

    private fun resolveSubtitleMimeType(subtitleStream: SubtitlesStream): String {
        subtitleStream.format?.mimeType?.takeIf { it.isNotBlank() }?.let { return it }

        val url = subtitleStream.getContent().lowercase(Locale.ROOT)
        return when {
            ".vtt" in url || "fmt=vtt" in url -> MimeTypes.TEXT_VTT
            ".srt" in url || "fmt=srt" in url -> MimeTypes.APPLICATION_SUBRIP
            ".ttml" in url || ".xml" in url || "fmt=ttml" in url || "fmt=srv" in url ->
                MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT
        }
    }

}
