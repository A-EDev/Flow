package io.github.aedev.flow.ui.screens.subscriptions

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.engagement.FeedInvalidationBus
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toUiModel
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionRefreshPlan
import io.github.aedev.flow.data.subscriptions.SubscriptionWatchedVideos
import io.github.aedev.flow.data.subscriptions.withHighQualityThumbnails
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.formatYouTubeRelativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel
    @Inject
    constructor(
        private val subscriptionRepository: SubscriptionRepository,
        private val subscriptionFeedRepository: SubscriptionFeedRepository,
        private val playerPreferences: PlayerPreferences,
        private val subscriptionGroupDao: SubscriptionGroupDao,
        private val subscriptionWatchedVideos: SubscriptionWatchedVideos,
        private val neuroEngine: FlowNeuroEngine,
    ) : ViewModel() {
        companion object {
            private const val TAG = "SubsViewModel"

            private const val DURATION_ENRICHMENT_BATCH_SIZE = 3
            private const val DURATION_METADATA_TIMEOUT_MS = 4_000L
            private const val DURATION_RETRY_AFTER_MS = 30 * 60 * 1_000L
            private const val RELATIVE_TIME_TICK_MS = 60L * 1000L
        }

        private val _uiState = MutableStateFlow(SubscriptionsUiState())
        val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

        private var latestFeedVideos: List<Video> = emptyList()
        private var watchedVideoIds: Set<String> = emptySet()
        private var unplayableVideoIds: Set<String> = emptySet()
        private var excludedShortsChannelIds: Set<String> = emptySet()
        private val durationEnrichmentAttemptedAt = mutableMapOf<String, Long>()
        private var durationEnrichmentJob: Job? = null
        private var visibleVideoIds: Set<String> = emptySet()
        private var hasPendingVisibleEnrichment = false
        private var hasStarted = false

        /**
         * Starts the preference/feed collectors. Deliberately not run from `init`: the TV shell
         * hoists this ViewModel at launch (see `FlowTvApp`), so constructing it would kick off the
         * subscription RSS fetch before the user ever opens Subscriptions. Called from the screens
         * instead, so the work still begins exactly when the feed becomes visible.
         *
         * Idempotent, and only ever called from composition (main thread).
         */
        fun ensureStarted() {
            if (hasStarted) return
            hasStarted = true

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionGroupDao.getAllGroups().collect { entities ->
                    val groups = entities.map { it.toUiModel() }
                    _uiState.update { it.copy(groups = groups) }
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.effectiveShortsShelfEnabled.collect { enabled ->
                    _uiState.update { it.copy(isShortsShelfEnabled = enabled) }
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                combine(
                    playerPreferences.subscriptionShowVideos,
                    playerPreferences.effectiveSubscriptionShowShorts,
                    playerPreferences.subscriptionShowLive,
                ) { showVideos, showShorts, showLive ->
                    Triple(showVideos, showShorts, showLive)
                }.distinctUntilChanged()
                    .collect { (showVideos, showShorts, showLive) ->
                        _uiState.update {
                            it.copy(
                                showSubscriptionVideos = showVideos,
                                showSubscriptionShorts = showShorts,
                                showSubscriptionLive = showLive,
                            )
                        }
                        refreshVisibleFeed()
                    }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.subscriptionShortsExcludedChannels
                    .distinctUntilChanged()
                    .collect { ids ->
                        excludedShortsChannelIds = ids
                        _uiState.update { it.copy(excludedShortsChannelIds = ids) }
                        refreshVisibleFeed()
                    }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.subsFullWidthView.collect { fullWidth ->
                    _uiState.update { it.copy(isFullWidthView = fullWidth) }
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.subsSortMode.collect { stored ->
                    val mode = SubscriptionSortMode.fromStorage(stored)
                    _uiState.update { it.copy(sortMode = mode) }
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.selectedSubscriptionGroup.collect { groupName ->
                    _uiState.update { it.copy(selectedGroupName = groupName) }
                    refreshVisibleFeed()
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                combine(
                    playerPreferences.subscriptionLastRefreshTime,
                    playerPreferences.subscriptionLastRefreshedCount,
                    playerPreferences.subscriptionShowCheckedVideoCount,
                ) { time, count, showCheckedCount ->
                    Triple(time, count, showCheckedCount)
                }.collect { (time, count, showCheckedCount) ->
                    _uiState.update {
                        it.copy(
                            lastRefreshTime = time,
                            lastRefreshText = if (time > 0L) formatYouTubeRelativeTime(time) else null,
                            lastRefreshVideoCount = count,
                            showLastRefreshVideoCount = showCheckedCount,
                        )
                    }
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionWatchedVideos.ids.collect { ids ->
                    watchedVideoIds = ids
                    refreshVisibleFeed()
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                combine(
                    playerPreferences.unplayableVideoIds,
                    playerPreferences.hideUnplayableVideosFromSubscriptions,
                ) { ids, hideUnplayable ->
                    if (hideUnplayable) ids else emptySet()
                }.distinctUntilChanged()
                    .collect { ids ->
                        unplayableVideoIds = ids
                        refreshVisibleFeed()
                    }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionRepository
                    .getAllSubscriptions()
                    .collect { allSubs ->
                        val notifStates = allSubs.associate { it.channelId to it.isNotificationEnabled }
                        _uiState.update { it.copy(notificationStates = notifStates) }
                    }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                FeedInvalidationBus.events.collect { event ->
                    if (event is FeedInvalidationBus.Event.ChannelBlocked || event is FeedInvalidationBus.Event.NotInterested) {
                        refreshVisibleFeed()
                    }
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionFeedRepository.observeFeed().collect { videos ->
                    latestFeedVideos = videos
                    updateVideos(videos)
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                _uiState.subscriptionCount
                    .map { observers -> observers > 0 }
                    .distinctUntilChanged()
                    .collectLatest { observed ->
                        if (!observed) return@collectLatest
                        while (true) {
                            delay(RELATIVE_TIME_TICK_MS)
                            refreshVisibleFeed()
                        }
                    }
            }

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                subscriptionRepository
                    .getAllSubscriptions()
                    .map { subs -> subs.map { it.channelId }.sorted() }
                    .distinctUntilChanged()
                    .collect { channelIds ->
                        Log.i(TAG, "Channel IDs changed: ${channelIds.size} channels")
                        publishSubscribedChannels()
                        if (channelIds.isNotEmpty()) {
                            runRefresh(force = false, showLoading = _uiState.value.recentVideos.isEmpty())
                        }
                    }
            }
        }

        private suspend fun publishSubscribedChannels() {
            val allSubs = subscriptionRepository.getAllSubscriptions().first()
            val channels =
                allSubs.map { sub ->
                    Channel(
                        id = sub.channelId,
                        name = sub.channelName,
                        thumbnailUrl = ThumbnailUrlResolver.resolveChannelAvatar(sub.channelThumbnail),
                        subscriberCount = 0L,
                        isSubscribed = true,
                        isMusic = sub.isMusic,
                    )
                }
            _uiState.update { it.copy(subscribedChannels = channels) }
            refreshVisibleFeed()
        }

        /**
         * Fetches only the channels that are actually due, unless [force] (an explicit pull-to-refresh)
         * asks for the whole subscription list.
         */
        private suspend fun runRefresh(
            force: Boolean,
            showLoading: Boolean,
        ) = runRefresh(subscriptionFeedRepository.planRefresh(force), showLoading)

        private suspend fun runRefresh(
            plan: SubscriptionRefreshPlan,
            showLoading: Boolean,
        ) {
            if (plan.isEmpty) {
                Log.i(TAG, "Nothing to refresh — every subscribed channel is still fresh")
                _uiState.update { it.copy(isLoading = false) }
                return
            }
            Log.i(TAG, "Refreshing ${plan.channelIds.size} channel(s), full=${plan.isFullRefresh}")

            if (showLoading) {
                _uiState.update { it.copy(isLoading = true) }
            }
            try {
                subscriptionFeedRepository.refresh(plan).collect { progress ->
                    latestFeedVideos = progress.videos
                    _uiState.update {
                        it.copy(
                            failedChannelIds = progress.failedChannelIds,
                            failedChannelReasons = progress.failedChannelReasons,
                            refreshProcessedChannels = progress.processedChannels,
                            refreshTotalChannels = progress.totalChannels,
                        )
                    }
                    updateVideos(progress.videos)
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        refreshProcessedChannels = 0,
                        refreshTotalChannels = 0,
                    )
                }
            }
        }

        private suspend fun refreshVisibleFeed() {
            if (latestFeedVideos.isNotEmpty()) {
                updateVideos(latestFeedVideos)
            }
        }

        private suspend fun updateVideos(videos: List<Video>) {
            val state = _uiState.value
            val sections =
                subscriptionFeedSections(
                    videos = videos.withHighQualityThumbnails().withSubscriptionAvatars(),
                    filters =
                        SubscriptionFeedFilters(
                            showVideos = state.showSubscriptionVideos,
                            showShorts = state.showSubscriptionShorts,
                            showLive = state.showSubscriptionLive,
                            watchedVideoIds = watchedVideoIds,
                            unplayableVideoIds = unplayableVideoIds,
                            allowedChannelIds =
                                state.selectedGroupName?.let { name ->
                                    state.groups
                                        .find { it.name == name }
                                        ?.channelIds
                                        ?.toHashSet()
                                },
                            excludedShortsChannelIds = excludedShortsChannelIds,
                            exclusions = neuroEngine.feedExclusions(),
                        ),
                    now = System.currentTimeMillis(),
                )
            _uiState.update { it.copy(recentVideos = sections.recentVideos, shorts = sections.shorts) }
        }

        private fun List<Video>.withSubscriptionAvatars(): List<Video> {
            val avatarByChannelId =
                _uiState.value.subscribedChannels
                    .asSequence()
                    .filter { it.thumbnailUrl.isNotBlank() }
                    .associate { it.id to ThumbnailUrlResolver.resolveChannelAvatar(it.thumbnailUrl) }
            if (avatarByChannelId.isEmpty()) return this

            return map { video ->
                val normalizedExistingAvatar = ThumbnailUrlResolver.resolveChannelAvatar(video.channelThumbnailUrl)
                if (video.channelThumbnailUrl.isBlank()) {
                    avatarByChannelId[video.channelId]?.let { avatar ->
                        video.copy(
                            channelThumbnailUrl = avatar,
                            channelThumbnailUrls = video.channelThumbnailUrls.ifEmpty { listOf(avatar) },
                        )
                    } ?: video
                } else if (normalizedExistingAvatar != video.channelThumbnailUrl) {
                    video.copy(channelThumbnailUrl = normalizedExistingAvatar)
                } else {
                    video
                }
            }
        }

        fun updateVisibleVideoIds(videoIds: Set<String>) {
            visibleVideoIds = videoIds
            if (videoIds.isEmpty()) {
                hasPendingVisibleEnrichment = false
                durationEnrichmentJob?.cancel()
                durationEnrichmentJob = null
                return
            }
            scheduleVisibleDurationEnrichment()
        }

        private fun scheduleVisibleDurationEnrichment() {
            if (durationEnrichmentJob?.isActive == true) {
                hasPendingVisibleEnrichment = true
                return
            }

            val nowMillis = System.currentTimeMillis()
            val visibleWindow =
                visibleSubscriptionEnrichmentWindow(
                    videos = _uiState.value.recentVideos,
                    visibleVideoIds = visibleVideoIds,
                )
            val candidates =
                missingDurationCandidates(
                    videos = visibleWindow,
                    attemptedAtMillis = durationEnrichmentAttemptedAt,
                    nowMillis = nowMillis,
                    retryAfterMillis = DURATION_RETRY_AFTER_MS,
                )
            if (candidates.isEmpty()) return

            hasPendingVisibleEnrichment = false
            durationEnrichmentJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    val runningJob = coroutineContext[Job]
                    try {
                        val enrichedById = mutableMapOf<String, Video>()
                        candidates.chunked(DURATION_ENRICHMENT_BATCH_SIZE).forEach { batch ->
                            val enrichedBatch =
                                supervisorScope {
                                    batch
                                        .map { video ->
                                            async(PerformanceDispatcher.networkIO) {
                                                fetchSubscriptionPlayerMetadata(video, DURATION_METADATA_TIMEOUT_MS)
                                            }
                                        }.awaitAll()
                                }
                            withContext(Dispatchers.Main.immediate) {
                                val attemptedAt = System.currentTimeMillis()
                                batch.forEach { video ->
                                    durationEnrichmentAttemptedAt[video.id] = attemptedAt
                                }
                            }
                            enrichedBatch.filterNotNull().associateByTo(enrichedById) { it.id }
                        }

                        if (enrichedById.isEmpty()) return@launch

                        val mergedVideos =
                            latestFeedVideos
                                .map { video -> enrichedById[video.id]?.let(video::withPlayerMetadata) ?: video }
                                .withHighQualityThumbnails()
                                .withSubscriptionAvatars()

                        latestFeedVideos = mergedVideos
                        updateVideos(mergedVideos)
                        subscriptionFeedRepository.updateEnrichedMetadata(enrichedById.values)
                        Log.d(TAG, "Duration enrichment applied to ${enrichedById.size} subscription videos")
                    } finally {
                        withContext(NonCancellable + Dispatchers.Main.immediate) {
                            if (durationEnrichmentJob === runningJob) {
                                durationEnrichmentJob = null
                                if (hasPendingVisibleEnrichment && visibleVideoIds.isNotEmpty()) {
                                    scheduleVisibleDurationEnrichment()
                                }
                            }
                        }
                    }
                }
        }

        fun selectGroup(groupName: String?) {
            _uiState.update { it.copy(selectedGroupName = groupName) }
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.setSelectedSubscriptionGroup(groupName)
                refreshVisibleFeed()
            }
        }

        fun createGroup(
            name: String,
            channelIds: List<String>,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) { subscriptionGroupDao.appendGroup(name, channelIds) }
        }

        fun updateGroup(
            oldName: String,
            newName: String,
            channelIds: List<String>,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val edited = subscriptionGroupDao.editGroup(oldName, newName, channelIds)
                if (edited && _uiState.value.selectedGroupName == oldName) {
                    _uiState.update { it.copy(selectedGroupName = newName) }
                    playerPreferences.setSelectedSubscriptionGroup(newName)
                }
            }
        }

        fun deleteGroup(name: String) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionGroupDao.deleteGroup(name)
                if (_uiState.value.selectedGroupName == name) {
                    selectGroup(null)
                }
            }
        }

        fun reorderGroups(
            fromIndex: Int,
            toIndex: Int,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) { subscriptionGroupDao.moveGroup(fromIndex, toIndex) }
        }

        fun importNewPipeBackup(
            uri: android.net.Uri,
            context: Context,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return@launch
                    parseNewPipeSubscriptionExport(json).forEach { subscriptionRepository.subscribe(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "NewPipe backup import failed", e)
                }
            }
        }

        fun selectChannel(channelId: String?) {
            _uiState.update { it.copy(selectedChannelId = channelId) }
        }

        /** Explicit user refresh: every subscribed channel, regardless of how recently it was fetched. */
        fun refreshFeed() {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                runRefresh(force = true, showLoading = true)
            }
        }

        /** Background top-up: only the channels that have aged out or have a pending upload signal. */
        fun refreshIfStaleOrMissedUploads() {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                runRefresh(force = false, showLoading = false)
            }
        }

        /** Re-runs only the channels the last refresh could not reach. */
        fun retryFailedChannels() {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val failed = _uiState.value.failedChannelIds
                if (failed.isEmpty()) return@launch
                _uiState.update { it.copy(failedChannelIds = emptySet(), failedChannelReasons = emptyMap()) }
                runRefresh(
                    plan = SubscriptionRefreshPlan(channelIds = failed.toList(), isFullRefresh = false),
                    showLoading = true,
                )
            }
        }

        fun dismissFailedChannels() {
            _uiState.update { it.copy(failedChannelIds = emptySet(), failedChannelReasons = emptyMap()) }
        }

        fun unsubscribe(channelId: String) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionRepository.unsubscribe(channelId)
            }
        }

        fun updateNotificationState(
            channelId: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionRepository.updateNotificationState(channelId, enabled)
            }
        }

        fun setShortsChannelExcluded(
            channelId: String,
            excluded: Boolean,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.setSubscriptionShortsChannelExcluded(channelId, excluded)
            }
        }

        fun toggleViewMode() {
            val newValue = !_uiState.value.isFullWidthView
            _uiState.update { it.copy(isFullWidthView = newValue) }
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.setSubsFullWidthView(newValue)
            }
        }

        fun setSortMode(mode: SubscriptionSortMode) {
            _uiState.update { it.copy(sortMode = mode) }
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.setSubsSortMode(mode.name)
            }
        }

        /**
         * Get a single subscription snapshot (suspend)
         */
        suspend fun getSubscriptionOnce(channelId: String): ChannelSubscription? =
            subscriptionRepository.getSubscription(channelId).firstOrNull()

        /**
         * Subscribe a channel (used for undo)
         */
        fun subscribeChannel(channel: ChannelSubscription) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionRepository.subscribe(channel)
            }
        }
    }
