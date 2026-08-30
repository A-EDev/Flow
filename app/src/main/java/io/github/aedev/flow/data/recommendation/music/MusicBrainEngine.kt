/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.screens.music.MusicArtist
import io.github.aedev.flow.ui.screens.music.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The music taste engine facade: owns the resident [MusicBrain], serializes all
 * access through one mutex, and debounces persistence. Constructor does no I/O —
 * state loads lazily on first use, never on the app cold-start path.
 *
 * Unlike the video engine's legacy statics, this is plain Hilt constructor
 * injection: no getInstance, no companion forwarding.
 */
@Singleton
class MusicBrainEngine
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val backfill: MusicBrainBackfill,
    ) {
        companion object {
            private const val TAG = "MusicBrainEngine"
            private const val SAVE_DEBOUNCE_MS = 5000L
            private const val LOCAL_MEDIA_PREFIX = "local_"
        }

        private val storage = MusicBrainStorage(appContext)
        private val playerPreferences by lazy { PlayerPreferences(appContext) }

        private val mutex = Mutex()
        private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var pendingSaveJob: Job? = null

        private var brain = MusicBrain()
        private var isInitialized = false

        /** Previous counted artist + timestamp, for session co-occurrence. Ephemeral, never persisted. */
        private var lastCounted: Pair<String, Long>? = null

        suspend fun ensureInitialized() {
            if (isInitialized) return
            // CPU-bound init (backfill replay) runs on Default so it never occupies
            // the app's limited disk/network dispatcher threads.
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    if (isInitialized) return@withLock
                    brain = storage.load() ?: MusicBrain()
                    if (!brain.backfilled) {
                        backfill.run(brain, System.currentTimeMillis())
                        brain.backfilled = true
                        storage.save(brain)
                    }
                    isInitialized = true
                    Log.i(TAG, "Initialized: plays=${brain.totalPlays} artists=${brain.artistAffinity.size}")
                }
            }
        }

        /**
         * One completed playback session for [track], with [playedFraction] =
         * actualPlayedTime / duration (seek-immune, from PlaybackStatsListener).
         * Milestones are crossed once from zero — a relisten is simply a new session.
         */
        suspend fun onListenSession(
            track: MusicTrack,
            playedFraction: Double,
        ) {
            if (track.videoId.isBlank() || track.videoId.startsWith(LOCAL_MEDIA_PREFIX)) return
            val pct = playedFraction.coerceIn(0.0, 1.0)
            val crossed = MusicBrainLearn.newlyCrossed(0.0, pct)
            if (crossed.isEmpty()) {
                Log.d(TAG, "listen ${track.videoId} pct=$pct below first milestone")
                return
            }
            if (playerPreferences.isDeepFlowCurrentlyActive()) return
            ensureInitialized()

            val signal = track.toMusicSignal(pct)
            if (signal.artistKey.isEmpty()) {
                Log.w(TAG, "listen ${track.videoId} has no artist key")
                return
            }
            val now = System.currentTimeMillis()
            mutex.withLock {
                val coArtist =
                    lastCounted
                        ?.takeIf { now - it.second < MusicBrainParams.SESSION_GAP_MS && it.first != signal.artistKey }
                        ?.first
                val counted = MusicBrainLearn.applyMusicSignal(brain, signal, crossed, now, coArtist)
                if (counted) lastCounted = signal.artistKey to now
                Log.i(TAG, "listen ${track.videoId} pct=${"%.2f".format(pct)} counted=$counted artist=${signal.artistKey}")
            }
            scheduleDebouncedSave()
        }

        /**
         * Fire-and-forget wrapper for callers whose own scope may already be dead
         * (e.g. a Service's lifecycleScope during onDestroy). Runs on the engine's
         * process-scoped scope so teardown-time sessions are never dropped.
         */
        fun onListenSessionAsync(
            track: MusicTrack,
            playedFraction: Double,
        ) {
            saveScope.launch {
                try {
                    onListenSession(track, playedFraction)
                } catch (e: Exception) {
                    Log.w(TAG, "Listen session failed: ${e.message}")
                }
            }
        }

        /** An explicit like counts as a full play regardless of progress and floors the score at 0.8. */
        suspend fun onExplicitLike(track: MusicTrack) {
            if (track.videoId.isBlank() || track.videoId.startsWith(LOCAL_MEDIA_PREFIX)) return
            if (playerPreferences.isDeepFlowCurrentlyActive()) return
            ensureInitialized()

            val signal = track.toMusicSignal(0.0).copy(isExplicitLike = true)
            if (signal.artistKey.isEmpty()) return
            val now = System.currentTimeMillis()
            mutex.withLock {
                val counted = MusicBrainLearn.applyMusicSignal(brain, signal, emptyList(), now, coArtist = null)
                if (counted) lastCounted = signal.artistKey to now
            }
            scheduleDebouncedSave()
        }

        /**
         * Reorder candidates for a surface ("quick_picks", "radio", "similar",
         * "discover", …). A cold brain returns the input order unchanged; blocked
         * artists are removed even from single-item lists.
         */
        suspend fun rankTracks(
            tracks: List<MusicTrack>,
            surface: String,
        ): List<MusicTrack> {
            if (tracks.isEmpty()) return tracks
            ensureInitialized()
            return withContext(Dispatchers.Default) {
                mutex.withLock {
                    if (tracks.size == 1) {
                        val key = tracks[0].primaryArtistKey()
                        if (brain.isArtistBlocked(key)) emptyList() else tracks
                    } else {
                        val inputs = tracks.map { MusicRankInput(trackId = it.videoId, artistKey = it.primaryArtistKey()) }
                        MusicBrainRanker.rank(brain, inputs, surface, System.currentTimeMillis()).map { tracks[it] }
                    }
                }
            }
        }

        /** On Repeat, rendered entirely from local meta — zero network. */
        suspend fun heavyRotationTracks(limit: Int): List<MusicTrack> {
            ensureInitialized()
            return mutex.withLock {
                MusicBrainRanker
                    .heavyRotation(brain, System.currentTimeMillis(), limit.coerceIn(1, 100))
                    .mapNotNull { trackId ->
                        val meta = brain.trackMeta[trackId] ?: return@mapNotNull null
                        MusicTrack(
                            videoId = trackId,
                            title = meta.title,
                            artist = meta.artist,
                            thumbnailUrl = meta.thumbnail,
                            duration = 0,
                            channelId = if (isIdKeyedArtist(meta.artistKey)) meta.artistKey else "",
                            artists =
                                listOf(
                                    MusicArtist(
                                        name = meta.artist,
                                        id = meta.artistKey.takeIf { isIdKeyedArtist(it) },
                                    ),
                                ),
                        )
                    }
            }
        }

        suspend fun dislikeArtist(
            artistId: String?,
            artistName: String,
        ) {
            ensureInitialized()
            mutex.withLock { MusicBrainLearn.applyDislike(brain, musicArtistKey(artistId, artistName), System.currentTimeMillis()) }
            scheduleDebouncedSave()
        }

        suspend fun blockArtist(
            artistId: String?,
            artistName: String,
        ) {
            ensureInitialized()
            mutex.withLock { MusicBrainLearn.blockArtist(brain, musicArtistKey(artistId, artistName)) }
            scheduleDebouncedSave()
        }

        suspend fun unblockArtist(artistKey: String) {
            ensureInitialized()
            mutex.withLock { MusicBrainLearn.unblockArtist(brain, artistKey.trim()) }
            scheduleDebouncedSave()
        }

        suspend fun getBlockedArtists(): List<String> {
            ensureInitialized()
            return mutex.withLock { brain.blockedArtists.sorted() }
        }

        suspend fun exportBrainToStream(out: OutputStream) {
            ensureInitialized()
            mutex.withLock { storage.save(brain) }
            storage.exportToStream(out)
        }

        suspend fun importBrainFromStream(input: InputStream) {
            mutex.withLock {
                brain = storage.importFromStream(input)
                lastCounted = null
                isInitialized = true
            }
        }

        suspend fun resetBrain() {
            mutex.withLock {
                brain = MusicBrain()
                // Leave backfilled=false so the warm start can run again, matching desktop reset.
                lastCounted = null
                isInitialized = true
                storage.save(brain)
            }
        }

        private fun scheduleDebouncedSave() {
            pendingSaveJob?.cancel()
            pendingSaveJob =
                saveScope.launch {
                    delay(SAVE_DEBOUNCE_MS)
                    mutex.withLock { storage.save(brain) }
                }
        }
    }

/** Primary-artist key for a UI track: browseId when known, lowercased name otherwise. */
internal fun MusicTrack.primaryArtistKey(): String {
    val primary = artists.firstOrNull()
    return musicArtistKey(primary?.id ?: channelId.takeIf { it.isNotBlank() }, primary?.name ?: artist)
}

internal fun MusicTrack.toMusicSignal(pct: Double): MusicSignal {
    val primary = artists.firstOrNull()
    return MusicSignal(
        trackId = videoId,
        artistKey = primaryArtistKey(),
        artistDisplay = (primary?.name ?: artist).trim(),
        percentPlayed = pct,
        title = title,
        thumbnail = thumbnailUrl,
    )
}
