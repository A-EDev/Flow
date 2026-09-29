package io.github.aedev.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.aedev.flow.data.local.entity.CollectionItemState
import io.github.aedev.flow.data.local.entity.DownloadCollectionEntity
import io.github.aedev.flow.data.local.entity.DownloadCollectionItemEntity
import kotlinx.coroutines.flow.Flow

/** A downloaded collection with how much of it is on disk. */
data class DownloadCollectionSummary(
    @Embedded val collection: DownloadCollectionEntity,
    val wantedCount: Int,
    val downloadedCount: Int,
    val downloadedBytes: Long,
) {
    /** Every song or video the collection still wants is on the device. */
    val isComplete: Boolean get() = wantedCount > 0 && downloadedCount >= wantedCount
}

@Dao
interface DownloadCollectionDao {
    @Upsert
    suspend fun upsertCollection(collection: DownloadCollectionEntity)

    @Upsert
    suspend fun upsertItems(items: List<DownloadCollectionItemEntity>)

    /** Writes the collection and its members together, so no member ever points at a missing row. */
    @Transaction
    suspend fun upsertWithItems(
        collection: DownloadCollectionEntity,
        items: List<DownloadCollectionItemEntity>,
    ) {
        upsertCollection(collection)
        upsertItems(items)
    }

    @Query("SELECT * FROM download_collections WHERE id = :id")
    suspend fun getCollection(id: String): DownloadCollectionEntity?

    @Query("SELECT * FROM download_collections WHERE id = :id")
    fun observeCollection(id: String): Flow<DownloadCollectionEntity?>

    @Query("SELECT * FROM download_collection_items WHERE collectionId = :collectionId ORDER BY position")
    suspend fun itemsOf(collectionId: String): List<DownloadCollectionItemEntity>

    @Query("SELECT * FROM download_collection_items WHERE collectionId = :collectionId ORDER BY position")
    fun observeItems(collectionId: String): Flow<List<DownloadCollectionItemEntity>>

    @Query("SELECT * FROM download_collection_items WHERE videoId = :videoId")
    suspend fun membershipsOf(videoId: String): List<DownloadCollectionItemEntity>

    @Query("UPDATE download_collection_items SET state = :state WHERE collectionId = :collectionId AND videoId = :videoId")
    suspend fun setState(
        collectionId: String,
        videoId: String,
        state: CollectionItemState,
    )

    @Query("UPDATE download_collections SET folderName = :folderName, folderLocation = :folderLocation WHERE id = :id")
    suspend fun setFolder(
        id: String,
        folderName: String,
        folderLocation: String?,
    )

    @Query("UPDATE download_collections SET lastSyncedAt = :syncedAt, remoteItemCount = :remoteItemCount WHERE id = :id")
    suspend fun markSynced(
        id: String,
        syncedAt: Long,
        remoteItemCount: Int,
    )

    @Query("UPDATE download_collections SET coverPath = :coverPath WHERE id = :id")
    suspend fun setCover(
        id: String,
        coverPath: String?,
    )

    @Query("DELETE FROM download_collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("SELECT folderName FROM download_collections")
    suspend fun folderNames(): List<String>

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM download_collection_items i
                WHERE i.collectionId = c.id AND i.state = 'WANTED') AS wantedCount,
            (SELECT COUNT(DISTINCT i.videoId) FROM download_collection_items i
                JOIN download_items di ON di.videoId = i.videoId
                WHERE i.collectionId = c.id AND i.state = 'WANTED' AND di.status = 'COMPLETED') AS downloadedCount,
            (SELECT COALESCE(SUM(di.totalBytes), 0) FROM download_collection_items i
                JOIN download_items di ON di.videoId = i.videoId
                WHERE i.collectionId = c.id AND i.state = 'WANTED' AND di.status = 'COMPLETED') AS downloadedBytes
        FROM download_collections c
        ORDER BY c.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<DownloadCollectionSummary>>
}
