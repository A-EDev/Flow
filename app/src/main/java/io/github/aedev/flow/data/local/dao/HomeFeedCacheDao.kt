package io.github.aedev.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.aedev.flow.data.local.entity.HomeFeedCacheEntity

@Dao
interface HomeFeedCacheDao {
    @Query("SELECT * FROM home_feed_cache WHERE bucket = :bucket AND expiresAt > :now ORDER BY orderIndex ASC")
    suspend fun getFreshBucket(bucket: String, now: Long): List<HomeFeedCacheEntity>

    @Query("""
        SELECT * FROM home_feed_cache
        WHERE bucket = 'RELATED' AND relatedSeedId = :seedId AND expiresAt > :now
        ORDER BY orderIndex ASC
    """)
    suspend fun getFreshRelated(seedId: String, now: Long): List<HomeFeedCacheEntity>

    @Query("""
        SELECT * FROM home_feed_cache
        WHERE bucket = 'RESERVE' AND expiresAt > :now
        ORDER BY cachedAt DESC, orderIndex ASC
        LIMIT :limit
    """)
    suspend fun getFreshReserve(now: Long, limit: Int): List<HomeFeedCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HomeFeedCacheEntity>)

    @Query("DELETE FROM home_feed_cache WHERE bucket = :bucket")
    suspend fun clearBucket(bucket: String)

    @Query("DELETE FROM home_feed_cache WHERE bucket = 'RELATED' AND relatedSeedId = :seedId")
    suspend fun clearRelated(seedId: String)

    @Query("DELETE FROM home_feed_cache WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM home_feed_cache WHERE videoId = :videoId")
    suspend fun deleteVideo(videoId: String)

    @Query("DELETE FROM home_feed_cache WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String)

    @Query("DELETE FROM home_feed_cache")
    suspend fun clearAll()

    @Query("""
        DELETE FROM home_feed_cache
        WHERE bucket = 'RESERVE'
        AND cacheKey NOT IN (
            SELECT cacheKey FROM home_feed_cache
            WHERE bucket = 'RESERVE'
            ORDER BY cachedAt DESC, orderIndex ASC
            LIMIT :maxRows
        )
    """)
    suspend fun trimReserve(maxRows: Int)

    @Query("""
        DELETE FROM home_feed_cache
        WHERE bucket = 'RELATED' AND relatedSeedId = :seedId
        AND cacheKey NOT IN (
            SELECT cacheKey FROM home_feed_cache
            WHERE bucket = 'RELATED' AND relatedSeedId = :seedId
            ORDER BY orderIndex ASC
            LIMIT :maxRows
        )
    """)
    suspend fun trimRelatedSeed(seedId: String, maxRows: Int)

    @Query("""
        DELETE FROM home_feed_cache
        WHERE bucket = 'RELATED'
        AND relatedSeedId NOT IN (
            SELECT relatedSeedId FROM home_feed_cache
            WHERE bucket = 'RELATED' AND relatedSeedId IS NOT NULL
            GROUP BY relatedSeedId
            ORDER BY MAX(cachedAt) DESC
            LIMIT :maxSeeds
        )
    """)
    suspend fun trimRelatedSeeds(maxSeeds: Int)
}
