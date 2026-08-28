package io.github.aedev.flow.ui.screens.shorts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.shorts.ShortWatchClassifier
import io.github.aedev.flow.data.shorts.ShortsRepository
import io.github.aedev.flow.data.shorts.queue.ShortsQueueChange
import io.github.aedev.flow.data.shorts.queue.ShortsQueueController
import io.github.aedev.flow.data.shorts.queue.ShortsQueueLoaderFactory
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.data.shorts.queue.openAtVideoId
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.StreamSizeEstimator
import io.github.aedev.flow.ui.components.FeedInvalidationBus
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject

/**
 * ShortsViewModel — Hilt-injected, InnerTube-first Shorts engine.
 *
 * Architecture:
 * - Uses [ShortsRepository] for InnerTube reel API (primary) + NewPipe (fallback)
 * - [ShortVideo] as the domain model (not generic [Video])
 * - Continuation-based infinite scroll (InnerTube pagination)
 * - Pre-resolves streams for adjacent shorts
 * - Reactive state via StateFlow for like/subscribe/save
 */
@HiltViewModel
class ShortsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: YouTubeRepository,
        private val shortsRepository: ShortsRepository,
        private val likedVideosRepository: LikedVideosRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val playlistRepository: PlaylistRepository,
        private val viewHistory: ViewHistory,
        private val queueFactory: ShortsQueueLoaderFactory,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ShortsUiState())
        val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()

        private var queue: ShortsQueueController? = null

        private val _commentsState = MutableStateFlow<List<io.github.aedev.flow.data.model.Comment>>(emptyList())
        val commentsState: StateFlow<List<io.github.aedev.flow.data.model.Comment>> = _commentsState.asStateFlow()

        private val _isLoadingComments = MutableStateFlow(false)
        val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

        private val savedShortIds = MutableStateFlow<Set<String>>(emptySet())

        private val _snackbarMessage = MutableStateFlow<String?>(null)
        val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

        fun clearSnackbar() {
            _snackbarMessage.value = null
        }

        init {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playlistRepository.getSavedShortsFlow().collect { savedVideos ->
                    savedShortIds.value = savedVideos.map { it.id }.toSet()
                }
            }

            viewModelScope.launch {
                shortsRepository.enrichmentUpdates.collect { enrichedShorts ->
                    val usable =
                        enrichedShorts.filter { it.title != "Short" || it.channelName != "Unknown" }
                    if (queue?.applyEnrichment(usable) != ShortsQueueChange.None) publishQueue()
                }
            }

            // Append discovery-ranked items when background discovery finishes after the InnerTube
            // fast path. Interleaving after the current position is the controller's job.
            viewModelScope.launch {
                shortsRepository.discoveryFeedUpdate.collect { newShorts ->
                    if (queue?.mergeDiscovery(newShorts) != ShortsQueueChange.None) publishQueue()
                }
            }
        }

        /** Mirrors the controller's state into [uiState], the single thing the screen observes. */
        private fun publishQueue() {
            val controller = queue ?: return
            _uiState.value =
                _uiState.value.copy(
                    shorts = controller.items.value,
                    currentIndex = controller.currentIndex.value,
                    hasMorePages = controller.hasMore,
                    isLoadingMore = controller.isLoadingMore.value,
                )
        }

        // REACTIVE STATE — Single Source of Truth

        /**
         * Returns a StateFlow<Boolean> for whether a video is liked.
         * UI should collectAsState() from this directly.
         *
         * `WhileSubscribed` matters here: the page calls this from `remember(video.id)`, so a
         * hand-rolled `launch { collect { } }` left one permanent Room observer per short scrolled
         * past — a few hundred of them after a long session, every one waking on every write.
         */
        fun isVideoLikedState(videoId: String): StateFlow<Boolean> =
            likedVideosRepository
                .getLikeState(videoId)
                .map { it == "LIKED" }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        /**
         * Returns a StateFlow<Boolean> for whether a channel is subscribed.
         *
         * Same lifetime rule as [isVideoLikedState].
         */
        fun isChannelSubscribedState(channelId: String): StateFlow<Boolean> =
            subscriptionRepository
                .isSubscribed(channelId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        /**
         * Returns a StateFlow<Boolean> for whether a short is saved.
         */
        fun isShortSavedState(videoId: String): StateFlow<Boolean> =
            savedShortIds
                .map { it.contains(videoId) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = savedShortIds.value.contains(videoId),
                )

        // QUEUE LOADING — one entry point for every surface

        /**
         * Opens the queue for [source].
         *
         * Every surface funnels through here: the Shorts tab, a shelf tap, a channel's Shorts tab,
         * saved Shorts, a related-shorts tap and an external link. Which loader that needs, and
         * whether the algorithmic feed follows it, is [ShortsQueueLoaderFactory]'s decision.
         *
         * Idempotent: re-entering the screen (a configuration change, or Compose re-running the
         * effect) must not refetch or reset the position.
         */
        fun load(source: ShortsQueueSource) {
            if (queue != null || _uiState.value.isLoading) return

            val resolved = queueFactory.resolve(source)
            val controller = queueFactory.create(resolved)
            queue = controller
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Resolving the tapped short's streams starts now rather than after the queue loads, so
            // playback is not gated on whichever network call the source happens to need.
            resolved.openAtVideoId?.let { prefetchPlaybackStreams(listOf(it)) }

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    controller.loadInitial(resolved.openAtVideoId)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    publishQueue()

                    // Pre-resolve around the opening position so the pager's prepare pass is a cache
                    // hit both forwards and backwards.
                    val items = controller.items.value
                    val at = controller.currentIndex.value
                    prefetchPlaybackStreams(
                        listOfNotNull(
                            items.getOrNull(at)?.id,
                            items.getOrNull(at + 1)?.id,
                        ),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading shorts queue", e)
                    queue = null
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: context.getString(R.string.error_failed_to_load_shorts),
                        )
                }
            }
        }

        /** Retries the source the screen was opened with, after a failure. */
        fun retry(source: ShortsQueueSource) {
            queue = null
            _uiState.value = _uiState.value.copy(error = null)
            load(source)
        }

        /**
         * Appends the next page. Re-entrancy and the hand-over to the feed are the controller's
         * concern, so calling this more often than necessary is harmless.
         */
        fun loadMoreShorts() {
            val controller = queue ?: return
            if (!controller.hasMore || controller.isLoadingMore.value) return

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
                try {
                    controller.loadMore()
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading more shorts", e)
                } finally {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    publishQueue()
                }
            }
        }

        /**
         * Resolve playback stream URLs ahead of the pager's own prepare pass. Results land in the
         * repository's single-flighted playback-stream cache, so the screen's later
         * [getPlaybackStreams] call for the same short returns instantly.
         */
        private fun prefetchPlaybackStreams(videoIds: List<String>) {
            if (videoIds.isEmpty()) return
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val prefs = PlayerPreferences(context)
                    val targetHeight =
                        shortsTargetHeight(
                            isWifi = isOnWifi(context),
                            wifiQuality = prefs.shortsQualityWifi.first(),
                            cellularQuality = prefs.shortsQualityCellular.first(),
                        )
                    val preferredLang = prefs.preferredAudioLanguage.first()
                    videoIds.forEach { id ->
                        launch { shortsRepository.resolvePlaybackStreams(id, targetHeight, preferredLang) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Shorts stream prefetch failed: ${e.message}")
                }
            }
        }

        // PAGE TRACKING & PRE-LOADING

        /**
         * The pager's single report of where the user is. Also the only place that decides to page
         * ahead — the screen used to trigger that a second time at a different threshold.
         */
        fun updateCurrentIndex(index: Int) {
            val controller = queue ?: return
            controller.setCurrentIndex(index)
            _uiState.value = _uiState.value.copy(currentIndex = controller.currentIndex.value)

            controller.items.value.getOrNull(index)?.id?.let { videoId ->
                viewModelScope.launch(PerformanceDispatcher.diskIO) {
                    shortsRepository.recordShown(videoId)
                }
            }

            if (index >= controller.items.value.size - PAGE_AHEAD_THRESHOLD) {
                loadMoreShorts()
            }
        }

        // STREAM RESOLUTION

        /**
         * Get stream info for a specific video. Used by the player.
         */
        suspend fun getVideoStreamInfo(videoId: String) = shortsRepository.resolveStreamInfo(videoId)

        suspend fun getPlaybackStreams(
            videoId: String,
            targetHeight: Int,
            preferredAudioLanguage: String,
        ) = shortsRepository.resolvePlaybackStreams(videoId, targetHeight, preferredAudioLanguage)

        suspend fun getAvailableQualities(videoId: String) = shortsRepository.getAvailableVideoQualities(videoId)

        suspend fun getInnerTubeDownloadFormats(videoId: String) = shortsRepository.getInnerTubeDownloadFormats(videoId)

        // USER ACTIONS
        suspend fun toggleLike(short: ShortVideo) {
            val video = short.toVideo()
            val isLiked = likedVideosRepository.getLikeState(video.id).first() == "LIKED"

            if (isLiked) {
                likedVideosRepository.removeLikeState(video.id)
            } else {
                likedVideosRepository.likeVideo(
                    io.github.aedev.flow.data.local.LikedVideoInfo(
                        videoId = video.id,
                        title = video.title,
                        thumbnail = video.thumbnailUrl,
                        channelName = video.channelName,
                    ),
                )
            }
        }

        suspend fun toggleSubscription(
            channelId: String,
            channelName: String,
            channelThumbnail: String,
        ) {
            val isSubscribed = subscriptionRepository.isSubscribed(channelId).first()

            if (isSubscribed) {
                subscriptionRepository.unsubscribe(channelId)
            } else {
                subscriptionRepository.subscribe(
                    io.github.aedev.flow.data.local.ChannelSubscription(
                        channelId = channelId,
                        channelName = channelName,
                        channelThumbnail = channelThumbnail,
                    ),
                )
            }
            runCatching {
                FlowNeuroEngine.onChannelSubscriptionChanged(
                    context,
                    channelId,
                    channelName,
                    subscribed = !isSubscribed,
                )
            }
            if (!isSubscribed) {
                // Newly subscribed: learn the channel's declared keyword tags.
                runCatching { repository.learnChannelTags(context, channelId) }
            }
        }

        fun toggleSaveShort(short: ShortVideo) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val video = short.toVideo()
                if (playlistRepository.isInSavedShorts(video.id)) {
                    playlistRepository.removeFromSavedShorts(video.id)
                } else {
                    playlistRepository.addToSavedShorts(video)
                    runCatching {
                        FlowNeuroEngine.onVideoInteraction(
                            video.copy(isShort = true),
                            InteractionType.SAVED,
                        )
                    }
                }
            }
        }

        fun recordShortProgress(
            short: ShortVideo,
            positionMs: Long,
            durationMs: Long,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val video = short.toVideo()
                val safeDuration =
                    when {
                        durationMs > 0L -> durationMs
                        video.duration > 0 -> video.duration * 1000L
                        else -> 60_000L
                    }
                val safePosition =
                    positionMs
                        .coerceAtLeast(1_000L)
                        .coerceAtMost(safeDuration)

                viewHistory.savePlaybackPosition(
                    videoId = video.id,
                    position = safePosition,
                    duration = safeDuration,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    isMusic = false,
                    isShort = true,
                )
            }
        }

        /**
         * Terminal signal for a short the user swiped away from before the watch
         * threshold fired. Early abandonment emits SKIPPED — the engine's main
         * source of negative watch evidence on Shorts.
         */
        fun recordShortAbandoned(
            short: ShortVideo,
            positionMs: Long,
            durationMs: Long,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val video = short.toVideo()
                val signal = ShortWatchClassifier.classifyAbandon(positionMs, durationMs, video.duration)
                if (signal != null) {
                    runCatching {
                        FlowNeuroEngine.onVideoInteraction(
                            video.copy(isShort = true),
                            signal.interaction,
                            percentWatched = signal.percent,
                        )
                        FlowNeuroEngine.recordSeenShorts(listOf(video.id))
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to record abandoned short in FlowNeuro", e)
                    }
                }
            }
        }

        fun recordShortWatched(
            short: ShortVideo,
            positionMs: Long,
            durationMs: Long,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val video = short.toVideo()
                val signal = ShortWatchClassifier.classify(positionMs, durationMs, video.duration)

                viewHistory.savePlaybackPosition(
                    videoId = video.id,
                    position = signal.position,
                    duration = signal.safeDuration,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    isMusic = false,
                    isShort = true,
                )

                runCatching {
                    FlowNeuroEngine.onVideoInteraction(
                        video.copy(isShort = true),
                        signal.interaction,
                        percentWatched = signal.percent,
                    )
                    FlowNeuroEngine.recordSeenShorts(listOf(video.id))
                }.onFailure { e ->
                    Log.w(TAG, "Failed to record watched short in FlowNeuro", e)
                }
            }
        }

        // COMMENTS
        fun loadComments(videoId: String) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _isLoadingComments.value = true
                _commentsState.value = emptyList()
                try {
                    val result =
                        withTimeoutOrNull(10_000L) {
                            repository.getComments(videoId)
                        }
                    _commentsState.value = result?.first ?: emptyList()
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading comments", e)
                } finally {
                    _isLoadingComments.value = false
                }
            }
        }

        fun loadCommentReplies(comment: io.github.aedev.flow.data.model.Comment) {
            val currentShort = _uiState.value.shorts.getOrNull(_uiState.value.currentIndex) ?: return
            val repliesPage = comment.repliesPage ?: return

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val url = "https://www.youtube.com/watch?v=${currentShort.id}"
                    val (replies, nextPage) = repository.getCommentReplies(url, repliesPage)

                    _commentsState.value =
                        _commentsState.value.map { c ->
                            if (c.id == comment.id) {
                                c.copy(
                                    replies = replies,
                                    repliesPage = nextPage,
                                )
                            } else {
                                c
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading replies", e)
                }
            }
        }

        fun wantMoreLikeThis(short: ShortVideo) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val video = short.toVideo()
                    FlowNeuroEngine.onVideoInteraction(
                        video,
                        InteractionType.LIKED,
                    )
                    _snackbarMessage.value = context.getString(R.string.shorts_showing_more_like_this)
                    Log.d(TAG, "Want more like this: ${short.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error signaling want more", e)
                }
            }
        }

        fun notInterested(short: ShortVideo) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val video = short.toVideo()
                    FlowNeuroEngine.markNotInterested(video)
                    FeedInvalidationBus.emit(FeedInvalidationBus.Event.NotInterested(video.id, video.channelId))

                    queue?.remove(short.id)
                    publishQueue()

                    _snackbarMessage.value = context.getString(R.string.shorts_showing_less_like_this)
                    Log.d(TAG, "Not interested: ${short.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking not interested", e)
                }
            }
        }

        /**
         * Total download size per `(resolution, codec)` pair for the streams the download dialog is
         * about to list. Pure: the caller has already resolved both extraction stacks, so nothing
         * here goes back to the network.
         */
        fun streamSizesFor(
            streamInfo: StreamInfo?,
            innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format>,
            innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format>,
        ): Map<String, Long> {
            val durationSeconds = streamInfo?.duration?.coerceAtLeast(0L) ?: 0L
            return StreamSizeEstimator.merge(
                StreamSizeEstimator.fromInnerTubeFormats(
                    innerTubeVideoFormats,
                    innerTubeAudioFormats,
                    durationSeconds * 1000L,
                ),
                StreamSizeEstimator.fromExtractorStreams(
                    (streamInfo?.videoStreams.orEmpty() + streamInfo?.videoOnlyStreams.orEmpty())
                        .filterIsInstance<VideoStream>(),
                    streamInfo?.audioStreams.orEmpty(),
                    durationSeconds,
                ),
            )
        }

        /**
         * Load detailed metadata (description, upload date, like count) for a Short from its StreamInfo.
         * The StreamInfo is typically already cached from playback setup — so this is usually instant.
         * Triggers a UI state update so FlowDescriptionBottomSheet always shows accurate data.
         */
        fun loadShortDetails(videoId: String) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val streamInfo = shortsRepository.resolveStreamInfo(videoId) ?: return@launch
                    val uploadDate = streamInfo.textualUploadDate?.takeIf { it.isNotBlank() } ?: ""
                    val description = streamInfo.description?.content?.takeIf { it.isNotBlank() } ?: ""
                    val likeCountText = if (streamInfo.likeCount > 0) formatLikeText(streamInfo.likeCount) else null

                    val existing = _uiState.value.shorts.firstOrNull { it.id == videoId } ?: return@launch
                    val enriched =
                        existing.copy(
                            uploadDate = uploadDate,
                            description = description.ifBlank { existing.description },
                            likeCountText = likeCountText ?: existing.likeCountText,
                        )
                    if (queue?.applyEnrichment(listOf(enriched)) != ShortsQueueChange.None) publishQueue()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load short details for $videoId: ${e.message}")
                }
            }
        }

        private fun formatLikeText(count: Long): String =
            when {
                count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
                count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
                count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
                count > 0 -> count.toString()
                else -> ""
            }

        companion object {
            private const val TAG = "ShortsViewModel"

            /** How close to the end of the queue the pager gets before the next page is fetched. */
            private const val PAGE_AHEAD_THRESHOLD = 5
        }
    }

/**
 * UI state for the Shorts screen.
 * Uses [ShortVideo] instead of generic [Video] for Shorts-specific data.
 */
data class ShortsUiState(
    val shorts: List<ShortVideo> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null,
)
