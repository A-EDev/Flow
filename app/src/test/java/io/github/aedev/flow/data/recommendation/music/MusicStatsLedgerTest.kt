/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicStatsLedgerTest {
    private val now = 1_700_000_000_000L

    private fun record(
        ledger: MusicStatsLedger,
        at: Long = now,
        artistKey: String = "UCa",
        artistName: String = "Artist A",
        trackId: String = "t1",
        trackTitle: String = "Song One",
        genre: String? = null,
        listenedMs: Long = 60_000L,
        counted: Boolean = true,
        newArtist: Boolean = false,
    ) = MusicStatsLedgerOps.record(
        ledger,
        at,
        artistKey,
        artistName,
        trackId,
        trackTitle,
        genre,
        listenedMs,
        counted,
        newArtist,
    )

    @Test
    fun `counted play lands in every aggregate of its month`() {
        val ledger = MusicStatsLedger()
        record(ledger, genre = "pop", newArtist = true)

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.plays).isEqualTo(1)
        assertThat(month.sessions).isEqualTo(1)
        assertThat(month.listenedMs).isEqualTo(60_000L)
        assertThat(month.artistPlays["UCa"]).isEqualTo(1)
        assertThat(month.artistNames["UCa"]).isEqualTo("Artist A")
        assertThat(month.trackPlays["t1"]).isEqualTo(1)
        assertThat(month.trackTitles["t1"]).isEqualTo("Song One")
        assertThat(month.genrePlays["pop"]).isEqualTo(1)
        assertThat(month.discoveredArtists).containsExactly("UCa")
        assertThat(month.dayPlays.values.sum()).isEqualTo(1)
        assertThat(month.hourPlays.values.sum()).isEqualTo(1)
    }

    @Test
    fun `partial session adds minutes and a session but no play`() {
        val ledger = MusicStatsLedger()
        record(ledger, listenedMs = 30_000L, counted = false)

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.plays).isEqualTo(0)
        assertThat(month.sessions).isEqualTo(1)
        assertThat(month.listenedMs).isEqualTo(30_000L)
        assertThat(month.artistPlays).isEmpty()
        assertThat(month.discoveredArtists).isEmpty()
    }

    @Test
    fun `plays split across calendar months`() {
        val ledger = MusicStatsLedger()
        val sixtyDays = 60L * 24 * 60 * 60 * 1000
        record(ledger, at = now)
        record(ledger, at = now - sixtyDays, trackId = "t2", trackTitle = "Song Two")

        assertThat(ledger.months).hasSize(2)
        assertThat(MusicStatsLedgerOps.monthKey(now)).isNotEqualTo(MusicStatsLedgerOps.monthKey(now - sixtyDays))
    }

    @Test
    fun `per-month artist cap drops the weakest and their names`() {
        val ledger = MusicStatsLedger()
        record(ledger, artistKey = "UCstrong", artistName = "Strong", trackId = "s")
        record(ledger, artistKey = "UCstrong", artistName = "Strong", trackId = "s")
        (0 until MusicStatsParams.ARTISTS_PER_MONTH).forEach { i ->
            record(ledger, artistKey = "UCa$i", artistName = "A$i", trackId = "t$i")
        }

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.artistPlays.size).isAtMost(MusicStatsParams.ARTISTS_PER_MONTH)
        assertThat(month.artistPlays).containsKey("UCstrong")
        assertThat(month.artistNames.keys).isEqualTo(month.artistPlays.keys)
    }

    @Test
    fun `ledger serialization round-trips`() {
        val ledger = MusicStatsLedger()
        record(ledger, genre = "pop", newArtist = true)
        record(ledger, listenedMs = 10_000L, counted = false)

        val restored = ledger.toSerializable().toLedger()
        val key = MusicStatsLedgerOps.monthKey(now)
        assertThat(restored.months.getValue(key).plays).isEqualTo(1)
        assertThat(restored.months.getValue(key).sessions).isEqualTo(2)
        assertThat(restored.months.getValue(key).listenedMs).isEqualTo(70_000L)
        assertThat(restored.months.getValue(key).discoveredArtists).containsExactly("UCa")
        assertThat(restored.schemaVersion).isEqualTo(MusicStatsParams.SCHEMA_VERSION)
    }
}
