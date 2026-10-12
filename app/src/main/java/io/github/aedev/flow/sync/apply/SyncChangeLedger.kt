package io.github.aedev.flow.sync.apply

import kotlinx.serialization.Serializable

/** How long a deletion keeps being announced, matching the subscription and desktop tombstones. */
const val TOMBSTONE_TTL_MS: Long = 365L * 24 * 60 * 60 * 1000

/**
 * What one record looked like at the last sync: a [hash] of the fields an edit can change and the
 * time it last changed here ([atMs]). A [deleted] entry is a tombstone still to be announced;
 * [record] holds the canonical JSON to send for it (verbatim when [fromPeer]), and for a live
 * playlist the template a later tombstone is built from.
 */
@Serializable
data class LedgerEntry(
    val hash: Int = 0,
    val atMs: Long = 0,
    val deleted: Boolean = false,
    val record: String? = null,
    val fromPeer: Boolean = false,
)

/** A deletion that arrived from a peer, kept so it reaches devices that never talked to that peer. */
data class PeerTombstone(
    val atMs: Long,
    val record: String,
)

/**
 * The last-synced state of one collection. Most of the app's stores keep neither an edit time nor
 * any trace of what was removed, so both are recovered by diffing the current records against
 * this: a changed hash means an edit since the last sync, a key that disappeared means a deletion.
 * Without it, every edit was stamped with the time of the sync (always beating the peer) and every
 * deletion was simply undone by the peer's copy.
 */
@Serializable
data class CollectionLedger(
    val initialized: Boolean = false,
    val entries: Map<String, LedgerEntry> = emptyMap(),
) {
    /** The stamp to send for a live record: when it last changed here. */
    fun stampOf(key: String): Long = entries[key]?.takeUnless { it.deleted }?.atMs ?: 0L

    fun tombstones(): Map<String, LedgerEntry> = entries.filterValues { it.deleted }

    /**
     * Fold the current records ([current]: key → content hash) into the ledger. The first run has
     * nothing to diff against, so every record gets stamp 0: an edit or deletion a peer made since
     * then wins over it, the same rule the desktop applies to records it has no edit time for.
     */
    fun observe(
        current: Map<String, Int>,
        nowMs: Long,
        templates: Map<String, String> = emptyMap(),
    ): CollectionLedger {
        if (!initialized) {
            return CollectionLedger(
                initialized = true,
                entries = current.mapValues { (key, hash) -> LedgerEntry(hash = hash, record = templates[key]) },
            )
        }
        val out = HashMap<String, LedgerEntry>(current.size + entries.size)
        for ((key, hash) in current) {
            val previous = entries[key]
            val atMs =
                when {
                    previous == null || previous.deleted || previous.hash != hash -> nowMs
                    else -> previous.atMs
                }
            out[key] = LedgerEntry(hash = hash, atMs = atMs, record = templates[key] ?: previous?.liveRecord())
        }
        val cutoff = nowMs - TOMBSTONE_TTL_MS
        for ((key, previous) in entries) {
            if (key in current) continue
            when {
                !previous.deleted -> out[key] = LedgerEntry(atMs = nowMs, deleted = true, record = previous.record)
                previous.atMs >= cutoff -> out[key] = previous
            }
        }
        return CollectionLedger(initialized = true, entries = out)
    }

    /**
     * Adopt the state a merge just wrote. Live records take the stamp of the version that won
     * ([stamps]), so the next export re-sends that stamp instead of a stale one; deletions this
     * device or a peer made stay as tombstones until they expire.
     */
    fun settle(
        current: Map<String, Int>,
        stamps: Map<String, Long>,
        peerTombstones: Map<String, PeerTombstone>,
        templates: Map<String, String> = emptyMap(),
    ): CollectionLedger {
        val out = HashMap<String, LedgerEntry>(current.size + entries.size)
        for ((key, hash) in current) {
            val previous = entries[key]?.takeUnless { it.deleted }
            out[key] =
                LedgerEntry(
                    hash = hash,
                    atMs = stamps[key] ?: previous?.atMs ?: 0L,
                    record = templates[key] ?: previous?.record,
                )
        }
        for ((key, entry) in entries) {
            if (key !in current && entry.deleted) out[key] = entry
        }
        for ((key, tombstone) in peerTombstones) {
            if (key in current) continue
            val mine = out[key]
            if (mine == null || !mine.deleted || mine.atMs < tombstone.atMs) {
                out[key] = LedgerEntry(atMs = tombstone.atMs, deleted = true, record = tombstone.record, fromPeer = true)
            }
        }
        return CollectionLedger(initialized = true, entries = out)
    }

    private fun LedgerEntry.liveRecord(): String? = record.takeUnless { deleted }
}
