package io.github.aedev.flow.sync.mapping

import io.github.aedev.flow.data.recommendation.TimeBucket
import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.BrainFlags
import io.github.aedev.flow.sync.canonical.BrainLwwMaps
import io.github.aedev.flow.sync.canonical.BrainPerVideo
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalFeedEntry
import io.github.aedev.flow.sync.canonical.CanonicalRejectionSignal
import io.github.aedev.flow.sync.canonical.CanonicalTopicEvidence
import io.github.aedev.flow.sync.canonical.CanonicalVector
import io.github.aedev.flow.sync.canonical.GCounter
import io.github.aedev.flow.sync.canonical.Lww
import io.github.aedev.flow.sync.merge.BrainCrdtState
import io.github.aedev.flow.sync.merge.Crdt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Mirror of the app's on-disk `SerializableBrain` (NeuroStorage, schemaVersion 13)
 * Maps the brain to/from [CanonicalBrain]. Additive counters become per-device
 * G-Counters, blocklists become OR-Sets and the timestamp/evidence maps become HLC-stamped LWW
 * registers — all three sourced from the sync sidecar ([BrainCrdtState]), which is where the state
 * the brain itself cannot express is kept. See [io.github.aedev.flow.sync.merge.BrainMerger].
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

    private fun SVector.toCanonical() = CanonicalVector.of(topics, duration, pacing, complexity, isLive)
    private fun CanonicalVector.toS() = SVector(topics, duration, pacing, complexity, isLive)

    // --- time-bucket keys ---

    private val bucketsByLooseName = TimeBucket.entries.associateBy { it.name.replace("_", "").lowercase() }

    /**
     * Canonical spelling of a time-vector map key: the [TimeBucket] enum name (`WEEKDAY_EVENING`),
     * which is what the shared golden fixture uses and what the storage layer looks up.
     *
     * A desktop build that has not yet adopted that spelling sends the Rust serde variant name
     * (`WeekdayEvening`); left as-is it matches no bucket and every time-of-day vector the desktop
     * sent is dropped on write-back. Unrecognized keys are passed through untouched.
     */
    private fun canonicalBucketKey(key: String): String =
        bucketsByLooseName[key.replace("_", "").lowercase()]?.name ?: key

    /**
     * Rewrite an incoming brain's time-vector keys into this platform's spelling, merging any
     * vectors that collapse onto the same bucket. Must run **before** the merge, or the two
     * spellings survive as two separate buckets.
     */
    fun normalizeIncoming(remote: CanonicalBrain): CanonicalBrain {
        val timeVectors = remote.vectors.timeVectors
        if (timeVectors.isEmpty() || timeVectors.keys.all { canonicalBucketKey(it) == it }) return remote
        val normalized = LinkedHashMap<String, CanonicalVector>(timeVectors.size)
        for ((key, vector) in timeVectors) {
            val canonicalKey = canonicalBucketKey(key)
            val existing = normalized[canonicalKey]
            normalized[canonicalKey] = if (existing == null) {
                vector
            } else {
                CanonicalVector(
                    topics = Crdt.mergeMaxDouble(existing.topics, vector.topics),
                    dims = Crdt.mergeMaxDouble(existing.dims, vector.dims),
                )
            }
        }
        return remote.copy(vectors = remote.vectors.copy(timeVectors = normalized))
    }

    /**
     * Build the canonical brain. Per-device counters, OR-Set stamps and the wire-only fields come
     * from the sidecar [state]; every LWW register is stamped with this snapshot's [hlc] so a
     * phone-side write actually wins on the peer instead of relying on the peer's ingest-time
     * stamping (which would make it lose every merge).
     */
    fun toCanonical(
        brain: SBrain,
        deviceId: String,
        hlc: String,
        state: BrainCrdtState,
    ): CanonicalBrain = CanonicalBrain(
        schema = brain.schemaVersion,
        deviceId = deviceId,
        hlc = hlc,
        vectors = CanonicalBrainVectors(
            globalVector = brain.global.toCanonical(),
            timeVectors = brain.timeVectors.mapKeys { canonicalBucketKey(it.key) }
                .mapValues { it.value.toCanonical() },
            shortsVector = brain.shortsVector.toCanonical(),
            topicAffinities = brain.topicAffinities,
            channelScores = brain.channelScores,
            channelTopicProfiles = brain.channelTopicProfiles,
        ),
        counters = BrainCounters(
            idfTotalDocuments = GCounter(state.idfDocs),
            totalInteractions = GCounter(state.interactions),
        ),
        idfWordFrequency = brain.idfWordFrequency.keys.associateWith { word ->
            GCounter(state.idfWords[word] ?: mapOf(deviceId to (brain.idfWordFrequency[word] ?: 0).toLong()))
        },
        perVideo = BrainPerVideo(
            watchHistoryMap = brain.watchHistoryMap,
            watchSignalProgress = state.watchSignalProgress,
        ),
        sets = state.sets,
        lwwMaps = BrainLwwMaps(
            suppressedVideoIds = brain.suppressedVideoIds.mapValues { Lww(it.value, hlc) },
            suppressedChannels = brain.suppressedChannels.mapValues { Lww(it.value, hlc) },
            rejectionPatterns = brain.rejectionPatterns.mapValues {
                Lww(CanonicalRejectionSignal(it.value.count, it.value.lastRejectedAt), hlc)
            },
            topicEvidence = brain.topicEvidence.mapValues {
                Lww(
                    CanonicalTopicEvidence(
                        positiveSignals = it.value.positiveSignals,
                        watchSignals = it.value.watchSignals,
                        explicitSignals = it.value.explicitSignals,
                        positiveScore = it.value.positiveScore,
                        videoIds = it.value.videoIds,
                        channelIds = it.value.channelIds,
                        firstSeenAt = it.value.firstSeenAt,
                        lastSeenAt = it.value.lastSeenAt,
                    ),
                    hlc,
                )
            },
            feedHistory = brain.feedHistory.mapValues {
                Lww(CanonicalFeedEntry(it.value.lastShown, it.value.showCount), hlc)
            },
            channelStrikes = state.channelStrikes,
        ),
        flags = BrainFlags(hasCompletedOnboarding = brain.hasCompletedOnboarding),
    )

    /**
     * Write a merged canonical brain back into a serializable brain, preserving [local]'s
     * device-local/derived fields (consecutiveSkips, lastPersona, personaStability,
     * recentQueryTokens, seenShortsHistory) which are never synced. The wire-only fields
     * (`watchSignalProgress`, `channelStrikes`) have no slot here — the sidecar keeps them.
     */
    fun writeBack(merged: CanonicalBrain, local: SBrain): SBrain = local.copy(
        schemaVersion = maxOf(local.schemaVersion, merged.schema),
        timeVectors = merged.vectors.timeVectors.mapValues { it.value.toS() }.ifEmpty { local.timeVectors },
        global = merged.vectors.globalVector.toS(),
        channelScores = merged.vectors.channelScores,
        topicAffinities = merged.vectors.topicAffinities,
        interactions = merged.counters.totalInteractions.sum().toInt(),
        blockedTopics = merged.sets.blockedTopics.members(),
        blockedChannels = merged.sets.blockedChannels.members(),
        preferredTopics = merged.sets.preferredTopics.members(),
        hasCompletedOnboarding = local.hasCompletedOnboarding || merged.flags.hasCompletedOnboarding,
        idfWordFrequency = merged.idfWordFrequency.mapValues { it.value.sum().toInt() },
        idfTotalDocuments = merged.counters.idfTotalDocuments.sum().toInt(),
        watchHistoryMap = merged.perVideo.watchHistoryMap,
        channelTopicProfiles = merged.vectors.channelTopicProfiles,
        shortsVector = merged.vectors.shortsVector.toS(),
        suppressedVideoIds = merged.lwwMaps.suppressedVideoIds.mapValues { it.value.value },
        suppressedChannels = merged.lwwMaps.suppressedChannels.mapValues { it.value.value },
        rejectionPatterns = merged.lwwMaps.rejectionPatterns.mapValues {
            SRejectionSignal(it.value.value.count, it.value.value.lastRejectedAt)
        },
        feedHistory = merged.lwwMaps.feedHistory.mapValues {
            SFeedEntry(it.value.value.lastShown, it.value.value.showCount)
        },
        topicEvidence = merged.lwwMaps.topicEvidence.mapValues {
            STopicEvidence(
                positiveSignals = it.value.value.positiveSignals,
                watchSignals = it.value.value.watchSignals,
                explicitSignals = it.value.value.explicitSignals,
                positiveScore = it.value.value.positiveScore,
                videoIds = it.value.value.videoIds,
                channelIds = it.value.value.channelIds,
                firstSeenAt = it.value.value.firstSeenAt,
                lastSeenAt = it.value.value.lastSeenAt,
            )
        },
    )
}
