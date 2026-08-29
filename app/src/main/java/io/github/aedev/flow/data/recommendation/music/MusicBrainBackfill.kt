/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import android.util.Log
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.music.PlaylistRepository
import io.github.aedev.flow.ui.screens.music.MusicTrack
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time warm start from existing listening history, run inside engine
 * initialization before the store is used. Two sources, replayed in order:
 *
 * 1. Room `watch_history` rows with isMusic = 1 (up to 3000, oldest-first so the
 *    ACT-R ring keeps the NEWEST plays). These rows historically carry
 *    position = 0 and no artist id, so progress falls back to the "history
 *    implies a real play" default and keys are lowercased names.
 * 2. The DataStore music history (last ~100 full tracks WITH artist browseIds and
 *    thumbnails). These do NOT replay as listens — the same plays are already in
 *    source 1 — they only enrich meta and fold name keys into id keys.
 */
@Singleton
class MusicBrainBackfill
    @Inject
    constructor(
        private val watchHistoryDao: WatchHistoryDao,
        private val musicPlaylistRepository: PlaylistRepository,
    ) {
        companion object {
            private const val TAG = "MusicBrainBackfill"
        }

        suspend fun run(
            brain: MusicBrain,
            nowMs: Long,
        ) {
            try {
                replayRoomHistory(brain, nowMs)
            } catch (e: Exception) {
                Log.w(TAG, "Room history backfill failed: ${e.message}")
            }
            try {
                enrichFromDataStoreHistory(brain)
            } catch (e: Exception) {
                Log.w(TAG, "DataStore history enrichment failed: ${e.message}")
            }
            Log.i(TAG, "Backfill done: plays=${brain.totalPlays} artists=${brain.artistAffinity.size}")
        }

        private suspend fun replayRoomHistory(
            brain: MusicBrain,
            nowMs: Long,
        ) {
            val rows =
                watchHistoryDao
                    .getMusicHistory()
                    .firstOrNull()
                    .orEmpty()
                    .take(MusicBrainParams.BACKFILL_MAX_ROWS)
                    .asReversed()

            for (row in rows) {
                val displayName = row.channelName.substringBefore(",").trim()
                val artistKey = musicArtistKey(row.channelId.takeIf { it.isNotBlank() }, displayName)
                if (artistKey.isEmpty()) continue

                val pct =
                    if (row.duration > 0 && row.position > 0) {
                        (row.position.toDouble() / row.duration).coerceIn(0.0, 1.0)
                    } else {
                        MusicBrainParams.BACKFILL_UNKNOWN_PROGRESS
                    }
                val crossed = MusicBrainLearn.newlyCrossed(0.0, pct)
                if (crossed.isEmpty()) continue

                val ts = if (row.timestamp in 1..nowMs) row.timestamp else nowMs
                MusicBrainLearn.applyMusicSignal(
                    brain,
                    MusicSignal(
                        trackId = row.videoId,
                        artistKey = artistKey,
                        artistDisplay = displayName,
                        percentPlayed = pct,
                        title = row.title,
                        thumbnail = row.thumbnailUrl,
                    ),
                    crossed,
                    ts,
                    // History rows have no intra-session ordering — the cooc graph warms up live.
                    coArtist = null,
                )
            }
        }

        private suspend fun enrichFromDataStoreHistory(brain: MusicBrain) {
            val tracks = musicPlaylistRepository.history.firstOrNull().orEmpty()
            for (track in tracks) {
                enrichFromMusicTrack(brain, track)
            }
        }

        private fun enrichFromMusicTrack(
            brain: MusicBrain,
            track: MusicTrack,
        ) {
            val primary = track.artists.firstOrNull()
            val key = musicArtistKey(primary?.id ?: track.channelId.takeIf { it.isNotBlank() }, primary?.name ?: track.artist)
            if (key.isEmpty() || track.videoId.isBlank()) return

            // Fold any name-keyed affinity from the Room replay into the id key.
            if (isIdKeyedArtist(key)) {
                val display = (primary?.name ?: track.artist).trim()
                val nameKey = display.lowercase()
                brain.artistAffinity.remove(nameKey)?.let { old ->
                    val target = brain.artistAffinity.getOrPut(key) { MusicAffinity() }
                    target.plays += old.plays
                    target.score = maxOf(target.score, old.score)
                    target.lastPlayed = maxOf(target.lastPlayed, old.lastPlayed)
                    target.liked = target.liked || old.liked
                }
                if (brain.seenArtists.remove(nameKey)) brain.seenArtists.add(key)
            }

            // Upgrade meta with the id key and a real thumbnail so On Repeat renders properly.
            if (track.videoId in brain.trackPlays && track.title.isNotBlank()) {
                brain.trackMeta[track.videoId] =
                    MusicTrackMeta(
                        title = track.title,
                        artist = (primary?.name ?: track.artist).ifBlank { key },
                        artistKey = key,
                        thumbnail = track.thumbnailUrl,
                    )
            }
        }
    }
