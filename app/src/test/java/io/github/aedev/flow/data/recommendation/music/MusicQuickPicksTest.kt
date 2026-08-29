/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.screens.music.MusicArtist
import io.github.aedev.flow.ui.screens.music.MusicTrack
import org.junit.Test

class MusicQuickPicksTest {
    private fun track(
        id: String,
        artistId: String,
    ) = MusicTrack(
        videoId = id,
        title = "T$id",
        artist = "A$artistId",
        thumbnailUrl = "",
        duration = 200,
        channelId = artistId,
        artists = listOf(MusicArtist("A$artistId", artistId)),
    )

    @Test
    fun `current track leads and artists are diversified first`() {
        val current = track("now", "UCa")
        val history =
            listOf(
                track("h1", "UCa"),
                track("h2", "UCa"),
                track("h3", "UCb"),
                track("h4", "UCc"),
                track("h5", "UCd"),
            )
        val seeds = MusicQuickPicks.selectSeeds(current, history)
        assertThat(seeds.first().videoId).isEqualTo("now")
        assertThat(seeds.map { it.videoId }).containsExactly("now", "h3", "h4", "h5", "h1").inOrder()
    }

    @Test
    fun `narrow history still fills the seed quota with repeats`() {
        val history = listOf(track("h1", "UCa"), track("h2", "UCa"), track("h3", "UCa"))
        val seeds = MusicQuickPicks.selectSeeds(null, history)
        assertThat(seeds.map { it.videoId }).containsExactly("h1", "h2", "h3").inOrder()
    }

    @Test
    fun `interleave round-robins lanes and dedupes globally`() {
        val laneA = listOf(track("a1", "UC1"), track("a2", "UC1"), track("shared", "UC1"))
        val laneB = listOf(track("b1", "UC2"), track("shared", "UC2"), track("b2", "UC2"))
        val mixed = MusicQuickPicks.interleave(listOf(laneA, laneB), limit = 10, excludedIds = emptySet())
        assertThat(mixed.map { it.videoId }).containsExactly("a1", "b1", "a2", "shared", "b2").inOrder()
    }

    @Test
    fun `interleave excludes seed ids and respects the limit`() {
        val lane = (0 until 10).map { track("t$it", "UC$it") }
        val mixed = MusicQuickPicks.interleave(listOf(lane), limit = 4, excludedIds = setOf("t0", "t1"))
        assertThat(mixed.map { it.videoId }).containsExactly("t2", "t3", "t4", "t5").inOrder()
    }

    @Test
    fun `interleave stops when all lanes are exhausted`() {
        val mixed = MusicQuickPicks.interleave(listOf(listOf(track("x", "UC1"))), limit = 24, excludedIds = emptySet())
        assertThat(mixed).hasSize(1)
    }
}
