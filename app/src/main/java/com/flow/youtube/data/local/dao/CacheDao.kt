package com.flow.youtube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flow.youtube.data.local.entity.MusicHomeCacheEntity
import com.flow.youtube.data.local.entity.MusicHomeChipEntity
import com.flow.youtube.data.local.entity.SubscriptionFeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    // Subscriptions
    @Query("SELECT * FROM subscription_feed_cache ORDER BY timestamp DESC LIMIT 200")
    fun getSubscriptionFeed(): Flow<List<SubscriptionFeedEntity>>

    /** Returns how many rows are currently in the cache. */
    @Query("SELECT COUNT(*) FROM subscription_feed_cache")
    suspend fun getSubscriptionFeedCount(): Int

    /** Returns the most-recent cachedAt timestamp, or null when the table is empty. */
    @Query("SELECT MAX(cachedAt) FROM subscription_feed_cache")
    suspend fun getLatestCachedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscriptionFeed(videos: List<SubscriptionFeedEntity>)

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
