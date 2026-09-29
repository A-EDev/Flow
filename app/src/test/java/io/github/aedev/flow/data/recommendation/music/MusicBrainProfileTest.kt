/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicBrainProfileTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `maturity labels follow the desktop thresholds`() {
        assertThat(MusicBrainProfile.maturityLabel(0)).isEqualTo("cold_start")
        assertThat(MusicBrainProfile.maturityLabel(14)).isEqualTo("cold_start")
        assertThat(MusicBrainProfile.maturityLabel(15)).isEqualTo("warming")
        assertThat(MusicBrainProfile.maturityLabel(80)).isEqualTo("warming")
        assertThat(MusicBrainProfile.maturityLabel(81)).isEqualTo("mature")
    }

    @Test
    fun `top artists exclude blocked and resolve display names`() {
        val brain = MusicBrain()
        brain.artistAffinity["UCa"] = MusicAffinity(plays = 10, score = 0.9, display = "Artist A")
        brain.artistAffinity["UCb"] = MusicAffinity(plays = 5, score = 0.8)
        brain.artistAffinity["UCblocked"] = MusicAffinity(plays = 20, score = 1.0)
        brain.blockedArtists.add("UCblocked")
        brain.trackMeta["t1"] = MusicTrackMeta(title = "T", artist = "Meta Name B", artistKey = "UCb", thumbnail = "")
        brain.totalPlays = 100

        val profile = MusicBrainProfile.tasteProfile(brain, now)

        assertThat(profile.topArtists.map { it.key }).containsExactly("UCa", "UCb").inOrder()
        assertThat(profile.topArtists[0].name).isEqualTo("Artist A")
        assertThat(profile.topArtists[1].name).isEqualTo("Meta Name B")
        assertThat(profile.distinctArtists).isEqualTo(2)
        assertThat(profile.maturity).isEqualTo("mature")
    }

    @Test
    fun `listening rhythm counts real play timestamps across all eight buckets`() {
        val brain = MusicBrain()
        brain.trackPlays["t1"] = mutableListOf(now, now - 3_600_000L)
        brain.trackPlays["t2"] = mutableListOf(now)

        val profile = MusicBrainProfile.tasteProfile(brain, now)

        assertThat(profile.timeOfDay).hasSize(MusicTimeBucket.entries.size)
        assertThat(profile.timeOfDay.sumOf { it.plays }).isEqualTo(3)
        assertThat(profile.trackedTracks).isEqualTo(2)
    }
}
