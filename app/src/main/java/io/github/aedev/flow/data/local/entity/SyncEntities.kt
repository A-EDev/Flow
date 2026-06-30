package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Idempotency ledger for FLOW-SYNC/1. A given `(peer, collection, payloadHash)`
 * is applied at most once; [hwmHlc] is the per-peer high-water mark for a future v2 delta sync.
 */
@Entity(tableName = "sync_log", primaryKeys = ["peerDeviceId", "collection", "payloadHash"])
data class SyncLogEntity(
    val peerDeviceId: String,
    val collection: String,
    val payloadHash: String,
    val appliedAt: Long,
    val hwmHlc: String,
)

/** A device this install has synced with (drives the "known devices" UI). */
@Entity(tableName = "sync_peers")
data class SyncPeerEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val platform: String,
    val lastSyncedAt: Long,
)
