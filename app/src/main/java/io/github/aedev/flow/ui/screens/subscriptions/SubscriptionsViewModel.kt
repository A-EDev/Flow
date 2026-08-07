package io.github.aedev.flow.ui.screens.subscriptions

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionRefreshPlan
import io.github.aedev.flow.data.subscriptions.withHighQualityThumbnails
import io.github.aedev.flow.data.subscriptions.withRelativeUploadDates
import io.github.aedev.flow.data.subscriptions.withStableUploadSortKeys
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel
    @Inject
    constructor(
        private val subscriptionRepository: SubscriptionRepository,
        private val subscriptionFeedRepository: SubscriptionFeedRepository,
        private val viewHistory: ViewHistory,
        private val database: AppDatabase,
        private val playerPreferences: PlayerPreferences,
        private val subscriptionGroupDao: SubscriptionGroupDao,
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
                playerPreferences.shortsShelfEnabled.collect { enabled ->
                    _uiState.update { it.copy(isShortsShelfEnabled = enabled) }
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                combine(
                    playerPreferences.subscriptionShowVideos,
                    playerPreferences.subscriptionShowShorts,
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
                combine(
                    viewHistory.getVideoHistoryFlow(),
                    playerPreferences.hideWatchedVideosFromSubscriptions,
                    playerPreferences.watchedThreshold,
                    database.downloadDao().getVideoDownloads(),
                ) { history, hideWatched, threshold, downloads ->
                    if (!hideWatched) return@combine emptySet<String>()
                    val downloadedIds = downloads.mapTo(HashSet()) { it.download.videoId }
                    history
                        .asSequence()
                        .filter { threshold.isWatched(it.position, it.duration) || it.videoId in downloadedIds }
                        .map { it.videoId }
                        .toHashSet()
                }.distinctUntilChanged()
                    .collect { ids ->
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
                subscriptionFeedRepository.observeFeed().collect { videos ->
                    latestFeedVideos = videos
                    updateVideos(videos)
                }
            }

            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                while (true) {
                    delay(RELATIVE_TIME_TICK_MS)
                    refreshVisibleFeed()
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
            val sortNow = System.currentTimeMillis()
            val unplayableIds = unplayableVideoIds
            val sortedVideos =
                videos
                    .withHighQualityThumbnails()
                    .withSubscriptionAvatars()
                    .filter { video -> video.id !in unplayableIds }
                    .filter { video ->
                        when {
                            video.isShort -> _uiState.value.showSubscriptionShorts
                            video.isLive -> _uiState.value.showSubscriptionLive
                            else -> _uiState.value.showSubscriptionVideos
                        }
                    }.withStableUploadSortKeys(sortNow)

            val (shorts, regular) = sortedVideos.partition { video -> video.isShort }

            // ── 1 short per channel (most recent first) ──────────────────
            val latestShortPerChannel =
                shorts
                    .groupBy { it.channelId }
                    .flatMap { (_, channelShorts) -> channelShorts.withStableUploadSortKeys(sortNow).take(1) }
                    .withStableUploadSortKeys(sortNow)

            val watchedIds = watchedVideoIds
            val unwatchedShorts =
                if (watchedIds.isNotEmpty()) {
                    latestShortPerChannel.filter { it.id !in watchedIds }
                } else {
                    latestShortPerChannel
                }

            val filteredRegular =
                if (watchedIds.isNotEmpty()) {
                    regular.filter { it.id !in watchedIds }
                } else {
                    regular
                }

            val selectedGroup = _uiState.value.selectedGroupName
            val allowedChannelIds: Set<String>? =
                if (selectedGroup != null) {
                    _uiState.value.groups
                        .find { it.name == selectedGroup }
                        ?.channelIds
                        ?.toHashSet()
                } else {
                    null
                }

            val groupFilteredRegular =
                if (allowedChannelIds != null) {
                    filteredRegular.filter { it.channelId in allowedChannelIds }
                } else {
                    filteredRegular
                }

            val groupFilteredShorts =
                (
                    if (allowedChannelIds != null) {
                        unwatchedShorts.filter { it.channelId in allowedChannelIds }
                    } else {
                        unwatchedShorts
                    }
                ).filter { it.channelId !in excludedShortsChannelIds }

            _uiState.update {
                it.copy(
                    recentVideos = groupFilteredRegular.withRelativeUploadDates(sortNow),
                    shorts = groupFilteredShorts.withRelativeUploadDates(sortNow),
                )
            }
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
                                                fetchDurationFromPlayerMetadata(video)
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
                                .map { video ->
                                    enrichedById[video.id]?.let { enriched ->
                                        video.copy(
                                            title = enriched.title.takeIf { it.isNotBlank() } ?: video.title,
                                            channelName = enriched.channelName.takeIf { it.isNotBlank() } ?: video.channelName,
                                            channelId = enriched.channelId.takeIf { it.isNotBlank() } ?: video.channelId,
                                            thumbnailUrl = enriched.thumbnailUrl.takeIf { it.isNotBlank() } ?: video.thumbnailUrl,
                                            duration = enriched.duration.takeIf { it > 0 } ?: video.duration,
                                            viewCount = maxOf(video.viewCount, enriched.viewCount),
                                            isLive = video.isLive || enriched.isLive,
                                        )
                                    } ?: video
                                }.withHighQualityThumbnails()
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

        private suspend fun fetchDurationFromPlayerMetadata(video: Video): Video? =
            withTimeoutOrNull(DURATION_METADATA_TIMEOUT_MS) {
                val response =
                    YouTube.player(video.id, client = YouTubeClient.ANDROID).getOrNull()
                        ?: YouTube.player(video.id, client = YouTubeClient.MOBILE).getOrNull()
                        ?: return@withTimeoutOrNull null
                val details = response.videoDetails ?: return@withTimeoutOrNull null
                val isLive = details.isLive == true || details.isLiveContent == true
                val duration = details.lengthSeconds.toIntOrNull()?.takeIf { it > 0 } ?: 0
                if (!isLive && duration <= 0) return@withTimeoutOrNull null

                val bestThumbnail =
                    details.thumbnail
                        ?.thumbnails
                        ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
                        ?.url
                        ?.let { ThumbnailUrlResolver.normalizeVideoThumbnail(video.id, it) }
                        ?: ThumbnailUrlResolver.normalizeVideoThumbnail(video.id, video.thumbnailUrl)

                video.copy(
                    title = details.title?.takeIf { it.isNotBlank() } ?: video.title,
                    channelName = details.author?.takeIf { it.isNotBlank() } ?: video.channelName,
                    channelId = details.channelId.takeIf { it.isNotBlank() } ?: video.channelId,
                    thumbnailUrl = bestThumbnail,
                    duration = if (isLive) 0 else duration,
                    viewCount = maxOf(video.viewCount, details.viewCount?.toLongOrNull() ?: 0L),
                    isLive = video.isLive || isLive,
                )
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
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val nextOrder = subscriptionGroupDao.getAllGroupsOnce().size
                subscriptionGroupDao.insertGroup(
                    SubscriptionGroupEntity(
                        name = name,
                        channelIds = channelIds.joinToString(","),
                        sortOrder = nextOrder,
                    ),
                )
            }
        }

        fun updateGroup(
            oldName: String,
            newName: String,
            channelIds: List<String>,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val existing = subscriptionGroupDao.getAllGroupsOnce().find { it.name == oldName }
                if (existing != null) {
                    if (oldName != newName) {
                        subscriptionGroupDao.deleteGroup(oldName)
                        subscriptionGroupDao.insertGroup(
                            existing.copy(name = newName, channelIds = channelIds.joinToString(",")),
                        )
                    } else {
                        subscriptionGroupDao.updateGroup(
                            existing.copy(channelIds = channelIds.joinToString(",")),
                        )
                    }
                    if (_uiState.value.selectedGroupName == oldName) {
                        _uiState.update { it.copy(selectedGroupName = newName) }
                        playerPreferences.setSelectedSubscriptionGroup(newName)
                    }
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

        fun moveGroup(
            name: String,
            direction: Int,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val groups = subscriptionGroupDao.getAllGroupsOnce().toMutableList()
                val currentIndex = groups.indexOfFirst { it.name == name }
                val targetIndex = (currentIndex + direction).coerceIn(0, groups.lastIndex)
                if (currentIndex < 0 || currentIndex == targetIndex) return@launch

                val moved = groups.removeAt(currentIndex)
                groups.add(targetIndex, moved)
                subscriptionGroupDao.insertAll(groups.mapIndexed { index, group -> group.copy(sortOrder = index) })
            }
        }

        fun importNewPipeBackup(
            uri: android.net.Uri,
            context: Context,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val jsonString = inputStream.bufferedReader().use { it.readText() }
                        val jsonObject = org.json.JSONObject(jsonString)

                        if (jsonObject.has("subscriptions")) {
                            val subscriptionsArray = jsonObject.getJSONArray("subscriptions")

                            for (i in 0 until subscriptionsArray.length()) {
                                val item = subscriptionsArray.getJSONObject(i)
                                val url = item.optString("url")
                                val name = item.optString("name")

                                if (url.isNotEmpty() && name.isNotEmpty()) {
                                    var channelId = ""
                                    if (url.contains("/channel/")) {
                                        channelId = url.substringAfter("/channel/")
                                    } else if (url.contains("/user/")) {
                                        channelId = url.substringAfter("/user/")
                                    }
                                    if (channelId.contains("/")) channelId = channelId.substringBefore("/")
                                    if (channelId.contains("?")) channelId = channelId.substringBefore("?")

                                    if (channelId.isNotEmpty()) {
                                        subscriptionRepository.subscribe(
                                            ChannelSubscription(
                                                channelId = channelId,
                                                channelName = name,
                                                channelThumbnail = "", // Will load lazily or show placeholder
                                                subscribedAt = System.currentTimeMillis(),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                _uiState.update { it.copy(failedChannelIds = emptySet()) }
                runRefresh(
                    plan = SubscriptionRefreshPlan(channelIds = failed.toList(), isFullRefresh = false),
                    showLoading = true,
                )
            }
        }

        fun dismissFailedChannels() {
            _uiState.update { it.copy(failedChannelIds = emptySet()) }
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

enum class SubscriptionSortMode {
    DEFAULT,
    NAME_ASC,
    RECENTLY_UPDATED,
    ;

    companion object {
        fun fromStorage(value: String?): SubscriptionSortMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

data class SubscriptionsUiState(
    val subscribedChannels: List<Channel> = emptyList(),
    val recentVideos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val selectedChannelId: String? = null,
    val isLoading: Boolean = false,
    val isFullWidthView: Boolean = false,
    val sortMode: SubscriptionSortMode = SubscriptionSortMode.DEFAULT,
    val isShortsShelfEnabled: Boolean = true,
    val notificationStates: Map<String, Boolean> = emptyMap(),
    val groups: List<SubscriptionGroup> = emptyList(),
    val selectedGroupName: String? = null,
    val refreshProcessedChannels: Int = 0,
    val refreshTotalChannels: Int = 0,
    val lastRefreshTime: Long = 0L,
    val lastRefreshText: String? = null,
    val lastRefreshVideoCount: Int = 0,
    val showLastRefreshVideoCount: Boolean = true,
    val showSubscriptionVideos: Boolean = true,
    val showSubscriptionShorts: Boolean = true,
    val showSubscriptionLive: Boolean = true,
    val excludedShortsChannelIds: Set<String> = emptySet(),
    /** Channels the last refresh could not reach at all; surfaced instead of silently showing less. */
    val failedChannelIds: Set<String> = emptySet(),
) {
    /** Display names for [failedChannelIds], falling back to the raw id for an unknown channel. */
    val failedChannelNames: List<String>
        get() {
            if (failedChannelIds.isEmpty()) return emptyList()
            val namesById = subscribedChannels.associate { it.id to it.name }
            return failedChannelIds
                .map { id -> namesById[id]?.takeIf { it.isNotBlank() } ?: id }
                .sorted()
        }
}

data class SubscriptionGroup(
    val name: String,
    val channelIds: List<String>,
    val sortOrder: Int = 0,
)

fun SubscriptionGroupEntity.toUiModel() =
    SubscriptionGroup(
        name = name,
        channelIds = if (channelIds.isBlank()) emptyList() else channelIds.split(",").filter { it.isNotBlank() },
        sortOrder = sortOrder,
    )
