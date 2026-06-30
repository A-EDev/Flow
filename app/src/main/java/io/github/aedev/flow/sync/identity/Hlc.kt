package io.github.aedev.flow.sync.identity

/**
 * Hybrid Logical Clock (Kulkarni et al.) — the timestamp used for every last-write-wins
 * decision in the sync merge engine.
 *
 * Serialized form: `"<physicalMs>:<counter>:<node>"`,
 * where [node] is the first 8 chars of the lowercase, hyphen-less device UUID. The total order
 * is (physicalMs, counter, node) — which respects causality and never silently loses a write to
 * wall-clock skew
 *
 * This type is immutable and comparable. Issuing/merging clocks is done by [HlcClock].
 */
data class Hlc(
    val physicalMs: Long,
    val counter: Int,
    val node: String,
) : Comparable<Hlc> {

    override fun compareTo(other: Hlc): Int {
        if (physicalMs != other.physicalMs) return physicalMs.compareTo(other.physicalMs)
        if (counter != other.counter) return counter.compareTo(other.counter)
        return node.compareTo(other.node)
    }

    /** Canonical string form. */
    fun encode(): String = "$physicalMs:$counter:$node"

    companion object {
        /** The lowest possible clock — sorts before any real stamp. Used as a default/"absent". */
        val ZERO = Hlc(0L, 0, "")

        /**
         * Parse the `"physicalMs:counter:node"` form. Returns [ZERO] on any malformed input so a
         * corrupt remote stamp degrades to "oldest" rather than throwing mid-merge.
         */
        fun decode(value: String?): Hlc {
            if (value.isNullOrEmpty()) return ZERO
            // node may itself be empty; split into at most 3 parts and keep the remainder as node.
            val first = value.indexOf(':')
            if (first < 0) return ZERO
            val second = value.indexOf(':', first + 1)
            if (second < 0) return ZERO
            val pt = value.substring(0, first).toLongOrNull() ?: return ZERO
            val c = value.substring(first + 1, second).toIntOrNull() ?: return ZERO
            val node = value.substring(second + 1)
            return Hlc(pt, c, node)
        }

        /** Derive the 8-char HLC node id from a device UUID. */
        fun nodeFromDeviceId(deviceId: String): String =
            deviceId.lowercase().replace("-", "").take(8)
    }
}

/**
 * Mutable, thread-safe issuer of [Hlc] stamps for a single device.
 *
 * - [now] stamps a local mutation (send / local event).
 * - [update] folds in a clock observed from a peer (receive event), keeping our clock ahead.
 *
 * The physical component is the wall clock; [physicalNow] is injectable so tests are
 * deterministic. All transitions are guaranteed monotonic non-decreasing.
 */
class HlcClock(
    private val node: String,
    private val physicalNow: () -> Long = { System.currentTimeMillis() },
) {
    private var lastPhysical: Long = 0L
    private var lastCounter: Int = 0
    private val lock = Any()

    /** Stamp a local event. Monotonic: never returns a value ≤ a previously returned one. */
    fun now(): Hlc = synchronized(lock) {
        val pt = physicalNow()
        val prevPhysical = lastPhysical
        lastPhysical = maxOf(prevPhysical, pt)
        lastCounter = if (lastPhysical == prevPhysical) lastCounter + 1 else 0
        Hlc(lastPhysical, lastCounter, node)
    }

    /** Fold in a peer's observed clock, then stamp the receive event. */
    fun update(remote: Hlc): Hlc = synchronized(lock) {
        val pt = physicalNow()
        val prevPhysical = lastPhysical
        val prevCounter = lastCounter
        val newPhysical = maxOf(prevPhysical, remote.physicalMs, pt)
        val newCounter = when {
            newPhysical == prevPhysical && newPhysical == remote.physicalMs ->
                maxOf(prevCounter, remote.counter) + 1
            newPhysical == prevPhysical -> prevCounter + 1
            newPhysical == remote.physicalMs -> remote.counter + 1
            else -> 0
        }
        lastPhysical = newPhysical
        lastCounter = newCounter
        Hlc(newPhysical, newCounter, node)
    }

    /** Seed the clock from a previously persisted high-water mark (e.g. on startup). */
    fun observe(remote: Hlc) {
        synchronized(lock) {
            if (remote.physicalMs > lastPhysical ||
                (remote.physicalMs == lastPhysical && remote.counter > lastCounter)
            ) {
                lastPhysical = remote.physicalMs
                lastCounter = remote.counter
            }
        }
    }
}
