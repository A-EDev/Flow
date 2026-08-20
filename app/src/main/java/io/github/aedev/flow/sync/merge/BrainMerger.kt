package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.BrainFlags
import io.github.aedev.flow.sync.canonical.BrainLwwMaps
import io.github.aedev.flow.sync.canonical.BrainPerVideo
import io.github.aedev.flow.sync.canonical.BrainSets
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalVector
import io.github.aedev.flow.sync.canonical.Lww

/**
 * Brain merge. Every field is a join-semilattice op so the merge is commutative,
 * associative, and idempotent:
 * - additive counters (idf docs/interactions/word-freq): **G-Counter** (per-device max, value=sum)
 *   — the per-device breakdown is maintained by the sidecar so repeat syncs never double-count.
 * - learned vectors / affinity maps: **per-key max** (preserves the stronger signal from either
 *   device — a 5-video device can never erase a 1000-video one).
 * - `perVideo` progress: **max-register** per key.
 * - `lwwMaps` (suppressions, rejection patterns, topic evidence, feed history, channel strikes):
 *   **LWW register** per key, higher HLC wins. Keys present on only one side always survive.
 * - blocklists / preferred topics: **OR-Set** union of add and remove stamps, so an explicit
 *   unblock propagates instead of being resurrected by the other device's stale add.
 * - onboarding flag: OR.
 *
 * The field grouping and per-field semantics mirror the desktop's `merge.rs` exactly; both sides
 * must converge on the *same* numbers, not merely converge.
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
            vectors = mergeVectors(local.vectors, remote.vectors),
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

    private fun mergeVectors(
        a: CanonicalBrainVectors,
        b: CanonicalBrainVectors,
    ) = CanonicalBrainVectors(
        globalVector = mergeVector(a.globalVector, b.globalVector),
        timeVectors = Crdt.mergeKeyed(a.timeVectors, b.timeVectors) { x, y -> mergeVector(x, y) },
        shortsVector = mergeVector(a.shortsVector, b.shortsVector),
        topicAffinities = Crdt.mergeMaxDouble(a.topicAffinities, b.topicAffinities),
        channelScores = Crdt.mergeMaxDouble(a.channelScores, b.channelScores),
        channelTopicProfiles =
            Crdt.mergeKeyed(a.channelTopicProfiles, b.channelTopicProfiles) { x, y ->
                Crdt.mergeMaxDouble(x, y)
            },
    )

    private fun mergeVector(
        a: CanonicalVector,
        b: CanonicalVector,
    ) = CanonicalVector(
        topics = Crdt.mergeMaxDouble(a.topics, b.topics),
        dims = Crdt.mergeMaxDouble(a.dims, b.dims),
    )
}
