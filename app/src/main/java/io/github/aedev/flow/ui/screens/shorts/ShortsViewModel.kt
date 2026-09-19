package io.github.aedev.flow.ui.screens.shorts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.comments.CommentsPager
import io.github.aedev.flow.data.engagement.VideoEngagementUseCase
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.shorts.ShortAudioTrack
import io.github.aedev.flow.data.shorts.ShortDetails
import io.github.aedev.flow.data.shorts.ShortPlaybackStreams
import io.github.aedev.flow.data.shorts.ShortVideoQuality
import io.github.aedev.flow.data.shorts.ShortWatchClassifier
import io.github.aedev.flow.data.shorts.ShortsFeedRepository
import io.github.aedev.flow.data.shorts.ShortsMetadataRepository
import io.github.aedev.flow.data.shorts.ShortsStreamResolver
import io.github.aedev.flow.data.shorts.queue.ShortsQueueChange
import io.github.aedev.flow.data.shorts.queue.ShortsQueueController
import io.github.aedev.flow.data.shorts.queue.ShortsQueueLoaderFactory
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.data.shorts.queue.isAlgorithmicFeed
import io.github.aedev.flow.data.shorts.queue.openAtVideoId
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.innertube.pages.VideoCommentSort
import io.github.aedev.flow.innertube.pages.reel.ReelOverlay
import io.github.aedev.flow.player.stream.StreamSizeEstimator
import io.github.aedev.flow.ui.components.FeedInvalidationBus
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class ShortsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: YouTubeRepository,
        private val feed: ShortsFeedRepository,
        private val streams: ShortsStreamResolver,
        private val metadata: ShortsMetadataRepository,
        private val engagement: VideoEngagementUseCase,
        private val playlistRepository: PlaylistRepository,
        private val viewHistory: ViewHistory,
        private val queueFactory: ShortsQueueLoaderFactory,
        private val playerPreferences: PlayerPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ShortsUiState())
        val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()

        private var queue: ShortsQueueController? = null

        /** Completed once any reel can play, so discovery never competes with the first resolve. */
        private val firstPlayback = CompletableDeferred<Unit>()

        private val comments =
            CommentsPager(
                repository = repository,
                scope = viewModelScope,
                fetchTimeoutMs = COMMENTS_FETCH_TIMEOUT_MS,
            )

        val commentsState: StateFlow<List<Comment>> = comments.comments
        val isLoadingComments: StateFlow<Boolean> = comments.isLoading
        val commentSortOptions: StateFlow<List<VideoCommentSort>> = comments.sortOptions
        val commentTotalText: StateFlow<String?> = comments.totalText

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
                feed.discoveryFeedUpdate.collect { newShorts ->
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
                    isLoadingMore = controller.isLoadingMore.value,
                )
        }

        /**
         * `WhileSubscribed` matters here: the page calls this from `remember(video.id)`, so a
         * hand-rolled `launch { collect { } }` left one permanent Room observer per short scrolled
         * past — a few hundred of them after a long session, every one waking on every write.
         */
        fun isVideoLikedState(videoId: String): StateFlow<Boolean> =
            engagement
                .likeState(videoId)
                .map { it == "LIKED" }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        fun isChannelSubscribedState(channelId: String): StateFlow<Boolean> =
            engagement
                .subscriptionState(channelId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        fun isShortSavedState(videoId: String): StateFlow<Boolean> =
            savedShortIds
                .map { it.contains(videoId) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = savedShortIds.value.contains(videoId),
                )

        /**
         * Opens the queue for [source]. Every surface funnels through here; which loader that needs,
         * and whether the algorithmic feed follows it, is [ShortsQueueLoaderFactory]'s decision.
         *
         * Idempotent: re-entering the screen must not refetch or reset the position.
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

                    val items = controller.items.value
                    val at = controller.currentIndex.value
                    prefetchPlaybackStreams(listOfNotNull(items.getOrNull(at)?.id, items.getOrNull(at + 1)?.id))
                    if (resolved.isAlgorithmicFeed) discoverOnceWatching()
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

        /** Discovery is up to eleven fetches; it waits for the first reel to become playable. */
        private fun discoverOnceWatching() {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                withTimeoutOrNull(FIRST_PLAYBACK_GRACE_MS) { firstPlayback.await() }
                runCatching { feed.discoverMore() }.onFailure { Log.w(TAG, "Discovery pass failed: ${it.message}") }
            }
        }

        fun retry(source: ShortsQueueSource) {
            queue = null
            _uiState.value = _uiState.value.copy(error = null)
            load(source)
        }

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

        private fun prefetchPlaybackStreams(videoIds: List<String>) {
            if (videoIds.isEmpty()) return
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val targetHeight =
                    shortsTargetHeight(
                        isWifi = isOnWifi(context),
                        wifiQuality = playerPreferences.shortsQualityWifi.first(),
                        cellularQuality = playerPreferences.shortsQualityCellular.first(),
                    )
                val preferredLang = playerPreferences.preferredAudioLanguage.first()
                videoIds.forEach { id ->
                    launch { runCatching { getPlaybackStreams(id, targetHeight, preferredLang) } }
                }
            }
        }

        /**
         * The pager's single report of where the user is. Also the only place that decides to page
         * ahead.
         */
        fun updateCurrentIndex(index: Int) {
            val controller = queue ?: return
            controller.setCurrentIndex(index)
            _uiState.value = _uiState.value.copy(currentIndex = controller.currentIndex.value)

            if (index >= controller.items.value.size - PAGE_AHEAD_THRESHOLD) {
                loadMoreShorts()
            }
        }

        /** A reel counts as seen once it has actually been on screen for a moment, never when fetched. */
        fun onReelShown(videoId: String) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) { feed.recordShown(videoId) }
        }

        /**
         * Streams for [videoId]; the `/player` response that carries them also names the reel, so
         * the queue learns its title, channel and view count from the same request.
         */
        suspend fun getPlaybackStreams(
            videoId: String,
            targetHeight: Int,
            preferredAudioLanguage: String,
        ): ShortPlaybackStreams? {
            val resolved = streams.resolve(videoId, targetHeight, preferredAudioLanguage) ?: return null
            firstPlayback.complete(Unit)
            resolved.details?.let { applyDetails(videoId, it) }
            return resolved
        }

        suspend fun availableQualities(videoId: String): List<ShortVideoQuality> = streams.availableQualities(videoId)

        suspend fun availableAudioTracks(videoId: String): List<ShortAudioTrack> = streams.availableAudioTracks(videoId)

        suspend fun downloadFormats(
            videoId: String,
        ): Pair<List<PlayerResponse.StreamingData.Format>, List<PlayerResponse.StreamingData.Format>> = streams.downloadFormats(videoId)

        /** Total download size per `(resolution, codec)` pair. Pure: nothing here goes to the network. */
        suspend fun streamSizesFor(
            videoId: String,
            videoFormats: List<PlayerResponse.StreamingData.Format>,
            audioFormats: List<PlayerResponse.StreamingData.Format>,
        ): Map<String, Long> = StreamSizeEstimator.fromInnerTubeFormats(videoFormats, audioFormats, streams.durationMs(videoId) ?: 0L)

        /** Counts, avatar, timestamp and sound for the reel on screen — one overlay request, cached. */
        fun loadShortDetails(videoId: String) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val overlay = runCatching { metadata.overlayFor(videoId) }.getOrNull() ?: return@launch
                enrich(videoId) { it.withOverlay(overlay) }
            }
        }

        /** The description sheet needs the watch page: description, absolute date, exact counts. */
        fun loadShortDescription(videoId: String) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val existing = _uiState.value.shorts.firstOrNull { it.id == videoId } ?: return@launch
                if (existing.description.isNotBlank()) return@launch
                val enriched = runCatching { repository.enrichFromWatchMetadata(existing.toVideo()) }.getOrNull() ?: return@launch
                enrich(videoId) { short ->
                    short.copy(
                        description = enriched.description.ifBlank { short.description },
                        uploadDate = enriched.uploadDate.ifBlank { short.uploadDate },
                        likeCount = if (enriched.likeCount > 0) enriched.likeCount else short.likeCount,
                        viewCount = if (enriched.viewCount > 0) enriched.viewCount else short.viewCount,
                        channelThumbnailUrl = enriched.channelThumbnailUrl.ifBlank { short.channelThumbnailUrl },
                    )
                }
            }
        }

        private fun applyDetails(
            videoId: String,
            details: ShortDetails,
        ) = enrich(videoId) { short ->
            short.copy(
                title = details.title.ifBlank { short.title },
                channelName = details.channelName.ifBlank { short.channelName },
                channelId = details.channelId.ifBlank { short.channelId },
                viewCount = details.viewCount ?: short.viewCount,
                durationMs = details.durationMs ?: short.durationMs,
            )
        }

        private fun ShortVideo.withOverlay(overlay: ReelOverlay): ShortVideo =
            copy(
                title = title.ifBlank { overlay.title.orEmpty() },
                channelName = channelName.ifBlank { overlay.channelName.orEmpty() },
                channelId = channelId.ifBlank { overlay.channelId.orEmpty() },
                channelThumbnailUrl = overlay.channelAvatarUrl ?: channelThumbnailUrl,
                likeCount = overlay.likeCount ?: likeCount,
                commentCount = overlay.commentCount ?: commentCount,
                uploadDate = uploadDate.ifBlank { overlay.relativeTimestamp.orEmpty() },
                soundTitle = overlay.soundTitle ?: soundTitle,
                soundThumbnailUrl = overlay.soundThumbnailUrl ?: soundThumbnailUrl,
            )

        private fun enrich(
            videoId: String,
            transform: (ShortVideo) -> ShortVideo,
        ) {
            val existing = _uiState.value.shorts.firstOrNull { it.id == videoId } ?: return
            val enriched = transform(existing)
            if (enriched == existing) return
            if (queue?.applyEnrichment(listOf(enriched)) != ShortsQueueChange.None) publishQueue()
        }

        /**
         * Liking a short carries no learning signal: the deliberate "more like this" action is
         * what feeds the engine, and firing on the like too would double-count it.
         */
        suspend fun toggleLike(short: ShortVideo) {
            val video = short.toVideo()
            if (engagement.likeState(video.id).first() == "LIKED") {
                engagement.removeLike(video.id)
            } else {
                engagement.like(video)
            }
        }

        suspend fun toggleSubscription(
            channelId: String,
            channelName: String,
            channelThumbnail: String,
        ) = engagement.toggleSubscription(channelId, channelName, channelThumbnail)

        fun toggleSaveShort(short: ShortVideo) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val video = short.toVideo()
                if (playlistRepository.isInSavedShorts(video.id)) {
                    playlistRepository.removeFromSavedShorts(video.id)
                } else {
                    playlistRepository.addToSavedShorts(video)
                    runCatching {
                        FlowNeuroEngine.onVideoInteraction(video.copy(isShort = true), InteractionType.SAVED)
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
                        else -> DEFAULT_REEL_DURATION_MS
                    }
                val safePosition = positionMs.coerceAtLeast(1_000L).coerceAtMost(safeDuration)

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
         * Terminal signal for a short the user swiped away from before the watch threshold fired.
         * Early abandonment emits SKIPPED — the engine's main source of negative watch evidence.
         */
        fun recordShortAbandoned(
            short: ShortVideo,
            positionMs: Long,
            durationMs: Long,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val video = short.toVideo()
                val signal = ShortWatchClassifier.classifyAbandon(positionMs, durationMs, video.duration) ?: return@launch
                runCatching {
                    FlowNeuroEngine.onVideoInteraction(video.copy(isShort = true), signal.interaction, percentWatched = signal.percent)
                    FlowNeuroEngine.recordSeenShorts(listOf(video.id))
                }.onFailure { e -> Log.w(TAG, "Failed to record abandoned short in FlowNeuro", e) }
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
                    FlowNeuroEngine.onVideoInteraction(video.copy(isShort = true), signal.interaction, percentWatched = signal.percent)
                    FlowNeuroEngine.recordSeenShorts(listOf(video.id))
                }.onFailure { e -> Log.w(TAG, "Failed to record watched short in FlowNeuro", e) }
            }
        }

        fun loadComments(videoId: String) = comments.load(videoId)

        fun selectCommentSort(
            videoId: String,
            sort: VideoCommentSort,
        ) = comments.selectSort(videoId, sort)

        fun loadCommentReplies(comment: Comment) {
            val currentShort = _uiState.value.shorts.getOrNull(_uiState.value.currentIndex) ?: return
            comments.loadReplies(currentShort.id, comment)
        }

        fun wantMoreLikeThis(short: ShortVideo) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    FlowNeuroEngine.onVideoInteraction(short.toVideo(), InteractionType.LIKED)
                    _snackbarMessage.value = context.getString(R.string.shorts_showing_more_like_this)
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
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking not interested", e)
                }
            }
        }

        companion object {
            private const val TAG = "ShortsViewModel"

            /** How close to the end of the queue the pager gets before the next page is fetched. */
            private const val PAGE_AHEAD_THRESHOLD = 5

            private const val COMMENTS_FETCH_TIMEOUT_MS = 10_000L

            private const val FIRST_PLAYBACK_GRACE_MS = 6_000L

            private const val DEFAULT_REEL_DURATION_MS = 60_000L
        }
    }

data class ShortsUiState(
    val shorts: List<ShortVideo> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)
