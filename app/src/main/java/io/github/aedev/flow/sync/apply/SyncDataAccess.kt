package io.github.aedev.flow.sync.apply

import android.content.Context
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.dao.PlaylistDao
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalLike
import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.canonical.CanonicalSetting
import io.github.aedev.flow.sync.canonical.CanonicalSubscriptionGroup
import io.github.aedev.flow.sync.canonical.CanonicalWatchHistory
import io.github.aedev.flow.sync.mapping.BrainMapper
import io.github.aedev.flow.sync.mapping.LikesMapper
import io.github.aedev.flow.sync.mapping.PlaylistMapper
import io.github.aedev.flow.sync.mapping.SettingsMapper
import io.github.aedev.flow.sync.mapping.SubscriptionsMapper
import io.github.aedev.flow.sync.mapping.WatchHistoryMapper
import io.github.aedev.flow.sync.merge.BrainCrdtState
import io.github.aedev.flow.sync.merge.BrainCrdtStore
import io.github.aedev.flow.sync.merge.BrainMerger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bridge between platform-neutral canonical records and the app's real stores (Room DAOs,
 * DataStore singletons, the FlowNeuro brain). Provides `read*` (local → canonical, for the send
 * side) and `write*` (merged canonical → store, for the apply side). The brain is stateful (its
 * G-Counter sidecar), so it exposes a combined read + merge-and-write.
 */
@Singleton
class SyncDataAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchHistoryDao: WatchHistoryDao,
    private val playlistDao: PlaylistDao,
    private val videoDao: VideoDao,
    private val subscriptionGroupDao: SubscriptionGroupDao,
    private val brainCrdtStore: BrainCrdtStore,
) {
    private val likedVideos: LikedVideosRepository by lazy { LikedVideosRepository.getInstance(context) }
    private val playerPrefs: PlayerPreferences by lazy { PlayerPreferences(context) }
    private val neuroEngine: FlowNeuroEngine by lazy { FlowNeuroEngine.getInstance(context) }

    // --- watch history ---

    suspend fun readWatchHistory(node: String): List<CanonicalWatchHistory> =
        watchHistoryDao.getAllHistory().first()
            .filter { !it.isLocal } // device-local media files don't sync
            .map { WatchHistoryMapper.toCanonical(it, node) }

    suspend fun writeWatchHistory(merged: List<CanonicalWatchHistory>) {
        val toUpsert = merged.filter { !it.deleted }.map { WatchHistoryMapper.toEntity(it) }
        if (toUpsert.isNotEmpty()) watchHistoryDao.upsertAll(toUpsert)
        for (d in merged) if (d.deleted) watchHistoryDao.deleteEntry(d.videoId)
    }

    // --- likes (export is liked-only; apply handles all 3 states) ---

    suspend fun readLikes(node: String): List<CanonicalLike> =
        likedVideos.getAllLikedVideos().first().map { LikesMapper.likedToCanonical(it, node) }

    suspend fun writeLikes(merged: List<CanonicalLike>) {
        for (like in merged) when (like.state) {
            CanonicalLike.STATE_LIKED -> likedVideos.likeVideo(LikesMapper.toLikedInfo(like))
            CanonicalLike.STATE_DISLIKED -> likedVideos.dislikeVideo(like.id)
            CanonicalLike.STATE_NONE -> likedVideos.removeLikeState(like.id)
        }
    }

    // --- settings (curated whitelist) ---

    suspend fun readSettings(hlc: String): List<CanonicalSetting> =
        SettingsMapper.exportToCanonical(playerPrefs.getExportData(), hlc)

    suspend fun writeSettings(merged: List<CanonicalSetting>) {
        playerPrefs.restoreData(SettingsMapper.applyToBackup(merged))
    }

    // --- subscriptions ---

    suspend fun readSubscriptions(hlc: String): List<CanonicalSubscriptionGroup> =
        subscriptionGroupDao.getAllGroupsOnce().map { SubscriptionsMapper.toCanonical(it, hlc) }

    suspend fun writeSubscriptions(merged: List<CanonicalSubscriptionGroup>) {
        val toUpsert = merged.filter { !it.deleted }.map { SubscriptionsMapper.toEntity(it) }
        if (toUpsert.isNotEmpty()) subscriptionGroupDao.insertAll(toUpsert)
        for (g in merged) if (g.deleted) subscriptionGroupDao.deleteGroup(g.name)
    }

    // --- playlists ---

    suspend fun readPlaylists(hlc: String): List<CanonicalPlaylist> {
        val playlists = playlistDao.getAllPlaylists().first()
        val refsByPlaylist = playlistDao.getAllPlaylistVideoCrossRefs().groupBy { it.playlistId }
        val videosById = videoDao.getAllVideos().associateBy { it.id }
        return playlists.map { p ->
            val items = (refsByPlaylist[p.id] ?: emptyList()).map { ref ->
                PlaylistMapper.ItemSource(ref, videosById[ref.videoId])
            }
            PlaylistMapper.toCanonical(p, items, hlc)
        }
    }

    suspend fun writePlaylists(merged: List<CanonicalPlaylist>) {
        val locals = playlistDao.getAllPlaylists().first()
        val bySyncId = locals.associateBy { it.syncId ?: it.id }
        val byYoutubeId = locals.filter { !it.isUserCreated }.associateBy { it.id }
        val allRefs = playlistDao.getAllPlaylistVideoCrossRefs().groupBy { it.playlistId }

        for (cp in merged) {
            val localId = resolveLocalId(cp, bySyncId, byYoutubeId)
            if (cp.deleted) {
                if (localId != null && localId != PlaylistMapper.WATCH_LATER_ID &&
                    localId != PlaylistMapper.SAVED_SHORTS_ID
                ) {
                    playlistDao.deletePlaylist(localId)
                }
                continue
            }
            val targetId = localId ?: newLocalId(cp)
            playlistDao.insertPlaylist(PlaylistMapper.toPlaylistEntity(cp, targetId))
            videoDao.insertVideosOrIgnore(PlaylistMapper.toVideoEntities(cp))

            val mergedRefs = PlaylistMapper.toCrossRefs(cp, targetId)
            val mergedVids = mergedRefs.map { it.videoId }.toSet()
            // Remove refs no longer present, then upsert the merged set (positions updated).
            for (ref in allRefs[targetId].orEmpty()) {
                if (ref.videoId !in mergedVids) playlistDao.removeVideoFromPlaylist(targetId, ref.videoId)
            }
            for (ref in mergedRefs) playlistDao.insertPlaylistVideoCrossRef(ref)
        }
    }

    private fun resolveLocalId(
        cp: CanonicalPlaylist,
        bySyncId: Map<String, io.github.aedev.flow.data.local.entity.PlaylistEntity>,
        byYoutubeId: Map<String, io.github.aedev.flow.data.local.entity.PlaylistEntity>,
    ): String? {
        if (cp.syncId == CanonicalPlaylist.RESERVED_WATCH_LATER) return PlaylistMapper.WATCH_LATER_ID
        bySyncId[cp.syncId]?.let { return it.id }
        if (cp.origin == CanonicalPlaylist.ORIGIN_YOUTUBE && cp.youtubeId != null) {
            byYoutubeId[cp.youtubeId]?.let { return it.id }
        }
        return null
    }

    private fun newLocalId(cp: CanonicalPlaylist): String = when {
        cp.syncId == CanonicalPlaylist.RESERVED_WATCH_LATER -> PlaylistMapper.WATCH_LATER_ID
        cp.origin == CanonicalPlaylist.ORIGIN_YOUTUBE && cp.youtubeId != null -> cp.youtubeId
        else -> "sync_${UUID.randomUUID()}"
    }

    // --- brain (stateful: G-Counter sidecar) ---

    suspend fun readBrain(myDevice: String, hlc: String): CanonicalBrain {
        val local = exportLocalBrain()
        var sidecar = attributeLocalGrowth(brainCrdtStore.load(), myDevice, local)
        brainCrdtStore.save(sidecar)
        return BrainMapper.toCanonical(local, myDevice, hlc, sidecar.idfDocs, sidecar.interactions, sidecar.idfWords)
    }

    /** Read local brain, merge the incoming brain into it (CRDT), and persist + reload the engine. */
    suspend fun mergeAndWriteBrain(remote: CanonicalBrain, myDevice: String, hlc: String) {
        val local = exportLocalBrain()
        var sidecar = attributeLocalGrowth(brainCrdtStore.load(), myDevice, local)
        val localCanonical = BrainMapper.toCanonical(local, myDevice, hlc, sidecar.idfDocs, sidecar.interactions, sidecar.idfWords)
        val merged = BrainMerger.merge(localCanonical, remote)
        val mergedBrain = BrainMapper.writeBack(merged, local)
        neuroEngine.importBrainFromStream(ByteArrayInputStream(BrainMapper.serialize(mergedBrain)))
        sidecar = BrainCrdtState.afterMerge(sidecar, merged)
        brainCrdtStore.save(sidecar)
    }

    private suspend fun exportLocalBrain(): BrainMapper.SBrain {
        val bytes = ByteArrayOutputStream().use { bos ->
            neuroEngine.exportBrainToStream(bos)
            bos.toByteArray()
        }
        return runCatching { BrainMapper.parse(bytes) }.getOrDefault(BrainMapper.SBrain())
    }

    private fun attributeLocalGrowth(state: BrainCrdtState, myDevice: String, brain: BrainMapper.SBrain) =
        BrainCrdtState.attributeLocal(
            state = state,
            myDevice = myDevice,
            idfDocsScalar = brain.idfTotalDocuments.toLong(),
            interactionsScalar = brain.interactions.toLong(),
            idfWordCounts = brain.idfWordFrequency.mapValues { it.value.toLong() },
        )
}
