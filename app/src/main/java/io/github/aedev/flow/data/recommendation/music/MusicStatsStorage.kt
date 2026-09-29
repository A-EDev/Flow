/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import android.content.Context
import io.github.aedev.flow.data.stats.LedgerTime
import io.github.aedev.flow.data.stats.MonthlyLedgerStore
import kotlinx.serialization.Serializable

/**
 * Persistence for the listening ledger, in its own files so brain saves stay cheap and the brain's
 * sync wire format is untouched. The original single file is kept as the closed-months file, so a
 * ledger written by an older version loads unchanged.
 */
internal class MusicStatsStorage(
    appContext: Context,
) {
    @Serializable
    data class SerializableMonth(
        val plays: Int = 0,
        val sessions: Int = 0,
        val listenedMs: Long = 0L,
        val artistPlays: Map<String, Int> = emptyMap(),
        val artistNames: Map<String, String> = emptyMap(),
        val trackPlays: Map<String, Int> = emptyMap(),
        val trackTitles: Map<String, String> = emptyMap(),
        val genrePlays: Map<String, Int> = emptyMap(),
        val discoveredArtists: List<String> = emptyList(),
        val dayPlays: Map<Int, Int> = emptyMap(),
        val hourPlays: Map<Int, Int> = emptyMap(),
        val dayMs: Map<Int, Long> = emptyMap(),
        val trackSkips: Map<String, Int> = emptyMap(),
        val artistSkips: Map<String, Int> = emptyMap(),
        val dislikedArtists: Map<String, Long> = emptyMap(),
        val blockedArtists: Map<String, Long> = emptyMap(),
        val trackArt: Map<String, String> = emptyMap(),
        val artistArt: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class SerializableStats(
        val schemaVersion: Int = MusicStatsParams.SCHEMA_VERSION,
        val months: Map<String, SerializableMonth> = emptyMap(),
    )

    private val store =
        MonthlyLedgerStore(
            appContext = appContext,
            tag = "MusicStatsStorage",
            hotFileName = "flow_music_stats_hot_v1.json",
            coldFileName = "flow_music_stats_v1.json",
            monthSerializer = SerializableMonth.serializer(),
        )

    suspend fun save(
        ledger: MusicStatsLedger,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val currentKey = LedgerTime.at(nowMs).monthKey
        store.save(
            currentKey = currentKey,
            current = ledger.months[currentKey]?.toSerializable(),
            closedKeys = ledger.months.keys - currentKey,
            knownKeys = emptySet(),
        ) { ledger.months.filterKeys { it != currentKey }.mapValues { it.value.toSerializable() } }
    }

    suspend fun load(): MusicStatsLedger? {
        val loaded = store.load()
        if (loaded.months.isEmpty()) return null
        return SerializableStats(months = loaded.months).toLedger()
    }

    /** Replaces the stored ledger, for a restore. */
    suspend fun replace(stats: SerializableStats) {
        store.replace(stats.months, emptySet(), LedgerTime.at(System.currentTimeMillis()).monthKey)
    }
}

private fun MonthListening.toSerializable() =
    MusicStatsStorage.SerializableMonth(
        plays = plays,
        sessions = sessions,
        listenedMs = listenedMs,
        artistPlays = artistPlays.toMap(),
        artistNames = artistNames.toMap(),
        trackPlays = trackPlays.toMap(),
        trackTitles = trackTitles.toMap(),
        genrePlays = genrePlays.toMap(),
        discoveredArtists = discoveredArtists.toList(),
        dayPlays = dayPlays.toMap(),
        hourPlays = hourPlays.toMap(),
        dayMs = dayMs.toMap(),
        trackSkips = trackSkips.toMap(),
        artistSkips = artistSkips.toMap(),
        dislikedArtists = dislikedArtists.toMap(),
        blockedArtists = blockedArtists.toMap(),
        trackArt = trackArt.toMap(),
        artistArt = artistArt.toMap(),
    )

internal fun MusicStatsLedger.toSerializable(): MusicStatsStorage.SerializableStats =
    MusicStatsStorage.SerializableStats(
        schemaVersion = schemaVersion,
        months = months.mapValues { (_, m) -> m.toSerializable() },
    )

internal fun MusicStatsStorage.SerializableStats.toLedger(): MusicStatsLedger {
    val ledger = MusicStatsLedger()
    ledger.schemaVersion = schemaVersion
    for ((key, m) in months) {
        ledger.months[key] =
            MonthListening(
                plays = m.plays,
                sessions = m.sessions,
                listenedMs = m.listenedMs,
                artistPlays = HashMap(m.artistPlays),
                artistNames = HashMap(m.artistNames),
                trackPlays = HashMap(m.trackPlays),
                trackTitles = HashMap(m.trackTitles),
                genrePlays = HashMap(m.genrePlays),
                discoveredArtists = HashSet(m.discoveredArtists),
                dayPlays = HashMap(m.dayPlays),
                hourPlays = HashMap(m.hourPlays),
                dayMs = HashMap(m.dayMs),
                trackSkips = HashMap(m.trackSkips),
                artistSkips = HashMap(m.artistSkips),
                dislikedArtists = HashMap(m.dislikedArtists),
                blockedArtists = HashMap(m.blockedArtists),
                trackArt = HashMap(m.trackArt),
                artistArt = HashMap(m.artistArt),
            )
    }
    return ledger
}
