package io.github.aedev.flow.sync.canonical

import kotlinx.serialization.Serializable

/**
 * Wire model for the `music_brain` collection — a field-for-field mirror of the desktop's
 * `MusicBrainSnapshot` in `canonical.rs` (camelCase on the wire). Device-local fields
 * (`backfilled`, `lastRotationDecay`, `artistRelated`, per-artist `display`) are deliberately
 * NOT on the wire; the write-back preserves them from the local brain.
 */

@Serializable
data class CanonicalMusicAffinity(
    /** Counted listens as a G-Counter (idempotent across re-syncs). */
    val plays: GCounter = GCounter(),
    /** EMA of completion-weighted listens, LWW-resolved via [hlc]. */
    val score: Double = 0.0,
    val lastPlayed: Long = 0,
    val liked: Boolean = false,
    val hlc: String = "",
)

@Serializable
data class CanonicalMusicTrackMeta(
    val title: String = "",
    val artist: String = "",
    val artistKey: String = "",
    val thumbnail: String = "",
)

@Serializable
data class CanonicalMusicBrain(
    val schema: Int = 1,
    val deviceId: String = "",
    val hlc: String = "",
    val artistAffinity: Map<String, CanonicalMusicAffinity> = emptyMap(),
    val genreAffinity: Map<String, Double> = emptyMap(),
    val artistCooc: Map<String, Double> = emptyMap(),
    val recentRotation: Map<String, Double> = emptyMap(),
    val timeBuckets: Map<String, Map<String, Double>> = emptyMap(),
    /** track -> recent play timestamps (ms). Merge = set-union then truncate to newest N. */
    val trackPlays: Map<String, List<Long>> = emptyMap(),
    val trackMeta: Map<String, CanonicalMusicTrackMeta> = emptyMap(),
    val totalPlays: GCounter = GCounter(),
    val seenArtists: OrSet = OrSet(),
    val blockedArtists: OrSet = OrSet(),
    val dislikedArtists: Map<String, Lww<Long>> = emptyMap(),
    val discoveryAppetite: Lww<Double>? = null,
)
