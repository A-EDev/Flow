package io.github.aedev.flow.data.subscriptions

import android.util.Log
import androidx.room.withTransaction
import io.github.aedev.flow.data.innertube.RssSubscriptionService
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.dao.CacheDao
import io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedMerger.preservingEnrichedMetadata
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One progressive step of a refresh, as the caller should render it. */
data class SubscriptionFeedRefreshProgress(
    val videos: List<Video>,
    val failedChannelIds: Set<String>,
    val processedChannels: Int,
    val totalChannels: Int,
)

/**
 * The single owner of the subscription feed cache.
 *
 * Every trigger — opening the screen, the periodic in-screen refresh, app startup and the
 * background new-upload check — goes through here, so there is one staleness policy, one merge
 * implementation and one writer for `subscription_feed_cache`.
 */
@Singleton
class SubscriptionFeedRepository
    @Inject
    constructor(
        private val subscriptionRepository: SubscriptionRepository,
        private val rssSubscriptionService: RssSubscriptionService,
        private val cacheDao: CacheDao,
        private val database: AppDatabase,
        private val playerPreferences: PlayerPreferences,
    ) {
        /**
         * Guards against overlapping refreshes; the screen, the startup pass and the worker can all
         * fire within the same second, and a second sweep would only duplicate the first one's work.
         */
        private val refreshLock = Mutex()

        fun observeFeed(): Flow<List<Video>> = cacheDao.getSubscriptionFeed().map { rows -> rows.map { it.toVideo() } }

        /** Which channels are due a refresh right now; empty when everything is still fresh. */
        suspend fun planRefresh(force: Boolean): SubscriptionRefreshPlan {
            val subscriptions = subscriptionRepository.getAllSubscriptions().first()
            return SubscriptionRefreshPlanner.plan(
                subscriptions = subscriptions,
                now = System.currentTimeMillis(),
                force = force,
            )
        }

        /**
         * Runs [plan] and writes the result. Emits a progressively more complete feed so the caller
         * can render partial results; the cache is written once, when the fetch finishes.
         *
         * Emits nothing at all when the plan is empty or another refresh already holds the lock.
         */
        fun refresh(plan: SubscriptionRefreshPlan): Flow<SubscriptionFeedRefreshProgress> =
            flow {
                if (plan.isEmpty) return@flow
                if (!refreshLock.tryLock()) {
                    Log.d(TAG, "Skip refresh: another subscription refresh is running")
                    return@flow
                }

                try {
                    val allCached = withContext(PerformanceDispatcher.diskIO) { loadCachedFeed() }
                    val plannedChannelIds = plan.channelIds.toHashSet()
                    val sliceCached =
                        if (plan.isFullRefresh) {
                            allCached
                        } else {
                            allCached.filter { it.channelId in plannedChannelIds }
                        }

                    var previewVideos = allCached
                    var latestChunkVideos = emptyList<Video>()
                    var failedChannelIds = emptySet<String>()
                    var processed = 0

                    rssSubscriptionService
                        .fetchSubscriptionVideos(
                            channelIds = plan.channelIds,
                            maxTotal = MAX_SUBSCRIPTION_CACHE_ITEMS,
                            knownVideoIds = if (plan.isFullRefresh) emptySet() else allCached.mapTo(HashSet()) { it.id },
                            onProgress = { done, _ -> processed = done },
                        ).collect { chunk ->
                            failedChannelIds = chunk.failedChannelIds
                            if (chunk.videos.isNotEmpty()) {
                                latestChunkVideos = chunk.videos
                                previewVideos =
                                    SubscriptionFeedMerger
                                        .mergeSubscriptionFeed(
                                            freshVideos = chunk.videos,
                                            cachedVideos = previewVideos,
                                            now = System.currentTimeMillis(),
                                            windowMs = SUBSCRIPTION_CACHE_WINDOW_MS,
                                            maxItems = MAX_SUBSCRIPTION_CACHE_ITEMS,
                                        ).withHighQualityThumbnails()
                            }
                            emit(
                                SubscriptionFeedRefreshProgress(
                                    videos = previewVideos,
                                    failedChannelIds = failedChannelIds,
                                    processedChannels = processed,
                                    totalChannels = plan.channelIds.size,
                                ),
                            )
                        }

                    val refreshTime = System.currentTimeMillis()
                    if (latestChunkVideos.isNotEmpty() || plan.isFullRefresh) {
                        val persisted =
                            persist(
                                plan = plan,
                                freshVideos = latestChunkVideos,
                                sliceCached = sliceCached,
                                refreshTime = refreshTime,
                            )
                        emit(
                            SubscriptionFeedRefreshProgress(
                                videos = persisted,
                                failedChannelIds = failedChannelIds,
                                processedChannels = plan.channelIds.size,
                                totalChannels = plan.channelIds.size,
                            ),
                        )
                    } else if (allCached.isNotEmpty()) {
                        withContext(PerformanceDispatcher.diskIO) {
                            playerPreferences.setSubscriptionLastRefresh(refreshTime, allCached.size)
                        }
                    }
                } finally {
                    refreshLock.unlock()
                }
            }

        /**
         * Splices the fetched channels back into the cache. A full refresh still replaces the table
         * outright; an incremental one only touches the rows of the channels it actually fetched, so
         * an unrelated channel's items are never dropped by a partial run.
         */
        private suspend fun persist(
            plan: SubscriptionRefreshPlan,
            freshVideos: List<Video>,
            sliceCached: List<Video>,
            refreshTime: Long,
        ): List<Video> {
            val priorById =
                sliceCached
                    .filter { it.id.isNotBlank() }
                    .groupBy { it.id }
                    .mapValues { (_, candidates) -> SubscriptionFeedMerger.mergeDuplicates(candidates, refreshTime) }

            val mergedSlice =
                SubscriptionFeedMerger
                    .mergeSubscriptionFeed(
                        freshVideos = freshVideos.map { fresh -> fresh.preservingEnrichedMetadata(priorById[fresh.id]) },
                        cachedVideos = if (plan.isFullRefresh) emptyList() else sliceCached,
                        now = refreshTime,
                        windowMs = SUBSCRIPTION_CACHE_WINDOW_MS,
                        maxItems = MAX_SUBSCRIPTION_CACHE_ITEMS,
                    ).withHighQualityThumbnails()

            val entities = mergedSlice.map { it.toEntity(refreshTime) }
            withContext(PerformanceDispatcher.diskIO) {
                database.withTransaction {
                    if (plan.isFullRefresh) {
                        cacheDao.clearSubscriptionFeed()
                    } else {
                        plan.channelIds.chunked(SQLITE_VARIABLE_LIMIT).forEach { ids ->
                            cacheDao.deleteSubscriptionFeedForChannels(ids)
                        }
                    }
                    cacheDao.insertSubscriptionFeed(entities)
                    cacheDao.pruneSubscriptionFeedOlderThan(refreshTime - SUBSCRIPTION_CACHE_WINDOW_MS)
                }
                subscriptionRepository.markFeedFetched(plan.channelIds, refreshTime)
                val cachedCount = cacheDao.getSubscriptionFeedCount()
                playerPreferences.setSubscriptionLastRefresh(refreshTime, cachedCount)
            }
            Log.i(TAG, "Persisted ${entities.size} rows for ${plan.channelIds.size} channels (full=${plan.isFullRefresh})")

            return if (plan.isFullRefresh) mergedSlice else loadCachedFeed()
        }

        /**
         * Adds videos the background new-upload check discovered, without disturbing rows the feed
         * has already enriched. The channel is deliberately *not* marked as fetched: RSS alone
         * cannot see Shorts or livestreams, so the feed still owes it a full pass.
         */
        suspend fun seedFromNotificationCheck(
            channelId: String,
            channelName: String?,
            entries: List<ChannelRssEntry>,
        ) {
            if (entries.isEmpty()) return
            val now = System.currentTimeMillis()
            val cutoff = now - SUBSCRIPTION_CACHE_WINDOW_MS
            val rows =
                entries
                    .filter { it.publishedAtMillis > cutoff }
                    .map { entry -> entry.toEntity(channelId = channelId, channelName = channelName, cachedAt = now) }
            if (rows.isEmpty()) return

            withContext(PerformanceDispatcher.diskIO) {
                cacheDao.insertSubscriptionFeedIfAbsent(rows)
            }
            Log.d(TAG, "Seeded ${rows.size} row(s) for $channelId from the notification check")
        }

        /** Writes back metadata the on-demand player lookup resolved for already-cached rows. */
        suspend fun updateEnrichedMetadata(videos: Collection<Video>) {
            if (videos.isEmpty()) return
            withContext(PerformanceDispatcher.diskIO) {
                database.withTransaction {
                    videos.forEach { video ->
                        cacheDao.updateSubscriptionFeedMetadata(
                            videoId = video.id,
                            title = video.title,
                            channelName = video.channelName,
                            channelId = video.channelId,
                            thumbnailUrl = video.thumbnailUrl,
                            duration = video.duration,
                            viewCount = video.viewCount,
                            isLive = video.isLive,
                        )
                    }
                }
            }
        }

        private suspend fun loadCachedFeed(): List<Video> = cacheDao.getSubscriptionFeed().first().map { it.toVideo() }

        private companion object {
            const val TAG = "SubsFeedRepo"
            const val SUBSCRIPTION_FEED_LOOKBACK_DAYS = 60L
            const val SUBSCRIPTION_CACHE_WINDOW_MS = SUBSCRIPTION_FEED_LOOKBACK_DAYS * 24L * 60L * 60L * 1000L
            const val MAX_SUBSCRIPTION_CACHE_ITEMS = 1500

            /** SQLite allows 999 bound variables per statement; stay comfortably below it. */
            const val SQLITE_VARIABLE_LIMIT = 500
        }
    }

private fun SubscriptionFeedEntity.toVideo() =
    Video(
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
        isUpcoming = isUpcoming,
    )

private fun Video.toEntity(cachedAtMillis: Long) =
    SubscriptionFeedEntity(
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
        cachedAt = cachedAtMillis,
    )

private fun ChannelRssEntry.toEntity(
    channelId: String,
    channelName: String?,
    cachedAt: Long,
) = SubscriptionFeedEntity(
    videoId = videoId,
    title = title,
    channelName = channelName.orEmpty(),
    channelId = channelId,
    thumbnailUrl =
        io.github.aedev.flow.utils.ThumbnailUrlResolver
            .normalizeVideoThumbnail(videoId, thumbnailUrl),
    // RSS carries neither; the feed's on-demand enrichment fills them in when the item is shown.
    duration = 0,
    viewCount = viewCount,
    uploadDate = "",
    timestamp = publishedAtMillis,
    channelThumbnailUrl = "",
    isShort = false,
    isLive = false,
    isUpcoming = publishedAtMillis > cachedAt + 60_000L,
    cachedAt = cachedAt,
)

private fun String.containsLiveMarker(): Boolean {
    val text = lowercase()
    return text.contains("live") ||
        text.contains("stream") ||
        text.contains("watching") ||
        text.contains("started")
}
