package io.github.aedev.flow.player.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.github.aedev.flow.R
import io.github.aedev.flow.player.cache.PlayerCacheManager
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.renderer.subtitle.Srv3SubtitleParser
import io.github.aedev.flow.player.resolver.VideoPlaybackResolver
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceFactory
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceResult
import io.github.aedev.flow.player.sabr.integration.SabrOrchestrator
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.surface.SurfaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Handles media loading and resolution.
 */
@UnstableApi
class MediaLoader(
    private val appContext: Context,
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>,
    private val cacheManager: PlayerCacheManager?,
    private val surfaceManager: SurfaceManager?,
) {
    companion object {
        private const val TAG = "MediaLoader"

        init {
            // TrackGroup derives its type from MimeTypes.getTrackType(sampleMimeType), so without
            // this an srv3 track group reports TRACK_TYPE_UNKNOWN and every `groups.filter { it.type
            // == C.TRACK_TYPE_TEXT }` lookup - including EnhancedPlayerManager's track-id override -
            // silently stops finding it. Registration is idempotent and keyed by MIME type, and
            // the empty codec prefix is right: srv3 never appears in a Format's codec string.
            MimeTypes.registerCustomMimeType(Srv3SubtitleParser.MIME_TYPE, "", C.TRACK_TYPE_TEXT)
        }

        internal fun subtitleTrackId(index: Int): String = "flow-subtitle-$index"
    }

    private var activeSabrOrchestrator: SabrOrchestrator? = null
    private var lastSourceWasSabr = false
    var onSabrFallbackNeeded: (() -> Unit)? = null

    /** Invoked with a subtitle track's display label once its fetch has finally given up. */
    var onSubtitleLoadFailed: ((String) -> Unit)? = null

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
     * @param playWhenReady Whether playback should start after the source is prepared.
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
        playWhenReady: Boolean = true,
        subtitleStreams: List<SubtitlesStream> = emptyList(),
        sabrInfo: SabrStreamInfo? = null,
        sabrVideoId: String? = null,
        sabrPreferred: Boolean = false,
        innerTubeVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        innerTubeAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        mediaId: String = "",
        mediaMetadata: MediaMetadata = MediaMetadata.EMPTY,
    ): Boolean {
        val finalDuration =
            when {
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

                Log.d(
                    TAG,
                    "Preparing media: video=${videoStream?.let(
                        VideoCodecUtils::qualityHeightFromStream,
                    ) ?: -1}p audioOnly=$audioOnly surfaceReady=${surfaceManager?.isSurfaceReady}",
                )

                val ctx = context ?: throw IllegalStateException("Context not initialized")
                val dataSourceFactory =
                    cacheManager?.getDataSourceFactory()
                        ?: DefaultDataSource.Factory(ctx)

                if (!audioOnly && surfaceManager?.isSurfaceReady != true && localFilePath == null) {
                    Log.w(TAG, "Surface not ready yet, preparing media and waiting for attach")
                }

                Log.d(TAG, "Resolving media with VideoPlaybackResolver for duration ${finalDuration}s")

                lastSourceWasSabr = false
                val mediaSource =
                    createMediaSource(
                        context = ctx,
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
                        innerTubeAudioFormats = innerTubeAudioFormats,
                        mediaId = mediaId,
                        mediaMetadata = mediaMetadata,
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

                    exoPlayer.playWhenReady = playWhenReady
                    Log.d(TAG, "Media loaded successfully via VideoPlaybackResolver")
                    return true
                } else {
                    Log.e(TAG, "Failed to resolve media source - streams invalid")
                    stateFlow.value =
                        stateFlow.value.copy(error = appContext.getString(R.string.error_failed_to_load_media_invalid_streams))
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media", e)
                stateFlow.value =
                    stateFlow.value.copy(error = appContext.getString(R.string.error_failed_to_load_media, e.message.orEmpty()))
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
        subtitleStreams: List<SubtitlesStream> = emptyList(),
        mediaId: String = "",
        mediaMetadata: MediaMetadata = MediaMetadata.EMPTY,
    ): MediaSource? {
        val ctx = context ?: return null
        val dataSourceFactory = cacheManager?.getDataSourceFactory() ?: DefaultDataSource.Factory(ctx)
        return try {
            createMediaSource(
                context = ctx,
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
                subtitleStreams = subtitleStreams,
                mediaId = mediaId,
                mediaMetadata = mediaMetadata,
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
        context: Context,
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
        innerTubeAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        mediaId: String = "",
        mediaMetadata: MediaMetadata = MediaMetadata.EMPTY,
    ): MediaSource? {
        val sabrAvailable =
            sabrInfo != null && sabrInfo.streamingUrl.isNotEmpty() &&
                sabrVideoId != null && sabrInfo.audioItag > 0 && sabrInfo.videoItag > 0

        val overridesDefaultAudio = StreamProcessor.overridesDefaultAudioTrack(audioStream)

        if (sabrAvailable && sabrPreferred && !overridesDefaultAudio) {
            createSabrMediaSource(
                sabrInfo!!,
                sabrVideoId!!,
                finalDuration,
                startPositionMs,
                mediaId,
                mediaMetadata,
            )?.let { return mergeSubtitleSourcesIfNeeded(it, subtitleStreams, dataSourceFactory, context) }
        }

        val mediaSource =
            if (localFilePath != null) {
                val localUri =
                    if (localFilePath.startsWith("content://")) {
                        android.net.Uri.parse(localFilePath)
                    } else {
                        android.net.Uri.fromFile(File(localFilePath))
                    }
                val localItem =
                    MediaItem
                        .Builder()
                        .setUri(localUri)
                        .setMediaId(mediaId)
                        .setMediaMetadata(mediaMetadata)
                        .apply { localFileMimeType(localUri)?.let(::setMimeType) }
                        .build()
                ProgressiveMediaSource
                    .Factory(DefaultDataSource.Factory(context))
                    .createMediaSource(localItem)
            } else {
                val resolver =
                    VideoPlaybackResolver(
                        cacheManager?.getDashDataSourceFactory() ?: dataSourceFactory,
                        cacheManager?.getProgressiveDataSourceFactory() ?: dataSourceFactory,
                        cacheManager?.getLiveDashDataSourceFactory()
                            ?: cacheManager?.getDashDataSourceFactory()
                            ?: dataSourceFactory,
                        cacheManager?.getLiveHlsDataSourceFactory()
                            ?: cacheManager?.getHlsDataSourceFactory()
                            ?: dataSourceFactory,
                        mediaId = mediaId,
                        mediaMetadata = mediaMetadata,
                    )

                val selectedStreams =
                    if (audioOnly) {
                        emptyList()
                    } else if (videoStream != null) {
                        listOf(videoStream)
                    } else if (!dashManifestUrl.isNullOrEmpty() && availableVideoStreams.size > 1) {
                        availableVideoStreams
                    } else {
                        listOfNotNull(currentVideoStream ?: availableVideoStreams.firstOrNull())
                    }
                Log.d(
                    TAG,
                    "Passing ${selectedStreams.size} stream(s) to resolver: ${selectedStreams.map {
                        "${VideoCodecUtils.qualityHeightFromStream(
                            it,
                        )}p"
                    }}",
                )
                resolver.resolve(
                    selectedStreams,
                    audioStream,
                    dashManifestUrl = if (audioOnly) null else dashManifestUrl,
                    hlsUrl = if (audioOnly) null else hlsUrl,
                    durationSeconds = finalDuration,
                    isLiveStream = isLiveStream && !audioOnly,
                )
            }

        if (mediaSource == null && sabrAvailable && !sabrPreferred) {
            Log.w(TAG, "No playable extractor streams — falling back to native SABR session")
            createSabrMediaSource(
                sabrInfo!!,
                sabrVideoId!!,
                finalDuration,
                startPositionMs,
                mediaId,
                mediaMetadata,
            )?.let { return mergeSubtitleSourcesIfNeeded(it, subtitleStreams, dataSourceFactory, context) }
        }

        return mergeSubtitleSourcesIfNeeded(mediaSource, subtitleStreams, dataSourceFactory, context)
    }

    private fun localFileMimeType(uri: Uri): String? =
        when (
            uri.lastPathSegment?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase(Locale.US)
        ) {
            "mp4", "m4v", "mov" -> MimeTypes.VIDEO_MP4
            "webm" -> MimeTypes.VIDEO_WEBM
            "mkv" -> "video/x-matroska"
            else -> null
        }

    private fun createSabrMediaSource(
        info: SabrStreamInfo,
        videoId: String,
        finalDuration: Long,
        startPositionMs: Long,
        mediaId: String,
        mediaMetadata: MediaMetadata,
    ): MediaSource? =
        try {
            releaseSabr()
            val durationMs = info.durationMs.takeIf { it > 0 } ?: (finalDuration * 1000L)
            val result =
                SabrMediaSourceFactory.create(
                    info = info,
                    videoId = videoId,
                    durationMs = durationMs,
                    startPositionMs = startPositionMs,
                    mediaId = mediaId,
                    mediaMetadata = mediaMetadata,
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

    private fun mergeSubtitleSourcesIfNeeded(
        mediaSource: MediaSource?,
        subtitleStreams: List<SubtitlesStream>,
        dataSourceFactory: DataSource.Factory,
        context: Context,
    ): MediaSource? {
        if (mediaSource == null || subtitleStreams.isEmpty()) return mediaSource

        val localDataSourceFactory by lazy { DefaultDataSource.Factory(context) }

        val subtitleSources =
            subtitleStreams.mapIndexedNotNull { index, subtitleStream ->
                val subtitleUrl = subtitleStream.getContent().takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                val uri = Uri.parse(subtitleUrl)
                val language = subtitleStream.languageTag ?: subtitleStream.locale?.toLanguageTag()
                val label = subtitleStream.displayLanguageName ?: language ?: "Unknown"
                val subtitleConfig =
                    MediaItem.SubtitleConfiguration
                        .Builder(uri)
                        .setMimeType(resolveSubtitleMimeType(subtitleStream))
                        .setLanguage(language)
                        .setLabel(if (subtitleStream.isAutoGenerated) "$label (Auto)" else label)
                        .setSelectionFlags(0)
                        .setRoleFlags(
                            if (subtitleStream.isAutoGenerated) {
                                C.ROLE_FLAG_SUBTITLE or C.ROLE_FLAG_TRANSCRIBES_DIALOG
                            } else {
                                C.ROLE_FLAG_SUBTITLE
                            },
                        ).setId(subtitleTrackId(index))
                        .build()

                val factory =
                    if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
                        dataSourceFactory
                    } else {
                        localDataSourceFactory
                    }
                SingleSampleMediaSource
                    .Factory(factory)
                    .setLoadErrorHandlingPolicy(SubtitleLoadErrorHandlingPolicy())
                    // Stays true: a propagated subtitle error would surface as a fatal
                    // ExoPlaybackException and stop the video over a failed sidecar text track.
                    .setTreatLoadErrorsAsEndOfStream(true)
                    .createMediaSource(subtitleConfig, C.TIME_UNSET)
                    .also { source ->
                        source.addEventListener(
                            Handler(Looper.getMainLooper()),
                            subtitleLoadFailureReporter(label),
                        )
                    }
            }

        if (subtitleSources.isEmpty()) return mediaSource

        Log.d(TAG, "Merged ${subtitleSources.size} subtitle source(s)")
        return MergingMediaSource(
            true,
            true,
            mediaSource,
            *subtitleSources.toTypedArray(),
        )
    }

    /**
     * Reports a subtitle fetch that has run out of retries.
     *
     * `treatLoadErrorsAsEndOfStream` turns that failure into an empty track, so without this the
     * user picks a language and simply gets nothing, with no clue that anything went wrong.
     * `wasCanceled` is Media3's signal that the loader chose not to retry, i.e. this is final.
     */
    private fun subtitleLoadFailureReporter(label: String): MediaSourceEventListener =
        object : MediaSourceEventListener {
            override fun onLoadError(
                windowIndex: Int,
                mediaPeriodId: MediaSource.MediaPeriodId?,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
                error: IOException,
                wasCanceled: Boolean,
            ) {
                val status = (error as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                if (!wasCanceled) {
                    Log.d(TAG, "Subtitle '$label' load failed (status=$status), retrying")
                    return
                }
                Log.w(TAG, "Subtitle '$label' gave up after retries (status=$status): ${error.message}")
                onSubtitleLoadFailed?.invoke(label)
            }
        }

    private fun resolveSubtitleMimeType(subtitleStream: SubtitlesStream): String {
        val url = subtitleStream.getContent().lowercase(Locale.ROOT)

        // Checked before subtitleStream.format.mimeType below: NewPipeExtractor gives every
        // TRANSCRIPT* format the same generic XML mimeType, which would otherwise route srv3 to the
        // TTML decoder — a decoder that can't parse YouTube's schema. The URL check covers streams
        // reaching us from the NewPipe extraction path, which sets no TRANSCRIPT3 format.
        if (subtitleStream.format == MediaFormat.TRANSCRIPT3 || "fmt=srv3" in url) return Srv3SubtitleParser.MIME_TYPE

        subtitleStream.format
            ?.mimeType
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return when {
            ".vtt" in url || "fmt=vtt" in url -> {
                MimeTypes.TEXT_VTT
            }

            ".srt" in url || "fmt=srt" in url -> {
                MimeTypes.APPLICATION_SUBRIP
            }

            ".ttml" in url || ".xml" in url || "fmt=ttml" in url -> {
                MimeTypes.APPLICATION_TTML
            }

            else -> {
                MimeTypes.TEXT_VTT
            }
        }
    }
}

/**
 * Retry policy for sidecar subtitle fetches.
 *
 * YouTube throttles `timedtext` requests that carry `&tlang=` far more aggressively than plain
 * caption fetches — a 429 on a translated track while the untranslated one loads fine from the same
 * IP seconds later is routine. The default three quick attempts frequently fall inside one throttle
 * window, so translated captions get one shot and then look permanently broken; backing off further
 * usually rides it out. Statuses that retrying cannot fix are given up on immediately.
 */
@UnstableApi
private class SubtitleLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = MAX_ATTEMPTS

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val status =
            (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                ?: return super.getRetryDelayMsFor(loadErrorInfo)
        val isTransient = status == HTTP_TOO_MANY_REQUESTS || status >= HTTP_SERVER_ERROR
        if (!isTransient) return C.TIME_UNSET
        val exponent = (loadErrorInfo.errorCount - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        return (INITIAL_BACKOFF_MS shl exponent).coerceAtMost(MAX_BACKOFF_MS)
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
        const val INITIAL_BACKOFF_MS = 500L
        const val MAX_BACKOFF_MS = 8_000L
        const val MAX_BACKOFF_EXPONENT = 4
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
    }
}
