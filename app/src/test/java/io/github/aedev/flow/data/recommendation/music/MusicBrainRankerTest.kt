/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicBrainRankerTest {
    private val now = 1_700_000_000_000L

    private fun input(
        trackId: String,
        artistKey: String,
    ) = MusicRankInput(trackId = trackId, artistKey = artistKey)

    private fun brainWithFavorite(
        artistKey: String = "UCfav",
        plays: Int = 20,
    ): MusicBrain {
        val brain = MusicBrain()
        brain.artistAffinity[artistKey] = MusicAffinity(plays = plays, score = 0.9, lastPlayed = now)
        brain.seenArtists.add(artistKey)
        brain.totalPlays = plays
        return brain
    }

    @Test
    fun `empty brain is a stable pass-through`() {
        val brain = MusicBrain()
        val inputs = (0 until 10).map { input("t$it", "UCa$it") }
        val order = MusicBrainRanker.rank(brain, inputs, MusicBrainRanker.SURFACE_QUICK_PICKS, now)
        assertThat(order).isEqualTo((0 until 10).toList())
    }

    @Test
    fun `quick picks promotes a familiar artist`() {
        val brain = brainWithFavorite()
        val inputs = listOf(input("u1", "UCunknown1"), input("u2", "UCunknown2"), input("f", "UCfav"))
        val order = MusicBrainRanker.rank(brain, inputs, MusicBrainRanker.SURFACE_QUICK_PICKS, now)
        assertThat(order.first()).isEqualTo(2)
    }

    @Test
    fun `discover exposes more novelty than quick picks on the same pool`() {
        val brain = brainWithFavorite()
        // Make novel artists taste-adjacent through the cooc graph so they count as discovery.
        (0 until 6).forEach { i -> brain.artistCooc[musicPairKey("UCfav", "UCnovel$i")] = 3.0 }
        brain.discoveryAppetite = 0.5
        val inputs =
            (0 until 6).map { input("f$it", "UCfav") } + (0 until 6).map { input("n$it", "UCnovel$it") }

        fun noveltyInTop(surface: String): Int {
            val order = MusicBrainRanker.rank(brain, inputs, surface, now)
            return order.take(6).count { inputs[it].artistKey.startsWith("UCnovel") }
        }
        assertThat(noveltyInTop(MusicBrainRanker.SURFACE_DISCOVER))
            .isGreaterThan(noveltyInTop(MusicBrainRanker.SURFACE_QUICK_PICKS))
    }

    @Test
    fun `blocked artist is dropped from ranking entirely`() {
        val brain = brainWithFavorite()
        brain.blockedArtists.add("UCbad")
        val inputs = listOf(input("a", "UCbad"), input("b", "UCfav"), input("c", "UCother"))
        val order = MusicBrainRanker.rank(brain, inputs, MusicBrainRanker.SURFACE_QUICK_PICKS, now)
        assertThat(order).doesNotContain(0)
        assertThat(order).hasSize(2)
    }

    @Test
    fun `disliked artist sinks below an unknown one but is not dropped`() {
        val brain = brainWithFavorite("UCdisliked")
        brain.dislikedArtists["UCdisliked"] = now - 1000
        val inputs = listOf(input("d", "UCdisliked"), input("u", "UCunknown"))
        val order = MusicBrainRanker.rank(brain, inputs, MusicBrainRanker.SURFACE_QUICK_PICKS, now)
        assertThat(order).isEqualTo(listOf(1, 0))
    }

    @Test
    fun `dislike cooldown expires after fourteen days`() {
        val brain = brainWithFavorite("UCdisliked")
        brain.dislikedArtists["UCdisliked"] = now - MusicBrainParams.DISLIKE_COOLDOWN_MS - 1
        assertThat(MusicBrainRanker.isInDislikeCooldown(brain, "UCdisliked", now)).isFalse()
    }

    @Test
    fun `no three consecutive tracks share an artist when an alternative exists`() {
        val brain = brainWithFavorite()
        val inputs =
            (0 until 5).map { input("f$it", "UCfav") } + listOf(input("x", "UCother1"), input("y", "UCother2"))
        val order = MusicBrainRanker.rank(brain, inputs, MusicBrainRanker.SURFACE_QUICK_PICKS, now)
        var run = 1
        var maxRun = 1
        for (i in 1 until order.size) {
            run = if (inputs[order[i]].artistKey == inputs[order[i - 1]].artistKey) run + 1 else 1
            maxRun = maxOf(maxRun, run)
        }
        assertThat(maxRun).isAtMost(MusicBrainParams.MAX_CONSECUTIVE_ARTIST)
    }

    @Test
    fun `heavy rotation orders by activation and recency dominates`() {
        val brain = MusicBrain()
        // "hot": 4 plays in the last 4 hours. "stale": 1 play 3 weeks ago.
        brain.trackPlays["hot"] = (0 until 4).map { now - it * 3_600_000L }.toMutableList()
        brain.trackPlays["stale"] = mutableListOf(now - 21L * 86_400_000L)
        val ids = MusicBrainRanker.heavyRotation(brain, now, 10)
        assertThat(ids.first()).isEqualTo("hot")
        assertThat(ids).doesNotContain("stale")
    }

    @Test
    fun `heavy rotation skips blocked artists`() {
        val brain = MusicBrain()
        brain.trackPlays["t"] = mutableListOf(now - 60_000L, now - 120_000L)
        brain.trackMeta["t"] = MusicTrackMeta("Song", "Bad", "UCbad", "")
        brain.blockedArtists.add("UCbad")
        assertThat(MusicBrainRanker.heavyRotation(brain, now, 10)).isEmpty()
    }

    @Test
    fun `activation rises with both frequency and recency`() {
        val brain = MusicBrain()
        brain.trackPlays["freq"] = (0 until 4).map { now - (it + 1) * 3_600_000L }.toMutableList()
        brain.trackPlays["single"] = mutableListOf(now - 4 * 3_600_000L)
        val freq = MusicBrainRanker.baseLevelActivation(brain, "freq", now)
        val single = MusicBrainRanker.baseLevelActivation(brain, "single", now)
        assertThat(freq).isGreaterThan(single)
        assertThat(single).isEqualTo(0.0)
    }

    @Test
    fun `context score is additive and cold-safe`() {
        val brain = MusicBrain()
        assertThat(MusicBrainRanker.contextScore(brain, MusicTimeBucket.WEEKDAY_MORNING, "phonk")).isEqualTo(0.0)
        brain.timeBuckets[MusicTimeBucket.WEEKDAY_MORNING] = hashMapOf("phonk" to 8.0, "jazz" to 2.0)
        val fit = MusicBrainRanker.contextScore(brain, MusicTimeBucket.WEEKDAY_MORNING, "phonk")
        assertThat(fit).isGreaterThan(0.0)
        assertThat(fit).isAtMost(1.0)
        assertThat(MusicBrainRanker.contextScore(brain, MusicTimeBucket.WEEKDAY_MORNING, null)).isEqualTo(0.0)
    }

    @Test
    fun `cooc boosts an artist that goes with favorites`() {
        val brain = brainWithFavorite()
        brain.artistCooc[musicPairKey("UCfav", "UCbuddy")] = 5.0
        val boosted = MusicBrainRanker.coocScore(brain, "UCbuddy", listOf("UCfav"))
        val stranger = MusicBrainRanker.coocScore(brain, "UCstranger", listOf("UCfav"))
        assertThat(boosted).isGreaterThan(stranger)
    }

    @Test
    fun `compose interleaves toward the target with adjacent novelty first`() {
        val order = (0 until 10).toList()
        val novel = setOf(5, 6, 7, 8, 9)
        val adjacent = setOf(8, 9)
        val composed = MusicBrainRanker.composeToRatio(order, novel, adjacent, target = 0.5)
        assertThat(composed).containsExactlyElementsIn(order)
        val firstNovel = composed.first { it in novel }
        assertThat(firstNovel).isAnyOf(8, 9)
        assertThat(composed.take(6).count { it in novel }).isAtLeast(2)
    }

    @Test
    fun `surface novelty targets are distinct and appetite flexes discovery only`() {
        val lowQp = MusicBrainRanker.surfaceTargetNovelty(MusicBrainRanker.SURFACE_QUICK_PICKS, 0.9)
        val highQp = MusicBrainRanker.surfaceTargetNovelty(MusicBrainRanker.SURFACE_QUICK_PICKS, 0.1)
        assertThat(lowQp).isEqualTo(highQp)

        val discoverLow = MusicBrainRanker.surfaceTargetNovelty(MusicBrainRanker.SURFACE_DISCOVER, 0.05)
        val discoverHigh = MusicBrainRanker.surfaceTargetNovelty(MusicBrainRanker.SURFACE_DISCOVER, 0.95)
        assertThat(discoverHigh).isGreaterThan(discoverLow)
        assertThat(discoverHigh).isAtMost(MusicBrainParams.NOVELTY_MAX)
        assertThat(lowQp).isLessThan(discoverLow)
    }
}
