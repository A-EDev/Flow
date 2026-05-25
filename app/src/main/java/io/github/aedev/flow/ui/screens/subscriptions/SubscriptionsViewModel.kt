package io.github.aedev.flow.ui.screens.subscriptions

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.room.withTransaction

class SubscriptionsViewModel : ViewModel() {
    companion object {
        private const val TAG = "SubsViewModel"
        /**
         * How old the subscription-feed cache may be before a background refresh is triggered.
         * 4 hours — balances freshness with avoiding an RSS fetch on every screen visit.
         */
        private const val FEED_CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
        private const val SOFT_REFRESH_MAX_AGE_MS = 10 * 60 * 1000L // 10 minutes
        private const val SUBSCRIPTION_CACHE_WINDOW_MS = 60L * 24L * 60L * 60L * 1000L
        private const val MAX_SUBSCRIPTION_CACHE_ITEMS = 600
        private const val SUSPICIOUS_FRESH_TIMESTAMP_MS = 5L * 60L * 1000L
        private const val RELATIVE_TIME_TICK_MS = 60L * 1000L
    }

    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var viewHistory: ViewHistory
    
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    private val ytRepository: YouTubeRepository = YouTubeRepository.getInstance()
    private lateinit var cacheDao: io.github.aedev.flow.data.local.dao.CacheDao
    private lateinit var database: AppDatabase
    private lateinit var playerPreferences: PlayerPreferences
    private lateinit var subscriptionGroupDao: SubscriptionGroupDao
    private var isInitialized = false
    private var isNetworkFetchRunning = false
    private var latestFeedVideos: List<Video> = emptyList()
    private var watchedVideoIds: Set<String> = emptySet()
    private var excludedShortsChannelIds: Set<String> = emptySet()

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        subscriptionRepository = SubscriptionRepository.getInstance(context)
        playerPreferences = PlayerPreferences(context)
        viewHistory = ViewHistory.getInstance(context)
        database = AppDatabase.getDatabase(context)
        cacheDao = database.cacheDao()
        subscriptionGroupDao = database.subscriptionGroupDao()
        
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
                playerPreferences.subscriptionShowLive
            ) { showVideos, showShorts, showLive ->
                Triple(showVideos, showShorts, showLive)
            }
                .distinctUntilChanged()
                .collect { (showVideos, showShorts, showLive) ->
                    _uiState.update {
                        it.copy(
                            showSubscriptionVideos = showVideos,
                            showSubscriptionShorts = showShorts,
                            showSubscriptionLive = showLive
                        )
                    }
                    if (latestFeedVideos.isNotEmpty()) {
                        updateVideos(latestFeedVideos)
                    }
                }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            playerPreferences.subscriptionShortsExcludedChannels
                .distinctUntilChanged()
                .collect { ids ->
                    excludedShortsChannelIds = ids
                    _uiState.update { it.copy(excludedShortsChannelIds = ids) }
                    if (latestFeedVideos.isNotEmpty()) {
                        updateVideos(latestFeedVideos)
                    }
                }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            playerPreferences.subsFullWidthView.collect { fullWidth ->
                _uiState.update { it.copy(isFullWidthView = fullWidth) }
            }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            playerPreferences.selectedSubscriptionGroup.collect { groupName ->
                _uiState.update { it.copy(selectedGroupName = groupName) }
                if (latestFeedVideos.isNotEmpty()) {
                    updateVideos(latestFeedVideos)
                }
            }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            combine(
                playerPreferences.subscriptionLastRefreshTime,
                playerPreferences.subscriptionLastRefreshedCount
            ) { time, count -> time to count }
                .collect { (time, count) ->
                    _uiState.update {
                        it.copy(
                            lastRefreshTime = time,
                            lastRefreshText = if (time > 0L) formatTimestamp(time) else null,
                            lastRefreshVideoCount = count
                        )
                    }
                }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            viewHistory.getVideoHistoryFlow()
                .combine(playerPreferences.hideWatchedVideos) { history, hideWatched ->
                    if (!hideWatched) return@combine emptySet<String>()
                    history
                        .asSequence()
                        .filter { it.progressPercentage >= 10f }
                        .map { it.videoId }
                        .toHashSet()
                }
                .distinctUntilChanged()
                .collect { ids ->
                    watchedVideoIds = ids
                    if (latestFeedVideos.isNotEmpty()) {
                        updateVideos(latestFeedVideos)
                    }
                }
        }
        
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository.getAllSubscriptions()
                .collect { allSubs ->
                    val notifStates = allSubs.associate { it.channelId to it.isNotificationEnabled }
                    _uiState.update { it.copy(notificationStates = notifStates) }
                }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            cacheDao.getSubscriptionFeed().collect { cachedFeed ->
                Log.d(TAG, "Cache observer: ${cachedFeed.size} entries in DB")
                
                val videos = cachedFeed.map { it.toVideo() }
                Log.d(TAG, "Cache observer: calling updateVideos with ${videos.size} videos")
                latestFeedVideos = videos
                updateVideos(videos)
                
            }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            while (true) {
                delay(RELATIVE_TIME_TICK_MS)
                if (latestFeedVideos.isNotEmpty()) {
                    updateVideos(latestFeedVideos)
                }
            }
        }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            subscriptionRepository.getAllSubscriptions()
                .map { subs -> subs.map { it.channelId }.sorted() }
                .distinctUntilChanged()
                .collect { channelIds ->
                    Log.i(TAG, "Channel IDs changed: ${channelIds.size} channels \u2014 triggering fetch")

                    val allSubs = subscriptionRepository.getAllSubscriptions().first()
                    val channels = allSubs.map { sub ->
                        Channel(
                            id = sub.channelId,
                            name = sub.channelName,
                            thumbnailUrl = sub.channelThumbnail,
                            subscriberCount = 0L,
                            isSubscribed = true,
                            isMusic = sub.isMusic
                        )
                    }
                    _uiState.update { it.copy(subscribedChannels = channels) }
                    if (latestFeedVideos.isNotEmpty()) {
                        updateVideos(latestFeedVideos)
                    }

                    if (channels.isNotEmpty()) {
                        if (_uiState.value.recentVideos.isEmpty()) {
                            _uiState.update { it.copy(isLoading = true) }
                        }

                        // ── Cache-age gate ─────────────────────────────────────────────────
                        val cacheCount   = cacheDao.getSubscriptionFeedCount()
                        val latestCachedAt = cacheDao.getLatestCachedAt() ?: 0L
                        val cacheAgeMs   = System.currentTimeMillis() - latestCachedAt
                        val isCacheStale = cacheCount == 0 || cacheAgeMs > FEED_CACHE_TTL_MS
                        val hasNewUploadSignal = hasNewUploadSignalSinceCache(latestCachedAt)

                        if (isCacheStale || hasNewUploadSignal) {
                            Log.i(
                                TAG,
                                "Refreshing subscriptions feed (stale=$isCacheStale, newSignal=$hasNewUploadSignal, age=${cacheAgeMs / 60_000}min, rows=$cacheCount)"
                            )
                            fetchAndCacheSubscriptionFeed(
                                channelIds = channels.map { it.id },
                                showLoading = true
                            )
                        } else {
                            Log.i(TAG, "Feed cache is fresh (age=${cacheAgeMs / 60_000}min, rows=$cacheCount) — skipping network fetch")
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    } else {
                        Log.w(TAG, "No channels \u2014 skipping fetch")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
        }

    }

    private suspend fun fetchAndCacheSubscriptionFeed(
        channelIds: List<String>,
        showLoading: Boolean
    ) {
        if (channelIds.isEmpty()) return
        if (isNetworkFetchRunning) {
            Log.d(TAG, "Skip fetch: another subscription fetch is running")
            return
        }

        isNetworkFetchRunning = true
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }

        try {
            val cachedBeforeFetch = withContext(PerformanceDispatcher.diskIO) {
                cacheDao.getSubscriptionFeed().first().map { it.toVideo() }
            }
            var refreshPreviewVideos = mergeSubscriptionFeed(
                freshVideos = latestFeedVideos,
                cachedVideos = cachedBeforeFetch,
                now = System.currentTimeMillis()
            )
            val cachedVideoIds = cachedBeforeFetch.map { it.id }.toHashSet()
            var finalVideos: List<Video> = emptyList()
            io.github.aedev.flow.data.innertube.RssSubscriptionService.fetchSubscriptionVideos(
                channelIds = channelIds,
                maxTotal = 600,
                knownVideoIds = cachedVideoIds,
                onProgress = { processed, total ->
                    _uiState.update {
                        it.copy(
                            refreshProcessedChannels = processed,
                            refreshTotalChannels = total
                        )
                    }
                }
            ).collect { videos ->
                Log.i(TAG, "Network emit received: ${videos.size} videos (shorts=${videos.count { it.isShort }}, regular=${videos.count { !it.isShort }})")
                if (videos.isNotEmpty()) {
                    finalVideos = videos
                    val previewVideos = mergeSubscriptionFeed(
                        freshVideos = videos,
                        cachedVideos = refreshPreviewVideos,
                        now = System.currentTimeMillis()
                    ).withHighQualityThumbnails().withSubscriptionAvatars()
                    refreshPreviewVideos = previewVideos
                    updateVideos(previewVideos)
                } else {
                    Log.w(TAG, "Network emit was empty!")
                }
            }
            val refreshTime = System.currentTimeMillis()
            if (finalVideos.isNotEmpty()) {
                val mergedVideos = mergeSubscriptionFeed(
                    freshVideos = finalVideos,
                    cachedVideos = refreshPreviewVideos.ifEmpty { cachedBeforeFetch },
                    now = refreshTime
                ).withHighQualityThumbnails().withSubscriptionAvatars()
                val entities = mergedVideos.map { video -> video.toSubscriptionFeedEntity(refreshTime) }
                withContext(PerformanceDispatcher.diskIO) {
                    database.withTransaction {
                        cacheDao.clearSubscriptionFeed()
                        cacheDao.insertSubscriptionFeed(entities)
                    }
                    playerPreferences.setSubscriptionLastRefresh(refreshTime, mergedVideos.size)
                }
                latestFeedVideos = mergedVideos
                updateVideos(mergedVideos)
            } else if (cachedBeforeFetch.isNotEmpty()) {
                withContext(PerformanceDispatcher.diskIO) {
                    playerPreferences.setSubscriptionLastRefresh(refreshTime, cachedBeforeFetch.size)
                }
            }
        } finally {
            if (showLoading) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        refreshProcessedChannels = 0,
                        refreshTotalChannels = 0
                    )
                }
            }
            isNetworkFetchRunning = false
        }
    }

    private suspend fun hasNewUploadSignalSinceCache(latestCachedAt: Long): Boolean {
        if (latestCachedAt <= 0L) return true
        val latestSignal = subscriptionRepository.getAllSubscriptions().first()
            .maxOfOrNull { it.lastCheckTime } ?: 0L
        return latestSignal > latestCachedAt
    }



    private fun mergeSubscriptionFeed(
        freshVideos: List<Video>,
        cachedVideos: List<Video>,
        now: Long
    ): List<Video> {
        val cutoff = now - SUBSCRIPTION_CACHE_WINDOW_MS
        return (freshVideos + cachedVideos)
            .asSequence()
            .filter { video -> effectiveUploadTimestamp(video, now) >= cutoff || video.isUpcoming }
            .toList()
            .groupBy { it.id }
            .values
            .map { candidates -> mergeDuplicateSubscriptionVideo(candidates, now) }
            .withStableUploadSortKeys(now)
            .take(MAX_SUBSCRIPTION_CACHE_ITEMS)
    }

    private fun mergeDuplicateSubscriptionVideo(candidates: List<Video>, now: Long): Video {
        val primary = candidates.first()
        val metadataSource = when {
            primary.hasStableUploadMetadata(now) -> primary
            else -> candidates.firstOrNull { it.hasStableUploadMetadata(now) } ?: primary
        }
        val hasStableMetadata = metadataSource.hasStableUploadMetadata(now)
        val metadataTimestamp = effectiveUploadTimestamp(metadataSource, now)
            .takeIf { hasStableMetadata && it > 0L }
        val isFutureUpcoming = candidates.any { candidate ->
            candidate.isUpcoming && effectiveUploadTimestamp(candidate, now) > now + 60_000L
        }
        val bestChannelThumbnail = candidates.firstOrNull { it.channelThumbnailUrl.isNotBlank() }?.channelThumbnailUrl
            ?: primary.channelThumbnailUrl
        val bestVideoThumbnail = candidates
            .asSequence()
            .map { ThumbnailUrlResolver.normalizeVideoThumbnail(it.id, it.thumbnailUrl) }
            .firstOrNull { it.isNotBlank() }
            ?: ThumbnailUrlResolver.normalizeVideoThumbnail(primary.id, primary.thumbnailUrl)
        val bestDescription = candidates.firstOrNull { it.description.isNotBlank() }?.description
            ?: primary.description

        return primary.copy(
            viewCount = candidates.maxOf { it.viewCount },
            thumbnailUrl = bestVideoThumbnail,
            uploadDate = if (hasStableMetadata) metadataSource.uploadDate else "",
            timestamp = metadataTimestamp ?: 0L,
            duration = candidates.maxOf { it.duration },
            description = bestDescription,
            channelThumbnailUrl = bestChannelThumbnail,
            isShort = candidates.any { it.isShort },
            isLive = candidates.any { it.isLive },
            isUpcoming = isFutureUpcoming
        )
    }

    private suspend fun updateVideos(videos: List<Video>) {
        val sortNow = System.currentTimeMillis()
        val sortedVideos = videos.withHighQualityThumbnails().withSubscriptionAvatars()
            .filter { video ->
                when {
                    video.isShort -> _uiState.value.showSubscriptionShorts
                    video.isLive -> _uiState.value.showSubscriptionLive
                    else -> _uiState.value.showSubscriptionVideos
                }
            }
            .withStableUploadSortKeys(sortNow)

        val (shorts, regular) = sortedVideos.partition { video -> video.isShort }
        Log.i(TAG, "updateVideos: total=${sortedVideos.size} → regular=${regular.size}, shorts=${shorts.size}")

        // ── 1 short per channel (most recent first) ──────────────────
        val latestShortPerChannel = shorts
            .groupBy { it.channelId }
            .flatMap { (_, channelShorts) -> channelShorts.withStableUploadSortKeys(sortNow).take(1) }
            .withStableUploadSortKeys(sortNow)
        Log.i(TAG, "Shorts after per-channel dedup: ${latestShortPerChannel.size}/${shorts.size}")

        val watchedIds = watchedVideoIds
        val unwatchedShorts = if (watchedIds.isNotEmpty()) {
            latestShortPerChannel.filter { it.id !in watchedIds }
        } else {
            latestShortPerChannel
        }

        Log.i(TAG, "Shorts after watched filter: ${unwatchedShorts.size}/${latestShortPerChannel.size} " +
                "(${latestShortPerChannel.size - unwatchedShorts.size} hidden as already watched)")

        val filteredRegular = if (watchedIds.isNotEmpty()) {
            val before = regular.size
            regular.filter { it.id !in watchedIds }.also { filtered ->
                Log.i(TAG, "Regular videos after watched filter: ${filtered.size}/$before " +
                        "(${before - filtered.size} hidden as already watched)")
            }
        } else {
            regular
        }

        val selectedGroup = _uiState.value.selectedGroupName
        val allowedChannelIds: Set<String>? = if (selectedGroup != null) {
            _uiState.value.groups.find { it.name == selectedGroup }?.channelIds?.toHashSet()
        } else {
            null
        }

        val groupFilteredRegular = if (allowedChannelIds != null) {
            filteredRegular.filter { it.channelId in allowedChannelIds }
        } else {
            filteredRegular
        }

        val groupFilteredShorts = (if (allowedChannelIds != null) {
            unwatchedShorts.filter { it.channelId in allowedChannelIds }
        } else {
            unwatchedShorts
        }).filter { it.channelId !in excludedShortsChannelIds }

        _uiState.update {
            it.copy(
                recentVideos = groupFilteredRegular.withRelativeUploadDates(sortNow),
                shorts = groupFilteredShorts.withRelativeUploadDates(sortNow)
            )
        }
    }

    private fun List<Video>.withHighQualityThumbnails(): List<Video> =
        map { video ->
            video.copy(
                thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(video.id, video.thumbnailUrl)
            )
        }

    private fun List<Video>.withSubscriptionAvatars(): List<Video> {
        val avatarByChannelId = _uiState.value.subscribedChannels
            .asSequence()
            .filter { it.thumbnailUrl.isNotBlank() }
            .associate { it.id to it.thumbnailUrl }
        if (avatarByChannelId.isEmpty()) return this

        return map { video ->
            if (video.channelThumbnailUrl.isBlank()) {
                avatarByChannelId[video.channelId]?.let { avatar ->
                    video.copy(channelThumbnailUrl = avatar)
                } ?: video
            } else {
                video
            }
        }
    }
    

    fun selectGroup(groupName: String?) {
        _uiState.update { it.copy(selectedGroupName = groupName) }
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            playerPreferences.setSelectedSubscriptionGroup(groupName)
            val cached = cacheDao.getSubscriptionFeed().first()
            if (cached.isNotEmpty()) {
                val videos = cached.map { it.toVideo() }
                latestFeedVideos = videos
                updateVideos(videos)
            }
        }
    }

    fun createGroup(name: String, channelIds: List<String>) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            val nextOrder = subscriptionGroupDao.getAllGroupsOnce().size
            subscriptionGroupDao.insertGroup(
                SubscriptionGroupEntity(
                    name = name,
                    channelIds = channelIds.joinToString(","),
                    sortOrder = nextOrder
                )
            )
        }
    }

    fun updateGroup(oldName: String, newName: String, channelIds: List<String>) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            val existing = subscriptionGroupDao.getAllGroupsOnce().find { it.name == oldName }
            if (existing != null) {
                if (oldName != newName) {
                    subscriptionGroupDao.deleteGroup(oldName)
                    subscriptionGroupDao.insertGroup(
                        existing.copy(name = newName, channelIds = channelIds.joinToString(","))
                    )
                } else {
                    subscriptionGroupDao.updateGroup(
                        existing.copy(channelIds = channelIds.joinToString(","))
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

    fun moveGroup(name: String, direction: Int) {
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

    private fun List<Video>.withStableUploadSortKeys(now: Long): List<Video> =
        map { video -> SortableVideo(video, effectiveUploadTimestamp(video, now)) }
            .sortedWith(
                compareByDescending<SortableVideo> { it.uploadTimestamp }
                    .thenByDescending { it.video.viewCount }
                    .thenBy { it.video.id }
            )
            .map { it.video }

    private fun List<Video>.withRelativeUploadDates(now: Long): List<Video> =
        map { video ->
            val uploadTimestamp = effectiveUploadTimestamp(video, now)
            val isFutureUpcoming = video.isUpcoming && uploadTimestamp > now + 60_000L
            if (isFutureUpcoming) {
                video.copy(isUpcoming = true)
            } else if (uploadTimestamp > 0L) {
                video.copy(
                    uploadDate = formatRelativeTime(uploadTimestamp, now),
                    isUpcoming = false
                )
            } else {
                video.copy(
                    uploadDate = video.uploadDate.takeUnless { isUnstableFreshUploadText(it) }.orEmpty(),
                    isUpcoming = false
                )
            }
        }

    private fun effectiveUploadTimestamp(video: Video, now: Long): Long {
        val parsedRelative = parseRelativeTime(video.uploadDate, now)
        val timestamp = video.timestamp
        val timestampLooksLikeFallbackNow =
            timestamp in (now - SUSPICIOUS_FRESH_TIMESTAMP_MS)..(now + SUSPICIOUS_FRESH_TIMESTAMP_MS)
        val relativeDateIsClearlyOlder =
            parsedRelative != null && parsedRelative < now - SUSPICIOUS_FRESH_TIMESTAMP_MS

        return when {
            timestamp <= 0L -> parsedRelative ?: 0L
            timestampLooksLikeFallbackNow && isUnstableFreshUploadText(video.uploadDate) -> parsedRelative ?: 0L
            timestampLooksLikeFallbackNow && relativeDateIsClearlyOlder -> parsedRelative ?: timestamp
            else -> timestamp
        }
    }

    private fun Video.hasStableUploadMetadata(now: Long): Boolean {
        val text = uploadDate.trim().lowercase()
        if (isLive || isUpcoming) return true
        if (timestamp <= 0L) return false
        val timestampLooksLikeFallbackNow =
            timestamp in (now - SUSPICIOUS_FRESH_TIMESTAMP_MS)..(now + SUSPICIOUS_FRESH_TIMESTAMP_MS)
        if (timestampLooksLikeFallbackNow && isUnstableFreshUploadText(text)) {
            return false
        }
        return true
    }

    private fun isUnstableFreshUploadText(value: String): Boolean {
        val text = value.trim().lowercase()
        if (text.isBlank() || text == "unknown" || text == "just now" || text == "today") return true
        return text.matches(Regex("""\d+\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours)(\s+ago)?"""))
    }

    private fun formatRelativeTime(timestamp: Long, now: Long): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        val seconds = diff / 1000L
        val minutes = seconds / 60L
        val hours = minutes / 60L
        val days = hours / 24L
        val weeks = days / 7L
        val months = days / 30L
        val years = days / 365L

        return when {
            years > 0 -> "${years}y ago"
            months > 0 -> "${months}mo ago"
            weeks > 0 -> "${weeks}w ago"
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            seconds > 10 -> "${seconds}s ago"
            else -> "Just now"
        }
    }

    private data class SortableVideo(
        val video: Video,
        val uploadTimestamp: Long
    )

    private fun parseRelativeTime(dateString: String, now: Long): Long? {
        try {
            val text = dateString.lowercase().trim()
            if (text.isBlank() || text == "unknown") return null
            
            if (text.contains("scheduled") || text.contains("premiere")) return now + 86400000L
            if (text.contains("live")) return now + 3600000L // Boost live streams
            
            val parts = text.split(" ")
            val valueLine = parts.firstOrNull { it.any { c -> c.isDigit() } } 
            val value = valueLine?.filter { it.isDigit() }?.toLongOrNull() ?: 1L
            
            val multiplier = when {
                text.contains("second") || text.endsWith("s ago") || text.matches(Regex("\\d+s")) -> 1000L
                text.contains("minute") || text.endsWith("m ago") || text.matches(Regex("\\d+m")) -> 60000L
                text.contains("hour") || text.endsWith("h ago") || text.matches(Regex("\\d+h")) -> 3600000L
                text.contains("day") || text.endsWith("d ago") || text.matches(Regex("\\d+d")) -> 86400000L
                text.contains("week") || text.endsWith("w ago") || text.matches(Regex("\\d+w")) -> 604800000L
                text.contains("month") || text.contains("mo ago") || text.matches(Regex("\\d+mo")) -> 2592000000L
                text.contains("year") || text.endsWith("y ago") || text.matches(Regex("\\d+y")) -> 31536000000L
                else -> return null
            }
            
            return now - (value * multiplier)
        } catch (e: Exception) {
            return null
        }
    }
    
    fun importNewPipeBackup(uri: android.net.Uri, context: Context) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(jsonString)
                    
                    if (jsonObject.has("subscriptions")) {
                        val subscriptionsArray = jsonObject.getJSONArray("subscriptions")
                        var importedCount = 0
                        
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
                                    val subscription = ChannelSubscription(
                                        channelId = channelId,
                                        channelName = name,
                                        channelThumbnail = "", // Will load lazily or show placeholder
                                        subscribedAt = System.currentTimeMillis()
                                    )
                                    subscriptionRepository.subscribe(subscription)
                                    importedCount++
                                }
                            }
                        }
                        // Refresh subs
                        if (importedCount > 0) {
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun selectChannel(channelId: String?) {
        _uiState.update { it.copy(selectedChannelId = channelId) }
    }

    
    fun refreshFeed() {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val channels = _uiState.value.subscribedChannels
            if (channels.isEmpty() || isNetworkFetchRunning) {
                _uiState.update { it.copy(isLoading = true) }
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            fetchAndCacheSubscriptionFeed(
                channelIds = channels.map { it.id },
                showLoading = true
            )
        }
    }

    fun refreshIfStaleOrMissedUploads(maxAgeMs: Long = SOFT_REFRESH_MAX_AGE_MS) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val channels = _uiState.value.subscribedChannels
            if (channels.isEmpty()) return@launch
            if (isNetworkFetchRunning) return@launch

            val cacheCount = cacheDao.getSubscriptionFeedCount()
            val latestCachedAt = cacheDao.getLatestCachedAt() ?: 0L
            val cacheAgeMs = System.currentTimeMillis() - latestCachedAt
            val staleByAge = cacheCount == 0 || cacheAgeMs > maxAgeMs
            val hasNewUploadSignal = hasNewUploadSignalSinceCache(latestCachedAt)

            if (staleByAge || hasNewUploadSignal) {
                Log.i(
                    TAG,
                    "Soft refresh triggered (staleByAge=$staleByAge, newSignal=$hasNewUploadSignal, age=${cacheAgeMs / 60_000}min)"
                )
                fetchAndCacheSubscriptionFeed(
                    channelIds = channels.map { it.id },
                    showLoading = false
                )
            }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository.unsubscribe(channelId)
        }
    }

    fun updateNotificationState(channelId: String, enabled: Boolean) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository.updateNotificationState(channelId, enabled)
        }
    }

    fun setShortsChannelExcluded(channelId: String, excluded: Boolean) {
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

    /**
     * Get a single subscription snapshot (suspend)
     */
    suspend fun getSubscriptionOnce(channelId: String): ChannelSubscription? {
        return subscriptionRepository.getSubscription(channelId).firstOrNull()
    }

    /**
     * Subscribe a channel (used for undo)
     */
    fun subscribeChannel(channel: ChannelSubscription) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository.subscribe(channel)
            refreshFeed()
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            else -> "Just now"
        }
    }
}

data class SubscriptionsUiState(
    val subscribedChannels: List<Channel> = emptyList(),
    val recentVideos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val selectedChannelId: String? = null,
    val isLoading: Boolean = false,
    val isFullWidthView: Boolean = false,
    val isShortsShelfEnabled: Boolean = true,
    val notificationStates: Map<String, Boolean> = emptyMap(),
    val groups: List<SubscriptionGroup> = emptyList(),
    val selectedGroupName: String? = null,
    val refreshProcessedChannels: Int = 0,
    val refreshTotalChannels: Int = 0,
    val lastRefreshTime: Long = 0L,
    val lastRefreshText: String? = null,
    val lastRefreshVideoCount: Int = 0,
    val showSubscriptionVideos: Boolean = true,
    val showSubscriptionShorts: Boolean = true,
    val showSubscriptionLive: Boolean = true,
    val excludedShortsChannelIds: Set<String> = emptySet()
)

data class SubscriptionGroup(
    val name: String,
    val channelIds: List<String>,
    val sortOrder: Int = 0
)

fun SubscriptionGroupEntity.toUiModel() = SubscriptionGroup(
    name = name,
    channelIds = if (channelIds.isBlank()) emptyList() else channelIds.split(",").filter { it.isNotBlank() },
    sortOrder = sortOrder
)

private fun io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity.toVideo() = Video(
    id = videoId,
    title = title,
    channelName = channelName,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
    duration = duration,
    viewCount = viewCount,
    uploadDate = uploadDate,
    timestamp = timestamp,
    channelThumbnailUrl = channelThumbnailUrl,
    isShort = isShort,
    isLive = isLive && uploadDate.containsLiveMarker(),
    isUpcoming = isUpcoming
)

private fun Video.toSubscriptionFeedEntity(
    cachedAtMillis: Long
) = io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity(
    videoId = id,
    title = title,
    channelName = channelName,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
    duration = duration,
    viewCount = viewCount,
    uploadDate = uploadDate,
    timestamp = timestamp,
    channelThumbnailUrl = channelThumbnailUrl,
    isShort = isShort,
    isLive = isLive,
    isUpcoming = isUpcoming,
    cachedAt = cachedAtMillis
)

private fun String.containsLiveMarker(): Boolean {
    val text = lowercase()
    return text.contains("live") ||
        text.contains("stream") ||
        text.contains("watching") ||
        text.contains("started")
}
