/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Persistence for the listening ledger — same typed-DataStore JSON pattern as
 * [MusicBrainStorage], its own file so brain saves and stats saves stay cheap
 * and the brain's sync wire format is untouched.
 */
internal class MusicStatsStorage(
    private val appContext: Context,
) {
    companion object {
        private const val TAG = "MusicStatsStorage"
        private const val FILE_NAME = "flow_music_stats_v1.json"

        private val json = Json { ignoreUnknownKeys = true }
    }

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
    )

    @Serializable
    data class SerializableStats(
        val schemaVersion: Int = MusicStatsParams.SCHEMA_VERSION,
        val months: Map<String, SerializableMonth> = emptyMap(),
    )

    private object StatsSerializer : Serializer<SerializableStats> {
        override val defaultValue: SerializableStats = SerializableStats()

        override suspend fun readFrom(input: InputStream): SerializableStats =
            try {
                val text = input.readBytes().decodeToString()
                if (text.isBlank()) defaultValue else json.decodeFromString(text)
            } catch (e: SerializationException) {
                throw CorruptionException("Corrupted music stats", e)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read music stats, starting empty: ${e.message}")
                defaultValue
            }

        override suspend fun writeTo(
            t: SerializableStats,
            output: OutputStream,
        ) {
            output.write(json.encodeToString(SerializableStats.serializer(), t).encodeToByteArray())
        }
    }

    private val Context.musicStatsDataStore: DataStore<SerializableStats> by dataStore(
        fileName = FILE_NAME,
        serializer = StatsSerializer,
        corruptionHandler =
            androidx.datastore.core.handlers
                .ReplaceFileCorruptionHandler { SerializableStats() },
    )

    suspend fun save(ledger: MusicStatsLedger) =
        withContext(Dispatchers.IO) {
            try {
                appContext.musicStatsDataStore.updateData { ledger.toSerializable() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist music stats", e)
            }
        }

    suspend fun load(): MusicStatsLedger? =
        withContext(Dispatchers.IO) {
            try {
                val stored = appContext.musicStatsDataStore.data.first()
                if (stored.months.isEmpty()) null else stored.toLedger()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load music stats", e)
                null
            }
        }
}

internal fun MusicStatsLedger.toSerializable(): MusicStatsStorage.SerializableStats =
    MusicStatsStorage.SerializableStats(
        schemaVersion = schemaVersion,
        months =
            months.mapValues { (_, m) ->
                MusicStatsStorage.SerializableMonth(
                    plays = m.plays,
                    sessions = m.sessions,
                    listenedMs = m.listenedMs,
                    artistPlays = m.artistPlays.toMap(),
                    artistNames = m.artistNames.toMap(),
                    trackPlays = m.trackPlays.toMap(),
                    trackTitles = m.trackTitles.toMap(),
                    genrePlays = m.genrePlays.toMap(),
                    discoveredArtists = m.discoveredArtists.toList(),
                    dayPlays = m.dayPlays.toMap(),
                    hourPlays = m.hourPlays.toMap(),
                )
            },
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
            )
    }
    return ledger
}
