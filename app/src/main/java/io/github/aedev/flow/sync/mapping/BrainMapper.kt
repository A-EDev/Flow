package io.github.aedev.flow.sync.mapping

import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalFeedEntry
import io.github.aedev.flow.sync.canonical.CanonicalRejectionSignal
import io.github.aedev.flow.sync.canonical.CanonicalTopicEvidence
import io.github.aedev.flow.sync.canonical.CanonicalVector
import io.github.aedev.flow.sync.canonical.GCounter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Mirror of the app's on-disk `SerializableBrain` (NeuroStorage, schemaVersion 13)
 * Maps the brain to/from [CanonicalBrain]. Additive counters become per-device
 * G-Counters (the per-device breakdown comes from the sync sidecar); learned vectors and
 * timestamp maps are join-semilattice merges (max/union) — see [io.github.aedev.flow.sync.merge.BrainMerger].
 */
object BrainMapper {

    const val SCHEMA_VERSION = 13

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class SVector(
        val topics: Map<String, Double> = emptyMap(),
        val duration: Double = 0.5,
        val pacing: Double = 0.5,
        val complexity: Double = 0.5,
        val isLive: Double = 0.0,
    )

    @Serializable
    data class SFeedEntry(val lastShown: Long = 0L, val showCount: Int = 0)

    @Serializable
    data class SRejectionSignal(val count: Int = 0, val lastRejectedAt: Long = 0L)

    @Serializable
    data class STopicEvidence(
        val positiveSignals: Int = 0,
        val watchSignals: Int = 0,
        val explicitSignals: Int = 0,
        val positiveScore: Double = 0.0,
        val videoIds: Set<String> = emptySet(),
        val channelIds: Set<String> = emptySet(),
        val firstSeenAt: Long = 0L,
        val lastSeenAt: Long = 0L,
    )

    @Serializable
    data class SBrain(
        val schemaVersion: Int = SCHEMA_VERSION,
        val timeVectors: Map<String, SVector> = emptyMap(),
        val global: SVector = SVector(),
        val channelScores: Map<String, Double> = emptyMap(),
        val topicAffinities: Map<String, Double> = emptyMap(),
        val interactions: Int = 0,
        val consecutiveSkips: Int = 0,
        val blockedTopics: Set<String> = emptySet(),
        val blockedChannels: Set<String> = emptySet(),
        val preferredTopics: Set<String> = emptySet(),
        val hasCompletedOnboarding: Boolean = false,
        val lastPersona: String? = null,
        val personaStability: Int = 0,
        val idfWordFrequency: Map<String, Int> = emptyMap(),
        val idfTotalDocuments: Int = 0,
        val watchHistoryMap: Map<String, Float> = emptyMap(),
        val seenShortsHistory: Map<String, Long> = emptyMap(),
        val channelTopicProfiles: Map<String, Map<String, Double>> = emptyMap(),
        val shortsVector: SVector = SVector(),
        val suppressedVideoIds: Map<String, Long> = emptyMap(),
        val suppressedChannels: Map<String, Long> = emptyMap(),
        val rejectionPatterns: Map<String, SRejectionSignal> = emptyMap(),
        val feedHistory: Map<String, SFeedEntry> = emptyMap(),
        val recentQueryTokens: List<List<String>> = emptyList(),
        val topicEvidence: Map<String, STopicEvidence> = emptyMap(),
    )

    fun parse(jsonBytes: ByteArray): SBrain =
        json.decodeFromString(SBrain.serializer(), String(jsonBytes, Charsets.UTF_8))

    fun serialize(brain: SBrain): ByteArray =
        json.encodeToString(SBrain.serializer(), brain).toByteArray(Charsets.UTF_8)

    // --- vector conversions ---

    private fun SVector.toCanonical() = CanonicalVector(topics, duration, pacing, complexity, isLive)
    private fun CanonicalVector.toS() = SVector(topics, duration, pacing, complexity, isLive)

    /**
     * Build the canonical brain. Counters are shipped as G-Counters using the per-device maps from
     * the sidecar ([idfDocsPerDevice], [interactionsPerDevice], [idfWordsPerDevice]).
     */
    fun toCanonical(
        brain: SBrain,
        deviceId: String,
        hlc: String,
        idfDocsPerDevice: Map<String, Long>,
        interactionsPerDevice: Map<String, Long>,
        idfWordsPerDevice: Map<String, Map<String, Long>>,
    ): CanonicalBrain = CanonicalBrain(
        schema = brain.schemaVersion,
        deviceId = deviceId,
        hlc = hlc,
        vectors = CanonicalBrainVectors(
            globalVector = brain.global.toCanonical(),
            timeVectors = brain.timeVectors.mapValues { it.value.toCanonical() },
            shortsVector = brain.shortsVector.toCanonical(),
            topicAffinities = brain.topicAffinities,
            channelScores = brain.channelScores,
            channelTopicProfiles = brain.channelTopicProfiles,
        ),
        idfTotalDocuments = GCounter(idfDocsPerDevice),
        totalInteractions = GCounter(interactionsPerDevice),
        idfWordFrequency = brain.idfWordFrequency.keys.associateWith { word ->
            GCounter(idfWordsPerDevice[word] ?: mapOf(deviceId to (brain.idfWordFrequency[word] ?: 0).toLong()))
        },
        watchHistoryMap = brain.watchHistoryMap,
        seenShortsHistory = brain.seenShortsHistory,
        suppressedVideoIds = brain.suppressedVideoIds,
        suppressedChannels = brain.suppressedChannels,
        rejectionPatterns = brain.rejectionPatterns.mapValues { CanonicalRejectionSignal(it.value.count, it.value.lastRejectedAt) },
        feedHistory = brain.feedHistory.mapValues { CanonicalFeedEntry(it.value.lastShown, it.value.showCount) },
        topicEvidence = brain.topicEvidence.mapValues {
            CanonicalTopicEvidence(
                positiveSignals = it.value.positiveSignals,
                watchSignals = it.value.watchSignals,
                explicitSignals = it.value.explicitSignals,
                positiveScore = it.value.positiveScore,
                videoIds = it.value.videoIds,
                channelIds = it.value.channelIds,
                firstSeenAt = it.value.firstSeenAt,
                lastSeenAt = it.value.lastSeenAt,
            )
        },
        blockedTopics = brain.blockedTopics,
        blockedChannels = brain.blockedChannels,
        preferredTopics = brain.preferredTopics,
        hasCompletedOnboarding = brain.hasCompletedOnboarding,
    )

    /**
     * Write a merged canonical brain back into a serializable brain, preserving [local]'s
     * device-local/derived fields (consecutiveSkips, lastPersona, personaStability,
     * recentQueryTokens) which are never synced.
     */
    fun writeBack(merged: CanonicalBrain, local: SBrain): SBrain = local.copy(
        schemaVersion = maxOf(local.schemaVersion, merged.schema),
        timeVectors = merged.vectors.timeVectors.mapValues { it.value.toS() }.ifEmpty { local.timeVectors },
        global = merged.vectors.globalVector.toS(),
        channelScores = merged.vectors.channelScores,
        topicAffinities = merged.vectors.topicAffinities,
        interactions = merged.totalInteractions.sum().toInt(),
        blockedTopics = merged.blockedTopics,
        blockedChannels = merged.blockedChannels,
        preferredTopics = merged.preferredTopics,
        hasCompletedOnboarding = local.hasCompletedOnboarding || merged.hasCompletedOnboarding,
        idfWordFrequency = merged.idfWordFrequency.mapValues { it.value.sum().toInt() },
        idfTotalDocuments = merged.idfTotalDocuments.sum().toInt(),
        watchHistoryMap = merged.watchHistoryMap,
        seenShortsHistory = merged.seenShortsHistory,
        channelTopicProfiles = merged.vectors.channelTopicProfiles,
        shortsVector = merged.vectors.shortsVector.toS(),
        suppressedVideoIds = merged.suppressedVideoIds,
        suppressedChannels = merged.suppressedChannels,
        rejectionPatterns = merged.rejectionPatterns.mapValues { SRejectionSignal(it.value.count, it.value.lastRejectedAt) },
        feedHistory = merged.feedHistory.mapValues { SFeedEntry(it.value.lastShown, it.value.showCount) },
        topicEvidence = merged.topicEvidence.mapValues {
            STopicEvidence(
                positiveSignals = it.value.positiveSignals,
                watchSignals = it.value.watchSignals,
                explicitSignals = it.value.explicitSignals,
                positiveScore = it.value.positiveScore,
                videoIds = it.value.videoIds,
                channelIds = it.value.channelIds,
                firstSeenAt = it.value.firstSeenAt,
                lastSeenAt = it.value.lastSeenAt,
            )
        },
    )
}
