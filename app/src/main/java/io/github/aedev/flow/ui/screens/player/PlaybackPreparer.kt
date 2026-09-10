package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.OfflineSubtitleStore
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PlaybackResumePolicy
import io.github.aedev.flow.player.sabr.SabrRoutingPolicy
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.stream.ResolvedPlayback
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Every hand-off from the player screen to [EnhancedPlayerManager]: arming the session, choosing the
 * position playback resumes from, pushing the streams, restoring the remembered speed and starting
 * playback — in that order, on the main thread.
 *
 * It owns no screen state and writes none: callers pass plain values and a currency check, so the
 * call sequence a load produces can be asserted against one mocked manager.
 *
 * The manager itself is the injected instance the ViewModel already holds; this class never creates,
 * looks up or releases one.
 */
internal class PlaybackPreparer(
    private val context: Context,
    private val playerManager: EnhancedPlayerManager,
    private val playerPreferences: PlayerPreferences,
    private val offlineSubtitleStore: OfflineSubtitleStore,
) {
    /** Arms the player and the media notification for [videoId] before any streams are handed over. */
    suspend fun beginSession(
        videoId: String,
        title: String,
        channel: String,
        thumbnail: String,
    ) = withContext(Dispatchers.Main) {
        playerManager.initialize(context)
        playerManager.startBackgroundService(videoId = videoId, title = title, channel = channel, thumbnail = thumbnail)
    }

    /** Publishes what plays next when the current video ends, and reports whether autoplay is on. */
    suspend fun applyAutoplayCandidates(
        videoId: String,
        videos: List<Video>,
    ): Boolean =
        withContext(Dispatchers.Main) {
            val autoplay = playerPreferences.autoplayEnabled.first()
            playerManager.setAutoplayCandidates(sourceVideoId = videoId, videos = videos, enabled = autoplay)
            autoplay
        }

    /** The merged result a load resolved, unpacked onto the full hand-off below. */
    suspend fun prepareMergedStreams(
        videoId: String,
        step: ResolvedPlayback.Merged,
        fallbackDurationSeconds: Long,
        isCurrent: () -> Boolean,
    ) {
        val streams = step.streams
        prepareMergedStreams(
            videoId = videoId,
            streamInfo = step.streamInfo,
            videoStream = streams.selectedVideoStream,
            audioStream = streams.selectedAudioStream,
            videoStreams = streams.videoStreams,
            audioStreams = streams.audioStreams,
            subtitles = streams.subtitles,
            savedPosition = step.savedPositionMs,
            fallbackDurationSeconds = fallbackDurationSeconds,
            localFilePath = streams.localFilePath,
            offlineSegments = step.offlineSegments,
            hlsUrl = streams.hlsUrl,
            isAdaptiveMode = streams.isAdaptiveMode,
            resumeOverrideRequested = step.resumeOverrideRequested,
            isCurrent = isCurrent,
            sabrInfo = streams.sabrInfo,
            itVideoFormats = streams.innerTubeVideoFormats,
            itAudioFormats = streams.innerTubeAudioFormats,
            preferredVideoCodec = streams.preferredCodecKey,
            dashManifestUrl = streams.dashManifestUrl,
            preferSabr = streams.preferSabr,
            preferredLiveQualityHeight = streams.preferredQuality.height,
        )
    }

    /** The InnerTube-only VOD assembly, unpacked onto the full hand-off below. */
    suspend fun prepareVodStreams(
        videoId: String,
        streams: PlaybackStreamPreparer.VodStreams,
        step: ResolvedPlayback.VodFromInnerTube,
        savedPositionMs: Long,
        isCurrent: () -> Boolean,
    ) = prepareVodStreams(
        videoId = videoId,
        videoStream = streams.videoStream,
        audioStream = streams.audioStream,
        videoStreams = streams.videoStreams,
        audioStreams = streams.audioStreams,
        subtitles = streams.subtitles,
        durationSeconds = streams.durationSeconds,
        savedPositionMs = savedPositionMs,
        resumeOverrideRequested = step.resumePositionOverrideMs != null,
        isAdaptiveMode = streams.isAdaptiveMode,
        sabrInfo = step.result.sabrInfo,
        itVideoFormats = step.result.videoFormats,
        itAudioFormats = step.result.audioFormats,
        preferredVideoCodec = step.preferredCodecKey,
        preferredLiveQualityHeight = step.preferredQuality.height,
        isCurrent = isCurrent,
    )

    suspend fun prepareMergedStreams(
        videoId: String,
        streamInfo: StreamInfo,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        subtitles: List<SubtitlesStream>,
        savedPosition: Long,
        fallbackDurationSeconds: Long,
        localFilePath: String?,
        offlineSegments: List<SponsorBlockSegment>?,
        hlsUrl: String?,
        isAdaptiveMode: Boolean,
        resumeOverrideRequested: Boolean,
        isCurrent: () -> Boolean,
        sabrInfo: SabrStreamInfo? = null,
        itVideoFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
        itAudioFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
        preferredVideoCodec: String = "auto",
        dashManifestUrl: String? = null,
        preferSabr: Boolean = false,
        preferredLiveQualityHeight: Int = 0,
    ) = withContext(Dispatchers.Main) {
        if (!isCurrent()) return@withContext
        if (playerManager.isPreparedForPlayback(videoId)) return@withContext

        playerManager.initialize(context)

        val durationMs =
            if (streamInfo.duration > 0L) streamInfo.duration * 1000L else fallbackDurationSeconds * 1000L
        val isLiveStream = streamInfo.streamType == StreamType.LIVE_STREAM
        val resumePosition =
            PlaybackResumePolicy.resolveStartPosition(
                savedPosition = savedPosition,
                durationMs = durationMs,
                resumeAllowed =
                    !isLiveStream &&
                        hlsUrl.isNullOrEmpty() &&
                        (resumeOverrideRequested || !playerManager.isCurrentQueueVideo(videoId)),
            )

        if (localFilePath != null) {
            playerManager.playLocalFile(
                videoId = videoId,
                filePath = localFilePath,
                savedSegments = offlineSegments,
                preservePosition = resumePosition.takeIf { it > 0L },
                subtitles = subtitles.ifEmpty { offlineSubtitleStore.load(videoId) },
            )
        } else {
            val effectiveDashUrl = dashManifestUrl?.takeIf { it.isNotEmpty() } ?: streamInfo.dashMpdUrl
            val hasAnySource =
                audioStream != null || videoStreams.isNotEmpty() ||
                    !effectiveDashUrl.isNullOrEmpty() || !hlsUrl.isNullOrEmpty() || sabrInfo != null
            if (hasAnySource) {
                if (audioStream == null) {
                    Log.w(TAG, "Preparing $videoId without a separate audio stream")
                }
                playerManager.setStreams(
                    videoId = videoId,
                    videoStream = if (isAdaptiveMode) null else videoStream,
                    audioStream = audioStream,
                    videoStreams = videoStreams,
                    audioStreams = audioStreams,
                    subtitles = subtitles,
                    durationSeconds = streamInfo.duration,
                    dashManifestUrl = effectiveDashUrl,
                    hlsUrl = hlsUrl,
                    streamType = streamInfo.streamType,
                    startPosition = resumePosition,
                    sabrInfo = sabrInfo,
                    itVideoFormats = itVideoFormats,
                    itAudioFormats = itAudioFormats,
                    preferredVideoCodec = preferredVideoCodec,
                    preferSabr = preferSabr,
                    preferredLiveQualityHeight = preferredLiveQualityHeight,
                )
            }
        }
        applyRememberedPlaybackSpeed(isLive = !hlsUrl.isNullOrEmpty())

        if (!isCurrent()) return@withContext
        playerManager.play()
    }

    /**
     * Returns false when playback was not started — the load is no longer current, or the player
     * already owns this media item — so the caller can skip the work that follows a real start.
     */
    suspend fun prepareLiveStreams(
        videoId: String,
        hlsUrl: String?,
        dashManifestUrl: String?,
        subtitles: List<SubtitlesStream>,
        isCurrent: () -> Boolean,
    ): Boolean =
        withContext(Dispatchers.Main) {
            if (!isCurrent()) return@withContext false
            if (playerManager.isPreparedForPlayback(videoId)) return@withContext false

            playerManager.setStreams(
                videoId = videoId,
                videoStream = null,
                audioStream = null,
                videoStreams = emptyList(),
                audioStreams = emptyList(),
                subtitles = subtitles,
                durationSeconds = 0L,
                dashManifestUrl = dashManifestUrl,
                hlsUrl = hlsUrl,
                streamType = StreamType.LIVE_STREAM,
                startPosition = 0L,
                preferredVideoCodec = playerPreferences.videoCodecPriority.first(),
                preferredLiveQualityHeight = preferredDefaultQualityHeight(),
            )
            applyRememberedPlaybackSpeed(isLive = true)

            if (!isCurrent()) return@withContext false
            playerManager.play()
            true
        }

    suspend fun prepareVodStreams(
        videoId: String,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        subtitles: List<SubtitlesStream>,
        durationSeconds: Long,
        savedPositionMs: Long,
        resumeOverrideRequested: Boolean,
        isAdaptiveMode: Boolean,
        sabrInfo: SabrStreamInfo?,
        itVideoFormats: List<PlayerResponse.StreamingData.Format>,
        itAudioFormats: List<PlayerResponse.StreamingData.Format>,
        preferredVideoCodec: String,
        preferredLiveQualityHeight: Int,
        isCurrent: () -> Boolean,
    ) = withContext(Dispatchers.Main) {
        if (!isCurrent()) return@withContext
        if (playerManager.isPreparedForPlayback(videoId)) return@withContext

        val resumePosition =
            PlaybackResumePolicy.resolveStartPosition(
                savedPosition = savedPositionMs,
                durationMs = durationSeconds * 1000L,
                resumeAllowed = resumeOverrideRequested || !playerManager.isCurrentQueueVideo(videoId),
            )
        val directMaxHeight = videoStreams.maxOfOrNull { VideoCodecUtils.qualityHeightFromStream(it) } ?: 0
        val preferSabr =
            sabrInfo != null && SabrRoutingPolicy.shouldPreferSabr(false, sabrInfo.videoHeight, directMaxHeight)

        playerManager.setStreams(
            videoId = videoId,
            videoStream = if (isAdaptiveMode) null else videoStream,
            audioStream = audioStream,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            subtitles = subtitles,
            durationSeconds = durationSeconds,
            dashManifestUrl = null,
            hlsUrl = null,
            streamType = StreamType.VIDEO_STREAM,
            startPosition = resumePosition,
            sabrInfo = sabrInfo,
            itVideoFormats = itVideoFormats,
            itAudioFormats = itAudioFormats,
            preferredVideoCodec = preferredVideoCodec,
            preferSabr = preferSabr,
            preferredLiveQualityHeight = preferredLiveQualityHeight,
        )
        applyRememberedPlaybackSpeed(isLive = false)

        if (!isCurrent()) return@withContext
        playerManager.play()
    }

    suspend fun prepareLocalMedia(
        videoId: String,
        localFilePath: String,
        offlineSegments: List<SponsorBlockSegment>?,
        savedPosition: Long,
        subtitles: List<SubtitlesStream>,
        isCurrent: () -> Boolean,
    ) = withContext(Dispatchers.Main) {
        if (!isCurrent()) return@withContext
        if (playerManager.isPreparedForPlayback(videoId)) return@withContext

        playerManager.initialize(context)
        val startPosition =
            PlaybackResumePolicy.resolveStartPosition(
                savedPosition = savedPosition,
                durationMs = 0L,
                resumeAllowed = !playerManager.isCurrentQueueVideo(videoId),
            )
        playerManager.playLocalFile(
            videoId = videoId,
            filePath = localFilePath,
            savedSegments = offlineSegments,
            preservePosition = startPosition.takeIf { it > 0L },
            subtitles = subtitles,
        )
        applyRememberedPlaybackSpeed(isLive = false)

        if (!isCurrent()) return@withContext
        playerManager.play()
    }

    private suspend fun applyRememberedPlaybackSpeed(isLive: Boolean) {
        if (isLive) {
            playerManager.setPlaybackSpeed(1.0f)
            return
        }
        if (playerPreferences.rememberPlaybackSpeed.first()) {
            playerManager.setPlaybackSpeed(playerPreferences.playbackSpeed.first())
        }
    }

    private suspend fun preferredDefaultQualityHeight(): Int {
        val quality =
            if (NetworkState.isOnWifi(context)) {
                playerPreferences.defaultQualityWifi.first()
            } else {
                playerPreferences.defaultQualityCellular.first()
            }
        return quality.height
    }

    private companion object {
        const val TAG = "PlaybackPreparer"
    }
}
