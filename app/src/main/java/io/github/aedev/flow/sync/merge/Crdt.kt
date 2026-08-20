package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.identity.Hlc

/**
 * Shared CRDT primitives. Every helper here is **commutative, associative, and
 * idempotent** so that any number of bidirectional merges in any order converge to one state
 * (proven by the property tests). The per-collection mergers compose these.
 *
 * The keyed CRDT *types* (`GCounter`, `OrSet`, `Lww`) live with the wire model in
 * `sync/canonical` because their serialized shape is part of the protocol; this object holds the
 * record-level helpers the collection mergers share.
 */
object Crdt {

    /** Compare two HLC strings by their total order. */
    fun compareHlc(a: String, b: String): Int = Hlc.compareEncoded(a, b)

    /** LWW string of an HLC; on a tie picks the lexicographically larger so it stays commutative. */
    fun maxHlc(a: String, b: String): String = Hlc.maxEncoded(a, b)

    /**
     * Pick the "winning" record for LWW: higher HLC wins; on a tie the larger [contentKey] wins
     * (keeps the choice independent of argument order → commutative).
     */
    fun <T> preferByHlc(x: T, xHlc: String, y: T, yHlc: String, contentKey: (T) -> String): T {
        val c = compareHlc(xHlc, yHlc)
        return when {
            c > 0 -> x
            c < 0 -> y
            else -> if (contentKey(x) >= contentKey(y)) x else y
        }
    }

    /** Tombstone resolution for exactly-two records keyed the same. */
    fun resolveDeleted(xDeleted: Boolean, xHlc: String, yDeleted: Boolean, yHlc: String): Boolean {
        if (xDeleted == yDeleted) return xDeleted
        // exactly one is a tombstone; it wins iff its HLC ≥ the live record's HLC.
        return if (xDeleted) compareHlc(xHlc, yHlc) >= 0 else compareHlc(yHlc, xHlc) >= 0
    }

    fun ifEmptyOther(a: String, b: String): String = a.ifEmpty { b }

    fun mergeMaxFloat(a: Map<String, Float>, b: Map<String, Float>): Map<String, Float> {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        val out = HashMap(a)
        for ((k, v) in b) out[k] = maxOf(out[k] ?: Float.NEGATIVE_INFINITY, v)
        return out
    }

    fun mergeMaxDouble(a: Map<String, Double>, b: Map<String, Double>): Map<String, Double> {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        val out = HashMap(a)
        for ((k, v) in b) out[k] = maxOf(out[k] ?: Double.NEGATIVE_INFINITY, v)
        return out
    }

    /** Merge two keyed records into a map by applying [combine] on key collisions. */
    inline fun <T> mergeKeyed(
        a: Map<String, T>,
        b: Map<String, T>,
        combine: (T, T) -> T,
    ): Map<String, T> {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        val out = HashMap(a)
        for ((k, v) in b) {
            val existing = out[k]
            out[k] = if (existing == null) v else combine(existing, v)
        }
        return out
    }
}
