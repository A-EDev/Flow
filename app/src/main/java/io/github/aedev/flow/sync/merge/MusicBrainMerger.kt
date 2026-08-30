package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.CanonicalMusicAffinity
import io.github.aedev.flow.sync.canonical.CanonicalMusicBrain
import io.github.aedev.flow.sync.canonical.CanonicalMusicTrackMeta
import io.github.aedev.flow.sync.canonical.Lww

/**
 * CRDT merge for the `music_brain` collection, mirroring the desktop's
 * `MergedMusicBrain::merge_snapshot` rule-for-rule (spec §19): commutative,
 * associative, and idempotent, so any merge order converges.
 */
object MusicBrainMerger {
    /** Ring size of `trackPlays` — matches `MusicBrainParams.TRACK_RING`. */
    private const val TRACK_RING = 8

    fun merge(
        a: CanonicalMusicBrain,
        b: CanonicalMusicBrain,
    ): CanonicalMusicBrain =
        CanonicalMusicBrain(
            schema = maxOf(a.schema, b.schema),
            deviceId = a.deviceId.ifEmpty { b.deviceId },
            hlc = Crdt.maxHlc(a.hlc, b.hlc),
            artistAffinity = Crdt.mergeKeyed(a.artistAffinity, b.artistAffinity, ::mergeAffinity),
            genreAffinity = Crdt.mergeMaxDouble(a.genreAffinity, b.genreAffinity),
            artistCooc = Crdt.mergeMaxDouble(a.artistCooc, b.artistCooc),
            recentRotation = Crdt.mergeMaxDouble(a.recentRotation, b.recentRotation),
            timeBuckets = Crdt.mergeKeyed(a.timeBuckets, b.timeBuckets, Crdt::mergeMaxDouble),
            trackPlays = Crdt.mergeKeyed(a.trackPlays, b.trackPlays, ::mergePlayTimestamps),
            trackMeta = Crdt.mergeKeyed(a.trackMeta, b.trackMeta, ::mergeTrackMeta),
            totalPlays = a.totalPlays.merge(b.totalPlays),
            seenArtists = a.seenArtists.merge(b.seenArtists),
            blockedArtists = a.blockedArtists.merge(b.blockedArtists),
            dislikedArtists = Crdt.mergeKeyed(a.dislikedArtists, b.dislikedArtists) { x, y -> x.merge(y) },
            discoveryAppetite = mergeAppetite(a.discoveryAppetite, b.discoveryAppetite),
        )

    private fun mergeAffinity(
        x: CanonicalMusicAffinity,
        y: CanonicalMusicAffinity,
    ): CanonicalMusicAffinity {
        // score comes from the HLC-winning side; ties break on the larger value so the
        // choice is independent of argument order.
        val scoreWinner =
            Crdt.preferByHlc(x, x.hlc, y, y.hlc) { it.score.toString() }
        return CanonicalMusicAffinity(
            plays = x.plays.merge(y.plays),
            score = scoreWinner.score,
            lastPlayed = maxOf(x.lastPlayed, y.lastPlayed),
            liked = x.liked || y.liked,
            hlc = Crdt.maxHlc(x.hlc, y.hlc),
        )
    }

    /** Union → dedupe → keep the newest [TRACK_RING], ascending (ring order). Idempotent. */
    private fun mergePlayTimestamps(
        x: List<Long>,
        y: List<Long>,
    ): List<Long> =
        (x.asSequence() + y.asSequence())
            .distinct()
            .sorted()
            .toList()
            .takeLast(TRACK_RING)

    /** Deterministic tie-break: keep the lexicographically greater (title, artist). */
    private fun mergeTrackMeta(
        x: CanonicalMusicTrackMeta,
        y: CanonicalMusicTrackMeta,
    ): CanonicalMusicTrackMeta {
        val cmp = compareValuesBy(x, y, { it.title }, { it.artist })
        return if (cmp >= 0) x else y
    }

    private fun mergeAppetite(
        x: Lww<Double>?,
        y: Lww<Double>?,
    ): Lww<Double>? =
        when {
            x == null -> y
            y == null -> x
            else -> x.merge(y)
        }
}
