package io.github.aedev.flow.ui.screens.player

import android.util.Log
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PlayerChannelMetadataPolicy
import io.github.aedev.flow.player.PlayerRelatedVideosPolicy
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.distinctBestImageUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.StreamInfo

/** What a secondary metadata fetch resolved to, for the player screen to fold into its own state. */
internal sealed interface SecondaryMetadata {
    val videoId: String
    val loadToken: Long

    class Channel(
        override val videoId: String,
        override val loadToken: Long,
        val fetchedAvatarUrl: String?,
        val embeddedAvatarUrl: String?,
        val subscriberCount: Long?,
    ) : SecondaryMetadata

    class Related(
        override val videoId: String,
        override val loadToken: Long,
        val videos: List<Video>,
    ) : SecondaryMetadata

    class Enriched(
        override val videoId: String,
        override val loadToken: Long,
        val video: Video,
        val streamInfo: StreamInfo,
        val relatedVideos: List<Video>,
    ) : SecondaryMetadata

    class LiveWatch(
        override val videoId: String,
        override val loadToken: Long,
        val video: Video,
        val relatedVideos: List<Video>,
        val channelAvatarUrl: String?,
        val subscriberCount: Long?,
    ) : SecondaryMetadata
}

/**
 * The metadata the player screen wants *after* the first frame: the channel avatar and subscriber
 * count, the related lane, the late NewPipe enrichment of an InnerTube-only load, and the live
 * watch refresh.
 *
 * Each concern holds at most one job, keyed by the video and the load it belongs to: a repeat call
 * for the same pair while that job is in flight is dropped, and any other pair cancels it. Nothing
 * here writes screen state — every outcome leaves through [onResult] for the caller to apply.
 */
internal class PlayerSecondaryMetadataLoader(
    private val repository: YouTubeRepository,
    private val playerManager: EnhancedPlayerManager,
    private val playerPreferences: PlayerPreferences,
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
    private val currentState: () -> VideoPlayerUiState,
    private val relatedVideosFor: (String) -> List<Video>,
    private val shortsEnabled: () -> Boolean,
    private val isPlaybackCurrent: (Long) -> Boolean,
    private val onResult: (SecondaryMetadata) -> Unit,
) {
    private class ConcurrentLoad {
        var job: Job? = null
        var videoId: String? = null
        var loadToken: Long = -1L

        fun claim(
            videoId: String,
            loadToken: Long,
        ): Boolean {
            if (this.videoId == videoId && this.loadToken == loadToken) {
                if (job?.isActive == true) return false
            } else {
                cancel()
            }
            this.videoId = videoId
            this.loadToken = loadToken
            return true
        }

        fun holds(
            videoId: String,
            loadToken: Long,
        ): Boolean = this.videoId == videoId && this.loadToken == loadToken

        fun takeOver(
            videoId: String,
            loadToken: Long,
        ) {
            cancel()
            this.videoId = videoId
            this.loadToken = loadToken
        }

        fun cancel() {
            job?.cancel()
            job = null
            videoId = null
            loadToken = -1L
        }
    }

    private val channelLoad = ConcurrentLoad()
    private val relatedLoad = ConcurrentLoad()
    private val enrichLoad = ConcurrentLoad()
    private val liveWatchLoad = ConcurrentLoad()

    /** Drops every fetch in flight; the next load re-arms the ones it needs. */
    fun cancel() {
        channelLoad.cancel()
        relatedLoad.cancel()
    }

    fun loadChannelMetadata(
        videoId: String,
        uploaderUrl: String?,
        channelId: String?,
        embeddedAvatarUrls: List<String>,
        loadToken: Long,
    ) {
        val embeddedAvatar =
            embeddedAvatarUrls
                .firstOrNull()
                ?.let(ThumbnailUrlResolver::resolveChannelAvatar)
                ?.takeIf { it.isNotBlank() }
        if (embeddedAvatar != null) {
            onResult(
                SecondaryMetadata.Channel(
                    videoId = videoId,
                    loadToken = loadToken,
                    fetchedAvatarUrl = embeddedAvatar,
                    embeddedAvatarUrl = null,
                    subscriberCount = null,
                ),
            )
        }

        val references = PlayerChannelMetadataPolicy.channelReferences(uploaderUrl, channelId)
        if (references.isEmpty()) return
        if (!channelLoad.claim(videoId, loadToken)) return

        channelLoad.job =
            scope.launch(networkDispatcher) {
                // Embedded avatars can update immediately, but the extra channel request waits until
                // playback has actually started so it cannot compete with the first media buffer.
                awaitPlaybackStarted(videoId)
                if (!isPlaybackCurrent(loadToken)) return@launch

                var channelInfo: org.schabi.newpipe.extractor.channel.ChannelInfo? = null
                for (reference in references) {
                    channelInfo =
                        withTimeoutOrNull(CHANNEL_INFO_TIMEOUT_MS) {
                            repository.getChannelInfo(reference)
                        }
                    if (channelInfo != null) break
                }

                if (!isPlaybackCurrent(loadToken) || channelInfo == null) return@launch

                val fetchedAvatar =
                    channelInfo.avatars
                        .distinctBestImageUrls(limit = 1)
                        .firstOrNull()
                        ?.let(ThumbnailUrlResolver::resolveChannelAvatar)
                        ?.takeIf { it.isNotBlank() }

                onResult(
                    SecondaryMetadata.Channel(
                        videoId = videoId,
                        loadToken = loadToken,
                        fetchedAvatarUrl = fetchedAvatar,
                        embeddedAvatarUrl = embeddedAvatar,
                        subscriberCount = channelInfo.subscriberCount.takeIf { it > 0L },
                    ),
                )
            }
    }

    fun loadRelatedVideos(
        videoId: String,
        primaryCandidates: List<Video>,
        loadToken: Long,
    ) {
        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = videoId,
                primary = primaryCandidates,
                fallback = playerManager.relatedCandidatesFor(videoId),
                current = relatedVideosFor(videoId),
                shortsEnabled = shortsEnabled(),
            )
        if (selected.isNotEmpty()) {
            relatedLoad.takeOver(videoId, loadToken)
            publish(videoId, selected, loadToken)
            return
        }

        if (!relatedLoad.claim(videoId, loadToken)) return

        relatedLoad.job =
            scope.launch(networkDispatcher) {
                // Keep this request off the critical startup path. It is only needed when the
                // playback resolver did not provide related items with its initial metadata.
                awaitPlaybackStarted(videoId)
                if (!isPlaybackCurrent(loadToken) || !relatedLoad.holds(videoId, loadToken)) return@launch

                val managerCandidates = playerManager.relatedCandidatesFor(videoId)
                if (managerCandidates.isNotEmpty()) {
                    publish(videoId, managerCandidates, loadToken)
                    return@launch
                }

                val fallbackCandidates =
                    withTimeoutOrNull(RELATED_FALLBACK_TIMEOUT_MS) {
                        repository.getRelatedCandidates(videoId)
                    }.orEmpty()
                if (!isPlaybackCurrent(loadToken) || !relatedLoad.holds(videoId, loadToken)) return@launch

                val resolved =
                    PlayerRelatedVideosPolicy.select(
                        videoId = videoId,
                        primary = primaryCandidates,
                        fallback = fallbackCandidates,
                        current = currentState().relatedVideos,
                        shortsEnabled = shortsEnabled(),
                    )
                if (resolved.isNotEmpty()) {
                    publish(videoId, resolved, loadToken)
                } else {
                    Log.d(TAG, "No related videos resolved for $videoId")
                }
            }
    }

    /**
     * Folds the NewPipe metadata a fast InnerTube load raced past into the screen once it lands,
     * then re-arms the related lane and the channel request from the richer references it carries.
     */
    fun enrichWhenReady(
        videoId: String,
        streamInfoDeferred: Deferred<Pair<StreamInfo?, Throwable?>>,
        loadToken: Long,
    ) {
        if (!enrichLoad.claim(videoId, loadToken)) return

        enrichLoad.job =
            scope.launch(networkDispatcher) {
                val streamInfo =
                    try {
                        streamInfoDeferred.await().first
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.d(TAG, "Late NewPipe metadata failed for $videoId: ${e.message}")
                        null
                    } ?: return@launch

                if (!isPlaybackCurrent(loadToken)) return@launch

                val relatedVideos =
                    PlayerRelatedVideosPolicy.select(
                        videoId = videoId,
                        primary = repository.getRelatedVideosFromStreamInfo(streamInfo),
                        fallback = emptyList(),
                        current = emptyList(),
                        shortsEnabled = shortsEnabled(),
                    )
                val cached = currentState().cachedVideo ?: return@launch
                if (cached.id != videoId) return@launch

                val enriched =
                    cached.copy(
                        title = streamInfo.name?.takeIf { it.isNotBlank() } ?: cached.title,
                        channelName = streamInfo.uploaderName?.takeIf { it.isNotBlank() } ?: cached.channelName,
                        channelId =
                            cached.channelId.takeIf { it.isNotBlank() }
                                ?: streamInfo.uploaderUrl?.substringAfterLast("/").orEmpty(),
                        thumbnailUrl =
                            streamInfo.thumbnails.maxByOrNull { it.height }?.url
                                ?: cached.thumbnailUrl,
                        duration = streamInfo.duration.toInt().takeIf { it > 0 } ?: cached.duration,
                        viewCount = streamInfo.viewCount.takeIf { it > 0L } ?: cached.viewCount,
                        description = streamInfo.description?.content ?: cached.description,
                    )

                withContext(Dispatchers.Main) {
                    onResult(
                        SecondaryMetadata.Enriched(
                            videoId = videoId,
                            loadToken = loadToken,
                            video = enriched,
                            streamInfo = streamInfo,
                            relatedVideos = relatedVideos,
                        ),
                    )
                }

                loadRelatedVideos(videoId, relatedVideos, loadToken)

                loadChannelMetadata(
                    videoId = videoId,
                    uploaderUrl = streamInfo.uploaderUrl,
                    channelId = enriched.channelId,
                    embeddedAvatarUrls = streamInfo.uploaderAvatars.distinctBestImageUrls(),
                    loadToken = loadToken,
                )
            }
    }

    fun refreshLiveWatchMetadata(
        videoId: String,
        fallbackVideo: Video,
        loadToken: Long,
    ) {
        if (!liveWatchLoad.claim(videoId, loadToken)) return

        liveWatchLoad.job =
            scope.launch(networkDispatcher) {
                val newPipeMeta =
                    withTimeoutOrNull(LIVE_NEWPIPE_METADATA_TIMEOUT_MS) {
                        repository.getLiveWatchMetadataFromNewPipe(videoId)
                    }
                val innerTubeMeta =
                    if (
                        newPipeMeta == null ||
                        newPipeMeta.relatedVideos.isEmpty() ||
                        newPipeMeta.subscriberCount == null
                    ) {
                        withTimeoutOrNull(LIVE_INNERTUBE_METADATA_TIMEOUT_MS) { repository.getLiveWatchMetadata(videoId) }
                    } else {
                        null
                    }
                val meta = newPipeMeta ?: innerTubeMeta ?: return@launch
                if (!isPlaybackCurrent(loadToken)) return@launch

                val likes =
                    if (playerPreferences.rytdEnabled.first()) {
                        withTimeoutOrNull(RYD_TIMEOUT_MS) { repository.returnYouTubeDislikeCounts(videoId) }?.likes
                    } else {
                        null
                    }
                val enriched =
                    fallbackVideo.copy(
                        title = meta.title?.takeIf { it.isNotBlank() } ?: fallbackVideo.title,
                        channelName = meta.channelName?.takeIf { it.isNotBlank() } ?: fallbackVideo.channelName,
                        channelId = meta.channelId?.takeIf { it.isNotBlank() } ?: fallbackVideo.channelId,
                        description = meta.description ?: fallbackVideo.description,
                        channelThumbnailUrl = meta.channelAvatarUrl ?: fallbackVideo.channelThumbnailUrl,
                        viewCount = meta.viewCount ?: fallbackVideo.viewCount,
                        likeCount = likes ?: fallbackVideo.likeCount,
                        isLive = true,
                    )
                val metadataRelated =
                    PlayerRelatedVideosPolicy.sanitize(
                        videoId = videoId,
                        candidates =
                            newPipeMeta?.relatedVideos?.takeIf { it.isNotEmpty() }
                                ?: innerTubeMeta?.relatedVideos
                                ?: meta.relatedVideos,
                        shortsEnabled = shortsEnabled(),
                    )
                val related =
                    metadataRelated.ifEmpty {
                        withTimeoutOrNull(LIVE_RELATED_SEARCH_TIMEOUT_MS) {
                            repository.getLiveRelatedVideosBySearch(
                                videoId = videoId,
                                title = enriched.title,
                                channelName = enriched.channelName,
                            )
                        }.orEmpty()
                    }

                withContext(Dispatchers.Main) {
                    onResult(
                        SecondaryMetadata.LiveWatch(
                            videoId = videoId,
                            loadToken = loadToken,
                            video = enriched,
                            relatedVideos = related,
                            channelAvatarUrl = meta.channelAvatarUrl,
                            subscriberCount = meta.subscriberCount ?: innerTubeMeta?.subscriberCount,
                        ),
                    )
                }
            }
    }

    private fun publish(
        videoId: String,
        videos: List<Video>,
        loadToken: Long,
    ) = onResult(SecondaryMetadata.Related(videoId = videoId, loadToken = loadToken, videos = videos))

    private suspend fun awaitPlaybackStarted(videoId: String) {
        withTimeoutOrNull(PLAYBACK_STARTED_TIMEOUT_MS) {
            playerManager.playerState.first { state ->
                state.currentVideoId == videoId && (state.isPlaying || state.hasEnded || state.error != null)
            }
        }
    }

    private companion object {
        const val TAG = "PlayerSecondaryMetadata"
        const val PLAYBACK_STARTED_TIMEOUT_MS = 15_000L
        const val CHANNEL_INFO_TIMEOUT_MS = 8_000L
        const val RELATED_FALLBACK_TIMEOUT_MS = 10_000L
        const val LIVE_NEWPIPE_METADATA_TIMEOUT_MS = 12_000L
        const val LIVE_INNERTUBE_METADATA_TIMEOUT_MS = 8_000L
        const val LIVE_RELATED_SEARCH_TIMEOUT_MS = 8_000L
        const val RYD_TIMEOUT_MS = 5_000L
    }
}
