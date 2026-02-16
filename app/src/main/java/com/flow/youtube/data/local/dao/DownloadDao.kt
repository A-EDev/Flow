package com.flow.youtube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.flow.youtube.data.local.entity.DownloadEntity
import com.flow.youtube.data.local.entity.DownloadItemEntity
import com.flow.youtube.data.local.entity.DownloadItemStatus
import com.flow.youtube.data.local.entity.DownloadWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    // ===== Download (parent) =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE videoId = :videoId")
    suspend fun deleteDownload(videoId: String)

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun getDownloadByVideoId(videoId: String): DownloadEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE videoId = :videoId)")
    suspend fun exists(videoId: String): Boolean

    // ===== Download Items (children) =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: DownloadItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<DownloadItemEntity>)

    @Update
    suspend fun updateItem(item: DownloadItemEntity)

    @Query("UPDATE download_items SET downloadedBytes = :downloadedBytes, status = :status WHERE id = :itemId")
    suspend fun updateProgress(itemId: Int, downloadedBytes: Long, status: DownloadItemStatus)

    @Query("UPDATE download_items SET status = :status WHERE id = :itemId")
    suspend fun updateStatus(itemId: Int, status: DownloadItemStatus)

    @Query("UPDATE download_items SET status = :status WHERE videoId = :videoId")
    suspend fun updateAllItemsStatus(videoId: String, status: DownloadItemStatus)

    @Query("UPDATE download_items SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, status = :status WHERE id = :itemId")
    suspend fun updateItemFull(itemId: Int, downloadedBytes: Long, totalBytes: Long, status: DownloadItemStatus)

    @Query("SELECT * FROM download_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Int): DownloadItemEntity?

    @Query("SELECT * FROM download_items WHERE videoId = :videoId")
    suspend fun getItemsByVideoId(videoId: String): List<DownloadItemEntity>

    @Query("DELETE FROM download_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Int)

    // ===== Combined Queries =====

    @Transaction
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloadsWithItems(): Flow<List<DownloadWithItems>>

    @Transaction
    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun getDownloadWithItems(videoId: String): DownloadWithItems?

    @Transaction
    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    fun getDownloadWithItemsFlow(videoId: String): Flow<DownloadWithItems?>

    /** Get downloads that have at least one item with VIDEO fileType */
    @Transaction
    @Query("""
        SELECT DISTINCT d.* FROM downloads d 
        INNER JOIN download_items di ON d.videoId = di.videoId 
        WHERE di.fileType = 'VIDEO' 
        ORDER BY d.createdAt DESC
    """)
    fun getVideoDownloads(): Flow<List<DownloadWithItems>>

    /** Get downloads that have AUDIO items but NO VIDEO items */
    @Transaction
    @Query("""
        SELECT DISTINCT d.* FROM downloads d 
        INNER JOIN download_items di ON d.videoId = di.videoId 
        WHERE di.fileType = 'AUDIO' 
        AND d.videoId NOT IN (
            SELECT videoId FROM download_items WHERE fileType = 'VIDEO'
        )
        ORDER BY d.createdAt DESC
    """)
    fun getAudioOnlyDownloads(): Flow<List<DownloadWithItems>>

    /** Get downloads with active (DOWNLOADING/PENDING) items */
    @Transaction
    @Query("""
        SELECT DISTINCT d.* FROM downloads d 
        INNER JOIN download_items di ON d.videoId = di.videoId 
        WHERE di.status IN ('DOWNLOADING', 'PENDING', 'PAUSED')
        ORDER BY d.createdAt DESC
    """)
    fun getActiveDownloads(): Flow<List<DownloadWithItems>>

    /** Check if a completed download exists for a video */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM download_items 
            WHERE videoId = :videoId AND status = 'COMPLETED'
        )
    """)
    suspend fun isDownloaded(videoId: String): Boolean

    /** Get total download storage size */
    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM download_items WHERE status = 'COMPLETED'")
    suspend fun getTotalDownloadSize(): Long

    /** Count completed downloads */
    @Query("SELECT COUNT(DISTINCT videoId) FROM download_items WHERE status = 'COMPLETED'")
    suspend fun getCompletedDownloadCount(): Int
}
