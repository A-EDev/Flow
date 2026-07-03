package io.github.aedev.flow.data.local

import android.content.Context
import io.github.aedev.flow.data.local.entity.HomeFeedCacheEntity
import io.github.aedev.flow.data.model.Video
import org.json.JSONArray

data class CachedHomeVideo(
    val video: Video,
    val source: String,
    val relatedSeedId: String? = null
)

data class HomeFeedCacheFilters(
    val watchedVideoIds: Set<String> = emptySet(),
    val suppressedVideoIds: Set<String> = emptySet(),
    val blockedChannelIds: Set<String> = emptySet(),
    val suppressedChannelIds: Set<String> = emptySet()
)

internal fun filterCachedHomeVideos(
    items: List<CachedHomeVideo>,
    filters: HomeFeedCacheFilters
): List<CachedHomeVideo> =
    items.filter { item ->
        val video = item.video
        video.id !in filters.watchedVideoIds &&
            video.id !in filters.suppressedVideoIds &&
            (video.channelId.isBlank() || video.channelId !in filters.blockedChannelIds) &&
            (video.channelId.isBlank() || video.channelId !in filters.suppressedChannelIds)
    }

internal fun selectReservePageFromCache(
    items: List<CachedHomeVideo>,
    maxRelated: Int = 4,
    maxDiscovery: Int = 4
): List<CachedHomeVideo> {
    val related = items.filter { it.source == HomeFeedCacheRepository.SOURCE_RELATED }.take(maxRelated)
    val discovery = items.filter { it.source == HomeFeedCacheRepository.SOURCE_DISCOVERY }.take(maxDiscovery)
    return related + discovery
}

class HomeFeedCacheRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).homeFeedCacheDao()

    suspend fun loadLastFeed(filters: HomeFeedCacheFilters, now: Long = System.currentTimeMillis()): List<Video> {
        dao.deleteExpired(now)
        return filterCachedHomeVideos(
            dao.getFreshBucket(BUCKET_LAST_FEED, now).map { it.toCachedHomeVideo() },
            filters
        ).map { it.video }
    }

    suspend fun saveLastFeed(videos: List<Video>, now: Long = System.currentTimeMillis()) {
        dao.clearBucket(BUCKET_LAST_FEED)
        dao.insertAll(
            videos.take(LAST_FEED_CAP).mapIndexed { index, video ->
                video.toEntity(
                    bucket = BUCKET_LAST_FEED,
                    source = SOURCE_LAST_FEED,
                    relatedSeedId = null,
                    orderIndex = index,
                    cachedAt = now,
                    expiresAt = now + LAST_FEED_TTL_MS
                )
            }
        )
    }

    suspend fun loadReservePage(
        filters: HomeFeedCacheFilters,
        now: Long = System.currentTimeMillis(),
        maxRelated: Int = 4,
        maxDiscovery: Int = 4
    ): List<CachedHomeVideo> {
        dao.deleteExpired(now)
        val freshReserve = dao.getFreshReserve(now, RESERVE_CAP)
            .map { it.toCachedHomeVideo() }
        return selectReservePageFromCache(
            filterCachedHomeVideos(freshReserve, filters),
            maxRelated = maxRelated,
            maxDiscovery = maxDiscovery
        )
    }

    suspend fun saveReserve(items: List<CachedHomeVideo>, now: Long = System.currentTimeMillis()) {
        if (items.isEmpty()) return
        dao.insertAll(
            items.distinctBy { it.video.id }
                .take(RESERVE_CAP)
                .mapIndexed { index, item ->
                    item.video.toEntity(
                        bucket = BUCKET_RESERVE,
                        source = item.source,
                        relatedSeedId = item.relatedSeedId,
                        orderIndex = index,
                        cachedAt = now,
                        expiresAt = now + RESERVE_TTL_MS
                    )
                }
        )
        dao.trimReserve(RESERVE_CAP)
    }

    suspend fun loadRelated(
        seedId: String,
        filters: HomeFeedCacheFilters,
        now: Long = System.currentTimeMillis()
    ): List<Video> {
        dao.deleteExpired(now)
        return filterCachedHomeVideos(
            dao.getFreshRelated(seedId, now).map { it.toCachedHomeVideo() },
            filters
        ).map { it.video }
    }

    suspend fun saveRelated(
        seedId: String,
        videos: List<Video>,
        now: Long = System.currentTimeMillis()
    ) {
        if (seedId.isBlank() || videos.isEmpty()) return
        dao.clearRelated(seedId)
        dao.insertAll(
            videos.take(RELATED_PER_SEED_CAP).mapIndexed { index, video ->
                video.toEntity(
                    bucket = BUCKET_RELATED,
                    source = SOURCE_RELATED,
                    relatedSeedId = seedId,
                    orderIndex = index,
                    cachedAt = now,
                    expiresAt = now + RELATED_TTL_MS
                )
            }
        )
        dao.trimRelatedSeed(seedId, RELATED_PER_SEED_CAP)
        dao.trimRelatedSeeds(RELATED_SEED_CAP)
    }

    suspend fun deleteVideo(videoId: String) {
        if (videoId.isNotBlank()) dao.deleteVideo(videoId)
    }

    suspend fun deleteChannel(channelId: String) {
        if (channelId.isNotBlank()) dao.deleteChannel(channelId)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    private fun Video.toEntity(
        bucket: String,
        source: String,
        relatedSeedId: String?,
        orderIndex: Int,
        cachedAt: Long,
        expiresAt: Long
    ): HomeFeedCacheEntity {
        val seedPart = relatedSeedId.orEmpty()
        return HomeFeedCacheEntity(
            cacheKey = "$bucket|$source|$seedPart|$id",
            bucket = bucket,
            videoId = id,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            viewCount = viewCount,
            likeCount = likeCount,
            uploadDate = uploadDate,
            timestamp = timestamp,
            description = description,
            channelThumbnailUrl = channelThumbnailUrl,
            tagsJson = JSONArray(tags).toString(),
            isMusic = isMusic,
            isLive = isLive,
            isShort = isShort,
            isUpcoming = isUpcoming,
            commentCountText = commentCountText,
            source = source,
            relatedSeedId = relatedSeedId,
            cachedAt = cachedAt,
            expiresAt = expiresAt,
            orderIndex = orderIndex
        )
    }

    private fun HomeFeedCacheEntity.toCachedHomeVideo(): CachedHomeVideo =
        CachedHomeVideo(
            video = Video(
                id = videoId,
                title = title,
                channelName = channelName,
                channelId = channelId,
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                viewCount = viewCount,
                likeCount = likeCount,
                uploadDate = uploadDate,
                timestamp = timestamp,
                description = description,
                channelThumbnailUrl = channelThumbnailUrl,
                tags = parseTags(tagsJson),
                isMusic = isMusic,
                isLive = isLive,
                isShort = isShort,
                isUpcoming = isUpcoming,
                commentCountText = commentCountText
            ),
            source = source,
            relatedSeedId = relatedSeedId
        )

    private fun parseTags(raw: String): List<String> = runCatching {
        val json = JSONArray(raw)
        List(json.length()) { index -> json.optString(index) }
            .filter { it.isNotBlank() }
    }.getOrElse { emptyList() }

    companion object {
        const val SOURCE_SUBS = "SUBS"
        const val SOURCE_RELATED = "RELATED"
        const val SOURCE_DISCOVERY = "DISCOVERY"
        const val SOURCE_VIRAL = "VIRAL"
        const val SOURCE_LAST_FEED = "LAST_FEED"

        private const val BUCKET_LAST_FEED = "LAST_FEED"
        private const val BUCKET_RESERVE = "RESERVE"
        private const val BUCKET_RELATED = "RELATED"

        private const val LAST_FEED_CAP = 60
        private const val RESERVE_CAP = 200
        private const val RELATED_PER_SEED_CAP = 20
        private const val RELATED_SEED_CAP = 50

        private const val LAST_FEED_TTL_MS = 8L * 60L * 60L * 1000L
        private const val RESERVE_TTL_MS = 12L * 60L * 60L * 1000L
        private const val RELATED_TTL_MS = 90L * 60L * 1000L
    }
}
