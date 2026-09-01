/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import io.github.aedev.flow.ui.screens.music.MusicTrack

/** The brain's view of one artist, rendered on that artist's page. */
data class MusicArtistInsights(
    val plays: Int,
    val liked: Boolean,
    val topTracks: List<MusicTrack>,
)

data class MusicTopArtist(
    val key: String,
    val name: String,
    val score: Double,
    val plays: Int,
    val liked: Boolean,
    val idKeyed: Boolean,
)

data class MusicTimeOfDayBucket(
    val bucket: MusicTimeBucket,
    val plays: Int,
    val topGenres: List<String>,
)

data class MusicTasteProfile(
    val topArtists: List<MusicTopArtist>,
    val topGenres: List<Pair<String, Double>>,
    val discoveryAppetite: Double,
    val totalPlays: Int,
    val distinctArtists: Int,
    val trackedTracks: Int,
    val onRepeatCount: Int,
    val maturity: String,
    val timeOfDay: List<MusicTimeOfDayBucket>,
)

/**
 * The single source of taste truth: one read-only, display-ready projection for
 * the stats/recap surfaces and the home section planner. Computed here so the
 * UI never re-derives taste from raw history. Ported from the desktop
 * `music_brain/profile.rs`.
 */
internal object MusicBrainProfile {
    const val TOP_ARTISTS = 12
    const val TOP_GENRES = 8
    const val BUCKET_TOP_GENRES = 4
    const val ON_REPEAT_PROBE = 50
    const val MATURITY_COLD_MAX = 15
    const val MATURITY_WARMING_MAX = 80

    fun maturityLabel(totalPlays: Int): String =
        when {
            totalPlays < MATURITY_COLD_MAX -> "cold_start"
            totalPlays <= MATURITY_WARMING_MAX -> "warming"
            else -> "mature"
        }

    fun tasteProfile(
        brain: MusicBrain,
        nowMs: Long,
    ): MusicTasteProfile {
        // artist_key -> display name; track meta first, learned affinity display second.
        val names = HashMap<String, String>()
        for (meta in brain.trackMeta.values) {
            if (meta.artistKey.isNotEmpty() && meta.artist.isNotEmpty()) {
                names.putIfAbsent(meta.artistKey, meta.artist)
            }
        }

        val ranked =
            brain.artistAffinity.entries
                .filter { !brain.isArtistBlocked(it.key) }
        val distinctArtists = ranked.size
        val topArtists =
            ranked
                .sortedByDescending { it.value.score }
                .take(TOP_ARTISTS)
                .map { (key, aff) ->
                    MusicTopArtist(
                        key = key,
                        name = names[key] ?: aff.display.ifEmpty { key },
                        score = aff.score,
                        plays = aff.plays,
                        liked = aff.liked,
                        idKeyed = isIdKeyedArtist(key),
                    )
                }

        // Listening rhythm from REAL play timestamps, not the genre histogram, so
        // the grid is populated even when no genre tags exist (the common case).
        val bucketPlays = HashMap<MusicTimeBucket, Int>()
        for (stamps in brain.trackPlays.values) {
            for (ts in stamps) {
                val bucket = MusicTimeBucket.fromTimestamp(ts)
                bucketPlays[bucket] = (bucketPlays[bucket] ?: 0) + 1
            }
        }
        val timeOfDay =
            MusicTimeBucket.entries.map { bucket ->
                MusicTimeOfDayBucket(
                    bucket = bucket,
                    plays = bucketPlays[bucket] ?: 0,
                    topGenres =
                        brain.timeBuckets[bucket]
                            .orEmpty()
                            .entries
                            .sortedByDescending { it.value }
                            .take(BUCKET_TOP_GENRES)
                            .map { it.key },
                )
            }

        return MusicTasteProfile(
            topArtists = topArtists,
            topGenres =
                brain.genreAffinity.entries
                    .sortedByDescending { it.value }
                    .take(TOP_GENRES)
                    .map { it.key to it.value },
            discoveryAppetite = brain.discoveryAppetite,
            totalPlays = brain.totalPlays,
            distinctArtists = distinctArtists,
            trackedTracks = brain.trackPlays.size,
            onRepeatCount = MusicBrainRanker.heavyRotation(brain, nowMs, ON_REPEAT_PROBE).size,
            maturity = maturityLabel(brain.totalPlays),
            timeOfDay = timeOfDay,
        )
    }
}
