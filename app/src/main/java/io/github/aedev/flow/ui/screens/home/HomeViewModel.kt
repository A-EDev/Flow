package io.github.aedev.flow.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.engagement.FeedInvalidationBus
import io.github.aedev.flow.data.feed.FeedPrefetchQueue
import io.github.aedev.flow.data.feed.FeedPrefetchRequest
import io.github.aedev.flow.data.local.CachedHomeVideo
import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.local.HomeFeedCacheRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.recommendation.ChannelMemoryRepository
import io.github.aedev.flow.data.recommendation.FeedExclusions
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.repository.needsChannelMetadata
import io.github.aedev.flow.data.shorts.ShortsFeedRepository
import io.github.aedev.flow.data.subscriptions.HomeSubscriptionUploads
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.ui.screens.home.chips.ChipFeedContext
import io.github.aedev.flow.ui.screens.home.chips.HomeChipFeeds
import io.github.aedev.flow.ui.screens.home.chips.HomeChipsState
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private data class Wave1FeedResults(
    val discovery: List<Pair<String, List<Video>>>,
    val related: RelatedGraphFetchResult,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: YouTubeRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val subscriptionFeedRepository: io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository,
        private val shortsRepository: ShortsFeedRepository,
        private val playerPreferences: io.github.aedev.flow.data.local.PlayerPreferences,
        private val shortsQueueHandoff: io.github.aedev.flow.data.shorts.queue.ShortsQueueHandoff,
        private val feedSources: HomeFeedSources,
        private val homeSubscriptionUploads: HomeSubscriptionUploads,
        private val persistentHomeFeedCache: HomeFeedCacheRepository,
        private val viewHistory: ViewHistory,
        private val channelMemory: ChannelMemoryRepository,
        private val chipFeeds: HomeChipFeeds,
        savedStateHandle: SavedStateHandle,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        fun shortsShelfSource(
            shelf: List<io.github.aedev.flow.data.model.Video>,
            tapped: io.github.aedev.flow.data.model.Video,
        ) = shortsQueueHandoff.sourceForShelf(shelf, tapped)

        companion object {
            private const val TAG = "HomeViewModel"
            private const val UI_STATE_SUBSCRIPTION_TIMEOUT_MS = 5_000L
            private const val MAX_RELATED_SEEDS = 4
            private const val MIN_PAGE_SIZE = 8
            private const val LOAD_MORE_GRAPH_SEEDS = 3
            private const val MAX_SAVED_SEEDS = 5
            private const val SAVED_RELATED_SLOTS = 8

            // Never-dry load-more: fallback related pass seeded from feed + saved interests.
            private const val LOAD_MORE_FALLBACK_SEEDS = 4
            private const val FEED_SEED_POOL = 30
            private const val LATE_SUBS_MAX = 8
            private const val LATE_SUBS_PER_CHANNEL = 5

            // Before the grid reports its viewport the first screen is still in view; never shift it.
            private const val LATE_SUBS_MIN_INDEX = 5
        }

        private val relatedPickIds = ConcurrentHashMap.newKeySet<String>()

        private val channelMetadataEnrichmentInFlight =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet<String>()

        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> =
            _uiState
                .map(HomeUiState::withUniqueLazyContent)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(UI_STATE_SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = _uiState.value.withUniqueLazyContent(),
                )

        private var isInitialized = false
        private val homePrefetchQueue =
            FeedPrefetchQueue(
                prefetchAheadItemCount = HOME_PREFETCH_AHEAD_VIDEO_COUNT,
                triggerRemainingItems = HOME_PREFETCH_TRIGGER_REMAINING_VIDEOS,
            )
        private val homePrefetchWorkerLock = Any()
        private var homePrefetchJob: Job? = null

        private var subsBacklog: List<Video> = emptyList()

        private var currentQueryIndex = 0
        private val discoveryQueries = mutableListOf<String>()
        private var wave2Job: Job? = null
        private var feedJob: Job? = null
        private var subsTopUpJob: Job? = null
        private var channelMemoryJob: Job? = null

        // The memory lane's share of the first page; late uploads and load-more pages stay within it.
        private var channelMemoryQuota = 0

        @Volatile
        private var lastVisibleVideoIndex = -1
        private var savedInterestJob: Job? = null

        private val watchedVideoIds = MutableStateFlow<Set<String>>(emptySet())
        private val watchedShortIds = MutableStateFlow<Set<String>>(emptySet())

        /** The chips above the feed and what the selected one shows. */
        internal val chips: StateFlow<HomeChipsState> = chipFeeds.state

        fun selectChip(key: String) = chipFeeds.select(key)

        fun refreshSelectedChip() = chipFeeds.refreshSelected()

        fun enrichChipVideo(video: Video) = chipFeeds.enrichVisible(video)

        init {
            chipFeeds.attach(
                scope = viewModelScope,
                savedState = savedStateHandle,
                context = ChipFeedContext(::cacheFilters, ::feedExclusions) { watchedVideoIds.value },
            )
            if (HomeFeedCache.isFresh()) {
                _uiState.update {
                    it.copy(
                        videos = HomeFeedCache.videos,
                        shorts = HomeFeedCache.shorts,
                        isLoading = false,
                        isFlowFeed = true,
                        lastRefreshTime = HomeFeedCache.timestamp,
                    )
                }
            } else {
                hydratePersistentHomeFeed()
                loadFlowFeed(forceRefresh = true)
            }
        }

        fun initialize(context: Context) {
            if (isInitialized) return
            isInitialized = true

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                combine(
                    viewHistory.getVideoHistoryFlow(),
                    playerPreferences.hideWatchedVideosFromHome,
                    playerPreferences.watchedThreshold,
                    playerPreferences.continueWatchingEnabled,
                    playerPreferences.hideWatchedShorts,
                ) { history, hideWatched, threshold, continueWatchingEnabled, hideWatchedShorts ->
                    filterHomeHistory(
                        history = history,
                        hideWatchedVideos = hideWatched,
                        watchedThreshold = threshold,
                        continueWatchingEnabled = continueWatchingEnabled,
                        hideWatchedShorts = hideWatchedShorts,
                    )
                }.collect { result ->
                    watchedVideoIds.value = result.watchedVideoIds
                    watchedShortIds.value = result.watchedShortIds
                    _uiState.update { state ->
                        val videos = state.videos.filterWatched(result.watchedVideoIds)
                        val shorts = state.shorts.filterWatched(result.watchedShortIds)
                        if (videos != state.videos || shorts != state.shorts) {
                            HomeFeedCache.update(videos, shorts)
                        }
                        state.copy(
                            videos = videos,
                            shorts = shorts,
                            continueWatchingVideos = result.continueWatchingVideos,
                        )
                    }
                }
            }

            viewModelScope.launch {
                FlowNeuroEngine.initialize(context)
            }

            viewModelScope.launch {
                FeedInvalidationBus.events.collect { event ->
                    when (event) {
                        is FeedInvalidationBus.Event.ChannelBlocked -> {
                            dropChannelFromFeed(event.channelId, event.videoId)
                        }

                        is FeedInvalidationBus.Event.ChannelUnsubscribed -> {
                            dropChannelFromFeed(event.channelId)
                        }

                        is FeedInvalidationBus.Event.NotInterested -> {
                            HomeFeedCache.filterOut(videoId = event.videoId)
                            subsBacklog = subsBacklog.filter { it.id != event.videoId && it.channelId != event.channelId }
                            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                                persistentHomeFeedCache.deleteVideo(event.videoId)
                            }
                            _uiState.update { state ->
                                state.copy(
                                    videos = state.videos.filter { it.id != event.videoId },
                                    shorts = state.shorts.filter { it.id != event.videoId },
                                )
                            }
                            // Full clear — topic signals changed, discovery queries will differ
                            shortsRepository.clearCaches()
                        }

                        is FeedInvalidationBus.Event.MarkedWatched -> {
                            HomeFeedCache.filterOut(videoId = event.videoId)
                            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                                persistentHomeFeedCache.deleteVideo(event.videoId)
                            }
                            _uiState.update { state ->
                                state.copy(
                                    videos = state.videos.filter { it.id != event.videoId },
                                    shorts = state.shorts.filter { it.id != event.videoId },
                                )
                            }
                        }
                    }
                }
            }

            viewModelScope.launch {
                playerPreferences.homeSubscriptionsEnabled
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { reloadFeed() }
            }

            viewModelScope.launch {
                playerPreferences.effectiveHomeShortsShelfEnabled.distinctUntilChanged().collect { enabled ->
                    if (!enabled) {
                        _uiState.update { it.copy(shorts = emptyList()) }
                    } else if (_uiState.value.shorts.isEmpty() && !_uiState.value.isLoading) {
                        reloadFeed()
                    }
                }
            }
        }

        fun onHomeVisible() {
            val state = _uiState.value
            startHomePrefetch(
                homePrefetchQueue.onVisible(
                    currentItemCount = state.videos.size,
                    feedReady = state.isReadyForPrefetch(),
                ),
            )
        }

        fun onHomeHidden() {
            homePrefetchQueue.onHidden()
            synchronized(homePrefetchWorkerLock) {
                homePrefetchJob?.cancel()
            }

            wave2Job?.cancel()
            savedInterestJob?.cancel()
            _uiState.update { it.copy(isLoadingMore = false) }
        }

        fun onHomeViewportChanged(lastVisibleVideoIndex: Int) {
            this.lastVisibleVideoIndex = lastVisibleVideoIndex
            val state = _uiState.value
            if (!state.isReadyForPrefetch()) return
            startHomePrefetch(
                homePrefetchQueue.onViewportChanged(
                    currentItemCount = state.videos.size,
                    lastVisibleItemIndex = lastVisibleVideoIndex,
                ),
            )
        }

        private fun HomeUiState.isReadyForPrefetch(): Boolean = videos.isNotEmpty() && !isLoading && isFlowFeed && hasMorePages

        private fun startHomePrefetch(request: FeedPrefetchRequest?) {
            request ?: return
            val worker =
                synchronized(homePrefetchWorkerLock) {
                    if (homePrefetchJob?.isCompleted == false) return
                    viewModelScope
                        .launch(
                            context = PerformanceDispatcher.networkIO,
                            start = CoroutineStart.LAZY,
                        ) {
                            drainHomePrefetchQueue(request.generation)
                        }.also { homePrefetchJob = it }
                }
            worker.start()
        }

        private suspend fun drainHomePrefetchQueue(generation: Int) {
            var pagesLoaded = 0
            var emptyPageAttempts = 0
            var allowRestart = true
            try {
                wave2Job?.takeIf { it.isActive }?.join()
                while (pagesLoaded < HOME_PREFETCH_MAX_PAGES_PER_RUN) {
                    val state = _uiState.value
                    val request = homePrefetchQueue.currentRequest(state.videos.size) ?: break
                    if (request.generation != generation || !state.hasMorePages) break

                    _uiState.update { it.copy(isLoadingMore = true) }
                    if (loadNextPrefetchPage(generation)) {
                        pagesLoaded++
                        continue
                    }

                    // A page that appended nothing leaves the feed at the same length, so the
                    // viewport index cannot change and nothing would re-arm this queue. Retry a
                    // few times — each attempt rotates queries and seeds — before giving up.
                    emptyPageAttempts++
                    if (emptyPageAttempts >= HOME_PREFETCH_EMPTY_PAGE_RETRIES) {
                        allowRestart = false
                        break
                    }
                    delay(HOME_PREFETCH_EMPTY_PAGE_BACKOFF_MS * emptyPageAttempts)
                }
                if (pagesLoaded >= HOME_PREFETCH_MAX_PAGES_PER_RUN) {
                    allowRestart = false
                }
            } catch (cancellation: CancellationException) {
                allowRestart = false
                throw cancellation
            } finally {
                val workerJob = currentCoroutineContext()[Job]
                val ownsLoadingState =
                    synchronized(homePrefetchWorkerLock) {
                        if (homePrefetchJob === workerJob) {
                            homePrefetchJob = null
                            true
                        } else {
                            false
                        }
                    }
                if (ownsLoadingState) {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    if (allowRestart) {
                        startHomePrefetch(homePrefetchQueue.currentRequest(_uiState.value.videos.size))
                    }
                }
            }
        }

        private fun resetHomePrefetch() {
            homePrefetchQueue.reset()
            synchronized(homePrefetchWorkerLock) {
                homePrefetchJob?.cancel()
            }
            _uiState.update { it.copy(isLoadingMore = false) }
        }

        fun removeContinueWatchingEntry(videoId: String) {
            viewModelScope.launch {
                viewHistory.clearVideoHistory(videoId)
            }
        }

        private fun dropChannelFromFeed(
            channelId: String,
            videoId: String? = null,
        ) {
            HomeFeedCache.filterOut(channelId = channelId, videoId = videoId)
            subsBacklog = subsBacklog.filter { it.channelId != channelId }
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                persistentHomeFeedCache.deleteChannel(channelId)
                videoId?.let { persistentHomeFeedCache.deleteVideo(it) }
            }
            _uiState.update { state ->
                state.copy(
                    videos = state.videos.filter { it.id != videoId && it.channelId != channelId },
                    shorts = state.shorts.filter { it.id != videoId && it.channelId != channelId },
                )
            }
            // Targeted eviction — preserves other channel caches in discovery engine
            shortsRepository.evictChannel(channelId)
        }

        private suspend fun subscriptionScope(): HomeSubscriptionScope =
            HomeSubscriptionScope.of(
                subscriptions = subscriptionRepository.getAllSubscriptionIds(),
                showOnHome = playerPreferences.homeSubscriptionsEnabled.first(),
            )

        private suspend fun feedExclusions(): FeedExclusions =
            runCatching { FlowNeuroEngine.feedExclusions() }
                .getOrDefault(FeedExclusions.NONE)
                .hidingChannels(subscriptionScope().hidden)

        private suspend fun cacheFilters(): HomeFeedCacheFilters =
            HomeFeedCacheFilters(watchedVideoIds = watchedVideoIds.value, exclusions = feedExclusions())

        private fun hydratePersistentHomeFeed() {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val cached =
                    runCatching {
                        persistentHomeFeedCache.loadLastFeed(cacheFilters())
                    }.getOrElse { emptyList() }
                if (cached.isEmpty()) return@launch
                val hydratedCached = repository.enrichLikelyCollabAvatarStacks(cached, limit = 8)

                _uiState.update { state ->
                    if (state.videos.isNotEmpty()) return@update state
                    val videos = hydratedCached.filterWatched(watchedVideoIds.value)
                    HomeFeedCache.update(videos, state.shorts)
                    state.copy(
                        videos = videos,
                        isFlowFeed = true,
                        error = null,
                        lastRefreshTime = System.currentTimeMillis(),
                    )
                }
                enrichVisibleChannelMetadata(hydratedCached)?.let {
                    persistentHomeFeedCache.saveLastFeed(it)
                }
            }
        }

        fun loadFlowFeed(forceRefresh: Boolean = false) {
            if (_uiState.value.isLoading && !forceRefresh) return

            wave2Job?.cancel()
            feedJob?.cancel()
            subsTopUpJob?.cancel()
            channelMemoryJob?.cancel()
            _uiState.update { it.copy(isLoading = true, error = null) }

            feedJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    try {
                        discoveryQueries.clear()
                        // A refresh restarts the discovery tree at its roots (broad pass);
                        // load-more regenerations then dig deeper per cluster.
                        discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries(resetDepth = true))
                        currentQueryIndex = 0

                        val userSubs = subscriptionScope().boosted
                        val region = playerPreferences.trendingRegion.first()
                        val fetchStart = System.currentTimeMillis()

                        // ── Wave 1: first 3 queries + related lanes; subscriptions come from the store ──
                        val wave1QueryCount = discoveryQueries.size.coerceAtMost(3)
                        val wave1Queries = discoveryQueries.take(wave1QueryCount)
                        currentQueryIndex = wave1QueryCount

                        val results =
                            supervisorScope {
                                val deferredDiscovery =
                                    async {
                                        wave1Queries
                                            .map { query ->
                                                async {
                                                    query to
                                                        runCatching {
                                                            repository.searchVideos(query).first
                                                        }.getOrElse { emptyList() }
                                                }
                                            }.awaitAll()
                                    }

                                // ── Related-graph lane: harvest /next neighbours of recent positives ──
                                val deferredRelated =
                                    async {
                                        val seedInputs = feedSources.historySeedInputs()
                                        val longTermInputs = feedSources.longTermSeedInputs()
                                        val seedIds = FlowNeuroEngine.selectRelatedSeeds(seedInputs, MAX_RELATED_SEEDS, longTermInputs)
                                        feedSources.fetchRelatedGraph(seedInputs + longTermInputs, seedIds, ::cacheFilters)
                                    }

                                Wave1FeedResults(
                                    discovery = deferredDiscovery.await(),
                                    related = deferredRelated.await(),
                                )
                            }

                        val discoveryPairs = results.discovery
                        val rawDiscovery = discoveryPairs.flatMap { it.second }
                        val relatedFetch = results.related
                        val rawRelated = relatedFetch.candidates

                        // Stale-query feedback: queries whose results are mostly
                        // already-shown get skipped by the next generation cycle.
                        reportQueryNovelty(discoveryPairs)

                        Log.d(TAG, "Wave 1 fetch completed in ${System.currentTimeMillis() - fetchStart}ms")

                        val subAvatarMap: Map<String, String> =
                            runCatching {
                                subscriptionRepository
                                    .getAllSubscriptions()
                                    .first()
                                    .filter {
                                        it.channelThumbnail.isNotEmpty() &&
                                            !ThumbnailUrlResolver.isUnusableChannelAvatar(it.channelThumbnail)
                                    }.associate { it.channelId to it.channelThumbnail }
                            }.getOrElse { emptyMap() }

                        // Extract shorts from all sources for the shelf, ranked by FlowNeuro
                        val now = System.currentTimeMillis()
                        val brain = FlowNeuroEngine.getBrainSnapshot()
                        val taste = feedTasteProfile(brain, FlowNeuroEngine.getPersona(brain))

                        val exclusions = feedExclusions()
                        // Subscriptions come from the store, which is local: the network top-up runs
                        // after first paint and never holds the feed back.
                        val storedFeed =
                            if (userSubs.isEmpty()) {
                                emptyList()
                            } else {
                                runCatching { subscriptionFeedRepository.observeFeed().first() }.getOrDefault(emptyList())
                            }
                        val feedShorts =
                            (storedFeed.storedSubscriptionReels(now) + rawDiscovery.extractShorts())
                                .distinctBy { it.id }
                                .filterWatched(watchedShortIds.value)
                                .filterRecentHomeSuggestion(now)
                                .filterNot(exclusions::hidesFromRecommendations)
                        if (feedShorts.isNotEmpty() && playerPreferences.effectiveHomeShortsShelfEnabled.first()) {
                            // A refresh replaces the shelf; appending kept the same reels at its front.
                            val rankedShorts =
                                FlowNeuroEngine
                                    .rank(feedShorts, userSubs)
                                    .recentlyShownLast { NeuroScoring.isRecentlySeen(brain.feedHistory[it], now) }
                            _uiState.update { state -> state.copy(shorts = rankedShorts) }
                        }

                        val watched = watchedVideoIds.value
                        // Cached uploads only: the network refresh runs after first paint.
                        val rawMemory = runCatching { channelMemory.storedUploads(now) }.getOrDefault(emptyList())
                        val lanes =
                            buildHomeFeedLanes(
                                rawSubs = storedFeed.storedSubscriptionVideos(now),
                                rawDiscovery = rawDiscovery,
                                rawMemory = rawMemory,
                                rawRelated = rawRelated,
                                rssFeed = storedFeed,
                                watched = watched,
                                exclusions = exclusions,
                                isRecentlyShown = { id -> NeuroScoring.isRecentlySeen(brain.feedHistory[id], now) },
                                taste = taste,
                                now = now,
                                freshSlotTarget = dynamicFreshSubSlots(userSubs.size),
                                subAvatarMap = subAvatarMap,
                                rank = { pool -> FlowNeuroEngine.rank(pool, userSubs) },
                            )

                        Log.d(
                            TAG,
                            "Flow candidates: subs=${lanes.subsPoolSize}, discovery=${lanes.discoveryPoolSize}, " +
                                "memory=${lanes.memoryPoolSize}, related=${rawRelated.size}, subCount=${userSubs.size}",
                        )

                        val mix =
                            assembleHomeFeed(
                                lanes = lanes,
                                onScreenIds = _uiState.value.videos.mapTo(HashSet()) { it.id },
                                subCount = userSubs.size,
                                totalInteractions = brain.totalInteractions,
                            )
                        val finalMix = mix.videos
                        subsBacklog = mix.subsBacklog
                        channelMemoryQuota = mix.quotas[FeedSource.CHANNEL_MEMORY] ?: 0
                        val memoryIds =
                            mix.sourceMix.items
                                .filter { it.source == FeedSource.CHANNEL_MEMORY }
                                .mapTo(HashSet()) { it.video.id }
                        relatedPickIds.clear()
                        mix.sourceMix.items
                            .filter { it.source == FeedSource.RELATED }
                            .mapTo(relatedPickIds) { it.video.id }

                        if (finalMix.isEmpty()) {
                            settleWithoutFeed()
                            return@launch
                        }
                        val relatedMetrics =
                            buildRelatedLaneMetrics(
                                seedInputs = relatedFetch.seedInputs,
                                seedIds = relatedFetch.seedIds,
                                fetchedPerSeed = relatedFetch.fetchedPerSeed,
                                mergedRelatedCandidates = rawRelated,
                                filteredRelatedCandidates = lanes.relatedCandidates,
                                selectedSourceCounts = mix.selectedSourceCounts,
                                finalFeedCount = finalMix.size,
                                finalRelatedVideoIds =
                                    mix.sourceMix.items
                                        .filter { it.source == FeedSource.RELATED }
                                        .mapTo(HashSet()) { it.video.id },
                                brain = brain,
                            )
                        Log.d(TAG, relatedMetrics.toLogString())

                        Log.d(
                            TAG,
                            "Flow mix: freshLane=${mix.freshAdded}, final=${finalMix.size}, " +
                                "quotas=${mix.quotas}, selected=${mix.sourceMix.sourceCounts}",
                        )

                        val spacedMix =
                            repository.enrichLikelyCollabAvatarStacks(
                                spaceByChannel(finalMix),
                                limit = 8,
                            )
                        val renderedIds = spacedMix.mapTo(HashSet()) { it.id }
                        val reserveCandidates =
                            cacheRelatedCandidates(lanes.bestRelated, lanes.relatedMetadata, renderedIds) +
                                cacheCandidates(FeedSource.DISCOVERY, lanes.bestDiscovery, renderedIds) +
                                cacheCandidates(FeedSource.SUBS, lanes.bestSubs, renderedIds)
                        var visibleFeed = emptyList<Video>()
                        _uiState.update { state ->
                            visibleFeed = spacedMix.filterWatched(watchedVideoIds.value)
                            state.copy(
                                videos = visibleFeed,
                                channelMemoryVideoIds = memoryIds,
                                isLoading = false,
                                isRefreshing = false,
                                hasMorePages = true,
                                isFlowFeed = true,
                                lastRefreshTime = now,
                            )
                        }
                        HomeFeedCache.update(visibleFeed, _uiState.value.shorts)
                        persistentHomeFeedCache.saveLastFeed(spacedMix)
                        persistentHomeFeedCache.saveReserve(reserveCandidates)
                        enrichVisibleChannelMetadata(spacedMix)?.let {
                            persistentHomeFeedCache.saveLastFeed(it)
                        }

                        // Enrich (post-paint) with related neighbours of saved/watched videos.
                        enrichFeedWithSavedInterest(userSubs, taste)

                        startWave2Discovery(finalMix, userSubs, taste)
                        startSubscriptionTopUp(userSubs, storedFeed)
                        startChannelMemoryRefresh(userSubs)
                        chipFeeds.refreshChips()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = appContext.getString(R.string.error_failed_to_load_feed),
                            )
                        }
                        settleWithoutFeed()
                    }
                }
        }

        /** Wave 2: the discovery queries wave 1 did not have time for, merged in after paint. */
        private fun startWave2Discovery(
            finalMix: List<Video>,
            userSubs: Set<String>,
            taste: FeedTasteProfile,
        ) {
            val wave2Queries = discoveryQueries.drop(currentQueryIndex)
            if (wave2Queries.isNotEmpty()) {
                val wave2FinalMixIds = finalMix.map { it.id }.toHashSet()
                wave2Job =
                    viewModelScope.launch(PerformanceDispatcher.networkIO) wave2@{
                        try {
                            val wave2Raw =
                                wave2Queries
                                    .map { q ->
                                        async {
                                            q to (
                                                withTimeoutOrNull(6_000L) {
                                                    try {
                                                        repository.searchVideos(q).first
                                                    } catch (cancellation: CancellationException) {
                                                        throw cancellation
                                                    } catch (error: Exception) {
                                                        Log.d(TAG, "Wave 2 query failed for $q: ${error.message}")
                                                        emptyList()
                                                    }
                                                } ?: emptyList()
                                            )
                                        }
                                    }.awaitAll()

                            reportQueryNovelty(wave2Raw)

                            val wave2Watched = watchedVideoIds.value
                            val wave2Valid =
                                wave2Raw
                                    .flatMap { it.second }
                                    .filterValid()
                                    .filterWatched(wave2Watched)
                                    .filter { !wave2FinalMixIds.contains(it.id) }
                                    .filterNot(feedExclusions()::hidesFromRecommendations)
                            if (wave2Valid.isEmpty()) return@wave2

                            val wave2Ranked =
                                demoteByFit(FlowNeuroEngine.rank(wave2Valid, userSubs), taste)
                                    .take(15)

                            if (wave2Ranked.isNotEmpty()) {
                                var updatedSnapshot: List<Video>? = null
                                _uiState.update { state ->
                                    val currentIds = state.videos.map { it.id }.toHashSet()
                                    val uniqueNew =
                                        wave2Ranked
                                            .filterWatched(watchedVideoIds.value)
                                            .filter { !currentIds.contains(it.id) }
                                            .distinctBy { it.channelId }
                                    if (uniqueNew.isEmpty()) return@update state
                                    val updated = state.videos + uniqueNew
                                    updatedSnapshot = updated
                                    HomeFeedCache.update(updated, state.shorts)
                                    state.copy(videos = updated)
                                }
                                updatedSnapshot?.let { persistentHomeFeedCache.saveLastFeed(it) }
                                currentQueryIndex = discoveryQueries.size
                                Log.d(TAG, "Wave 2 merged ${wave2Ranked.size} extra candidates")
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            Log.d(TAG, "Wave 2 failed: ${error.message}")
                        }
                    }
            }
        }

        /**
         * Reports per-query result novelty to the engine: a query whose results
         * are mostly videos already shown recently gets marked stale and skipped
         * by the next discovery-generation cycle.
         */
        private suspend fun reportQueryNovelty(pairs: List<Pair<String, List<Video>>>) {
            if (pairs.isEmpty()) return
            runCatching {
                val recentlyShown = FlowNeuroEngine.getRecentlyShownVideoIds(48L)
                if (recentlyShown.isEmpty()) return
                pairs.forEach { (query, queryResults) ->
                    if (queryResults.size >= 5) {
                        val novel = queryResults.count { it.id !in recentlyShown }
                        FlowNeuroEngine.reportQueryResultNovelty(
                            query,
                            novel.toDouble() / queryResults.size,
                        )
                    }
                }
            }
        }

        /** Cheapest load-more source: candidates already fetched and persisted by an earlier pass. */
        private suspend fun fillPageFromReserve(
            page: MutableList<Video>,
            channelCounts: MutableMap<String, Int>,
            pageIds: MutableSet<String>,
            now: Long,
        ): Int {
            val reserveRows =
                try {
                    persistentHomeFeedCache.loadReservePage(cacheFilters())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.d(TAG, "Reserve prefetch unavailable: ${error.message}")
                    emptyList()
                }
            reserveRows
                .filter { it.source == HomeFeedCacheRepository.SOURCE_RELATED }
                .mapTo(relatedPickIds) { it.video.id }
            val reserveVideos =
                reserveRows
                    .map { it.video }
                    .filterValid()
                    .filterRecentHomeSuggestion(now)
            val reserveAdded =
                addUniquePageVideos(
                    candidates = reserveVideos,
                    targetList = page,
                    channelCounts = channelCounts,
                    usedVideoIds = pageIds,
                    targetSize = MIN_PAGE_SIZE,
                )
            if (reserveAdded > 0) {
                runCatching {
                    persistentHomeFeedCache.consumeReserve(page.take(reserveAdded).map { it.id })
                }
            }
            return reserveAdded
        }

        /**
         * Digs related lanes from the widest seed universe — history, liked, playlists and the
         * feed videos on screen. Returns how many seeds were expanded.
         */
        private suspend fun fillPageFromRelatedGraph(
            page: MutableList<Video>,
            channelCounts: MutableMap<String, Int>,
            pageIds: MutableSet<String>,
            now: Long,
            userSubs: Set<String>,
            taste: FeedTasteProfile,
            brain: UserBrain,
        ): Int {
            val seedInputs = loadMoreSeedInputs()
            val seedIds = FlowNeuroEngine.selectRelatedSeeds(seedInputs, LOAD_MORE_GRAPH_SEEDS)
            if (seedIds.isEmpty()) return 0

            val graphFetch = feedSources.fetchRelatedGraph(seedInputs, seedIds, ::cacheFilters)
            val graphCandidates =
                graphFetch.candidates
                    .filterValidGraph()
                    .filterWatchedGraph(watchedVideoIds.value)
                    .filterRecentHomeSuggestionGraph(now)
            val graphMetadata = graphCandidates.associateBy { it.video.id }
            val graphRanked =
                demoteByFit(
                    applyGraphBoost(
                        FlowNeuroEngine.rank(graphCandidates.map { it.video }, userSubs),
                        graphMetadata,
                    ),
                    taste,
                )
            val graphStartIndex = page.size
            addUniquePageVideos(
                candidates = graphRanked,
                targetList = page,
                channelCounts = channelCounts,
                usedVideoIds = pageIds,
                targetSize = MIN_PAGE_SIZE,
            )
            page.drop(graphStartIndex).mapTo(relatedPickIds) { it.id }
            persistentHomeFeedCache.saveReserve(
                cacheRelatedCandidates(graphRanked, graphMetadata, pageIds),
            )
            if (page.size >= MIN_PAGE_SIZE) {
                val selectedGraphIds = page.drop(graphStartIndex).mapTo(HashSet()) { it.id }
                Log.d(
                    TAG,
                    buildRelatedLaneMetrics(
                        seedInputs = graphFetch.seedInputs,
                        seedIds = graphFetch.seedIds,
                        fetchedPerSeed = graphFetch.fetchedPerSeed,
                        mergedRelatedCandidates = graphFetch.candidates,
                        filteredRelatedCandidates = graphCandidates,
                        selectedSourceCounts = mapOf(FeedSource.RELATED to selectedGraphIds.size),
                        finalFeedCount = selectedGraphIds.size,
                        finalRelatedVideoIds = selectedGraphIds,
                        brain = brain,
                    ).toLogString(),
                )
            }
            return seedIds.size
        }

        private suspend fun loadNextPrefetchPage(generation: Int): Boolean {
            try {
                val now = System.currentTimeMillis()
                val userSubs = subscriptionScope().boosted
                val brain = FlowNeuroEngine.getBrainSnapshot()
                val taste = feedTasteProfile(brain, FlowNeuroEngine.getPersona(brain))
                val currentIds =
                    _uiState.value.videos
                        .map { it.id }
                        .toHashSet()
                val page = mutableListOf<Video>()
                val channelCounts = HashMap<String, Int>()
                val pageIds = HashSet<String>(currentIds)

                fillPageFromChannelMemory(page, channelCounts, pageIds, userSubs, now)

                val reserveAdded = fillPageFromReserve(page, channelCounts, pageIds, now)
                if (page.size >= MIN_PAGE_SIZE) {
                    val appended = appendLoadMorePage(page, generation)
                    appended?.let { persistentHomeFeedCache.saveLastFeed(it) }
                    Log.d(TAG, "Load-more filled from reserve: +$reserveAdded")
                    return appended != null
                }

                val graphSeedCount = fillPageFromRelatedGraph(page, channelCounts, pageIds, now, userSubs, taste, brain)
                if (page.size >= MIN_PAGE_SIZE) {
                    val appended = appendLoadMorePage(page, generation)
                    appended?.let { persistentHomeFeedCache.saveLastFeed(it) }
                    Log.d(TAG, "Load-more filled from reserve/graph: reserve=$reserveAdded graphSeeds=$graphSeedCount")
                    return appended != null
                }

                if (currentQueryIndex >= discoveryQueries.size) {
                    discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries())
                }

                val queryA = discoveryQueries.getOrNull(currentQueryIndex++)
                val queryB = discoveryQueries.getOrNull(currentQueryIndex++)

                val searchQueries = listOfNotNull(queryA, queryB)

                val rawVideos =
                    coroutineScope {
                        searchQueries
                            .map { query ->
                                async {
                                    withTimeoutOrNull(6_000L) {
                                        try {
                                            repository.searchVideos(query).first
                                        } catch (cancellation: CancellationException) {
                                            throw cancellation
                                        } catch (error: Exception) {
                                            Log.d(TAG, "Prefetch query failed for $query: ${error.message}")
                                            emptyList()
                                        }
                                    } ?: emptyList()
                                }
                            }.awaitAll()
                            .flatten()
                    }
                if (!homePrefetchQueue.isCurrent(generation)) return false

                // Extract shorts for shelf — rank through FlowNeuro
                val moreShorts =
                    rawVideos
                        .extractShorts()
                        .filterWatched(watchedShortIds.value)
                        .filterRecentHomeSuggestion(now)
                        .filterNot(feedExclusions()::hidesFromRecommendations)
                if (moreShorts.isNotEmpty() && playerPreferences.effectiveHomeShortsShelfEnabled.first()) {
                    val rankedMore =
                        FlowNeuroEngine
                            .rank(moreShorts, userSubs)
                            .recentlyShownLast { NeuroScoring.isRecentlySeen(brain.feedHistory[it], now) }
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + rankedMore).distinctBy { it.id })
                    }
                }

                val newVideos =
                    rawVideos
                        .filterValid()
                        .filterWatched(watchedVideoIds.value)
                        .filterRecentHomeSuggestion(now)

                if (newVideos.isNotEmpty()) {
                    val rankedDiscovery = demoteByFit(FlowNeuroEngine.rank(newVideos, userSubs), taste)
                    addUniquePageVideos(
                        candidates = rankedDiscovery,
                        targetList = page,
                        channelCounts = channelCounts,
                        usedVideoIds = pageIds,
                        targetSize = MIN_PAGE_SIZE,
                    )
                    persistentHomeFeedCache.saveReserve(
                        cacheCandidates(FeedSource.DISCOVERY, rankedDiscovery, pageIds),
                    )
                }

                if (page.size < MIN_PAGE_SIZE && subsBacklog.isNotEmpty()) {
                    val exclusions = feedExclusions()
                    subsBacklog = subsBacklog.filterNot(exclusions::hidesFromRecommendations)
                    addUniquePageVideos(
                        candidates = subsBacklog,
                        targetList = page,
                        channelCounts = channelCounts,
                        usedVideoIds = pageIds,
                        targetSize = MIN_PAGE_SIZE,
                    )
                    subsBacklog = subsBacklog.filterNot { pageIds.contains(it.id) }
                }

                // Never run dry: when every other source thinned out, escalate with a
                // wider related pass — more seeds, drawn from the feed itself and saved
                // interests. The engine's seed cooldown keeps the lanes rotating, and
                // its scarcity fallback re-admits cooled seeds when the pool is thin.
                if (page.size < MIN_PAGE_SIZE) {
                    val fallbackInputs = loadMoreSeedInputs()
                    val fallbackSeeds =
                        FlowNeuroEngine.selectRelatedSeeds(fallbackInputs, LOAD_MORE_FALLBACK_SEEDS)
                    if (fallbackSeeds.isNotEmpty() && homePrefetchQueue.isCurrent(generation)) {
                        val fallbackFetch =
                            feedSources.fetchRelatedGraph(fallbackInputs, fallbackSeeds, ::cacheFilters)
                        val fallbackCandidates =
                            fallbackFetch.candidates
                                .filterValidGraph()
                                .filterWatchedGraph(watchedVideoIds.value)
                                .filterRecentHomeSuggestionGraph(now)
                        val fallbackMetadata = fallbackCandidates.associateBy { it.video.id }
                        val fallbackRanked =
                            demoteByFit(
                                applyGraphBoost(
                                    FlowNeuroEngine.rank(fallbackCandidates.map { it.video }, userSubs),
                                    fallbackMetadata,
                                ),
                                taste,
                            )
                        val fallbackStartIndex = page.size
                        addUniquePageVideos(
                            candidates = fallbackRanked,
                            targetList = page,
                            channelCounts = channelCounts,
                            usedVideoIds = pageIds,
                            targetSize = MIN_PAGE_SIZE,
                        )
                        page.drop(fallbackStartIndex).mapTo(relatedPickIds) { it.id }
                        persistentHomeFeedCache.saveReserve(
                            cacheRelatedCandidates(fallbackRanked, fallbackMetadata, pageIds),
                        )
                        Log.d(TAG, "Load-more fallback: ${fallbackSeeds.size} feed/saved seeds → page=${page.size}")
                    }
                }

                if (page.isNotEmpty()) {
                    val appended = appendLoadMorePage(page, generation)
                    appended?.let { persistentHomeFeedCache.saveLastFeed(it) }
                    return appended != null
                }
                return false
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.d(TAG, "Home prefetch page failed: ${error.message}")
                return false
            }
        }

        private suspend fun appendLoadMorePage(
            page: List<Video>,
            generation: Int,
        ): List<Video>? {
            if (page.isEmpty() || !homePrefetchQueue.isCurrent(generation)) return null
            val exclusions = feedExclusions()
            var updatedSnapshot: List<Video>? = null
            var appendedPage = emptyList<Video>()
            _uiState.update { state ->
                if (!homePrefetchQueue.isCurrent(generation)) return@update state
                val existingVideoIds = state.videos.mapTo(HashSet()) { it.id }
                appendedPage =
                    page
                        .filterWatched(watchedVideoIds.value)
                        .filterNot { it.id in existingVideoIds || exclusions.hidesFromRecommendations(it) }
                if (appendedPage.isEmpty()) return@update state
                val tailChannels = state.videos.takeLast(2).map { it.channelId }
                val updated = state.videos + spaceByChannel(appendedPage, seedRecent = tailChannels)
                updatedSnapshot = updated
                HomeFeedCache.update(updated, state.shorts)
                state.copy(
                    videos = updated,
                    hasMorePages = true,
                )
            }
            if (appendedPage.isEmpty()) return null
            return enrichVisibleChannelMetadata(appendedPage) ?: updatedSnapshot
        }

        private suspend fun enrichVisibleChannelMetadata(videos: List<Video>): List<Video>? {
            val enriched = repository.enrichMissingChannelMetadata(videos)
            if (enriched == videos) return null

            val originalById = videos.associateBy { it.id }
            val updates =
                enriched
                    .filter { enrichedVideo -> originalById[enrichedVideo.id] != enrichedVideo }
                    .associateBy { it.id }
            var updatedSnapshot: List<Video>? = null
            _uiState.update { state ->
                val updated =
                    state.videos.map { current ->
                        updates[current.id]?.let(current::withChannelMetadataFrom) ?: current
                    }
                if (updated == state.videos) return@update state
                updatedSnapshot = updated
                HomeFeedCache.update(updated, state.shorts)
                state.copy(videos = updated)
            }
            return updatedSnapshot
        }

        fun enrichChannelMetadataIfMissing(video: Video) {
            val videoId = video.id
            if (!video.needsChannelMetadata() || !channelMetadataEnrichmentInFlight.add(videoId)) return

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val enriched =
                        repository
                            .enrichMissingChannelMetadata(listOf(video), limit = 1)
                            .firstOrNull()
                            ?: return@launch
                    if (enriched == video) return@launch

                    _uiState.update { state ->
                        val updated =
                            state.videos.map { current ->
                                if (current.id != videoId) {
                                    current
                                } else {
                                    current.withChannelMetadataFrom(enriched)
                                }
                            }
                        if (updated == state.videos) {
                            state
                        } else {
                            HomeFeedCache.update(updated, state.shorts)
                            state.copy(videos = updated)
                        }
                    }
                } finally {
                    channelMetadataEnrichmentInFlight.remove(videoId)
                }
            }
        }

        /**
         * Nothing to fall back to: the trending kiosk this used to load is retired, and the charts
         * that replaced it belong on Explore, not mixed into the feed. The screen settles empty and
         * offers a refresh instead of filling itself with unrelated content.
         */
        private fun settleWithoutFeed() {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasMorePages = false,
                    isFlowFeed = false,
                )
            }
        }

        /** The viewer's refresh. Ignored while a load is running, so repeated taps cannot stack pipelines (#1171). */
        fun refreshFeed() {
            if (feedJob?.isActive == true) return
            reloadFeed()
        }

        /** Starts over, replacing a load already running: a changed setting must apply to the next feed. */
        private fun reloadFeed() {
            resetHomePrefetch()
            wave2Job?.cancel()
            HomeFeedCache.clear()
            _uiState.update { it.copy(isRefreshing = true) }
            loadFlowFeed(forceRefresh = true)
        }

        /**
         * Tops the subscription store up from channel tabs after first paint, then adds what it found
         * below the cards on screen. One at a time; a reload cancels it, so a changed setting cannot be
         * undone by a late merge.
         */
        private fun startSubscriptionTopUp(
            userSubs: Set<String>,
            storedFeed: List<Video>,
        ) {
            if (userSubs.isEmpty() || subsTopUpJob?.isActive == true) return
            subsTopUpJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    val uploads =
                        try {
                            homeSubscriptionUploads.fetch(
                                subscriptions = subscriptionOwners(userSubs),
                                priorityChannelIds = storedFeed.channelsMissingLengths(System.currentTimeMillis()),
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            Log.d(TAG, "Subscription top-up failed: ${error.message}")
                            emptyList()
                        }
                    if (uploads.isEmpty()) return@launch
                    // Passive channel profiling: upload titles teach the engine what each channel is about.
                    runCatching { FlowNeuroEngine.onChannelUploadsObserved(uploads) }
                    subscriptionFeedRepository.mergeChannelTabUploads(uploads)
                    mergeLateSubscriptions(uploads, userSubs)
                }
        }

        private suspend fun mergeLateSubscriptions(
            uploads: List<Video>,
            userSubs: Set<String>,
        ) {
            val exclusions = feedExclusions()
            val videos = uploads.filterNot(exclusions::hidesFromRecommendations)
            val onScreen = _uiState.value.videos.mapTo(HashSet()) { it.id }
            val candidates =
                videos
                    .filterValid()
                    .filterWatched(watchedVideoIds.value)
                    .filterNot { it.id in onScreen }
                    .groupBy { it.channelId }
                    .values
                    .flatMap { uploads -> uploads.sortedByDescending { it.timestamp }.take(LATE_SUBS_PER_CHANNEL) }
            val late =
                FlowNeuroEngine
                    .rank(candidates, userSubs)
                    .distinctBy { it.channelId }
                    .take(LATE_SUBS_MAX)
            var merged: List<Video>? = null
            var added = 0
            _uiState.update { state ->
                if (state.isLoading || state.videos.isEmpty()) return@update state
                val below = lastVisibleVideoIndex.coerceAtLeast(LATE_SUBS_MIN_INDEX)
                val updated = insertBelowViewport(state.videos, late.filterWatched(watchedVideoIds.value), below)
                if (updated.size == state.videos.size) return@update state
                added = updated.size - state.videos.size
                merged = updated
                HomeFeedCache.update(updated, state.shorts)
                state.copy(videos = updated)
            }
            merged?.let {
                persistentHomeFeedCache.saveLastFeed(it)
                Log.d(TAG, "Subscription top-up added $added uploads below the viewport")
            }
        }

        /**
         * Refreshes channel memory after first paint and slips new uploads in below the viewport,
         * within the lane's quota and one per channel.
         */
        private fun startChannelMemoryRefresh(userSubs: Set<String>) {
            if (channelMemoryJob?.isActive == true) return
            channelMemoryJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    val fresh =
                        try {
                            channelMemory.refresh()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            Log.d(TAG, "Channel memory refresh failed: ${error.message}")
                            emptyList()
                        }
                    if (fresh.isEmpty()) return@launch
                    val now = System.currentTimeMillis()
                    val brain = FlowNeuroEngine.getBrainSnapshot()
                    val exclusions = feedExclusions()
                    val ranked =
                        FlowNeuroEngine.rank(
                            fresh
                                .filterValid()
                                .filterWatched(watchedVideoIds.value)
                                .filterNot {
                                    exclusions.hidesFromRecommendations(it) ||
                                        NeuroScoring.isRecentlySeen(brain.feedHistory[it.id], now)
                                },
                            userSubs,
                        )
                    var added = 0
                    _uiState.update { state ->
                        if (state.isLoading || state.videos.isEmpty()) return@update state
                        val memoryOnScreen = state.videos.filter { it.id in state.channelMemoryVideoIds }
                        val late =
                            channelMemoryPicks(
                                ranked = ranked,
                                onScreenIds = state.videos.mapTo(HashSet()) { it.id },
                                onScreenMemoryChannels = memoryOnScreen.mapTo(HashSet()) { it.channelId },
                                room = channelMemoryQuota - memoryOnScreen.size,
                            )
                        val below = lastVisibleVideoIndex.coerceAtLeast(LATE_SUBS_MIN_INDEX)
                        val updated = insertBelowViewport(state.videos, late, below)
                        added = updated.size - state.videos.size
                        if (added == 0) return@update state
                        HomeFeedCache.update(updated, state.shorts)
                        state.copy(videos = updated, channelMemoryVideoIds = state.channelMemoryVideoIds + late.map { it.id })
                    }
                    if (added > 0) Log.d(TAG, "Channel memory added $added uploads below the viewport")
                }
        }

        /** One upload from a remembered channel per load-more page, so the lane outlives the first screen. */
        private suspend fun fillPageFromChannelMemory(
            page: MutableList<Video>,
            channelCounts: MutableMap<String, Int>,
            pageIds: MutableSet<String>,
            userSubs: Set<String>,
            now: Long,
        ) {
            val brain = FlowNeuroEngine.getBrainSnapshot()
            val exclusions = feedExclusions()
            val candidates =
                runCatching { channelMemory.storedUploads(now) }
                    .getOrDefault(emptyList())
                    .filterValid()
                    .filterWatched(watchedVideoIds.value)
                    .filterNot { exclusions.hidesFromRecommendations(it) || NeuroScoring.isRecentlySeen(brain.feedHistory[it.id], now) }
            if (candidates.isEmpty()) return
            val pick =
                channelMemoryPicks(
                    ranked = FlowNeuroEngine.rank(candidates, userSubs),
                    onScreenIds = pageIds,
                    onScreenMemoryChannels = page.mapTo(HashSet()) { it.channelId },
                    room = 1,
                ).firstOrNull() ?: return
            if (addUniqueVideo(pick, page, channelCounts, pageIds, maxPerChannel = 1)) {
                _uiState.update { it.copy(channelMemoryVideoIds = it.channelMemoryVideoIds + pick.id) }
            }
        }

        private suspend fun subscriptionOwners(channelIds: Set<String>): List<FeedItemOwner> =
            subscriptionRepository
                .getAllSubscriptions()
                .first()
                .filter { it.channelId in channelIds }
                .map { FeedItemOwner(id = it.channelId, name = it.channelName, avatarUrl = it.channelThumbnail) }

        fun retry() {
            resetHomePrefetch()
            wave2Job?.cancel()
            loadFlowFeed(forceRefresh = true)
        }

        private fun cacheCandidates(
            source: FeedSource,
            videos: List<Video>,
            excludedIds: Set<String> = emptySet(),
        ): List<CachedHomeVideo> =
            videos
                .asSequence()
                .filterNot { it.id in excludedIds }
                .distinctBy { it.id }
                .map { CachedHomeVideo(it, source.name) }
                .toList()

        private fun cacheRelatedCandidates(
            videos: List<Video>,
            metadata: Map<String, GraphCandidate>,
            excludedIds: Set<String> = emptySet(),
        ): List<CachedHomeVideo> =
            videos
                .asSequence()
                .filterNot { it.id in excludedIds }
                .distinctBy { it.id }
                .map { video ->
                    CachedHomeVideo(
                        video = video,
                        source = FeedSource.RELATED.name,
                        relatedSeedId = metadata[video.id]?.seedId,
                    )
                }.toList()

        private fun addUnique(
            video: Video?,
            targetList: MutableList<Video>,
            channelCounts: MutableMap<String, Int>,
            usedVideoIds: MutableSet<String>,
            maxPerChannel: Int = 2,
        ): Boolean = addUniqueVideo(video, targetList, channelCounts, usedVideoIds, maxPerChannel)

        /**
         * The load-more seed universe: saved interests (history, liked, playlists)
         * PLUS the feed itself — so paging can always dig another related lane and
         * the feed never runs dry. The engine's seed cooldown rotates them.
         */
        private suspend fun loadMoreSeedInputs(): List<GraphSeedInput> =
            (
                savedInterestSeedInputs(feedSources.gatherSavedSeedSources(), emptySet()) +
                    feedSeedInputs(_uiState.value.videos, System.currentTimeMillis(), FEED_SEED_POOL, relatedPickIds)
            ).distinctBy { it.id }

        /**
         * Enriches the feed with related neighbours of the videos the user saved/watched, on top of the
         * lane quotas. Runs after first paint so it never delays load; chosen seeds enter a cooldown.
         */
        private fun enrichFeedWithSavedInterest(
            userSubs: Set<String>,
            taste: FeedTasteProfile,
        ) {
            savedInterestJob?.cancel()
            savedInterestJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    try {
                        val now = System.currentTimeMillis()
                        val seedInputs =
                            savedInterestSeedInputs(
                                feedSources.gatherSavedSeedSources(),
                                feedSources.activeSavedSeedCooldown(now),
                            )
                        val seeds =
                            FlowNeuroEngine.selectRelatedSeeds(
                                seedInputs,
                                MAX_SAVED_SEEDS,
                            )
                        if (seeds.isEmpty()) return@launch
                        feedSources.markSeedsUsed(seeds, now)

                        val exclusions = feedExclusions()
                        val relatedCandidates =
                            feedSources
                                .fetchRelatedGraphCandidates(seedInputs, seeds, ::cacheFilters)
                                .filterValidGraph()
                                .filterWatchedGraph(watchedVideoIds.value)
                                .filterRecentHomeSuggestionGraph(now)
                                .filterNot { exclusions.hidesFromRecommendations(it.video) }
                        if (relatedCandidates.isEmpty()) return@launch

                        val existing = _uiState.value.videos.mapTo(HashSet()) { it.id }
                        val relatedMetadata = relatedCandidates.associateBy { it.video.id }
                        val enriched =
                            demoteByFit(
                                applyGraphBoost(
                                    FlowNeuroEngine.rank(
                                        relatedCandidates
                                            .map { it.video }
                                            .filterNot { existing.contains(it.id) },
                                        userSubs,
                                    ),
                                    relatedMetadata,
                                ),
                                taste,
                            ).take(SAVED_RELATED_SLOTS)
                        if (enriched.isEmpty()) return@launch
                        enriched.mapTo(relatedPickIds) { it.id }

                        _uiState.update { state ->
                            val visibleEnriched = enriched.filterWatched(watchedVideoIds.value)
                            val tail = state.videos.takeLast(2).map { it.channelId }
                            val merged = state.videos + spaceByChannel(visibleEnriched, seedRecent = tail)
                            HomeFeedCache.update(merged, state.shorts)
                            state.copy(videos = merged)
                        }
                        Log.d(TAG, "Saved-interest enrichment: +${enriched.size} from ${seeds.size} seeds")
                    } catch (e: Exception) {
                        Log.d(TAG, "Saved-interest enrichment failed: ${e.message}")
                    }
                }
        }

        fun recordShelfImpressions(visibleIds: List<String>) {
            val knownIds = _uiState.value.shorts.mapTo(HashSet()) { it.id }
            val ids = feedImpressionIds(visibleIds, knownIds)
            if (ids.isEmpty()) return
            viewModelScope.launch { FlowNeuroEngine.recordFeedImpressions(ids) }
        }

        // Viewport impressions: count only items actually scrolled into view.
        fun recordImpressions(visibleKeys: List<String>) {
            if (visibleKeys.isEmpty()) return
            val knownIds = _uiState.value.videos.mapTo(HashSet()) { it.id }
            val ids = feedImpressionIds(visibleKeys, knownIds)
            if (ids.isEmpty()) return
            viewModelScope.launch { FlowNeuroEngine.recordFeedImpressions(ids) }
        }
    }
