package com.flow.youtube.data.local.dao

import androidx.room.*
import com.flow.youtube.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    // ── Writes ──────────────────────────────────────────────────────────────

    /** Save / update a single entry (e.g. real-time playback position). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)

    /**
     * Bulk insert many entries at once.
     * Uses IGNORE so that actual watch-progress records already in the DB are
     * never overwritten by imported stubs.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<WatchHistoryEntity>)

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteEntry(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()

    // ── Reads ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>

    /** Paged version for very large histories (UI only needs recent items). */
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryPage(limit: Int, offset: Int): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE isMusic = 0 ORDER BY timestamp DESC")
    fun getVideoHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE isMusic = 1 ORDER BY timestamp DESC")
    fun getMusicHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId")
    fun getEntry(videoId: String): Flow<WatchHistoryEntity?>

    @Query("SELECT position FROM watch_history WHERE videoId = :videoId")
    suspend fun getPosition(videoId: String): Long?

    @Query("SELECT COUNT(*) FROM watch_history")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM watch_history WHERE isMusic = 0")
    fun getVideoCount(): Flow<Int>

    /**
     * Returns video IDs that the user has already watched (position > 0 OR appeared in history).
     * Used to filter watched shorts from the subscription shelf.
     */
    @Query("SELECT videoId FROM watch_history WHERE isMusic = 0")
    suspend fun getAllWatchedVideoIds(): List<String>
}
