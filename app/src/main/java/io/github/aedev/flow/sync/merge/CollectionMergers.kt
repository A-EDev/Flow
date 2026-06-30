package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.CanonicalLike
import io.github.aedev.flow.sync.canonical.CanonicalSetting
import io.github.aedev.flow.sync.canonical.CanonicalSubscriptionGroup
import io.github.aedev.flow.sync.canonical.CanonicalWatchHistory

/**
 * Per-collection merge functions. Each `merge(local, remote)` is commutative,
 * associative, and idempotent — verified by `MergeConvergenceTest`. Results are returned in a
 * canonical sort order so equal inputs yield byte-identical output.
 */

object WatchHistoryMerger {
    fun merge(
        local: List<CanonicalWatchHistory>,
        remote: List<CanonicalWatchHistory>,
    ): List<CanonicalWatchHistory> {
        val byId = LinkedHashMap<String, CanonicalWatchHistory>(local.size + remote.size)
        for (r in local) byId[r.videoId] = r
        for (r in remote) {
            val e = byId[r.videoId]
            byId[r.videoId] = if (e == null) r else mergeOne(e, r)
        }
        return byId.values.sortedBy { it.videoId }
    }

    fun mergeOne(x: CanonicalWatchHistory, y: CanonicalWatchHistory): CanonicalWatchHistory {
        val primary = Crdt.preferByHlc(x, x.hlc, y, y.hlc) { contentKey(it) }
        val secondary = if (primary === x) y else x
        return CanonicalWatchHistory(
            videoId = x.videoId,
            title = Crdt.ifEmptyOther(primary.title, secondary.title),
            channelName = Crdt.ifEmptyOther(primary.channelName, secondary.channelName),
            channelId = Crdt.ifEmptyOther(primary.channelId, secondary.channelId),
            thumbnailUrl = Crdt.ifEmptyOther(primary.thumbnailUrl, secondary.thumbnailUrl),
            watchedAtMs = maxOf(x.watchedAtMs, y.watchedAtMs),
            progress = maxOf(x.progress, y.progress),
            durationSeconds = maxOf(x.durationSeconds, y.durationSeconds),
            isMusic = x.isMusic || y.isMusic, // music↔video leak rule: never flip music off
            isShort = x.isShort || y.isShort,
            hlc = Crdt.maxHlc(x.hlc, y.hlc),
            deleted = Crdt.resolveDeleted(x.deleted, x.hlc, y.deleted, y.hlc),
        )
    }

    private fun contentKey(r: CanonicalWatchHistory) = "${r.watchedAtMs}|${r.title}|${r.channelId}"
}

object LikesMerger {
    fun merge(local: List<CanonicalLike>, remote: List<CanonicalLike>): List<CanonicalLike> {
        val byKey = LinkedHashMap<String, CanonicalLike>(local.size + remote.size)
        for (r in local) byKey[key(r)] = r
        for (r in remote) {
            val e = byKey[key(r)]
            byKey[key(r)] = if (e == null) r else mergeOne(e, r)
        }
        return byKey.values.sortedWith(compareBy({ it.kind }, { it.id }))
    }

    fun mergeOne(x: CanonicalLike, y: CanonicalLike): CanonicalLike {
        val winner = Crdt.preferByHlc(x, x.hlc, y, y.hlc) { "${it.updatedAtMs}|${it.state}" }
        val loser = if (winner === x) y else x
        return winner.copy(
            hlc = Crdt.maxHlc(x.hlc, y.hlc),
            updatedAtMs = maxOf(x.updatedAtMs, y.updatedAtMs),
            title = Crdt.ifEmptyOther(winner.title, loser.title),
            channelName = Crdt.ifEmptyOther(winner.channelName, loser.channelName),
            thumbnailUrl = Crdt.ifEmptyOther(winner.thumbnailUrl, loser.thumbnailUrl),
        )
    }

    private fun key(r: CanonicalLike) = "${r.kind}|${r.id}"
}

object SettingsMerger {
    fun merge(local: List<CanonicalSetting>, remote: List<CanonicalSetting>): List<CanonicalSetting> {
        val byKey = LinkedHashMap<String, CanonicalSetting>(local.size + remote.size)
        for (r in local) byKey[r.key] = r
        for (r in remote) {
            val e = byKey[r.key]
            byKey[r.key] = if (e == null) r else mergeOne(e, r)
        }
        return byKey.values.sortedBy { it.key }
    }

    fun mergeOne(x: CanonicalSetting, y: CanonicalSetting): CanonicalSetting {
        val winner = Crdt.preferByHlc(x, x.hlc, y, y.hlc) { it.value.toString() }
        return winner.copy(hlc = Crdt.maxHlc(x.hlc, y.hlc))
    }
}

object SubscriptionsMerger {
    fun merge(
        local: List<CanonicalSubscriptionGroup>,
        remote: List<CanonicalSubscriptionGroup>,
    ): List<CanonicalSubscriptionGroup> {
        val byName = LinkedHashMap<String, CanonicalSubscriptionGroup>(local.size + remote.size)
        for (g in local) byName[g.name] = normalize(g)
        for (g in remote) {
            val e = byName[g.name]
            byName[g.name] = if (e == null) normalize(g) else mergeOne(e, g)
        }
        return byName.values.sortedBy { it.name }
    }

    fun mergeOne(x: CanonicalSubscriptionGroup, y: CanonicalSubscriptionGroup): CanonicalSubscriptionGroup {
        val channelIds = (x.channelIds.toSet() + y.channelIds.toSet()).sorted() // OR-Set union
        val sortWinner = Crdt.preferByHlc(x, x.hlc, y, y.hlc) { it.sortOrder.toString() }
        return CanonicalSubscriptionGroup(
            name = x.name,
            channelIds = channelIds,
            sortOrder = sortWinner.sortOrder,
            hlc = Crdt.maxHlc(x.hlc, y.hlc),
            deleted = Crdt.resolveDeleted(x.deleted, x.hlc, y.deleted, y.hlc),
        )
    }

    /** Canonical form: channelIds sorted + de-duped, so `a ⊕ a == a`. */
    private fun normalize(g: CanonicalSubscriptionGroup) =
        g.copy(channelIds = g.channelIds.toSortedSet().toList())
}
