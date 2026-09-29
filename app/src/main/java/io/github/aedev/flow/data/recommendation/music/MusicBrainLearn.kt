/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import kotlin.math.pow

/**
 * Pure learning functions — no I/O, no Android types. Every mutation of a
 * [MusicBrain] flows through here, called under the engine mutex.
 */
internal object MusicBrainLearn {
    fun ema(
        current: Double,
        target: Double,
        alpha: Double,
    ): Double = (current + (target - current) * alpha).coerceIn(0.0, 1.0)

    fun newlyCrossed(
        prev: Double,
        next: Double,
    ): List<Double> = MusicBrainParams.milestones.filter { prev < it && it <= next }

    fun milestoneWeight(m: Double): Double =
        when {
            m >= 0.90 -> 1.00
            m >= 0.50 -> 0.70
            else -> 0.35
        }

    /**
     * The complete learning step for one listen. Returns true when the listen COUNTED
     * (crossed the 50% milestone or was an explicit like) — the caller uses that to
     * advance the session co-occurrence anchor.
     */
    fun applyMusicSignal(
        brain: MusicBrain,
        sig: MusicSignal,
        crossed: List<Double>,
        nowMs: Long,
        coArtist: String?,
    ): Boolean {
        decayRotationIfDue(brain, nowMs)

        val artist = sig.artistKey
        if (artist.isEmpty()) return false

        // Fold an older name-keyed entry into this id-keyed one before learning, so
        // backfilled "drake" and live "UC…" never accumulate as two artists.
        reconcileNameKey(brain, sig)

        // Novelty must be read BEFORE the milestone loop marks the artist seen.
        val wasNovel = artist !in brain.seenArtists

        for (m in crossed) {
            if (m >= 0.15) brain.seenArtists.add(artist)
            val e = brain.artistAffinity.getOrPut(artist) { MusicAffinity() }
            e.score = ema(e.score, milestoneWeight(m), MusicBrainParams.ALPHA_LONG)
            e.lastPlayed = nowMs
            if (sig.artistDisplay.isNotBlank()) e.display = sig.artistDisplay
        }

        val counted = crossed.any { it >= MusicBrainParams.COUNT_MILESTONE } || sig.isExplicitLike

        if (counted) {
            pushPlay(brain, sig.trackId, nowMs)
            storeTrackMeta(brain, sig)

            val e = brain.artistAffinity.getOrPut(artist) { MusicAffinity() }
            e.plays += 1
            e.lastPlayed = nowMs
            if (sig.isExplicitLike) {
                e.liked = true
                e.score = maxOf(e.score, MusicBrainParams.LIKE_SCORE_FLOOR)
            }

            brain.recentRotation[artist] =
                ema(brain.recentRotation[artist] ?: 0.0, 1.0, MusicBrainParams.ALPHA_MED)

            brain.seenArtists.add(artist)
            brain.totalPlays += 1
            updateDiscoveryAppetite(brain, wasNovel)

            sig.genre?.takeIf { it.isNotBlank() }?.let { genre ->
                brain.genreAffinity[genre] =
                    ema(brain.genreAffinity[genre] ?: 0.0, 1.0, MusicBrainParams.ALPHA_LONG)
                val bucket = brain.timeBuckets.getOrPut(MusicTimeBucket.fromTimestamp(nowMs)) { HashMap() }
                bucket[genre] = (bucket[genre] ?: 0.0) + 1.0
            }

            if (coArtist != null && coArtist != artist) {
                val key = musicPairKey(artist, coArtist)
                brain.artistCooc[key] = (brain.artistCooc[key] ?: 0.0) + 1.0
            }

            // A real listen forgives a dislike; a block does not forgive.
            brain.dislikedArtists.remove(artist)
        }

        prune(brain)
        return counted
    }

    /** Lazy daily decay of the rotation overlay — no background job needed. */
    fun decayRotationIfDue(
        brain: MusicBrain,
        nowMs: Long,
    ) {
        if (brain.lastRotationDecay == 0L) {
            brain.lastRotationDecay = nowMs
            return
        }
        val elapsedDays = (nowMs - brain.lastRotationDecay) / 86_400_000L
        if (elapsedDays <= 0) return
        val factor = MusicBrainParams.ROTATION_DECAY_PER_DAY.pow(elapsedDays.toDouble())
        val iterator = brain.recentRotation.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val decayed = entry.value * factor
            if (decayed < MusicBrainParams.ROTATION_PRUNE_FLOOR) iterator.remove() else entry.setValue(decayed)
        }
        brain.lastRotationDecay = nowMs
    }

    fun updateDiscoveryAppetite(
        brain: MusicBrain,
        wasNovel: Boolean,
    ) {
        var appetite = brain.discoveryAppetite
        appetite += (MusicBrainParams.DISCOVERY_NEUTRAL - appetite) * MusicBrainParams.APPETITE_REGRESS
        if (wasNovel) appetite += MusicBrainParams.APPETITE_NOVEL_BONUS
        brain.discoveryAppetite = appetite.coerceIn(MusicBrainParams.APPETITE_MIN, MusicBrainParams.APPETITE_MAX)
    }

    /** Reversible cooldown; a second dislike inside the cooldown escalates to a permanent block. */
    fun applyDislike(
        brain: MusicBrain,
        artistKey: String,
        nowMs: Long,
    ) {
        if (artistKey.isEmpty()) return
        if (artistKey in brain.blockedArtists) return
        if (artistKey in brain.dislikedArtists) {
            blockArtist(brain, artistKey)
            return
        }
        brain.dislikedArtists[artistKey] = nowMs
        brain.artistAffinity[artistKey]?.let {
            it.score = (it.score * MusicBrainParams.DISLIKE_SCORE_FACTOR).coerceIn(0.0, 1.0)
            it.liked = false
        }
        brain.recentRotation.remove(artistKey)
        brain.discoveryAppetite =
            (brain.discoveryAppetite - MusicBrainParams.APPETITE_DISLIKE_NUDGE)
                .coerceIn(MusicBrainParams.APPETITE_MIN, MusicBrainParams.APPETITE_MAX)
        prune(brain)
    }

    fun blockArtist(
        brain: MusicBrain,
        artistKey: String,
    ) {
        if (artistKey.isEmpty()) return
        brain.blockedArtists.add(artistKey)
        brain.dislikedArtists.remove(artistKey)
        brain.recentRotation.remove(artistKey)
        brain.artistAffinity[artistKey]?.let {
            it.liked = false
            it.score = 0.0
        }
        prune(brain)
    }

    /** Affinity/plays/track history are preserved across a block, so unblocking warms back up. */
    fun unblockArtist(
        brain: MusicBrain,
        artistKey: String,
    ) {
        brain.blockedArtists.remove(artistKey)
    }

    /**
     * Every artist the shelves must hide right now: blocked, plus disliked ones
     * still inside the cooldown. Each artist contributes BOTH key forms — the
     * brain key and the lowercased display name — because the same artist is
     * id-keyed on tracks that carry a browseId and name-keyed on tracks that
     * don't, and feedback must remove them everywhere regardless.
     */
    fun hiddenArtistKeys(
        brain: MusicBrain,
        nowMs: Long,
    ): Set<String> {
        val keys = HashSet<String>(brain.blockedArtists)
        brain.dislikedArtists.forEach { (key, ts) ->
            if (nowMs - ts < MusicBrainParams.DISLIKE_COOLDOWN_MS) keys.add(key)
        }
        if (keys.isEmpty()) return emptySet()
        val expanded = HashSet<String>(keys.size * 2)
        for (key in keys) {
            expanded.add(key)
            val display =
                brain.artistAffinity[key]?.display?.takeIf { it.isNotBlank() }
                    ?: brain.trackMeta.values
                        .firstOrNull { it.artistKey == key }
                        ?.artist
            display
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?.let { expanded.add(it) }
        }
        return expanded
    }

    /** Replace the "fans also like" edges for one artist — platform knowledge, newest fetch wins. */
    fun recordArtistRelated(
        brain: MusicBrain,
        artistKey: String,
        relatedKeys: List<String>,
    ) {
        if (artistKey.isEmpty()) return
        val edges =
            relatedKeys
                .filter { it.isNotEmpty() && it != artistKey }
                .distinct()
                .take(MusicBrainParams.ARTIST_RELATED_EDGES)
        if (edges.isEmpty()) return
        brain.artistRelated[artistKey] = edges.toMutableList()
        prune(brain)
    }

    private fun pushPlay(
        brain: MusicBrain,
        trackId: String,
        ts: Long,
    ) {
        if (trackId.isEmpty()) return
        val ring = brain.trackPlays.getOrPut(trackId) { ArrayList(MusicBrainParams.TRACK_RING) }
        ring.add(ts)
        while (ring.size > MusicBrainParams.TRACK_RING) ring.removeAt(0)
    }

    private fun storeTrackMeta(
        brain: MusicBrain,
        sig: MusicSignal,
    ) {
        if (sig.title.isBlank() || sig.trackId.isEmpty()) return
        val display = sig.artistDisplay.ifBlank { sig.artistKey }
        brain.trackMeta[sig.trackId] =
            MusicTrackMeta(
                title = sig.title,
                artist = display,
                artistKey = sig.artistKey,
                thumbnail = sig.thumbnail,
            )
    }

    /**
     * When a live id-keyed play arrives for an artist we previously learned under a
     * lowercased name key (backfill, or history rows with no browseId), merge the
     * name-keyed state into the id key once so affinity never splits across two keys.
     */
    private fun reconcileNameKey(
        brain: MusicBrain,
        sig: MusicSignal,
    ) {
        if (!isIdKeyedArtist(sig.artistKey)) return
        val nameKey = sig.artistDisplay.trim().lowercase()
        if (nameKey.isEmpty() || nameKey == sig.artistKey) return

        brain.artistAffinity.remove(nameKey)?.let { old ->
            val target = brain.artistAffinity.getOrPut(sig.artistKey) { MusicAffinity() }
            target.plays += old.plays
            target.score = maxOf(target.score, old.score)
            target.lastPlayed = maxOf(target.lastPlayed, old.lastPlayed)
            target.liked = target.liked || old.liked
            if (target.display.isBlank()) target.display = old.display
        }
        brain.recentRotation.remove(nameKey)?.let { old ->
            brain.recentRotation[sig.artistKey] = maxOf(brain.recentRotation[sig.artistKey] ?: 0.0, old)
        }
        if (brain.seenArtists.remove(nameKey)) brain.seenArtists.add(sig.artistKey)
        if (nameKey in brain.blockedArtists) {
            brain.blockedArtists.remove(nameKey)
            brain.blockedArtists.add(sig.artistKey)
        }
        brain.dislikedArtists.remove(nameKey)?.let { ts -> brain.dislikedArtists[sig.artistKey] = ts }
        brain.trackMeta.entries.forEach { entry ->
            if (entry.value.artistKey == nameKey) {
                entry.setValue(entry.value.copy(artistKey = sig.artistKey))
            }
        }
    }

    private fun <K> keepTopBy(
        map: MutableMap<K, *>,
        maxSize: Int,
        keep: Int,
        scoreOf: (K) -> Double,
    ) {
        if (map.size <= maxSize) return
        val survivors =
            map.keys
                .sortedByDescending(scoreOf)
                .take(keep)
                .toHashSet()
        map.keys.retainAll(survivors)
    }

    fun prune(brain: MusicBrain) {
        keepTopBy(brain.artistAffinity, MusicBrainParams.ARTIST_AFFINITY_MAX, MusicBrainParams.ARTIST_AFFINITY_KEEP) {
            brain.artistAffinity[it]?.score ?: 0.0
        }
        keepTopBy(brain.trackPlays, MusicBrainParams.TRACK_PLAYS_MAX, MusicBrainParams.TRACK_PLAYS_KEEP) {
            brain.trackPlays[it]?.lastOrNull()?.toDouble() ?: 0.0
        }
        if (brain.trackMeta.size > brain.trackPlays.size) {
            brain.trackMeta.keys.retainAll(brain.trackPlays.keys)
        }
        keepTopBy(brain.recentRotation, MusicBrainParams.RECENT_ROTATION_MAX, MusicBrainParams.RECENT_ROTATION_KEEP) {
            brain.recentRotation[it] ?: 0.0
        }
        keepTopBy(brain.artistCooc, MusicBrainParams.ARTIST_COOC_MAX, MusicBrainParams.ARTIST_COOC_KEEP) {
            brain.artistCooc[it] ?: 0.0
        }
        keepTopBy(brain.artistRelated, MusicBrainParams.ARTIST_RELATED_MAX, MusicBrainParams.ARTIST_RELATED_KEEP) {
            brain.artistAffinity[it]?.score ?: 0.0
        }
        keepTopBy(brain.genreAffinity, MusicBrainParams.GENRE_AFFINITY_MAX, MusicBrainParams.GENRE_AFFINITY_MAX) {
            brain.genreAffinity[it] ?: 0.0
        }
        if (brain.seenArtists.size > MusicBrainParams.SEEN_ARTISTS_MAX) {
            val excess = brain.seenArtists.size - MusicBrainParams.SEEN_ARTISTS_KEEP
            val iterator = brain.seenArtists.iterator()
            repeat(excess) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        keepTopBy(brain.dislikedArtists, MusicBrainParams.DISLIKED_MAX, MusicBrainParams.DISLIKED_MAX) {
            brain.dislikedArtists[it]?.toDouble() ?: 0.0
        }
        if (brain.blockedArtists.size > MusicBrainParams.BLOCKED_MAX) {
            val excess = brain.blockedArtists.size - MusicBrainParams.BLOCKED_MAX
            val iterator = brain.blockedArtists.iterator()
            repeat(excess) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }
}
