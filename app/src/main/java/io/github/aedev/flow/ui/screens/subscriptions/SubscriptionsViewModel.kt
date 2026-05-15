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
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SubscriptionsViewModel : ViewModel() {
    companion object {
        private const val TAG = "SubsViewModel"
        /**
         * How old the subscription-feed cache may be before a background refresh is triggered.
         * 4 hours — balances freshness with avoiding an RSS fetch on every screen visit.
         */
        private const val FEED_CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
        private const val SOFT_REFRESH_MAX_AGE_MS = 10 * 60 * 1000L // 10 minutes
    }

    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var viewHistory: ViewHistory
    
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    private val ytRepository: YouTubeRepository = YouTubeRepository.getInstance()
    private lateinit var cacheDao: io.github.aedev.flow.data.local.dao.CacheDao
    private lateinit var playerPreferences: PlayerPreferences
    private lateinit var subscriptionGroupDao: SubscriptionGroupDao
    private var isInitialized = false
    private var isNetworkFetchRunning = false
    private var latestFeedVideos: List<Video> = emptyList()
    private var watchedVideoIds: Set<String> = emptySet()

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        subscriptionRepository = SubscriptionRepository.getInstance(context)
        playerPreferences = PlayerPreferences(context)
        viewHistory = ViewHistory.getInstance(context)
        val db = AppDatabase.getDatabase(context)
        cacheDao = db.cacheDao()
        subscriptionGroupDao = db.subscriptionGroupDao()
        
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
            playerPreferences.subsFullWidthView.collect { fullWidth ->
                _uiState.update { it.copy(isFullWidthView = fullWidth) }
            }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            viewHistory.getVideoHistoryFlow()
                .combine(playerPreferences.hideWatchedVideos) { history, hideWatched ->
                    // Always remove fully watched videos from Subs; the setting lowers the cutoff.
                    val threshold = if (hideWatched) 10f else 90f
                    history
                        .asSequence()
                        .filter { it.progressPercentage >= threshold }
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
                if (cachedFeed.isNotEmpty()) {
                    val videos = cachedFeed.map { entity ->
                         Video(
                            id = entity.videoId,
                            title = entity.title,
                            channelName = entity.channelName,
                            channelId = entity.channelId,
                            thumbnailUrl = entity.thumbnailUrl,
                            duration = entity.duration,
                            viewCount = entity.viewCount,
                            uploadDate = entity.uploadDate,
                            timestamp = entity.timestamp,
                            channelThumbnailUrl = entity.channelThumbnailUrl,
                            isShort = entity.isShort
                        )
                    }
                    Log.d(TAG, "Cache observer: calling updateVideos with ${videos.size} videos")
                    latestFeedVideos = videos
                    updateVideos(videos)
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
                            isSubscribed = true
                        )
                    }
                    _uiState.update { it.copy(subscribedChannels = channels) }

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
            io.github.aedev.flow.data.innertube.RssSubscriptionService.fetchSubscriptionVideos(
                channelIds = channelIds,
                maxTotal = 200
            ).collect { videos ->
                Log.i(TAG, "Network emit received: ${videos.size} videos (shorts=${videos.count { it.isShort }}, regular=${videos.count { !it.isShort }})")
                if (videos.isNotEmpty()) {
                    latestFeedVideos = videos
                    updateVideos(videos)
                    val entities = videos.map { video ->
                        io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity(
                            videoId = video.id,
                            title = video.title,
                            channelName = video.channelName,
                            channelId = video.channelId,
                            thumbnailUrl = video.thumbnailUrl,
                            duration = video.duration,
                            viewCount = video.viewCount,
                            uploadDate = video.uploadDate,
                            timestamp = video.timestamp,
                            channelThumbnailUrl = video.channelThumbnailUrl,
                            isShort = video.isShort,
                            cachedAt = System.currentTimeMillis()
                        )
                    }
                    withContext(PerformanceDispatcher.diskIO) {
                        cacheDao.clearSubscriptionFeed()
                        cacheDao.insertSubscriptionFeed(entities)
                    }
                } else {
                    Log.w(TAG, "Network emit was empty!")
                }
            }
        } finally {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = false) }
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



    private suspend fun updateVideos(videos: List<Video>) {
        val sortedVideos = videos.sortedByDescending { it.timestamp }

        val (shorts, regular) = sortedVideos.partition { video -> video.isShort }
        Log.i(TAG, "updateVideos: total=${sortedVideos.size} → regular=${regular.size}, shorts=${shorts.size}")

        // ── 1 short per channel (most recent first) ──────────────────
        val latestShortPerChannel = shorts
            .groupBy { it.channelId }
            .flatMap { (_, channelShorts) -> channelShorts.sortedByDescending { it.timestamp }.take(1) }
            .sortedByDescending { it.timestamp }
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

        val groupFilteredShorts = if (allowedChannelIds != null) {
            unwatchedShorts.filter { it.channelId in allowedChannelIds }
        } else {
            unwatchedShorts
        }

        _uiState.update { it.copy(recentVideos = groupFilteredRegular, shorts = groupFilteredShorts) }
    }
    

    fun selectGroup(groupName: String?) {
        _uiState.update { it.copy(selectedGroupName = groupName) }
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            val cached = cacheDao.getSubscriptionFeed().first()
            if (cached.isNotEmpty()) {
                val videos = cached.map { entity ->
                    Video(
                        id = entity.videoId,
                        title = entity.title,
                        channelName = entity.channelName,
                        channelId = entity.channelId,
                        thumbnailUrl = entity.thumbnailUrl,
                        duration = entity.duration,
                        viewCount = entity.viewCount,
                        uploadDate = entity.uploadDate,
                        timestamp = entity.timestamp,
                        channelThumbnailUrl = entity.channelThumbnailUrl,
                        isShort = entity.isShort
                    )
                }
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

    private fun parseRelativeTime(dateString: String): Long {
        try {
            val now = System.currentTimeMillis()
            val text = dateString.lowercase().trim()
            
            if (text.contains("scheduled") || text.contains("premiere")) return now + 86400000L
            if (text.contains("live")) return now + 3600000L // Boost live streams
            
            val parts = text.split(" ")
            val valueLine = parts.firstOrNull { it.any { c -> c.isDigit() } } 
            val value = valueLine?.filter { it.isDigit() }?.toLongOrNull() ?: 1L
            
            val multiplier = when {
                text.contains("second") -> 1000L
                text.contains("minute") -> 60000L
                text.contains("hour") -> 3600000L
                text.contains("day") -> 86400000L
                text.contains("week") -> 604800000L
                text.contains("month") -> 2592000000L
                text.contains("year") -> 31536000000L
                else -> 86400000L
            }
            
            return now - (value * multiplier)
        } catch (e: Exception) {
            return System.currentTimeMillis() 
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
    val selectedGroupName: String? = null
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

