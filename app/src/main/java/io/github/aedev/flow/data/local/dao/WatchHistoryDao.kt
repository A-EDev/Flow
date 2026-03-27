package io.github.aedev.flow.data.local.dao

import androidx.room.*
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
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

    /**
     * Returns the most recently watched non-music video **only if that specific video
     * is still in progress**.  By restricting to the maximum timestamp we avoid the
     * "stack fallback" problem where finishing one video causes the previous unfinished
     * video to pop up in the continue-watching mini-player instead.
     *
     * Criteria:
     *  - Must be the absolute latest watched video (by timestamp)
     *  - position saved (> 0)
     *  - less than 95% watched
     *  - more than 30 seconds of content remaining
     */
    @Query("""
        SELECT * FROM watch_history
        WHERE isMusic = 0
        AND duration > 0
        AND position > 0
        AND (CAST(position AS REAL) / CAST(duration AS REAL)) < 0.95
        AND (duration - position) > 30000
        AND timestamp = (SELECT MAX(timestamp) FROM watch_history WHERE isMusic = 0)
        LIMIT 1
    """)
    suspend fun getLatestUnfinishedVideo(): WatchHistoryEntity?

    /**
     * Marks a video as fully watched by setting position = duration.
     * This excludes it from the continue-watching popup on the next launch.
     * Called when the user explicitly dismisses the restored-session mini-player.
     */
    @Query("UPDATE watch_history SET position = duration WHERE videoId = :videoId")
    suspend fun markAsWatched(videoId: String)
}
