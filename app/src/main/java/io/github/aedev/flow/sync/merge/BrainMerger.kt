package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.BrainFlags
import io.github.aedev.flow.sync.canonical.BrainLwwMaps
import io.github.aedev.flow.sync.canonical.BrainPerVideo
import io.github.aedev.flow.sync.canonical.BrainSets
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.Lww

/**
 * Brain merge for everything except the learned vectors. Every field is a join-semilattice op so
 * the merge is commutative, associative, and idempotent:
 * - additive counters (idf docs/interactions/word-freq): **G-Counter** (per-device max, value=sum)
 *   — the per-device breakdown is maintained by the sidecar so repeat syncs never double-count, and
 *   [CounterOwnership] then settles who may write each entry.
 * - `perVideo` progress: **max-register** per key.
 * - `lwwMaps` (suppressions, rejection patterns, topic evidence, feed history, channel strikes):
 *   **LWW register** per key, higher HLC wins. Keys present on only one side always survive.
 * - blocklists / preferred topics: **OR-Set** union of add and remove stamps, so an explicit
 *   unblock propagates instead of being resurrected by the other device's stale add.
 * - onboarding flag: OR.
 *
 * The learned vectors are left empty here: they are an experience-weighted mean over every
 * device's snapshot, which no pairwise merge can compute. [BrainVectorBlend] derives them.
 */
object BrainMerger {
    fun merge(
        local: CanonicalBrain,
        remote: CanonicalBrain,
    ): CanonicalBrain =
        CanonicalBrain(
            schema = maxOf(local.schema, remote.schema),
            deviceId = local.deviceId,
            hlc = Crdt.maxHlc(local.hlc, remote.hlc),
            vectors = CanonicalBrainVectors(),
            counters =
                BrainCounters(
                    idfTotalDocuments = local.counters.idfTotalDocuments.merge(remote.counters.idfTotalDocuments),
                    totalInteractions = local.counters.totalInteractions.merge(remote.counters.totalInteractions),
                ),
            idfWordFrequency = Crdt.mergeKeyed(local.idfWordFrequency, remote.idfWordFrequency) { a, b -> a.merge(b) },
            perVideo =
                BrainPerVideo(
                    watchHistoryMap = Crdt.mergeMaxFloat(local.perVideo.watchHistoryMap, remote.perVideo.watchHistoryMap),
                    watchSignalProgress =
                        Crdt.mergeMaxFloat(
                            local.perVideo.watchSignalProgress,
                            remote.perVideo.watchSignalProgress,
                        ),
                ),
            sets =
                BrainSets(
                    blockedTopics = local.sets.blockedTopics.merge(remote.sets.blockedTopics),
                    blockedChannels = local.sets.blockedChannels.merge(remote.sets.blockedChannels),
                    preferredTopics = local.sets.preferredTopics.merge(remote.sets.preferredTopics),
                ),
            lwwMaps =
                BrainLwwMaps(
                    suppressedVideoIds = mergeLww(local.lwwMaps.suppressedVideoIds, remote.lwwMaps.suppressedVideoIds),
                    suppressedChannels = mergeLww(local.lwwMaps.suppressedChannels, remote.lwwMaps.suppressedChannels),
                    rejectionPatterns = mergeLww(local.lwwMaps.rejectionPatterns, remote.lwwMaps.rejectionPatterns),
                    topicEvidence = mergeLww(local.lwwMaps.topicEvidence, remote.lwwMaps.topicEvidence),
                    feedHistory = mergeLww(local.lwwMaps.feedHistory, remote.lwwMaps.feedHistory),
                    channelStrikes = mergeLww(local.lwwMaps.channelStrikes, remote.lwwMaps.channelStrikes),
                ),
            flags =
                BrainFlags(
                    hasCompletedOnboarding = local.flags.hasCompletedOnboarding || remote.flags.hasCompletedOnboarding,
                ),
        )

    private fun <T> mergeLww(
        a: Map<String, Lww<T>>,
        b: Map<String, Lww<T>>,
    ): Map<String, Lww<T>> = Crdt.mergeKeyed(a, b) { x, y -> x.merge(y) }
}
