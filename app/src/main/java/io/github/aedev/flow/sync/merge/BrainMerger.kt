package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalFeedEntry
import io.github.aedev.flow.sync.canonical.CanonicalRejectionSignal
import io.github.aedev.flow.sync.canonical.CanonicalTopicEvidence
import io.github.aedev.flow.sync.canonical.CanonicalVector

/**
 * Brain merge. Every field is a join-semilattice op so the merge is commutative,
 * associative, and idempotent:
 * - additive counters (idf docs/interactions/word-freq): **G-Counter** (per-device max, value=sum)
 *   — the per-device breakdown is maintained by the sidecar so repeat syncs never double-count.
 * - learned vectors / affinity maps: **per-key max** (preserves the stronger signal from either
 *   device — a 5-video device can never erase a 1000-video one).
 * - timestamp maps: max (suppression/seen/feed) or min (first-seen).
 * - blocklists / preferred topics: **OR-Set union**.
 * - onboarding flag: OR.
 */
object BrainMerger {

    fun merge(local: CanonicalBrain, remote: CanonicalBrain): CanonicalBrain = CanonicalBrain(
        schema = maxOf(local.schema, remote.schema),
        deviceId = local.deviceId,
        hlc = Crdt.maxHlc(local.hlc, remote.hlc),
        vectors = mergeVectors(local.vectors, remote.vectors),
        idfTotalDocuments = local.idfTotalDocuments.merge(remote.idfTotalDocuments),
        totalInteractions = local.totalInteractions.merge(remote.totalInteractions),
        idfWordFrequency = Crdt.mergeKeyed(local.idfWordFrequency, remote.idfWordFrequency) { a, b -> a.merge(b) },
        watchHistoryMap = Crdt.mergeMaxFloat(local.watchHistoryMap, remote.watchHistoryMap),
        seenShortsHistory = Crdt.mergeMaxLong(local.seenShortsHistory, remote.seenShortsHistory),
        suppressedVideoIds = Crdt.mergeMaxLong(local.suppressedVideoIds, remote.suppressedVideoIds),
        suppressedChannels = Crdt.mergeMaxLong(local.suppressedChannels, remote.suppressedChannels),
        rejectionPatterns = Crdt.mergeKeyed(local.rejectionPatterns, remote.rejectionPatterns) { a, b ->
            CanonicalRejectionSignal(maxOf(a.count, b.count), maxOf(a.lastRejectedAt, b.lastRejectedAt))
        },
        feedHistory = Crdt.mergeKeyed(local.feedHistory, remote.feedHistory) { a, b ->
            CanonicalFeedEntry(maxOf(a.lastShown, b.lastShown), maxOf(a.showCount, b.showCount))
        },
        topicEvidence = Crdt.mergeKeyed(local.topicEvidence, remote.topicEvidence) { a, b -> mergeEvidence(a, b) },
        blockedTopics = Crdt.orSetUnion(local.blockedTopics, remote.blockedTopics),
        blockedChannels = Crdt.orSetUnion(local.blockedChannels, remote.blockedChannels),
        preferredTopics = Crdt.orSetUnion(local.preferredTopics, remote.preferredTopics),
        hasCompletedOnboarding = local.hasCompletedOnboarding || remote.hasCompletedOnboarding,
    )

    private fun mergeVectors(a: CanonicalBrainVectors, b: CanonicalBrainVectors) = CanonicalBrainVectors(
        globalVector = mergeVector(a.globalVector, b.globalVector),
        timeVectors = Crdt.mergeKeyed(a.timeVectors, b.timeVectors) { x, y -> mergeVector(x, y) },
        shortsVector = mergeVector(a.shortsVector, b.shortsVector),
        topicAffinities = Crdt.mergeMaxDouble(a.topicAffinities, b.topicAffinities),
        channelScores = Crdt.mergeMaxDouble(a.channelScores, b.channelScores),
        channelTopicProfiles = Crdt.mergeKeyed(a.channelTopicProfiles, b.channelTopicProfiles) { x, y ->
            Crdt.mergeMaxDouble(x, y)
        },
    )

    private fun mergeVector(a: CanonicalVector, b: CanonicalVector) = CanonicalVector(
        topics = Crdt.mergeMaxDouble(a.topics, b.topics),
        duration = maxOf(a.duration, b.duration),
        pacing = maxOf(a.pacing, b.pacing),
        complexity = maxOf(a.complexity, b.complexity),
        isLive = maxOf(a.isLive, b.isLive),
    )

    private fun mergeEvidence(a: CanonicalTopicEvidence, b: CanonicalTopicEvidence) = CanonicalTopicEvidence(
        positiveSignals = maxOf(a.positiveSignals, b.positiveSignals),
        watchSignals = maxOf(a.watchSignals, b.watchSignals),
        explicitSignals = maxOf(a.explicitSignals, b.explicitSignals),
        positiveScore = maxOf(a.positiveScore, b.positiveScore),
        videoIds = Crdt.orSetUnion(a.videoIds, b.videoIds),
        channelIds = Crdt.orSetUnion(a.channelIds, b.channelIds),
        firstSeenAt = minNonZero(a.firstSeenAt, b.firstSeenAt),
        lastSeenAt = maxOf(a.lastSeenAt, b.lastSeenAt),
    )

    private fun minNonZero(a: Long, b: Long) = when {
        a == 0L -> b
        b == 0L -> a
        else -> minOf(a, b)
    }
}
