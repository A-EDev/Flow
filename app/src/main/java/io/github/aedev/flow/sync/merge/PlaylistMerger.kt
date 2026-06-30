package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.canonical.CanonicalPlaylistItem

/**
 * Playlist merge. Playlists are matched by `syncId` (the mapper is responsible for
 * adopting a shared syncId across the matching rules before merge). Metadata is LWW(HLC);
 * `items` form an OR-Map keyed by `videoId` with per-item tombstones, LWW positions, and a
 * deterministic re-rank to 0..n-1 so manual reorders converge and `a ⊕ a == a`.
 */
object PlaylistMerger {

    private val WHITESPACE = Regex("\\s+")

    fun merge(local: List<CanonicalPlaylist>, remote: List<CanonicalPlaylist>): List<CanonicalPlaylist> {
        val reconciled = reconcileBySyncId(local, remote)
        val byId = LinkedHashMap<String, CanonicalPlaylist>(local.size + reconciled.size)
        for (p in local) byId[p.syncId] = normalize(p)
        for (p in reconciled) {
            val e = byId[p.syncId]
            byId[p.syncId] = if (e == null) normalize(p) else mergeOne(e, p)
        }
        return byId.values.sortedBy { it.syncId }
    }

    private fun reconcileBySyncId(
        local: List<CanonicalPlaylist>,
        remote: List<CanonicalPlaylist>,
    ): List<CanonicalPlaylist> {
        val localSyncIds = local.mapTo(HashSet()) { it.syncId }
        val localByTitle = HashMap<String, String>()
        for (p in local) if (isReconcilable(p)) localByTitle.putIfAbsent(titleKey(p), p.syncId)
        if (localByTitle.isEmpty()) return remote
        return remote.map { r ->
            if (r.syncId in localSyncIds || !isReconcilable(r)) return@map r
            val adopted = localByTitle[titleKey(r)] ?: return@map r
            r.copy(syncId = adopted)
        }
    }

    private fun isReconcilable(p: CanonicalPlaylist): Boolean =
        !p.deleted &&
            p.isUserCreated &&
            p.origin == CanonicalPlaylist.ORIGIN_LOCAL &&
            p.syncId != CanonicalPlaylist.RESERVED_WATCH_LATER &&
            p.title.isNotBlank()

    private fun titleKey(p: CanonicalPlaylist): String =
        (if (p.isMusic) "m:" else "v:") + p.title.trim().lowercase().replace(WHITESPACE, " ")

    fun mergeOne(x: CanonicalPlaylist, y: CanonicalPlaylist): CanonicalPlaylist {
        val winner = Crdt.preferByHlc(x, x.updatedHlc, y, y.updatedHlc) { it.title }
        val loser = if (winner === x) y else x
        return winner.copy(
            syncId = x.syncId,
            title = Crdt.ifEmptyOther(winner.title, loser.title),
            description = Crdt.ifEmptyOther(winner.description, loser.description),
            youtubeId = winner.youtubeId ?: loser.youtubeId,
            createdAtMs = earliest(x.createdAtMs, y.createdAtMs),
            updatedHlc = Crdt.maxHlc(x.updatedHlc, y.updatedHlc),
            deleted = Crdt.resolveDeleted(x.deleted, x.updatedHlc, y.deleted, y.updatedHlc),
            isMusic = x.isMusic || y.isMusic,
            isProtected = x.isProtected || y.isProtected,
            items = mergeItems(x.items, y.items),
        )
    }

    private fun mergeItems(
        a: List<CanonicalPlaylistItem>,
        b: List<CanonicalPlaylistItem>,
    ): List<CanonicalPlaylistItem> {
        val byVid = LinkedHashMap<String, CanonicalPlaylistItem>(a.size + b.size)
        for (it in a) byVid[it.videoId] = it
        for (it in b) {
            val e = byVid[it.videoId]
            byVid[it.videoId] = if (e == null) it else mergeItem(e, it)
        }
        // Stable order by (position, videoId), then re-rank to 0..n-1 (canonical, convergent).
        return byVid.values
            .sortedWith(compareBy({ it.position }, { it.videoId }))
            .mapIndexed { idx, item -> item.copy(position = idx.toLong()) }
    }

    private fun mergeItem(x: CanonicalPlaylistItem, y: CanonicalPlaylistItem): CanonicalPlaylistItem {
        val posWinner = Crdt.preferByHlc(x, x.hlc, y, y.hlc) { it.position.toString() }
        val metaWinner = Crdt.preferByHlc(x, x.hlc, y, y.hlc) { it.title }
        val loser = if (metaWinner === x) y else x
        return CanonicalPlaylistItem(
            videoId = x.videoId,
            position = posWinner.position,
            addedAtMs = earliest(x.addedAtMs, y.addedAtMs),
            deleted = Crdt.resolveDeleted(x.deleted, x.hlc, y.deleted, y.hlc),
            title = Crdt.ifEmptyOther(metaWinner.title, loser.title),
            channelName = Crdt.ifEmptyOther(metaWinner.channelName, loser.channelName),
            channelId = Crdt.ifEmptyOther(metaWinner.channelId, loser.channelId),
            thumbnailUrl = Crdt.ifEmptyOther(metaWinner.thumbnailUrl, loser.thumbnailUrl),
            durationSeconds = maxOf(x.durationSeconds, y.durationSeconds),
            isMusic = x.isMusic || y.isMusic,
            hlc = Crdt.maxHlc(x.hlc, y.hlc),
        )
    }

    /** Canonical form: items sorted + re-ranked so a single playlist is its own merge fixpoint. */
    private fun normalize(p: CanonicalPlaylist): CanonicalPlaylist {
        if (p.items.isEmpty()) return p
        val ranked = p.items
            .sortedWith(compareBy({ it.position }, { it.videoId }))
            .mapIndexed { idx, item -> item.copy(position = idx.toLong()) }
        return p.copy(items = ranked)
    }

    private fun earliest(a: Long, b: Long): Long = when {
        a == 0L -> b
        b == 0L -> a
        else -> minOf(a, b)
    }
}
