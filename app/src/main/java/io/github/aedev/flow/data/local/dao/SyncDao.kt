package io.github.aedev.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.aedev.flow.data.local.entity.SyncLogEntity
import io.github.aedev.flow.data.local.entity.SyncPeerEntity

@Dao
interface SyncLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SyncLogEntity)

    @Query("SELECT COUNT(*) FROM sync_log WHERE peerDeviceId = :peer AND collection = :collection AND payloadHash = :hash")
    suspend fun count(peer: String, collection: String, hash: String): Int

    @Query("SELECT hwmHlc FROM sync_log WHERE peerDeviceId = :peer AND collection = :collection ORDER BY appliedAt DESC LIMIT 1")
    suspend fun highWaterMark(peer: String, collection: String): String?

    suspend fun isAlreadyApplied(peer: String, collection: String, hash: String): Boolean =
        count(peer, collection, hash) > 0
}

@Dao
interface SyncPeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: SyncPeerEntity)

    @Query("SELECT * FROM sync_peers ORDER BY lastSyncedAt DESC")
    suspend fun getAll(): List<SyncPeerEntity>

    @Query("DELETE FROM sync_peers WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)
}
