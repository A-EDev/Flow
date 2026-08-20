package io.github.aedev.flow.sync.canonical

import io.github.aedev.flow.sync.identity.Hlc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

/**
 * Platform-neutral canonical records exchanged over FLOW-SYNC/1.
 *
 * Conventions: epoch **milliseconds** for all times; `progress` is a 0..1 fraction;
 * `durationSeconds` is integer seconds; deletions are **tombstones** (`deleted=true`), never
 * omissions; every mergeable record carries an `hlc` string. Android maps its
 * Room/DataStore/brain values to/from these in `sync/mapping`.
 *
 * These types are the unit the merge engine operates on, so they are deliberately decoupled
 * from both DB schemas. Keep field names in sync with the desktop `canonical.rs`.
 */

@Serializable
data class CanonicalWatchHistory(
    val videoId: String,
    val title: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val watchedAtMs: Long = 0,
    val progress: Double = 0.0,
    val durationSeconds: Long = 0,
    val isMusic: Boolean = false,
    val isShort: Boolean = false,
    val hlc: String = "",
    val deleted: Boolean = false,
)

@Serializable
data class CanonicalPlaylistItem(
    val videoId: String,
    /** Ascending display rank (0-based).*/
    val position: Long = 0,
    val addedAtMs: Long = 0,
    val deleted: Boolean = false,
    val title: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0,
    val isMusic: Boolean = false,
    val hlc: String = "",
)

@Serializable
data class CanonicalPlaylist(
    val syncId: String,
    val origin: String = ORIGIN_LOCAL,
    val youtubeId: String? = null,
    val title: String = "",
    val description: String = "",
    val isMusic: Boolean = false,
    val isUserCreated: Boolean = true,
    val isProtected: Boolean = false,
    val createdAtMs: Long = 0,
    val updatedHlc: String = "",
    val deleted: Boolean = false,
    val items: List<CanonicalPlaylistItem> = emptyList(),
) {
    companion object {
        const val ORIGIN_LOCAL = "local"
        const val ORIGIN_YOUTUBE = "youtube"

        /** Reserved id for the cross-platform Watch Later playlist */
        const val RESERVED_WATCH_LATER = "reserved:watch-later"
    }
}

/** Minimal display metadata for a like (desktop §6.3 `meta`). `artist` ⇄ Android `channelName`. */
@Serializable
data class CanonicalLikeMeta(
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
)

@Serializable
data class CanonicalLike(
    val kind: String, // "video" | "music"
    val id: String,
    val state: String, // "liked" | "disliked" | "none"
    val updatedAtMs: Long = 0,
    val hlc: String = "",
    val meta: CanonicalLikeMeta = CanonicalLikeMeta(),
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
) {
    companion object {
        const val KIND_VIDEO = "video"
        const val KIND_MUSIC = "music"
        const val STATE_LIKED = "liked"
        const val STATE_DISLIKED = "disliked"
        const val STATE_NONE = "none"
    }
}

/** A single synced setting. [value] is a typed JSON primitive; the mapper coerces per key. */
@Serializable
data class CanonicalSetting(
    val key: String,
    val value: JsonElement,
    val hlc: String = "",
)

@Serializable
data class CanonicalSubscriptionGroup(
    val name: String,
    val channelIds: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val hlc: String = "",
    val deleted: Boolean = false,
)

/**
 * One channel the user follows. Unsubscribing is a **tombstone** (`deleted=true`) carrying the
 * moment it happened, not an omission — otherwise the peer's copy simply re-subscribes the channel
 * on the next merge.
 *
 * Per-device concerns (notification opt-in, feed-fetch bookkeeping) are deliberately not on the
 * wire: whether a channel should buzz *this* phone is not a property of the subscription.
 */
@Serializable
data class CanonicalSubscribedChannel(
    val channelId: String,
    val name: String = "",
    val avatarUrl: String = "",
    val subscribedAtMs: Long = 0,
    val isMusic: Boolean = false,
    val hlc: String = "",
    val deleted: Boolean = false,
)

// --- Brain CRDT primitives (wire forms must match the desktop's `canonical.rs` byte for byte) ---

/**
 * G-Counter: per-device sub-counts; value = sum; merge = per-device max.
 *
 * The **wire form is the `{device: count}` map itself** (desktop plan §6.5), not an object wrapping
 * it. Android used to emit `{"perDevice":{...}}`, which the desktop rejected outright with
 * `invalid type: map, expected u64` — the hard parse failure behind the "brain sync fails" reports.
 */
@Serializable(with = GCounterSerializer::class)
data class GCounter(
    val perDevice: Map<String, Long> = emptyMap(),
) {
    fun sum(): Long = perDevice.values.sum()

    fun merge(other: GCounter): GCounter {
        if (other.perDevice.isEmpty()) return this
        if (perDevice.isEmpty()) return other
        val out = HashMap(perDevice)
        for ((d, c) in other.perDevice) {
            out[d] = maxOf(out[d] ?: Long.MIN_VALUE, c)
        }
        return GCounter(out)
    }
}

object GCounterSerializer : KSerializer<GCounter> {
    private val delegate = MapSerializer(String.serializer(), Long.serializer())
    override val descriptor: SerialDescriptor = SerialDescriptor("GCounter", delegate.descriptor)

    override fun serialize(
        encoder: Encoder,
        value: GCounter,
    ) = delegate.serialize(encoder, value.perDevice)

    override fun deserialize(decoder: Decoder): GCounter = GCounter(delegate.deserialize(decoder))
}

/**
 * Observed-remove set: member to the HLC of its latest add / latest remove. A member is present iff
 * its add stamp is >= its remove stamp (add wins on tie).
 *
 * The tombstones are the point: a plain `Set<String>` can express "blocked" but has no way to say
 * "explicitly unblocked", so an unblock on one device could never propagate to the other.
 */
@Serializable
data class OrSet(
    val adds: Map<String, String> = emptyMap(),
    val removes: Map<String, String> = emptyMap(),
) {
    fun contains(member: String): Boolean {
        val added = adds[member] ?: return false
        val removed = removes[member] ?: return true
        return Hlc.compareEncoded(added, removed) >= 0
    }

    fun members(): Set<String> = adds.keys.filterTo(LinkedHashSet()) { contains(it) }

    fun add(
        member: String,
        hlc: String,
    ): OrSet = copy(adds = adds + (member to Hlc.maxEncoded(adds[member].orEmpty(), hlc)))

    fun remove(
        member: String,
        hlc: String,
    ): OrSet = copy(removes = removes + (member to Hlc.maxEncoded(removes[member].orEmpty(), hlc)))

    fun merge(other: OrSet): OrSet {
        if (other.adds.isEmpty() && other.removes.isEmpty()) return this
        if (adds.isEmpty() && removes.isEmpty()) return other
        return OrSet(mergeStamps(adds, other.adds), mergeStamps(removes, other.removes))
    }

    private fun mergeStamps(
        a: Map<String, String>,
        b: Map<String, String>,
    ): Map<String, String> {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        val out = HashMap(a)
        for ((k, v) in b) out[k] = Hlc.maxEncoded(out[k].orEmpty(), v)
        return out
    }
}

/** Last-write-wins register; the higher HLC wins, the device id inside it breaking ties. */
@Serializable
data class Lww<T>(
    val value: T,
    val hlc: String = "",
) {
    fun merge(other: Lww<T>): Lww<T> = if (Hlc.compareEncoded(other.hlc, hlc) > 0) other else this
}

// --- Brain ---

/**
 * A topic vector plus its scalar "dimensions". The desktop models the scalars as a nested [dims]
 * map (`duration`/`pacing`/`complexity`/`isLive`, camelCase); Android used to inline them as
 * siblings of [topics], so serde dropped every one of them on ingest.
 */
@Serializable
data class CanonicalVector(
    val topics: Map<String, Double> = emptyMap(),
    val dims: Map<String, Double> = emptyMap(),
) {
    val duration: Double get() = dims[DIM_DURATION] ?: 0.5
    val pacing: Double get() = dims[DIM_PACING] ?: 0.5
    val complexity: Double get() = dims[DIM_COMPLEXITY] ?: 0.5
    val isLive: Double get() = dims[DIM_IS_LIVE] ?: 0.0

    companion object {
        const val DIM_DURATION = "duration"
        const val DIM_PACING = "pacing"
        const val DIM_COMPLEXITY = "complexity"
        const val DIM_IS_LIVE = "isLive"

        fun of(
            topics: Map<String, Double>,
            duration: Double,
            pacing: Double,
            complexity: Double,
            isLive: Double,
        ) = CanonicalVector(
            topics = topics,
            dims =
                mapOf(
                    DIM_DURATION to duration,
                    DIM_PACING to pacing,
                    DIM_COMPLEXITY to complexity,
                    DIM_IS_LIVE to isLive,
                ),
        )
    }
}

@Serializable
data class CanonicalRejectionSignal(
    val count: Int = 0,
    val lastRejectedAt: Long = 0,
)

@Serializable
data class CanonicalFeedEntry(
    val lastShown: Long = 0,
    val showCount: Int = 0,
)

/**
 * Repeat-offender counter for a channel. Android's brain has no strike concept, so it ships this
 * map empty — but the field must exist, or a round-trip through a phone would silently erase the
 * desktop's strikes.
 */
@Serializable
data class CanonicalChannelStrike(
    val count: Int = 0,
    val firstAt: Long = 0,
    val lastAt: Long = 0,
)

/**
 * [negativeSignals] is carried for wire parity with the desktop only: Android's on-disk brain does
 * not persist it (addendum §10), so Android always emits 0 and never reads it back.
 */
@Serializable
data class CanonicalTopicEvidence(
    val positiveSignals: Int = 0,
    val negativeSignals: Int = 0,
    val watchSignals: Int = 0,
    val explicitSignals: Int = 0,
    val positiveScore: Double = 0.0,
    val videoIds: Set<String> = emptySet(),
    val channelIds: Set<String> = emptySet(),
    val firstSeenAt: Long = 0,
    val lastSeenAt: Long = 0,
)

/** The blendable, experience-weighted learned vectors */
@Serializable
data class CanonicalBrainVectors(
    val globalVector: CanonicalVector = CanonicalVector(),
    val timeVectors: Map<String, CanonicalVector> = emptyMap(),
    val shortsVector: CanonicalVector = CanonicalVector(),
    val topicAffinities: Map<String, Double> = emptyMap(),
    val channelScores: Map<String, Double> = emptyMap(),
    val channelTopicProfiles: Map<String, Map<String, Double>> = emptyMap(),
)

@Serializable
data class BrainCounters(
    val idfTotalDocuments: GCounter = GCounter(),
    val totalInteractions: GCounter = GCounter(),
)

/** Max-register merge per key. Android has no `watchSignalProgress`; it round-trips untouched. */
@Serializable
data class BrainPerVideo(
    val watchHistoryMap: Map<String, Float> = emptyMap(),
    val watchSignalProgress: Map<String, Float> = emptyMap(),
)

@Serializable
data class BrainSets(
    val blockedTopics: OrSet = OrSet(),
    val blockedChannels: OrSet = OrSet(),
    val preferredTopics: OrSet = OrSet(),
)

@Serializable
data class BrainLwwMaps(
    val suppressedVideoIds: Map<String, Lww<Long>> = emptyMap(),
    val suppressedChannels: Map<String, Lww<Long>> = emptyMap(),
    val rejectionPatterns: Map<String, Lww<CanonicalRejectionSignal>> = emptyMap(),
    val topicEvidence: Map<String, Lww<CanonicalTopicEvidence>> = emptyMap(),
    val feedHistory: Map<String, Lww<CanonicalFeedEntry>> = emptyMap(),
    val channelStrikes: Map<String, Lww<CanonicalChannelStrike>> = emptyMap(),
)

@Serializable
data class BrainFlags(
    val hasCompletedOnboarding: Boolean = false,
)

/**
 * The canonical brain — a field-for-field mirror of the desktop's `FlowNeuroBrainSnapshot`
 * (desktop plan §6.5). It is shipped as this device's contribution snapshot; the receiver merges it
 * into its own. Counters are G-Counters, sets are OR-Sets, `lwwMaps` entries are HLC-stamped
 * registers, and `perVideo` progress is a max-register.
 *
 * `seenShortsHistory` is deliberately **absent**: it is an Android-local dedup cache with no
 * desktop counterpart, which the desktop discarded silently. Cross-device shorts dedup needs a
 * spec change (a `perVideo` entry) before it can go back on the wire.
 */
@Serializable
data class CanonicalBrain(
    val schema: Int = 13,
    val deviceId: String = "",
    val hlc: String = "",
    val vectors: CanonicalBrainVectors = CanonicalBrainVectors(),
    val counters: BrainCounters = BrainCounters(),
    val idfWordFrequency: Map<String, GCounter> = emptyMap(),
    val perVideo: BrainPerVideo = BrainPerVideo(),
    val sets: BrainSets = BrainSets(),
    val lwwMaps: BrainLwwMaps = BrainLwwMaps(),
    val flags: BrainFlags = BrainFlags(),
)
