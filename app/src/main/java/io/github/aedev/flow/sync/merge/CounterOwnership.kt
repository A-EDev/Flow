package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalMusicBrain
import io.github.aedev.flow.sync.canonical.GCounter

/**
 * Who may write a G-Counter entry. Each device is the only writer of its own entry, so after a
 * plain per-device-max merge two corrections apply:
 *
 * - this device's own entries are restored from its local state, ignoring a peer's copy of them;
 * - the sender's own entries are taken from the sender as they are, not maxed.
 *
 * The second rule is what lets a peer lower its own count. Desktop builds before the attribution
 * fix inflated their own entry on every sync and now correct it once; a max merge would keep the
 * inflated copy on this phone forever. Every other device's entry still merges by max, since a
 * relayed copy may lag behind the original.
 */
object CounterOwnership {
    fun reconcile(
        merged: GCounter,
        local: GCounter,
        senderCopies: List<GCounter>,
        own: Set<String>,
        sender: Set<String>,
    ): GCounter {
        val out = HashMap(merged.perDevice)
        for (key in sender - own) {
            senderCopies.mapNotNull { it.perDevice[key] }.maxOrNull()?.let { out[key] = it }
        }
        for (key in own) {
            val mine = local.perDevice[key]
            if (mine == null) out.remove(key) else out[key] = mine
        }
        return GCounter(out)
    }

    fun brain(
        merged: CanonicalBrain,
        local: CanonicalBrain,
        remotes: List<CanonicalBrain>,
        own: Set<String>,
        sender: Set<String>,
    ): CanonicalBrain {
        fun settle(
            pick: (CanonicalBrain) -> GCounter?,
            mergedCounter: GCounter,
        ) = reconcile(mergedCounter, pick(local) ?: GCounter(), remotes.mapNotNull(pick), own, sender)

        return merged.copy(
            counters =
                BrainCounters(
                    idfTotalDocuments = settle({ it.counters.idfTotalDocuments }, merged.counters.idfTotalDocuments),
                    totalInteractions = settle({ it.counters.totalInteractions }, merged.counters.totalInteractions),
                ),
            idfWordFrequency =
                merged.idfWordFrequency
                    .mapValues { (word, counter) -> settle({ it.idfWordFrequency[word] }, counter) }
                    .filterValues { it.perDevice.isNotEmpty() },
        )
    }

    fun music(
        merged: CanonicalMusicBrain,
        local: CanonicalMusicBrain,
        remote: CanonicalMusicBrain,
        own: Set<String>,
        sender: Set<String>,
    ): CanonicalMusicBrain =
        merged.copy(
            totalPlays = reconcile(merged.totalPlays, local.totalPlays, listOf(remote.totalPlays), own, sender),
            artistAffinity =
                merged.artistAffinity.mapValues { (artist, affinity) ->
                    affinity.copy(
                        plays =
                            reconcile(
                                affinity.plays,
                                local.artistAffinity[artist]?.plays ?: GCounter(),
                                listOfNotNull(remote.artistAffinity[artist]?.plays),
                                own,
                                sender,
                            ),
                    )
                },
        )
}
