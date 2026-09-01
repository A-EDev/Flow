/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import kotlin.math.ln
import kotlin.math.pow

/** Minimal projection of a candidate for ranking. */
data class MusicRankInput(
    val trackId: String,
    val artistKey: String,
    val genre: String? = null,
)

/** Per-surface term weights. Surfaces not listed fall through to the balanced default. */
internal data class MusicSurfaceWeights(
    val fam: Double,
    val act: Double,
    val rot: Double,
    val prox: Double,
    val cooc: Double,
    val ctx: Double,
    val discovery: Double,
)

/**
 * Pure read-side scoring and sequencing. Takes a [MusicBrain] and a candidate
 * list, returns a permutation of indices. A cold brain is a stable pass-through.
 */
internal object MusicBrainRanker {
    const val SURFACE_QUICK_PICKS = "quick_picks"
    const val SURFACE_HEAVY_ROTATION = "heavy_rotation"
    const val SURFACE_RADIO = "radio"
    const val SURFACE_SIMILAR = "similar"
    const val SURFACE_DISCOVER = "discover"
    const val SURFACE_DAILY_DISCOVER = "daily_discover"

    fun squash(x: Double): Double = x / (1 + x)

    /**
     * ACT-R base-level activation: ln(Σ dtᵢ^-d) over the play-timestamp ring,
     * clamped at 0 so one stale play never reads as heavy rotation.
     */
    fun baseLevelActivation(
        brain: MusicBrain,
        trackId: String,
        nowMs: Long,
    ): Double {
        val stamps = brain.trackPlays[trackId] ?: return 0.0
        var sum = 0.0
        for (ts in stamps) {
            val dtHours = ((nowMs - ts) / 3_600_000.0).coerceAtLeast(MusicBrainParams.ACT_DT_FLOOR_HOURS)
            sum += dtHours.pow(-MusicBrainParams.ACT_DECAY)
        }
        if (sum <= 0.0) return 0.0
        return maxOf(ln(sum), 0.0)
    }

    internal fun surfaceWeights(surface: String): MusicSurfaceWeights =
        when (surface) {
            SURFACE_QUICK_PICKS, SURFACE_HEAVY_ROTATION -> {
                MusicSurfaceWeights(fam = 0.50, act = 0.35, rot = 0.25, prox = 0.10, cooc = 0.15, ctx = 0.15, discovery = 0.05)
            }

            SURFACE_RADIO -> {
                MusicSurfaceWeights(fam = 0.35, act = 0.20, rot = 0.20, prox = 0.15, cooc = 0.25, ctx = 0.20, discovery = 0.25)
            }

            SURFACE_SIMILAR, SURFACE_DISCOVER, SURFACE_DAILY_DISCOVER -> {
                MusicSurfaceWeights(fam = 0.15, act = 0.05, rot = 0.10, prox = 0.20, cooc = 0.20, ctx = 0.08, discovery = 0.60)
            }

            else -> {
                MusicSurfaceWeights(fam = 0.35, act = 0.20, rot = 0.20, prox = 0.15, cooc = 0.15, ctx = 0.12, discovery = 0.20)
            }
        }

    fun coocScore(
        brain: MusicBrain,
        artistKey: String,
        anchors: List<String>,
    ): Double {
        if (artistKey.isEmpty() || brain.artistCooc.isEmpty()) return 0.0
        var best = 0.0
        for (anchor in anchors) {
            if (anchor == artistKey) continue
            val w = brain.artistCooc[musicPairKey(artistKey, anchor)] ?: continue
            best = maxOf(best, squash(w))
        }
        return best
    }

    /** Time-of-day fit: purely additive, never a filter, damped when the bucket is thin. */
    fun contextScore(
        brain: MusicBrain,
        bucket: MusicTimeBucket,
        genre: String?,
    ): Double {
        if (genre.isNullOrBlank()) return 0.0
        val hist = brain.timeBuckets[bucket] ?: return 0.0
        val total = hist.values.sum()
        if (total <= 0.0) return 0.0
        val share = (hist[genre] ?: 0.0) / total
        val confidence = total / (total + MusicBrainParams.CONTEXT_CONFIDENCE_K)
        return share * confidence
    }

    fun isInDislikeCooldown(
        brain: MusicBrain,
        artistKey: String,
        nowMs: Long,
    ): Boolean {
        val ts = brain.dislikedArtists[artistKey] ?: return false
        return nowMs - ts < MusicBrainParams.DISLIKE_COOLDOWN_MS
    }

    private fun scoreCandidate(
        brain: MusicBrain,
        input: MusicRankInput,
        w: MusicSurfaceWeights,
        anchors: List<String>,
        bucket: MusicTimeBucket,
        nowMs: Long,
    ): Double {
        val artist = input.artistKey
        val fam = brain.artistAffinity[artist]?.score ?: 0.0
        val act = squash(baseLevelActivation(brain, input.trackId, nowMs))
        val rot = (brain.recentRotation[artist] ?: 0.0).coerceIn(0.0, 1.0)
        val prox = (input.genre?.let { brain.genreAffinity[it] } ?: 0.0).coerceIn(0.0, 1.0)
        val cooc = coocScore(brain, artist, anchors)
        val ctx = contextScore(brain, bucket, input.genre)
        val novel = if (artist.isEmpty() || artist in brain.seenArtists) 0.0 else 1.0
        val discovery = w.discovery * novel * brain.discoveryAppetite

        val base = w.fam * fam + w.act * act + w.rot * rot + w.prox * prox + w.cooc * cooc + w.ctx * ctx + discovery
        val cooldown = if (isInDislikeCooldown(brain, artist, nowMs)) MusicBrainParams.DISLIKE_COOLDOWN_MULTIPLIER else 1.0
        return base * cooldown
    }

    fun surfaceTargetNovelty(
        surface: String,
        appetite: Double,
    ): Double {
        val base =
            when (surface) {
                SURFACE_QUICK_PICKS, SURFACE_HEAVY_ROTATION -> 0.15
                SURFACE_RADIO -> 0.35
                SURFACE_SIMILAR -> 0.55
                SURFACE_DISCOVER, SURFACE_DAILY_DISCOVER -> 0.75
                else -> 0.30
            }
        val flex =
            when (surface) {
                SURFACE_RADIO, SURFACE_SIMILAR, SURFACE_DISCOVER, SURFACE_DAILY_DISCOVER -> {
                    (appetite - MusicBrainParams.DISCOVERY_NEUTRAL) * 0.5
                }

                else -> {
                    0.0
                }
            }
        return (base + flex).coerceIn(MusicBrainParams.NOVELTY_MIN, MusicBrainParams.NOVELTY_MAX)
    }

    /** A novel artist only counts as discovery (not noise) when adjacent to existing taste. */
    fun isTasteAdjacent(
        brain: MusicBrain,
        input: MusicRankInput,
        anchors: List<String>,
    ): Boolean =
        coocScore(brain, input.artistKey, anchors) > 0.0 ||
            (input.genre?.let { brain.genreAffinity[it] } ?: 0.0) > MusicBrainParams.ADJACENT_GENRE_THRESHOLD

    /**
     * The full pipeline: score → hard-drop blocked → sink dislike-cooldowns to the
     * end → compose to the surface's novelty target → spread long same-artist runs.
     */
    fun rank(
        brain: MusicBrain,
        inputs: List<MusicRankInput>,
        surface: String,
        nowMs: Long,
    ): List<Int> {
        if (inputs.size <= 1) return inputs.indices.toList()

        val w = surfaceWeights(surface)
        val bucket = MusicTimeBucket.fromTimestamp(nowMs)
        val anchors = brain.topArtists(MusicBrainParams.COOC_ANCHOR_ARTISTS).map { it.first }

        val scores = inputs.map { scoreCandidate(brain, it, w, anchors, bucket, nowMs) }
        val order =
            inputs.indices
                .sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it })
                .filterNot { brain.isArtistBlocked(inputs[it].artistKey) }

        val (suppressed, primary) = order.partition { isInDislikeCooldown(brain, inputs[it].artistKey, nowMs) }

        val target = surfaceTargetNovelty(surface, brain.discoveryAppetite)
        val novel = primary.filter { inputs[it].artistKey.isNotEmpty() && inputs[it].artistKey !in brain.seenArtists }.toSet()
        val adjacent = primary.filter { it in novel && isTasteAdjacent(brain, inputs[it], anchors) }.toSet()
        val composed = composeToRatio(primary, novel, adjacent, target)

        val maxRun =
            if (surface == SURFACE_RADIO) {
                MusicBrainParams.RADIO_MAX_CONSECUTIVE_ARTIST
            } else {
                MusicBrainParams.MAX_CONSECUTIVE_ARTIST
            }
        return spreadArtists(composed + suppressed, inputs, maxRun)
    }

    /** Interleave familiar and novel picks toward the target ratio, adjacent novelty first. */
    internal fun composeToRatio(
        order: List<Int>,
        novel: Set<Int>,
        adjacent: Set<Int>,
        target: Double,
    ): List<Int> {
        val novelQueue = ArrayDeque(order.filter { it in novel && it in adjacent } + order.filter { it in novel && it !in adjacent })
        val familiarQueue = ArrayDeque(order.filter { it !in novel })
        val result = ArrayList<Int>(order.size)
        var novelCount = 0.0
        repeat(order.size) {
            val wantNovel =
                when {
                    novelQueue.isEmpty() -> false
                    familiarQueue.isEmpty() -> true
                    else -> (novelCount + 0.5) / (result.size + 1.0) < target
                }
            if (wantNovel) {
                result.add(novelQueue.removeFirst())
                novelCount += 1.0
            } else {
                result.add(familiarQueue.removeFirst())
            }
        }
        return result
    }

    /**
     * Break same-artist runs longer than [maxRun]. Short runs are GOOD for music
     * shelves (album blocks) — only long ones get broken, and only when a
     * different-artist candidate actually exists further down. [previousArtist]
     * seeds the run so a batch appended after an existing queue also avoids a
     * same-artist seam at the boundary. Unknown artists (empty key) never count
     * as a run — two unattributed tracks are not evidence of repetition.
     */
    internal fun spreadArtists(
        order: List<Int>,
        inputs: List<MusicRankInput>,
        maxRun: Int = MusicBrainParams.MAX_CONSECUTIVE_ARTIST,
        previousArtist: String? = null,
    ): List<Int> {
        val remaining = ArrayDeque(order)
        val result = ArrayList<Int>(order.size)
        var lastArtist: String? = previousArtist?.takeIf { it.isNotEmpty() }
        var run = if (lastArtist != null) maxRun else 0
        while (remaining.isNotEmpty()) {
            var pos = 0
            if (run >= maxRun && !lastArtist.isNullOrEmpty()) {
                val alt = remaining.indexOfFirst { inputs[it].artistKey != lastArtist }
                if (alt >= 0) pos = alt
            }
            val idx = remaining.removeAt(pos)
            val artist = inputs[idx].artistKey
            if (artist.isNotEmpty() && artist == lastArtist) {
                run += 1
            } else {
                run = 1
                lastArtist = artist
            }
            result.add(idx)
        }
        return result
    }

    /**
     * On Repeat: tracks ordered by raw ACT-R activation. Zero network — the caller
     * resolves display data from [MusicBrain.trackMeta]. Blocked artists are skipped;
     * a track with no meta is kept (it just cannot be attributed).
     */
    fun heavyRotation(
        brain: MusicBrain,
        nowMs: Long,
        limit: Int,
    ): List<String> =
        brain.trackPlays.keys
            .asSequence()
            .filter { trackId ->
                val meta = brain.trackMeta[trackId]
                meta == null || !brain.isArtistBlocked(meta.artistKey)
            }.map { it to baseLevelActivation(brain, it, nowMs) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()

    /**
     * Rediscover: one best track per loved-but-quiet artist — strong affinity,
     * enough counted plays, and nothing played for [MusicBrainParams.REDISCOVER_STALE_MS].
     * Ordered by affinity so the most-missed artist leads. Zero network.
     */
    fun rediscover(
        brain: MusicBrain,
        nowMs: Long,
        limit: Int,
    ): List<String> {
        val staleBefore = nowMs - MusicBrainParams.REDISCOVER_STALE_MS

        // One pass over the track library: the best-remembered track per artist
        // (most ring entries, newest play breaks ties).
        data class Best(
            val trackId: String,
            val plays: Int,
            val newest: Long,
        )
        val bestByArtist = HashMap<String, Best>()
        for ((trackId, meta) in brain.trackMeta) {
            val stamps = brain.trackPlays[trackId] ?: continue
            if (stamps.isEmpty()) continue
            val candidate = Best(trackId, stamps.size, stamps.max())
            val current = bestByArtist[meta.artistKey]
            if (current == null ||
                candidate.plays > current.plays ||
                (candidate.plays == current.plays && candidate.newest > current.newest)
            ) {
                bestByArtist[meta.artistKey] = candidate
            }
        }

        return brain.artistAffinity.entries
            .asSequence()
            .filter { (key, aff) ->
                aff.plays >= MusicBrainParams.REDISCOVER_MIN_PLAYS &&
                    aff.score >= MusicBrainParams.REDISCOVER_MIN_SCORE &&
                    aff.lastPlayed in 1 until staleBefore &&
                    !brain.isArtistBlocked(key) &&
                    !isInDislikeCooldown(brain, key, nowMs)
            }.sortedByDescending { it.value.score }
            .mapNotNull { (key, _) -> bestByArtist[key]?.trackId }
            .take(limit)
            .toList()
    }

    /**
     * Time-of-day rotation: tracks the user actually plays in the CURRENT time
     * bucket, ranked by in-bucket play count with activation as the tie-break.
     * Bucket-conditional, so it complements (not duplicates) On Repeat. Zero network.
     */
    fun timeOfDayRotation(
        brain: MusicBrain,
        nowMs: Long,
        limit: Int,
    ): List<String> {
        val bucket = MusicTimeBucket.fromTimestamp(nowMs)
        return brain.trackPlays.entries
            .asSequence()
            .filter { (trackId, _) ->
                val meta = brain.trackMeta[trackId]
                meta == null ||
                    (!brain.isArtistBlocked(meta.artistKey) && !isInDislikeCooldown(brain, meta.artistKey, nowMs))
            }.map { (trackId, stamps) ->
                Triple(trackId, stamps.count { MusicTimeBucket.fromTimestamp(it) == bucket }, stamps)
            }.filter { it.second >= MusicBrainParams.TIME_BUCKET_MIN_PLAYS }
            .sortedWith(
                compareByDescending<Triple<String, Int, List<Long>>> { it.second }
                    .thenByDescending { baseLevelActivation(brain, it.first, nowMs) },
            ).take(limit)
            .map { it.first }
            .toList()
    }
}
