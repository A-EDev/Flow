package io.github.aedev.flow.sync.canonical

import kotlinx.serialization.Serializable
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

// --- Brain ---

@Serializable
data class CanonicalVector(
    val topics: Map<String, Double> = emptyMap(),
    val duration: Double = 0.5,
    val pacing: Double = 0.5,
    val complexity: Double = 0.5,
    val isLive: Double = 0.0,
)

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

@Serializable
data class CanonicalTopicEvidence(
    val positiveSignals: Int = 0,
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

/** G-Counter: per-device sub-counts; value = sum; merge = per-device max */
@Serializable
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

    fun withDevice(deviceId: String, subCount: Long): GCounter =
        GCounter(perDevice + (deviceId to subCount))
}

/**
 * The canonical brain. Counters are G-Counters (per-device). [vectors] is shipped as this
 * device's contribution snapshot; the receiver stores it per-peer and recomputes the effective
 * blend. Sets are OR-Sets (union in v1); timestamp maps are Max-Registers.
 */
@Serializable
data class CanonicalBrain(
    val schema: Int = 13,
    val deviceId: String = "",
    val hlc: String = "",
    val vectors: CanonicalBrainVectors = CanonicalBrainVectors(),
    val idfTotalDocuments: GCounter = GCounter(),
    val totalInteractions: GCounter = GCounter(),
    val idfWordFrequency: Map<String, GCounter> = emptyMap(),
    val watchHistoryMap: Map<String, Float> = emptyMap(),
    val seenShortsHistory: Map<String, Long> = emptyMap(),
    val suppressedVideoIds: Map<String, Long> = emptyMap(),
    val suppressedChannels: Map<String, Long> = emptyMap(),
    val rejectionPatterns: Map<String, CanonicalRejectionSignal> = emptyMap(),
    val feedHistory: Map<String, CanonicalFeedEntry> = emptyMap(),
    val topicEvidence: Map<String, CanonicalTopicEvidence> = emptyMap(),
    val blockedTopics: Set<String> = emptySet(),
    val blockedChannels: Set<String> = emptySet(),
    val preferredTopics: Set<String> = emptySet(),
    val hasCompletedOnboarding: Boolean = false,
)
