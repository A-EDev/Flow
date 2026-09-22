package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.OfflineSubtitleStore
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.error.VideoErrorMapper
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.PlaybackFailure
import io.github.aedev.flow.player.stream.ResolvedPlayback
import io.github.aedev.flow.player.stream.StoryboardSpec
import io.github.aedev.flow.player.stream.UpcomingDetails
import io.github.aedev.flow.ui.screens.player.state.*
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.SubtitlesStream

/** The load a step belongs to: the video it resolved for, and the token saying it is still current. */
internal data class LoadContext(
    val videoId: String,
    val token: Long,
)

/**
 * Applies what one load resolved: the screen state each step lands on, the hand-off to the player,
 * and the metadata fetches that follow it — in the order the load produced them.
 *
 * This is the second writer of the player screen's state, alongside [VideoPlayerViewModel]: the
 * ViewModel owns the session entry points and the player-state mirror, this class owns everything a
 * resolved step and its secondary metadata land on. Both write the one [MutableStateFlow] the
 * ViewModel constructs and passes in, and both gate on the same load token, so a superseded load
 * stops writing at exactly the points it used to.
 *
 * Every collaborator here — the player manager included — is the instance the ViewModel already
 * holds; this class creates, looks up and releases none of them.
 */
internal class PlaybackSessionApplier(
    private val context: Context,
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val isLoadCurrent: (Long) -> Boolean,
    private val playbackPreparer: PlaybackPreparer,
    private val streamPreparer: PlaybackStreamPreparer,
    private val secondaryMetadata: PlayerSecondaryMetadataLoader,
    private val liveChat: LiveChatController,
    private val repository: YouTubeRepository,
    private val viewHistory: ViewHistory,
    private val playerPreferences: PlayerPreferences,
    private val sponsorBlockRepository: SponsorBlockRepository,
    private val videoDownloadManager: VideoDownloadManager,
    private val offlineSubtitleStore: OfflineSubtitleStore,
    private val playerManager: EnhancedPlayerManager,
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val enterUpcoming: (
        videoId: String,
        releaseMs: Long?,
        relatedVideos: List<Video>,
        loadToken: Long,
        details: UpcomingDetails?,
    ) -> Boolean,
    private val tryEnterUpcoming: suspend (videoId: String, relatedVideos: List<Video>, loadToken: Long) -> Boolean,
) {
    suspend fun apply(
        step: ResolvedPlayback,
        load: LoadContext,
    ) {
        when (step) {
            is ResolvedPlayback.LocalCopyReady -> {
                uiState.update { it.applyLocalCopyReady(load.videoId, step) }
                if (step.needsSponsorBlockBackfill) backfillSponsorBlockSegments(load.videoId)
                prepareLocalMedia(load, step.localFilePath, step.offlineSegments)
            }

            is ResolvedPlayback.LocalCopyAfterFailure -> {
                uiState.update { it.applyLocalCopyAfterFailure() }
                step.localFilePath?.let { prepareLocalMedia(load, it, step.offlineSegments) }
            }

            is ResolvedPlayback.OfflineFallback -> {
                if (isLoadCurrent(load.token)) {
                    uiState.update { it.applyOfflineFallback(step) }
                }
            }

            is ResolvedPlayback.Live -> {
                prepareLiveStreamFromInnerTube(load, step.result, step.relatedVideos)
            }

            is ResolvedPlayback.VodFromInnerTube -> {
                applyVodFromInnerTube(load, step)
            }

            is ResolvedPlayback.Upcoming -> {
                enterUpcoming(load.videoId, step.releaseTimeMs, step.relatedVideos, load.token, step.details)
                armCountdownMetadata(load, step.relatedVideos, step.details?.channelId)
            }

            is ResolvedPlayback.Failed -> {
                applyPlaybackFailure(load, step)
            }
        }
    }

    fun applySecondary(result: SecondaryMetadata) {
        when (result) {
            is SecondaryMetadata.Channel -> applyChannelMetadata(result)
            is SecondaryMetadata.Related -> publishRelatedVideos(result.videoId, result.videos, result.loadToken)
            is SecondaryMetadata.LiveWatch -> applyLiveWatchMetadata(result)
            is SecondaryMetadata.Category -> applyCategory(result)
            is SecondaryMetadata.Heatmap -> applyHeatmap(result)
            is SecondaryMetadata.Chapters -> applyChapters(result)
            is SecondaryMetadata.WatchInfo -> applyWatchInfo(result)
        }
    }

    /**
     * The same request answers both counts.
     *
     * Its like count is only adopted when the watch page withheld one, which is what a creator who
     * hides likes leaves behind: the response carries an `unset_like_count_entity_key` and nothing
     * else, and the screen was showing that as a flat zero.
     */
    fun startDislikeLoad(load: LoadContext) {
        scope.launch(networkDispatcher) {
            if (playerPreferences.rytdEnabled.first()) {
                val counts = withTimeoutOrNull(5000L) { repository.returnYouTubeDislikeCounts(load.videoId) }
                if (counts == null || !isLoadCurrent(load.token)) return@launch
                uiState.update { state ->
                    val cached = state.cachedVideo?.takeIf { it.id == load.videoId } ?: return@update state
                    val likes = counts.likes?.takeIf { it > 0L && cached.likeCount <= 0L }
                    state.copy(
                        dislikeCount = counts.dislikes ?: state.dislikeCount,
                        cachedVideo = likes?.let { cached.copy(likeCount = it) } ?: cached,
                    )
                }
            }
        }
    }

    suspend fun prepareLocalMedia(
        load: LoadContext,
        localFilePath: String,
        offlineSegments: List<SponsorBlockSegment>?,
        savedPosition: Long? = null,
    ) {
        playbackPreparer.prepareLocalMedia(
            videoId = load.videoId,
            localFilePath = localFilePath,
            offlineSegments = offlineSegments,
            savedPosition = savedPosition ?: viewHistory.getPlaybackPosition(load.videoId).first(),
            subtitles = offlineSubtitlesFor(load.videoId),
            isCurrent = { isLoadCurrent(load.token) },
        )
    }

    /** Re-pushes what the screen already holds when the player turns out to own no media item. */
    suspend fun armLatePrepare(
        load: LoadContext,
        latest: VideoPlayerUiState,
    ) {
        val videoId = load.videoId
        when (val prepare = latest.latePrepare(videoId)) {
            null -> {
                Unit
            }

            is LatePrepare.LocalFile -> {
                Log.w(TAG, "Late prepare: arming local playback for $videoId")
                prepareLocalMedia(
                    load = load,
                    localFilePath = prepare.localFilePath,
                    offlineSegments = prepare.offlineSegments,
                    savedPosition = prepare.savedPosition ?: viewHistory.getPlaybackPosition(videoId).first(),
                )
            }
        }
    }

    /**
     * The engine's click signal.
     *
     * It used to hang off the NewPipe metadata step, which meant it only ever fired on a load that
     * leg won; a load InnerTube resolved by itself taught the engine nothing. Off the startup path
     * because it takes the brain mutex and updates vectors, none of which first frame needs.
     */
    private fun recordWatchClick(video: Video) {
        scope.launch(ioDispatcher) {
            try {
                FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.CLICK)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record interaction", e)
            }
        }
    }

    private suspend fun applyVodFromInnerTube(
        load: LoadContext,
        step: ResolvedPlayback.VodFromInnerTube,
    ) {
        try {
            prepareVodStreamFromInnerTube(load, step)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "InnerTube VOD fallback failed for ${load.videoId}", e)
            if (tryEnterUpcoming(load.videoId, step.relatedVideos, load.token)) {
                armCountdownMetadata(load, step.relatedVideos, channelId = null)
            } else {
                val videoError = VideoErrorMapper.from(context, e, load.videoId)
                if (isLoadCurrent(load.token)) {
                    uiState.update { it.applyVodFailure(step.relatedVideos, videoError) }
                }
            }
        }
    }

    private suspend fun applyPlaybackFailure(
        load: LoadContext,
        step: ResolvedPlayback.Failed,
    ) {
        if (!isLoadCurrent(load.token)) return
        val videoError =
            when (step.failure) {
                PlaybackFailure.TIMEOUT -> VideoErrorMapper.fromTimeout(context)
                else -> VideoErrorMapper.from(context, step.cause, load.videoId)
            }
        if (step.failure == PlaybackFailure.UNEXPECTED && !videoError.isRetryable) {
            playerPreferences.markVideoUnplayable(load.videoId)
        }
        uiState.update { it.applyPlaybackFailure(step.relatedVideos, videoError) }
    }

    private fun backfillSponsorBlockSegments(videoId: String) {
        scope.launch(networkDispatcher) {
            try {
                val segments = sponsorBlockRepository.getSegments(videoId)
                if (segments.isNotEmpty()) {
                    videoDownloadManager.saveSponsorBlockData(
                        videoId,
                        sponsorBlockRepository.serializeSegments(segments),
                    )
                    Log.d(TAG, "Backfilled ${segments.size} SB segments for $videoId")
                    uiState.update { it.copy(offlineSponsorBlockSegments = segments) }
                } else {
                    Log.d(TAG, "No SB segments available for $videoId (backfill)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "SB backfill failed for $videoId", e)
            }
        }
    }

    private suspend fun prepareLiveStreamFromInnerTube(
        load: LoadContext,
        result: InnerTubeVideoStreamExtractor.VideoExtractionResult,
        relatedVideos: List<Video>,
    ) = withContext(Dispatchers.Main) {
        if (!isLoadCurrent(load.token)) return@withContext

        val videoId = load.videoId
        val streams = streamPreparer.assembleLive(videoId, uiState.value.cachedVideo, result)
        val identity = streams.identity
        GlobalPlayerState.setCurrentVideo(identity.enrichedVideo)
        recordWatchClick(identity.enrichedVideo)

        playbackPreparer.beginSession(videoId, identity.title, identity.channel, identity.thumbnail)
        playbackPreparer.applyAutoplayCandidates(videoId = videoId, videos = relatedVideos)

        uiState.update { it.applyLiveStreams(relatedVideos, streams.hlsUrl) }

        val liveStarted =
            playbackPreparer.prepareLiveStreams(
                videoId = videoId,
                hlsUrl = streams.hlsUrl,
                dashManifestUrl = streams.dashManifestUrl,
                subtitles = streams.subtitles,
                isCurrent = { isLoadCurrent(load.token) },
            )
        if (!liveStarted) return@withContext

        secondaryMetadata.loadChannelMetadata(
            videoId = videoId,
            uploaderUrl = null,
            channelId = identity.channelId,
            embeddedAvatarUrls = identity.embeddedAvatarUrls,
            loadToken = load.token,
        )

        liveChat.start(videoId)

        secondaryMetadata.refreshLiveWatchMetadata(videoId, identity.enrichedVideo, load.token)
    }

    private suspend fun prepareVodStreamFromInnerTube(
        load: LoadContext,
        step: ResolvedPlayback.VodFromInnerTube,
    ) = withContext(Dispatchers.Main) {
        if (!isLoadCurrent(load.token)) return@withContext

        val videoId = load.videoId
        val result = step.result
        val relatedVideos = step.relatedVideos
        val streams = streamPreparer.assembleVod(videoId, uiState.value.cachedVideo, step)
        val identity = streams.identity
        GlobalPlayerState.setCurrentVideo(identity.enrichedVideo)
        recordWatchClick(identity.enrichedVideo)

        playbackPreparer.beginSession(videoId, identity.title, identity.channel, identity.thumbnail)

        val autoplay = playbackPreparer.applyAutoplayCandidates(videoId = videoId, videos = relatedVideos)

        val savedPositionMs =
            step.resumePositionOverrideMs
                ?.takeIf { it > 0L }
                ?: viewHistory.getPlaybackPosition(videoId).first()

        Log.w(
            TAG,
            "VOD fallback playing $videoId via InnerTube ${result.usedClient.clientName} " +
                "(sabr=${result.sabrInfo != null}, video=${streams.videoStreams.size}, " +
                "audio=${streams.audioStreams.size})",
        )

        uiState.update {
            it.applyVodStreams(
                cachedVideo = identity.enrichedVideo,
                // A stream that was live but is not now. The extractor reported this as a
                // POST_LIVE_STREAM type; the player response splits it across two flags.
                isArchivedLivestream =
                    result.playerResponse.videoDetails?.isLiveContent == true &&
                        result.playerResponse.videoDetails?.isLive != true,
                relatedVideos = relatedVideos,
                videoStream = streams.videoStream,
                audioStream = streams.audioStream,
                availableQualities = streams.availableQualities,
                savedPositionMs = savedPositionMs,
                isAdaptiveMode = streams.isAdaptiveMode,
                autoplayEnabled = autoplay,
                innerTubeVideoFormats = result.videoFormats,
                innerTubeAudioFormats = result.audioFormats,
                streamSizes = streams.streamSizes,
                storyboard =
                    StoryboardSpec.parse(
                        spec =
                            result.playerResponse.storyboards
                                ?.playerStoryboardSpecRenderer
                                ?.spec,
                        durationMs = streams.durationSeconds * 1000L,
                    ),
            )
        }

        // Queue and preloaded playback may already own this media item. Arm secondary metadata
        // before the prepared-player return so those transitions still populate the screen.
        secondaryMetadata.loadRelatedVideos(videoId, relatedVideos, load.token)
        // A SABR/web client already returned the category, so spend nothing fetching it again.
        result.playerResponse.microformat
            ?.playerMicroformatRenderer
            ?.category
            ?.let { repository.rememberVideoCategory(videoId, it) }
        secondaryMetadata.loadCategory(videoId, load.token)
        secondaryMetadata.loadHeatmap(videoId, load.token)
        secondaryMetadata.loadChapters(videoId, load.token)
        secondaryMetadata.loadWatchInfo(videoId, identity.enrichedVideo, load.token)
        secondaryMetadata.loadChannelMetadata(
            videoId = videoId,
            uploaderUrl = null,
            channelId = identity.channelId,
            embeddedAvatarUrls = identity.embeddedAvatarUrls,
            loadToken = load.token,
        )

        playbackPreparer.prepareVodStreams(
            videoId = videoId,
            streams = streams,
            step = step,
            savedPositionMs = savedPositionMs,
            isCurrent = { isLoadCurrent(load.token) },
        )
    }

    /**
     * A countdown never starts playback, so the channel row and the related lane cannot wait for
     * the first frame the way a playing video's do.
     */
    fun armCountdownMetadata(
        load: LoadContext,
        relatedVideos: List<Video>,
        channelId: String?,
    ) {
        if (!isLoadCurrent(load.token)) return
        secondaryMetadata.loadChannelMetadata(
            videoId = load.videoId,
            uploaderUrl = null,
            channelId = channelId?.takeIf { it.isNotBlank() } ?: uiState.value.cachedVideo?.channelId,
            embeddedAvatarUrls = emptyList(),
            loadToken = load.token,
            awaitPlayback = false,
        )
        secondaryMetadata.loadRelatedVideos(load.videoId, relatedVideos, load.token, awaitPlayback = false)
    }

    private fun applyHeatmap(result: SecondaryMetadata.Heatmap) {
        if (!isLoadCurrent(result.loadToken)) return
        uiState.update { it.copy(heatmap = result.heatmap) }
    }

    private fun applyChapters(result: SecondaryMetadata.Chapters) {
        if (!isLoadCurrent(result.loadToken)) return
        uiState.update { it.applyChapters(result.chapters) }
    }

    private fun applyWatchInfo(result: SecondaryMetadata.WatchInfo) {
        if (!isLoadCurrent(result.loadToken) || uiState.value.cachedVideo?.id != result.videoId) return
        GlobalPlayerState.setCurrentVideo(result.video)
        uiState.update { it.applyWatchInfo(result.video) }
    }

    /** Folded into the tags the engine ingests, so the watch signal carries it. */
    private fun applyCategory(result: SecondaryMetadata.Category) {
        if (!isLoadCurrent(result.loadToken)) return

        uiState.update { state ->
            val cached = state.cachedVideo?.takeIf { it.id == result.videoId } ?: return@update state
            if (result.category in cached.tags) return@update state
            state.copy(cachedVideo = cached.copy(tags = cached.tags + result.category))
        }

        uiState.value.cachedVideo
            ?.takeIf { it.id == result.videoId }
            ?.let(GlobalPlayerState::setCurrentVideo)
    }

    private fun applyChannelMetadata(result: SecondaryMetadata.Channel) {
        if (!isLoadCurrent(result.loadToken)) return

        uiState.update { it.applyChannelMetadata(result) }

        uiState.value.cachedVideo
            ?.takeIf { it.id == result.videoId }
            ?.let(GlobalPlayerState::setCurrentVideo)
    }

    /** The one place related items reach the player: the autoplay queue and the lane together. */
    private fun publishRelatedVideos(
        videoId: String,
        videos: List<Video>,
        loadToken: Long,
    ) {
        if (!isLoadCurrent(loadToken) || videos.isEmpty()) return
        val state = uiState.value
        if (state.cachedVideo?.id != videoId) return

        scope.launch {
            if (!isLoadCurrent(loadToken)) return@launch
            val autoplay = playerPreferences.autoplayEnabled.first()
            if (!isLoadCurrent(loadToken)) return@launch
            playerManager.setAutoplayCandidates(
                sourceVideoId = videoId,
                videos = videos,
                enabled = autoplay,
            )
            uiState.update { it.applyRelatedVideos(videoId, videos) }
        }
    }

    private fun applyLiveWatchMetadata(result: SecondaryMetadata.LiveWatch) {
        if (!isLoadCurrent(result.loadToken)) return

        GlobalPlayerState.setCurrentVideo(result.video)
        uiState.update { it.applyLiveWatchMetadata(result) }
        publishRelatedVideos(result.videoId, result.relatedVideos, result.loadToken)
    }

    private suspend fun offlineSubtitlesFor(videoId: String): List<SubtitlesStream> {
        val stored = offlineSubtitleStore.load(videoId)
        if (stored.isEmpty() && NetworkState.isOnline(context)) {
            scope.launch(networkDispatcher) {
                offlineSubtitleStore.saveForVideo(videoId)
            }
        }
        return stored
    }

    private fun cachedDurationSeconds(): Long =
        uiState.value.cachedVideo
            ?.duration
            ?.toLong() ?: 0L

    private companion object {
        const val TAG = "PlaybackSessionApplier"
    }
}
