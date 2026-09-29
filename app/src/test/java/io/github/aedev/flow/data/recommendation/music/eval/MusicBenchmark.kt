/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music.eval

import io.github.aedev.flow.data.recommendation.music.MusicBrain
import io.github.aedev.flow.data.recommendation.music.MusicBrainLearn
import io.github.aedev.flow.data.recommendation.music.MusicBrainRanker
import io.github.aedev.flow.data.recommendation.music.MusicRankInput
import io.github.aedev.flow.data.recommendation.music.MusicSignal

/**
 * Offline benchmark over the real learn + rank pipeline with music-native
 * metrics — some deliberately the INVERSE of the video engine's: surfacing a
 * loved track again is a success here, not a repetition bug.
 */
internal object MusicBenchmark {
    private const val COMFORT_ARTISTS = 4
    private const val TRACKS_PER_COMFORT_ARTIST = 5
    private const val NOVEL_ARTISTS = 30
    private const val TRAINING_DAYS = 25
    private const val LISTENS_PER_DAY = 6
    private const val TOP_K = 10

    data class SurfaceMetrics(
        val hitRate: Double,
        val discoveryRate: Double,
        val maxArtistRun: Int,
    )

    data class Result(
        val brain: MusicBrain,
        val heldoutRelistens: Set<String>,
        val quickPicks: SurfaceMetrics,
        val discover: SurfaceMetrics,
        val coldPassthroughHolds: Boolean,
        val blockedLeaks: Int,
    )

    private fun comfortTrackId(
        artist: Int,
        track: Int,
    ) = "c${artist}t$track"

    private fun signal(
        trackId: String,
        artistKey: String,
        pct: Double = 1.0,
    ) = MusicSignal(
        trackId = trackId,
        artistKey = artistKey,
        artistDisplay = artistKey.removePrefix("UC"),
        percentPlayed = pct,
        title = "Track $trackId",
        thumbnail = "",
    )

    /** Comfort-listener persona: a few artists on heavy rotation, sessions that co-listen them. */
    fun trainBrain(now: Long): MusicBrain {
        val brain = MusicBrain()
        var lastCounted: String? = null
        val dayMs = 86_400_000L
        val start = now - TRAINING_DAYS.toLong() * dayMs

        var tick = 0
        for (day in 0 until TRAINING_DAYS) {
            for (listen in 0 until LISTENS_PER_DAY) {
                val artistIdx = tick % COMFORT_ARTISTS
                // The first two tracks of artist 0 are the "on repeat" favorites.
                val trackIdx = if (artistIdx == 0) tick % 2 else tick % TRACKS_PER_COMFORT_ARTIST
                val artistKey = "UCcomf$artistIdx"
                val trackId = comfortTrackId(artistIdx, trackIdx)
                val ts = start + day * dayMs + listen * 600_000L
                val sig = signal(trackId, artistKey)
                val counted =
                    MusicBrainLearn.applyMusicSignal(
                        brain,
                        sig,
                        MusicBrainLearn.newlyCrossed(0.0, sig.percentPlayed),
                        ts,
                        lastCounted?.takeIf { it != artistKey },
                    )
                if (counted) lastCounted = artistKey
                tick++
            }
        }
        return brain
    }

    fun candidatePool(): List<MusicRankInput> {
        val comfort =
            (0 until COMFORT_ARTISTS).flatMap { a ->
                (0 until 3).map { t -> MusicRankInput(comfortTrackId(a, t), "UCcomf$a") }
            }
        val novel = (0 until NOVEL_ARTISTS).map { MusicRankInput("n$it", "UCnov$it") }
        return comfort + novel
    }

    private fun maxArtistRun(
        order: List<Int>,
        pool: List<MusicRankInput>,
    ): Int {
        var run = 1
        var maxRun = 1
        for (i in 1 until order.size) {
            run = if (pool[order[i]].artistKey == pool[order[i - 1]].artistKey) run + 1 else 1
            maxRun = maxOf(maxRun, run)
        }
        return maxRun
    }

    private fun surfaceMetrics(
        brain: MusicBrain,
        pool: List<MusicRankInput>,
        surface: String,
        heldout: Set<String>,
        now: Long,
    ): SurfaceMetrics {
        val order = MusicBrainRanker.rank(brain, pool, surface, now)
        val topK = order.take(TOP_K)
        val hits = topK.count { pool[it].trackId in heldout }
        val discovery = topK.count { pool[it].artistKey !in brain.seenArtists }
        return SurfaceMetrics(
            hitRate = hits.toDouble() / heldout.size,
            discoveryRate = discovery.toDouble() / TOP_K,
            maxArtistRun = maxArtistRun(order, pool),
        )
    }

    fun run(now: Long = System.currentTimeMillis()): Result {
        val brain = trainBrain(now)
        val pool = candidatePool()

        // The relisten set the ranker should keep surfacing: the persona's two
        // by-construction favorites (every play of artist 0 lands on these).
        val heldout = setOf(comfortTrackId(0, 0), comfortTrackId(0, 1))

        val quickPicks = surfaceMetrics(brain, pool, MusicBrainRanker.SURFACE_QUICK_PICKS, heldout, now)
        val discover = surfaceMetrics(brain, pool, MusicBrainRanker.SURFACE_DISCOVER, heldout, now)

        // Pass-through is guaranteed modulo the artist-spread rule (which applies on
        // every brain, desktop included) — so judge it on a pool with no 3-in-a-row artist.
        val interleaved =
            (0 until 3).flatMap { t ->
                (0 until COMFORT_ARTISTS).map { a -> MusicRankInput(comfortTrackId(a, t), "UCcomf$a") }
            } + (0 until NOVEL_ARTISTS).map { MusicRankInput("n$it", "UCnov$it") }
        val cold = MusicBrainRanker.rank(MusicBrain(), interleaved, MusicBrainRanker.SURFACE_QUICK_PICKS, now)
        val coldHolds = cold == interleaved.indices.toList()

        MusicBrainLearn.blockArtist(brain, "UCcomf1")
        val afterBlock = MusicBrainRanker.rank(brain, pool, MusicBrainRanker.SURFACE_QUICK_PICKS, now)
        val blockedLeaks = afterBlock.count { pool[it].artistKey == "UCcomf1" }

        return Result(
            brain = brain,
            heldoutRelistens = heldout,
            quickPicks = quickPicks,
            discover = discover,
            coldPassthroughHolds = coldHolds,
            blockedLeaks = blockedLeaks,
        )
    }

    fun renderReport(r: Result): String =
        buildString {
            appendLine("═══ Music Benchmark ═══")
            appendLine("trainedPlays=${r.brain.totalPlays}  artists=${r.brain.artistAffinity.size}  cooc=${r.brain.artistCooc.size}")
            appendLine("heldout relistens        = ${r.heldoutRelistens.sorted()}")
            appendLine("quick_picks hitRate@10   = %.3f".format(r.quickPicks.hitRate))
            appendLine("quick_picks discovery    = %.3f".format(r.quickPicks.discoveryRate))
            appendLine("quick_picks maxRun       = ${r.quickPicks.maxArtistRun}")
            appendLine("discover hitRate@10      = %.3f".format(r.discover.hitRate))
            appendLine("discover discovery       = %.3f".format(r.discover.discoveryRate))
            appendLine("discover maxRun          = ${r.discover.maxArtistRun}")
            appendLine("cold passthrough         = ${r.coldPassthroughHolds}")
            appendLine("blocked leaks            = ${r.blockedLeaks}")
            appendLine("discoveryAppetite        = %.3f".format(r.brain.discoveryAppetite))
        }
}
