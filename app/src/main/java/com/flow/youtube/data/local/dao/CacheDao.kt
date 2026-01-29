package com.flow.youtube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flow.youtube.data.local.entity.MusicHomeCacheEntity
import com.flow.youtube.data.local.entity.SubscriptionFeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    // Subscriptions
    @Query("SELECT * FROM subscription_feed_cache ORDER BY uploadDate DESC LIMIT 100")
    fun getSubscriptionFeed(): Flow<List<SubscriptionFeedEntity>>

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
}
