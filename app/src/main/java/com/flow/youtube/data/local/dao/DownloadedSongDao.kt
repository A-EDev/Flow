package com.flow.youtube.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flow.youtube.data.local.entity.DownloadedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedSongDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: DownloadedSongEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<DownloadedSongEntity>)
    
    @Update
    suspend fun update(song: DownloadedSongEntity)
    
    @Delete
    suspend fun delete(song: DownloadedSongEntity)
    
    @Query("DELETE FROM downloaded_songs WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM downloaded_songs")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM downloaded_songs WHERE id = :id")
    suspend fun getById(id: String): DownloadedSongEntity?
    
    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<DownloadedSongEntity>
    
    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun getAllAsFlow(): Flow<List<DownloadedSongEntity>>
    
    @Query("SELECT * FROM downloaded_songs WHERE downloadStatus = 'COMPLETED' ORDER BY downloadedAt DESC")
    suspend fun getAllCompleted(): List<DownloadedSongEntity>
    
    @Query("SELECT * FROM downloaded_songs WHERE downloadStatus = 'COMPLETED' ORDER BY downloadedAt DESC")
    fun getAllCompletedAsFlow(): Flow<List<DownloadedSongEntity>>
    
    @Query("SELECT * FROM downloaded_songs ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 50): List<DownloadedSongEntity>
    
    @Query("SELECT * FROM downloaded_songs ORDER BY playCount DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 50): List<DownloadedSongEntity>
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE id = :id AND downloadStatus = 'COMPLETED')")
    suspend fun isDownloaded(id: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE id = :id)")
    suspend fun exists(id: String): Boolean
    
    @Query("SELECT COUNT(*) FROM downloaded_songs WHERE downloadStatus = 'COMPLETED'")
    suspend fun getDownloadedCount(): Int
    
    @Query("SELECT SUM(fileSize) FROM downloaded_songs WHERE downloadStatus = 'COMPLETED'")
    suspend fun getTotalDownloadSize(): Long?
    
    @Query("UPDATE downloaded_songs SET downloadStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadedSongEntity.DownloadStatus)
    
    @Query("UPDATE downloaded_songs SET lastPlayedAt = :timestamp, playCount = playCount + 1 WHERE id = :id")
    suspend fun updatePlayStats(id: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE downloaded_songs SET downloadStatus = 'EXPIRED' WHERE id IN (:ids)")
    suspend fun markAsExpired(ids: List<String>)
    
    @Query("SELECT * FROM downloaded_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY downloadedAt DESC")
    suspend fun search(query: String): List<DownloadedSongEntity>
}
