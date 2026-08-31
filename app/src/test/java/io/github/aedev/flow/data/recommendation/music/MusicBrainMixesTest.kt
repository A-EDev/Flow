/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicBrainMixesTest {
    private val now = 1_700_000_000_000L

    private fun brainWith(vararg artists: Pair<String, Double>): MusicBrain {
        val brain = MusicBrain()
        artists.forEachIndexed { i, (key, score) ->
            brain.artistAffinity[key] = MusicAffinity(plays = 5, score = score, lastPlayed = now)
            val trackId = "t_$key"
            brain.trackMeta[trackId] = MusicTrackMeta(title = "Song $key", artist = "Artist $key", artistKey = key, thumbnail = "")
            brain.trackPlays[trackId] = mutableListOf(now - (i + 1) * 3_600_000L)
        }
        return brain
    }

    private fun cooc(
        brain: MusicBrain,
        a: String,
        b: String,
        weight: Double,
    ) {
        brain.artistCooc[musicPairKey(a, b)] = weight
    }

    @Test
    fun `clusters co-occurring artists into a mix`() {
        val brain = brainWith("UCa" to 0.9, "UCb" to 0.8, "UCc" to 0.7)
        cooc(brain, "UCa", "UCb", 3.0)

        val mixes = MusicBrainMixes.dailyMixes(brain, now, maxMixes = 3)

        assertThat(mixes).isNotEmpty()
        val first = mixes.first()
        assertThat(first.seedTrackIds).containsAtLeast("t_UCa", "t_UCb")
        assertThat(first.label).isEqualTo("Artist UCa")
    }

    @Test
    fun `empty graph yields no mixes`() {
        val brain = brainWith("UCa" to 0.9, "UCb" to 0.8)
        assertThat(MusicBrainMixes.dailyMixes(brain, now, maxMixes = 3)).isEmpty()
    }

    @Test
    fun `an artist below the cooc weight floor does not join a cluster`() {
        val brain = brainWith("UCa" to 0.9, "UCb" to 0.8)
        cooc(brain, "UCa", "UCb", 0.5)
        assertThat(MusicBrainMixes.dailyMixes(brain, now, maxMixes = 3)).isEmpty()
    }

    @Test
    fun `a blocked artist never anchors or joins a mix`() {
        val brain = brainWith("UCa" to 0.9, "UCb" to 0.8, "UCc" to 0.7)
        cooc(brain, "UCa", "UCb", 3.0)
        cooc(brain, "UCb", "UCc", 2.0)
        brain.blockedArtists.add("UCa")

        val mixes = MusicBrainMixes.dailyMixes(brain, now, maxMixes = 3)

        assertThat(mixes).hasSize(1)
        assertThat(mixes.first().seedTrackIds).containsExactly("t_UCb", "t_UCc").inOrder()
    }

    @Test
    fun `clustered artists are not reused by later mixes`() {
        val brain = brainWith("UCa" to 0.9, "UCb" to 0.8, "UCc" to 0.7, "UCd" to 0.6)
        cooc(brain, "UCa", "UCb", 3.0)
        cooc(brain, "UCc", "UCd", 2.0)

        val mixes = MusicBrainMixes.dailyMixes(brain, now, maxMixes = 3)

        assertThat(mixes).hasSize(2)
        val allSeeds = mixes.flatMap { it.seedTrackIds }
        assertThat(allSeeds).containsNoDuplicates()
    }
}
