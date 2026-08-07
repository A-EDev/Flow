package io.github.aedev.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.aedev.flow.data.local.entity.MusicHomeCacheEntity
import io.github.aedev.flow.data.local.entity.MusicHomeChipEntity
import io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    // Subscriptions
    @Query("SELECT * FROM subscription_feed_cache ORDER BY timestamp DESC LIMIT 1500")
    fun getSubscriptionFeed(): Flow<List<SubscriptionFeedEntity>>

    /** Returns how many rows are currently in the cache. */
    @Query("SELECT COUNT(*) FROM subscription_feed_cache")
    suspend fun getSubscriptionFeedCount(): Int

    /** Returns the most-recent cachedAt timestamp, or null when the table is empty. */
    @Query("SELECT MAX(cachedAt) FROM subscription_feed_cache")
    suspend fun getLatestCachedAt(): Long?

    /** Rows for the given channels only, so an incremental refresh can merge against them. */
    @Query("SELECT * FROM subscription_feed_cache WHERE channelId IN (:channelIds)")
    suspend fun getSubscriptionFeedForChannels(channelIds: List<String>): List<SubscriptionFeedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscriptionFeed(videos: List<SubscriptionFeedEntity>)

    /**
     * Adds rows without touching ones that already exist, so the background new-upload check can
     * seed the feed without clobbering metadata the feed has since enriched.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubscriptionFeedIfAbsent(videos: List<SubscriptionFeedEntity>)

    @Query(
        """
        UPDATE subscription_feed_cache
        SET title = :title,
            channelName = :channelName,
            channelId = :channelId,
            thumbnailUrl = :thumbnailUrl,
            duration = :duration,
            viewCount = :viewCount,
            isLive = :isLive
        WHERE videoId = :videoId
        """,
    )
    suspend fun updateSubscriptionFeedMetadata(
        videoId: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String,
        duration: Int,
        viewCount: Long,
        isLive: Boolean,
    )

    @Query("DELETE FROM subscription_feed_cache WHERE channelId = :channelId")
    suspend fun deleteSubscriptionFeedForChannel(channelId: String)

    /** Callers must chunk [channelIds] to stay under SQLite's bound-variable limit. */
    @Query("DELETE FROM subscription_feed_cache WHERE channelId IN (:channelIds)")
    suspend fun deleteSubscriptionFeedForChannels(channelIds: List<String>)

    /**
     * Drops rows that have aged past the feed's lookback window. Upcoming items are kept because
     * their timestamp is a future premiere date, not an upload date.
     */
    @Query(
        """
        DELETE FROM subscription_feed_cache
        WHERE isUpcoming = 0 AND timestamp > 0 AND timestamp < :cutoffMillis
        """,
    )
    suspend fun pruneSubscriptionFeedOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM subscription_feed_cache")
    suspend fun clearSubscriptionFeed()

    // Music
    @Query("SELECT * FROM music_home_cache ORDER BY orderBy ASC")
    fun getMusicHomeSections(): Flow<List<MusicHomeCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicHomeSections(sections: List<MusicHomeCacheEntity>)

    @Query("DELETE FROM music_home_cache")
    suspend fun clearMusicHomeCache()

    // Music Chips
    @Query("SELECT * FROM music_home_chips_cache ORDER BY orderBy ASC")
    fun getMusicHomeChips(): Flow<List<MusicHomeChipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicHomeChips(chips: List<MusicHomeChipEntity>)

    @Query("DELETE FROM music_home_chips_cache")
    suspend fun clearMusicHomeChips()
}
