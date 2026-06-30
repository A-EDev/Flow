package io.github.aedev.flow.sync.apply

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.BackupRepository
import io.github.aedev.flow.data.local.dao.SyncLogDao
import io.github.aedev.flow.data.local.dao.SyncPeerDao
import io.github.aedev.flow.data.local.entity.SyncLogEntity
import io.github.aedev.flow.data.local.entity.SyncPeerEntity
import io.github.aedev.flow.sync.identity.DeviceIdentity
import io.github.aedev.flow.sync.merge.LikesMerger
import io.github.aedev.flow.sync.merge.PlaylistMerger
import io.github.aedev.flow.sync.merge.SettingsMerger
import io.github.aedev.flow.sync.merge.SubscriptionsMerger
import io.github.aedev.flow.sync.merge.WatchHistoryMerger
import io.github.aedev.flow.sync.protocol.ApplyStats
import io.github.aedev.flow.sync.protocol.CollectionWire
import io.github.aedev.flow.sync.protocol.SyncCollection
import io.github.aedev.flow.sync.protocol.SyncSerialization
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ReceivedCollection(val lines: List<String>, val hash: String)

/** Identifies the peer for the ledger/known-devices list. */
data class PeerInfo(val deviceId: String, val deviceName: String, val platform: String)

/**
 *  Sender side: [exportPayload]. Receiver side:
 * [applyPayload] — takes a mandatory pre-merge backup, merges each collection with its local
 * state (CRDT), applies Room collections in a single `withTransaction`, applies DataStore/brain
 * collections (individually atomic), records the `sync_log` idempotency ledger + known peer, and
 * restores the backup on any failure.
 */
@Singleton
class SyncApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val dataAccess: SyncDataAccess,
    private val syncLogDao: SyncLogDao,
    private val syncPeerDao: SyncPeerDao,
    private val deviceIdentity: DeviceIdentity,
) {
    // --- sender ---

    suspend fun exportPayload(collections: List<String>, node: String, hlc: String): Map<String, CollectionWire> {
        val out = LinkedHashMap<String, CollectionWire>()
        for (c in collections) {
            val wire = when (c) {
                SyncCollection.WATCH_HISTORY -> SyncSerialization.encodeWatchHistory(dataAccess.readWatchHistory(node))
                SyncCollection.PLAYLISTS -> SyncSerialization.encodePlaylists(dataAccess.readPlaylists(hlc))
                SyncCollection.LIKES -> SyncSerialization.encodeLikes(dataAccess.readLikes(node))
                SyncCollection.SETTINGS -> SyncSerialization.encodeSettings(dataAccess.readSettings(hlc))
                SyncCollection.SUBSCRIPTIONS -> SyncSerialization.encodeSubscriptions(dataAccess.readSubscriptions(hlc))
                SyncCollection.FLOW_NEURO_BRAIN -> SyncSerialization.encodeBrain(dataAccess.readBrain(node, hlc))
                else -> null
            }
            if (wire != null) out[c] = wire
        }
        return out
    }

    // --- receiver ---

    suspend fun applyPayload(
        peer: PeerInfo,
        received: Map<String, ReceivedCollection>,
        hlc: String,
    ): Map<String, ApplyStats> {
        val node = deviceIdentity.hlcNode()
        val myDeviceId = deviceIdentity.deviceId()
        val stats = LinkedHashMap<String, ApplyStats>()

        // Idempotency: drop collections whose exact payload we've already applied from this peer.
        val fresh = HashMap<String, ReceivedCollection>()
        for ((collection, rc) in received) {
            if (syncLogDao.isAlreadyApplied(peer.deviceId, collection, rc.hash)) {
                stats[collection] = ApplyStats(skipped = rc.lines.size)
            } else {
                fresh[collection] = rc
            }
        }
        if (fresh.isEmpty()) {
            recordPeer(peer)
            return stats
        }

        takeBackup()
        var failedCollection: String? = null
        try {
            // Room-backed collections: one transaction, all-or-nothing.
            db.withTransaction {
                fresh[SyncCollection.WATCH_HISTORY]?.let { rc ->
                    failedCollection = SyncCollection.WATCH_HISTORY
                    val remote = SyncSerialization.decodeWatchHistory(rc.lines)
                    val local = dataAccess.readWatchHistory(node)
                    val merged = WatchHistoryMerger.merge(local, remote)
                    dataAccess.writeWatchHistory(merged)
                    stats[SyncCollection.WATCH_HISTORY] = statsFor(remote.map { it.videoId to it.deleted }, local.map { it.videoId })
                }
                fresh[SyncCollection.SUBSCRIPTIONS]?.let { rc ->
                    failedCollection = SyncCollection.SUBSCRIPTIONS
                    val remote = SyncSerialization.decodeSubscriptions(rc.lines)
                    val local = dataAccess.readSubscriptions(hlc)
                    val merged = SubscriptionsMerger.merge(local, remote)
                    dataAccess.writeSubscriptions(merged)
                    stats[SyncCollection.SUBSCRIPTIONS] = statsFor(remote.map { it.name to it.deleted }, local.map { it.name })
                }
                fresh[SyncCollection.PLAYLISTS]?.let { rc ->
                    failedCollection = SyncCollection.PLAYLISTS
                    val remote = SyncSerialization.decodePlaylists(rc.lines)
                    val local = dataAccess.readPlaylists(hlc)
                    val merged = PlaylistMerger.merge(local, remote)
                    dataAccess.writePlaylists(merged)
                    stats[SyncCollection.PLAYLISTS] = statsFor(remote.map { it.syncId to it.deleted }, local.map { it.syncId })
                }
            }

            // DataStore-backed collections: individually atomic.
            fresh[SyncCollection.LIKES]?.let { rc ->
                failedCollection = SyncCollection.LIKES
                val remote = SyncSerialization.decodeLikes(rc.lines)
                val local = dataAccess.readLikes(node)
                val merged = LikesMerger.merge(local, remote)
                dataAccess.writeLikes(merged)
                stats[SyncCollection.LIKES] = statsFor(remote.map { "${it.kind}|${it.id}" to (it.state == "none") }, local.map { "${it.kind}|${it.id}" })
            }
            fresh[SyncCollection.SETTINGS]?.let { rc ->
                failedCollection = SyncCollection.SETTINGS
                val remote = SyncSerialization.decodeSettings(rc.lines)
                val local = dataAccess.readSettings(hlc)
                val merged = SettingsMerger.merge(local, remote)
                dataAccess.writeSettings(merged)
                stats[SyncCollection.SETTINGS] = ApplyStats(updated = remote.size)
            }

            // Brain: stateful CRDT merge + engine reload.
            fresh[SyncCollection.FLOW_NEURO_BRAIN]?.let { rc ->
                failedCollection = SyncCollection.FLOW_NEURO_BRAIN
                SyncSerialization.decodeBrain(rc.lines)?.let { remote ->
                    dataAccess.mergeAndWriteBrain(remote, myDeviceId, hlc)
                    stats[SyncCollection.FLOW_NEURO_BRAIN] = ApplyStats(updated = 1)
                }
            }
            failedCollection = null

            // Ledger: record each applied payload + the peer.
            val now = System.currentTimeMillis()
            for ((collection, rc) in fresh) {
                syncLogDao.insert(SyncLogEntity(peer.deviceId, collection, rc.hash, now, hlc))
            }
            recordPeer(peer)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "merge failed at collection=$failedCollection; rolling back", e)
            runCatching { restoreBackup() }
            val where = failedCollection?.let { " applying '$it'" } ?: ""
            val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            throw SyncApplyException("Merge failed$where and was rolled back. Cause: $reason", e)
        } finally {
            runCatching { snapshotFile().delete() }
        }
        return stats
    }

    private companion object {
        const val TAG = "SyncApplier"
    }

    private suspend fun recordPeer(peer: PeerInfo) {
        syncPeerDao.upsert(SyncPeerEntity(peer.deviceId, peer.deviceName, peer.platform, System.currentTimeMillis()))
    }

    /** added = new keys, updated = existing keys, tombstoned = deletes in the incoming set. */
    private fun statsFor(incoming: List<Pair<String, Boolean>>, localKeys: List<String?>): ApplyStats {
        val local = localKeys.filterNotNull().toHashSet()
        var added = 0; var updated = 0; var tombstoned = 0
        for ((key, deleted) in incoming) {
            when {
                deleted -> tombstoned++
                key in local -> updated++
                else -> added++
            }
        }
        return ApplyStats(added = added, updated = updated, tombstoned = tombstoned)
    }

    // --- pre-merge backup (cross-store rollback net; reuses the existing master backup) ---

    private fun snapshotFile() = File(context.cacheDir, "sync_premerge_backup.zip")

    private suspend fun takeBackup() {
        val result = BackupRepository(context).exportMasterBackup(Uri.fromFile(snapshotFile()))
        if (result.isFailure) {
            throw SyncApplyException("Could not take the pre-merge safety backup; aborting merge", result.exceptionOrNull())
        }
    }

    private suspend fun restoreBackup() {
        BackupRepository(context).importMasterBackup(Uri.fromFile(snapshotFile()))
    }
}

class SyncApplyException(message: String, cause: Throwable?) : Exception(message, cause)
