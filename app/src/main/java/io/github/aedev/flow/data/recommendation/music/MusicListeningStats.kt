/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import io.github.aedev.flow.data.stats.LedgerTime
import java.time.ZoneId

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
    /** Day of month (1..31) -> listening time, partial sessions included. */
    val dayMs: MutableMap<Int, Long> = HashMap(),
    /** Sessions ended before the first milestone after a deliberate listen. */
    val trackSkips: MutableMap<String, Int> = HashMap(),
    val artistSkips: MutableMap<String, Int> = HashMap(),
    /** Artists marked "not interested" this month, with when. */
    val dislikedArtists: MutableMap<String, Long> = HashMap(),
    /** Artists blocked this month, with when. */
    val blockedArtists: MutableMap<String, Long> = HashMap(),
    /** Artwork URLs seen with a play, for the recap's portraits; never fetched just for it. */
    val trackArt: MutableMap<String, String> = HashMap(),
    val artistArt: MutableMap<String, String> = HashMap(),
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
    const val SKIPS_PER_MONTH = 150
    const val SAID_NO_PER_MONTH = 100

    /** A session shorter than this is a mis-tap, not a skip. */
    const val MIN_SKIP_LISTEN_MS = 5_000L
}

/** Pure mutation functions, called under the engine mutex — no I/O, no Android. */
object MusicStatsLedgerOps {
    /** Calendar month key, local time: "2026-08". Sortable lexicographically. */
    fun monthKey(
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = LedgerTime.at(nowMs, zone).monthKey

    /**
     * One listening session. [skipped] marks a deliberate listen that ended before the first
     * milestone; it names the track and artist so the recap can say what was passed over.
     */
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
        skipped: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
        artworkUrl: String = "",
    ) {
        if (artistKey.isEmpty() || (listenedMs <= 0L && !counted)) return
        val moment = LedgerTime.at(nowMs, zone)
        val month = ledger.months.getOrPut(moment.monthKey) { MonthListening() }
        month.sessions += 1
        val listened = listenedMs.coerceAtLeast(0L)
        month.listenedMs += listened
        if (listened > 0L) month.dayMs[moment.dayOfMonth] = (month.dayMs[moment.dayOfMonth] ?: 0L) + listened

        if (!counted) {
            if (skipped && listened >= MusicStatsParams.MIN_SKIP_LISTEN_MS) {
                month.trackSkips[trackId] = (month.trackSkips[trackId] ?: 0) + 1
                month.artistSkips[artistKey] = (month.artistSkips[artistKey] ?: 0) + 1
                nameIn(month, artistKey, artistName, trackId, trackTitle)
            }
            prune(ledger)
            return
        }

        month.plays += 1
        month.artistPlays[artistKey] = (month.artistPlays[artistKey] ?: 0) + 1
        month.trackPlays[trackId] = (month.trackPlays[trackId] ?: 0) + 1
        nameIn(month, artistKey, artistName, trackId, trackTitle)
        if (artworkUrl.isNotBlank()) {
            month.trackArt[trackId] = artworkUrl
            month.artistArt[artistKey] = artworkUrl
        }
        genre?.takeIf { it.isNotBlank() }?.let { month.genrePlays[it] = (month.genrePlays[it] ?: 0) + 1 }
        if (newArtist) month.discoveredArtists.add(artistKey)
        month.dayPlays[moment.dayOfMonth] = (month.dayPlays[moment.dayOfMonth] ?: 0) + 1
        month.hourPlays[moment.hourOfDay] = (month.hourPlays[moment.hourOfDay] ?: 0) + 1

        prune(ledger)
    }

    /** "Not interested" ([blocked] false) or "don't recommend" ([blocked] true) on an artist. */
    fun recordSaidNo(
        ledger: MusicStatsLedger,
        nowMs: Long,
        artistKey: String,
        artistName: String,
        blocked: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (artistKey.isEmpty()) return
        val month = ledger.months.getOrPut(LedgerTime.at(nowMs, zone).monthKey) { MonthListening() }
        (if (blocked) month.blockedArtists else month.dislikedArtists)[artistKey] = nowMs
        if (artistName.isNotBlank()) month.artistNames[artistKey] = artistName
        prune(ledger)
    }

    private fun nameIn(
        month: MonthListening,
        artistKey: String,
        artistName: String,
        trackId: String,
        trackTitle: String,
    ) {
        if (artistName.isNotBlank()) month.artistNames[artistKey] = artistName
        if (trackTitle.isNotBlank()) month.trackTitles[trackId] = trackTitle
    }

    fun prune(ledger: MusicStatsLedger) {
        LedgerTime.capMonths(ledger.months, MusicStatsParams.MONTHS_MAX)
        for (month in ledger.months.values) {
            LedgerTime.capWeakest(month.trackSkips, MusicStatsParams.SKIPS_PER_MONTH)
            LedgerTime.capWeakest(month.artistSkips, MusicStatsParams.SKIPS_PER_MONTH)
            LedgerTime.capWeakest(month.dislikedArtists, MusicStatsParams.SAID_NO_PER_MONTH)
            LedgerTime.capWeakest(month.blockedArtists, MusicStatsParams.SAID_NO_PER_MONTH)
            LedgerTime.capWeakest(month.genrePlays, MusicStatsParams.GENRES_PER_MONTH)
            capNamed(
                month.artistPlays,
                MusicStatsParams.ARTISTS_PER_MONTH,
                month.artistNames,
                month.artistSkips,
                month.dislikedArtists,
                month.blockedArtists,
            )
            capNamed(month.trackPlays, MusicStatsParams.TRACKS_PER_MONTH, month.trackTitles, month.trackSkips)
            month.trackArt.keys.retainAll(month.trackPlays.keys)
            month.artistArt.keys.retainAll(month.artistPlays.keys)
        }
    }

    /** Caps [plays], dropping a name only when nothing else in the month still refers to it. */
    private fun capNamed(
        plays: MutableMap<String, Int>,
        cap: Int,
        names: MutableMap<String, String>,
        vararg otherUses: Map<String, *>,
    ) {
        if (plays.size <= cap) return
        val dropped =
            plays.entries
                .sortedBy { it.value }
                .take(plays.size - cap)
                .map { it.key }
        dropped.forEach { key ->
            plays.remove(key)
            if (otherUses.none { key in it }) names.remove(key)
        }
    }
}
