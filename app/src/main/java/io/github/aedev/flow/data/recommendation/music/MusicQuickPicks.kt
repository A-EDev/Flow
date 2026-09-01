/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import io.github.aedev.flow.ui.screens.music.MusicTrack

/**
 * The Quick Picks lane model, ported from the desktop composer: several
 * personalized lanes plus a discovery lane, round-robin interleaved. The
 * diversity of the shelf comes from the LANE STRUCTURE — a single ranked pool
 * collapses into familiar content, which is exactly the failure this replaces.
 */
object MusicQuickPicks {
    const val SEED_LIMIT = 5
    const val LANE_SIZE = 20
    const val TARGET = 24

    /** Artist-graph lanes: how many top artists get a lane, and how many of their related artists join the similar lane. */
    const val ARTIST_LANE_COUNT = 3
    const val SIMILAR_ARTIST_COUNT = 3

    /**
     * Charts can smuggle regional content the taste model never chose, so the
     * discovery lane gets the smallest quota of the shelf.
     */
    const val DISCOVERY_MAX_PICKS = 2

    /**
     * Shelf-wide cap per artist. Lanes overlap on a loved artist (their own lane,
     * related lanes, fans-also-like), which floods the shelf without this.
     */
    const val MAX_PER_ARTIST = 3

    /**
     * Seed selection: the currently playing track always leads, then history
     * newest-first with one seed per distinct artist; topped up with repeats
     * only when the history is too narrow to fill the quota.
     */
    fun selectSeeds(
        current: MusicTrack?,
        history: List<MusicTrack>,
        limit: Int = SEED_LIMIT,
    ): List<MusicTrack> {
        val candidates =
            (listOfNotNull(current) + history)
                .filter { it.videoId.isNotBlank() }
                .distinctBy { it.videoId }
        val seeds = ArrayList<MusicTrack>(limit)
        val usedArtists = HashSet<String>()
        for (candidate in candidates) {
            if (seeds.size >= limit) break
            val key = candidate.primaryArtistKey()
            if (key.isEmpty() || usedArtists.add(key)) seeds.add(candidate)
        }
        if (seeds.size < limit) {
            for (candidate in candidates) {
                if (seeds.size >= limit) break
                if (seeds.none { it.videoId == candidate.videoId }) seeds.add(candidate)
            }
        }
        return seeds
    }

    /**
     * Round-robin across lanes: one unseen item from each lane per pass, so no
     * single lane (or one dominant artist pool) can own the shelf. A lane with a
     * cap in [laneCaps] stops contributing at its cap (used to keep charts to a
     * minority quota), and no artist exceeds [maxPerArtist] picks shelf-wide.
     * Stops at the limit or when a full pass makes no progress.
     */
    fun interleave(
        lanes: List<List<MusicTrack>>,
        limit: Int,
        excludedIds: Set<String>,
        laneCaps: List<Int>? = null,
        maxPerArtist: Int = MAX_PER_ARTIST,
    ): List<MusicTrack> {
        val cursors = IntArray(lanes.size)
        val picked = IntArray(lanes.size)
        val seen = HashSet(excludedIds)
        val artistCounts = HashMap<String, Int>()
        val result = ArrayList<MusicTrack>(limit)
        while (result.size < limit) {
            var progressed = false
            for (i in lanes.indices) {
                if (result.size >= limit) break
                val cap = laneCaps?.getOrNull(i) ?: Int.MAX_VALUE
                if (picked[i] >= cap) continue
                val lane = lanes[i]
                var cursor = cursors[i]
                var pick: MusicTrack? = null
                while (cursor < lane.size) {
                    val candidate = lane[cursor]
                    cursor++
                    if (!seen.add(candidate.videoId)) continue
                    val artistKey = candidate.primaryArtistKey()
                    if (artistKey.isNotEmpty() && (artistCounts[artistKey] ?: 0) >= maxPerArtist) continue
                    pick = candidate
                    break
                }
                cursors[i] = cursor
                if (pick != null) {
                    result.add(pick)
                    picked[i]++
                    pick.primaryArtistKey().takeIf { it.isNotEmpty() }?.let {
                        artistCounts[it] = (artistCounts[it] ?: 0) + 1
                    }
                    progressed = true
                }
            }
            if (!progressed) break
        }
        return result
    }
}
