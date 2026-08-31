/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import java.util.Calendar

/**
 * Per-month listening aggregates — the data a Wrapped-style recap needs but the
 * brain deliberately does not keep (rings hold only the 8 newest plays, affinity
 * is all-time). Device-local, never synced, never part of the brain wire format.
 *
 * Display names are stored in-ledger on purpose: the brain prunes weak
 * affinities and old track meta, and a December recap must still render a
 * March artist by name.
 */
class MonthListening(
    /** Counted plays: the ≥50% milestone or an explicit like. */
    var plays: Int = 0,
    /** Every session that produced any listening time, partial ones included. */
    var sessions: Int = 0,
    var listenedMs: Long = 0L,
    val artistPlays: MutableMap<String, Int> = HashMap(),
    val artistNames: MutableMap<String, String> = HashMap(),
    val trackPlays: MutableMap<String, Int> = HashMap(),
    val trackTitles: MutableMap<String, String> = HashMap(),
    val genrePlays: MutableMap<String, Int> = HashMap(),
    /** Artists whose first counted play ever happened this month. */
    val discoveredArtists: MutableSet<String> = HashSet(),
    /** Day of month (1..31) -> counted plays; feeds streaks and the calendar. */
    val dayPlays: MutableMap<Int, Int> = HashMap(),
    /** Hour of day (0..23) -> counted plays; feeds the listening clock. */
    val hourPlays: MutableMap<Int, Int> = HashMap(),
)

class MusicStatsLedger {
    val months: MutableMap<String, MonthListening> = HashMap()
    var schemaVersion: Int = MusicStatsParams.SCHEMA_VERSION
}

object MusicStatsParams {
    const val SCHEMA_VERSION = 1
    const val MONTHS_MAX = 36
    const val ARTISTS_PER_MONTH = 250
    const val TRACKS_PER_MONTH = 300
    const val GENRES_PER_MONTH = 50
}

/** Pure mutation functions, called under the engine mutex — no I/O, no Android. */
object MusicStatsLedgerOps {
    /** Calendar month key, local time: "2026-08". Sortable lexicographically. */
    fun monthKey(nowMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun record(
        ledger: MusicStatsLedger,
        nowMs: Long,
        artistKey: String,
        artistName: String,
        trackId: String,
        trackTitle: String,
        genre: String?,
        listenedMs: Long,
        counted: Boolean,
        newArtist: Boolean,
    ) {
        if (artistKey.isEmpty() || (listenedMs <= 0L && !counted)) return
        val month = ledger.months.getOrPut(monthKey(nowMs)) { MonthListening() }
        month.sessions += 1
        month.listenedMs += listenedMs.coerceAtLeast(0L)
        if (!counted) {
            prune(ledger)
            return
        }

        month.plays += 1
        month.artistPlays[artistKey] = (month.artistPlays[artistKey] ?: 0) + 1
        if (artistName.isNotBlank()) month.artistNames[artistKey] = artistName
        month.trackPlays[trackId] = (month.trackPlays[trackId] ?: 0) + 1
        if (trackTitle.isNotBlank()) month.trackTitles[trackId] = trackTitle
        genre?.takeIf { it.isNotBlank() }?.let { month.genrePlays[it] = (month.genrePlays[it] ?: 0) + 1 }
        if (newArtist) month.discoveredArtists.add(artistKey)

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        month.dayPlays[day] = (month.dayPlays[day] ?: 0) + 1
        month.hourPlays[hour] = (month.hourPlays[hour] ?: 0) + 1

        prune(ledger)
    }

    fun prune(ledger: MusicStatsLedger) {
        if (ledger.months.size > MusicStatsParams.MONTHS_MAX) {
            ledger.months.keys
                .sorted()
                .take(ledger.months.size - MusicStatsParams.MONTHS_MAX)
                .forEach { ledger.months.remove(it) }
        }
        for (month in ledger.months.values) {
            capCounts(month.artistPlays, MusicStatsParams.ARTISTS_PER_MONTH, month.artistNames)
            capCounts(month.trackPlays, MusicStatsParams.TRACKS_PER_MONTH, month.trackTitles)
            capCounts(month.genrePlays, MusicStatsParams.GENRES_PER_MONTH, names = null)
        }
    }

    private fun capCounts(
        counts: MutableMap<String, Int>,
        cap: Int,
        names: MutableMap<String, String>?,
    ) {
        if (counts.size <= cap) return
        counts.entries
            .sortedBy { it.value }
            .take(counts.size - cap)
            .map { it.key }
            .forEach { key ->
                counts.remove(key)
                names?.remove(key)
            }
    }
}
