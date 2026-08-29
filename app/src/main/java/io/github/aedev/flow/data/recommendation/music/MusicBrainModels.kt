/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import java.util.Calendar
import java.util.EnumMap

/*
 * MusicBrain — the local music taste model
 *
 * Deliberately the OPPOSITE of the video engine on every axis that matters:
 * entities (artist/track/genre) instead of title tokens, relistening REWARDED
 * instead of gated, coherence instead of diversity. This package must never
 * touch the video engine (enforced by MusicBrainLeakGuardTest).
 */

/** Long-term per-artist taste. Mutable on purpose: learning updates it in place under the engine mutex. */
class MusicAffinity(
    var plays: Int = 0,
    var score: Double = 0.0,
    var lastPlayed: Long = 0L,
    var liked: Boolean = false,
)

/** Display metadata kept locally so On Repeat renders with zero network calls. */
data class MusicTrackMeta(
    val title: String,
    val artist: String,
    val artistKey: String,
    val thumbnail: String,
)

/** Hour-of-day × weekday/weekend bucket. Wire names must match the desktop sync format exactly. */
enum class MusicTimeBucket(
    val wireName: String,
) {
    WEEKDAY_MORNING("WeekdayMorning"),
    WEEKDAY_AFTERNOON("WeekdayAfternoon"),
    WEEKDAY_EVENING("WeekdayEvening"),
    WEEKDAY_NIGHT("WeekdayNight"),
    WEEKEND_MORNING("WeekendMorning"),
    WEEKEND_AFTERNOON("WeekendAfternoon"),
    WEEKEND_EVENING("WeekendEvening"),
    WEEKEND_NIGHT("WeekendNight"),
    ;

    companion object {
        fun fromParts(
            hour: Int,
            isWeekend: Boolean,
        ): MusicTimeBucket {
            val slot =
                when (hour) {
                    in 6..11 -> 0
                    in 12..17 -> 1
                    in 18..23 -> 2
                    else -> 3
                }
            return entries[if (isWeekend) 4 + slot else slot]
        }

        /** Buckets use LOCAL time, matching the desktop engine. */
        fun fromTimestamp(timestampMs: Long): MusicTimeBucket {
            val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY
            return fromParts(cal.get(Calendar.HOUR_OF_DAY), isWeekend)
        }

        fun fromWire(name: String): MusicTimeBucket? = entries.firstOrNull { it.wireName == name }
    }
}

/** One observed listen (or explicit like) flowing into the brain. */
data class MusicSignal(
    val trackId: String,
    val artistKey: String,
    val artistDisplay: String,
    val genre: String? = null,
    val percentPlayed: Double,
    val isExplicitLike: Boolean = false,
    val title: String = "",
    val thumbnail: String = "",
)

/**
 * The resident music taste state. One small JSON blob (< ~0.5 MB at every cap),
 * persisted whole via [MusicBrainStorage]. All mutation happens in
 * MusicBrainLearn functions under the engine mutex.
 */
class MusicBrain {
    val artistAffinity: MutableMap<String, MusicAffinity> = HashMap()
    val genreAffinity: MutableMap<String, Double> = HashMap()

    /** ACT-R base-level history: play timestamps (ms), oldest→newest, ring of [MusicBrainParams.TRACK_RING]. */
    val trackPlays: MutableMap<String, MutableList<Long>> = HashMap()
    val trackMeta: MutableMap<String, MusicTrackMeta> = HashMap()

    /** Medium-term "into it lately" overlay, decayed daily. */
    val recentRotation: MutableMap<String, Double> = HashMap()

    /** Single-user session co-occurrence graph, keyed by [musicPairKey]. Pure user co-listening. */
    val artistCooc: MutableMap<String, Double> = HashMap()
    val timeBuckets: MutableMap<MusicTimeBucket, MutableMap<String, Double>> = EnumMap(MusicTimeBucket::class.java)
    val seenArtists: MutableSet<String> = HashSet()

    /** Reversible cooldown: artistKey → dislike timestamp (ms). A counted listen forgives it. */
    val dislikedArtists: MutableMap<String, Long> = HashMap()

    /** Permanent denylist. Affinity/history is preserved so an unblock warms back up naturally. */
    val blockedArtists: MutableSet<String> = HashSet()

    var discoveryAppetite: Double = MusicBrainParams.DISCOVERY_NEUTRAL
    var totalPlays: Int = 0
    var lastRotationDecay: Long = 0L
    var schemaVersion: Int = MusicBrainParams.SCHEMA_VERSION

    /** Device-local: one-time warm start from existing history has run. Never synced. */
    var backfilled: Boolean = false

    fun isArtistBlocked(key: String): Boolean = key.isNotEmpty() && key in blockedArtists

    fun topArtists(n: Int): List<Pair<String, Double>> =
        artistAffinity.entries
            .sortedByDescending { it.value.score }
            .take(n)
            .map { it.key to it.value.score }
}

/**
 * Identity is load-bearing: every map in the brain is keyed by this. Prefer the
 * YouTube Music browseId (case preserved); fall back to the lowercased display name.
 */
fun musicArtistKey(
    artistId: String?,
    artistName: String?,
): String {
    val id = artistId?.trim().orEmpty()
    if (id.isNotEmpty()) return id
    return artistName?.trim().orEmpty().lowercase()
}

/** Name keys are force-lowercased, so any uppercase implies a routable browseId. */
fun isIdKeyedArtist(key: String): Boolean = key.startsWith("UC") || key.any { it in 'A'..'Z' }

/** Stable unordered pair key for the co-occurrence graph. */
fun musicPairKey(
    a: String,
    b: String,
): String = if (a <= b) "$a|$b" else "$b|$a"

/**
 * Every constant, mirroring the desktop values verbatim.
 * They are tuned as a set — retune only with MusicBenchmark deltas in hand.
 */
object MusicBrainParams {
    const val SCHEMA_VERSION = 1

    // Learning
    val milestones = doubleArrayOf(0.15, 0.50, 0.90)
    const val COUNT_MILESTONE = 0.50
    const val SESSION_GAP_MS = 1_800_000L
    const val ALPHA_LONG = 0.15
    const val ALPHA_MED = 0.35
    const val ROTATION_DECAY_PER_DAY = 0.85
    const val ROTATION_PRUNE_FLOOR = 0.02
    const val LIKE_SCORE_FLOOR = 0.8
    const val DISLIKE_SCORE_FACTOR = 0.5
    const val DISCOVERY_NEUTRAL = 0.30
    const val APPETITE_REGRESS = 0.02
    const val APPETITE_NOVEL_BONUS = 0.03
    const val APPETITE_DISLIKE_NUDGE = 0.03
    const val APPETITE_MIN = 0.05
    const val APPETITE_MAX = 0.95

    // Model caps: (max trigger, keep)
    const val ARTIST_AFFINITY_MAX = 600
    const val ARTIST_AFFINITY_KEEP = 500
    const val TRACK_PLAYS_MAX = 400
    const val TRACK_PLAYS_KEEP = 350
    const val TRACK_RING = 8
    const val RECENT_ROTATION_MAX = 400
    const val RECENT_ROTATION_KEEP = 300
    const val ARTIST_COOC_MAX = 2000
    const val ARTIST_COOC_KEEP = 1500
    const val GENRE_AFFINITY_MAX = 200
    const val SEEN_ARTISTS_MAX = 3000
    const val SEEN_ARTISTS_KEEP = 2500
    const val DISLIKED_MAX = 200
    const val BLOCKED_MAX = 1000

    // Ranking
    const val ACT_DECAY = 0.5
    const val ACT_DT_FLOOR_HOURS = 0.05
    const val DISLIKE_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000
    const val DISLIKE_COOLDOWN_MULTIPLIER = 0.1
    const val MAX_CONSECUTIVE_ARTIST = 2
    const val COOC_ANCHOR_ARTISTS = 12
    const val CONTEXT_CONFIDENCE_K = 8.0
    const val ADJACENT_GENRE_THRESHOLD = 0.1
    const val NOVELTY_MIN = 0.05
    const val NOVELTY_MAX = 0.95

    // Backfill
    const val BACKFILL_MAX_ROWS = 3000
    const val BACKFILL_UNKNOWN_PROGRESS = 0.6
}
