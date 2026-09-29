/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

/** A Daily Mix is only seeds — the UI expands them into a shelf via related recall. */
data class DailyMixSeed(
    val label: String,
    val seedTrackIds: List<String>,
)

/**
 * Daily Mixes — Spotify-style auto-playlists built WITHOUT any genre taxonomy:
 * the structure is the user's own co-listening graph. Ported verbatim from the
 * desktop `music_brain/mixes.rs`. A cold graph yields no mixes at all — the
 * shelf simply doesn't appear until real multi-artist sessions exist.
 */
internal object MusicBrainMixes {
    const val MAX_CANDIDATE_ARTISTS = 25
    const val CLUSTER_MAX_ARTISTS = 5
    const val MIN_COOC_WEIGHT = 1.0
    const val SEEDS_PER_MIX = 4
    const val MAX_MIXES_CAP = 8

    fun dailyMixes(
        brain: MusicBrain,
        nowMs: Long,
        maxMixes: Int,
        seedsPerMix: Int = SEEDS_PER_MIX,
    ): List<DailyMixSeed> {
        val cap = maxMixes.coerceIn(1, MAX_MIXES_CAP)
        if (brain.artistCooc.isEmpty() || brain.trackMeta.isEmpty()) return emptyList()

        val artistsWithTracks =
            brain.trackMeta.values.mapNotNullTo(HashSet()) { meta ->
                meta.artistKey.takeIf { it.isNotEmpty() }
            }
        val candidates =
            brain
                .topArtists(MAX_CANDIDATE_ARTISTS)
                .map { it.first }
                .filter { it in artistsWithTracks && !brain.isArtistBlocked(it) }

        val assigned = HashSet<String>()
        val mixes = ArrayList<DailyMixSeed>()
        for (anchor in candidates) {
            if (mixes.size >= cap) break
            if (anchor in assigned) continue
            assigned.add(anchor)

            val neighbors =
                candidates
                    .filter { it != anchor && it !in assigned }
                    .mapNotNull { c ->
                        brain.artistCooc[musicPairKey(anchor, c)]
                            ?.takeIf { it >= MIN_COOC_WEIGHT }
                            ?.let { c to it }
                    }.sortedByDescending { it.second }
                    .take(CLUSTER_MAX_ARTISTS - 1)
                    .map { it.first }
            neighbors.forEach { assigned.add(it) }

            val cluster = listOf(anchor) + neighbors
            if (cluster.size < 2) continue

            val seeds = ArrayList<String>(seedsPerMix)
            var label = ""
            for (artist in cluster) {
                val (trackId, display) = topTrackForArtist(brain, artist, nowMs) ?: continue
                if (label.isEmpty() && display.isNotEmpty()) label = display
                seeds.add(trackId)
                if (seeds.size >= seedsPerMix) break
            }
            if (seeds.isEmpty()) continue
            mixes.add(DailyMixSeed(label.ifEmpty { "Daily" }, seeds))
        }
        return mixes
    }

    /** The artist's track with the highest ACT-R activation, as (trackId, display name). */
    private fun topTrackForArtist(
        brain: MusicBrain,
        artistKey: String,
        nowMs: Long,
    ): Pair<String, String>? =
        brain.trackMeta.entries
            .filter { it.value.artistKey == artistKey }
            .maxByOrNull { MusicBrainRanker.baseLevelActivation(brain, it.key, nowMs) }
            ?.let { it.key to it.value.artist }
}
