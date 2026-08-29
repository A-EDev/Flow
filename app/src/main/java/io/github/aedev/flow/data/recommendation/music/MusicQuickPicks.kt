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
     * single lane (or one dominant artist pool) can own the shelf. Stops at the
     * limit or when a full pass makes no progress.
     */
    fun interleave(
        lanes: List<List<MusicTrack>>,
        limit: Int,
        excludedIds: Set<String>,
    ): List<MusicTrack> {
        val cursors = IntArray(lanes.size)
        val seen = HashSet(excludedIds)
        val result = ArrayList<MusicTrack>(limit)
        while (result.size < limit) {
            var progressed = false
            for (i in lanes.indices) {
                if (result.size >= limit) break
                val lane = lanes[i]
                var cursor = cursors[i]
                while (cursor < lane.size && !seen.add(lane[cursor].videoId)) cursor++
                if (cursor < lane.size) {
                    result.add(lane[cursor])
                    cursor++
                    progressed = true
                }
                cursors[i] = cursor
            }
            if (!progressed) break
        }
        return result
    }
}
