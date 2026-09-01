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
 * Whole-brain persistence: one typed-DataStore JSON document, mirroring the
 * NeuroStorage pattern. Conversions are top-level and Context-free so an
 * offline diagnostic can parse an exported brain without Android.
 */
internal class MusicBrainStorage(
    private val appContext: Context,
) {
    companion object {
        private const val TAG = "MusicBrainStorage"
        private const val FILE_NAME = "flow_music_brain_v1.json"

        private val json = Json { ignoreUnknownKeys = true }
        private val exportJson = Json { encodeDefaults = true }
    }

    @Serializable
    data class SerializableAffinity(
        val plays: Int = 0,
        val score: Double = 0.0,
        val lastPlayed: Long = 0L,
        val liked: Boolean = false,
        val display: String = "",
    )

    @Serializable
    data class SerializableTrackMeta(
        val title: String = "",
        val artist: String = "",
        val artistKey: String = "",
        val thumbnail: String = "",
    )

    @Serializable
    data class SerializableMusicBrain(
        val schemaVersion: Int = MusicBrainParams.SCHEMA_VERSION,
        val artistAffinity: Map<String, SerializableAffinity> = emptyMap(),
        val genreAffinity: Map<String, Double> = emptyMap(),
        val trackPlays: Map<String, List<Long>> = emptyMap(),
        val trackMeta: Map<String, SerializableTrackMeta> = emptyMap(),
        val recentRotation: Map<String, Double> = emptyMap(),
        val artistCooc: Map<String, Double> = emptyMap(),
        val artistRelated: Map<String, List<String>> = emptyMap(),
        val timeBuckets: Map<String, Map<String, Double>> = emptyMap(),
        val seenArtists: List<String> = emptyList(),
        val dislikedArtists: Map<String, Long> = emptyMap(),
        val blockedArtists: List<String> = emptyList(),
        val discoveryAppetite: Double = MusicBrainParams.DISCOVERY_NEUTRAL,
        val totalPlays: Int = 0,
        val lastRotationDecay: Long = 0L,
        val backfilled: Boolean = false,
    )

    private object BrainSerializer : Serializer<SerializableMusicBrain> {
        override val defaultValue: SerializableMusicBrain = SerializableMusicBrain()

        override suspend fun readFrom(input: InputStream): SerializableMusicBrain =
            try {
                val text = input.readBytes().decodeToString()
                if (text.isBlank()) defaultValue else json.decodeFromString(text)
            } catch (e: SerializationException) {
                throw CorruptionException("Corrupted music brain", e)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read music brain, starting cold: ${e.message}")
                defaultValue
            }

        override suspend fun writeTo(
            t: SerializableMusicBrain,
            output: OutputStream,
        ) {
            output.write(exportJson.encodeToString(SerializableMusicBrain.serializer(), t).encodeToByteArray())
        }
    }

    private val Context.musicBrainDataStore: DataStore<SerializableMusicBrain> by dataStore(
        fileName = FILE_NAME,
        serializer = BrainSerializer,
        corruptionHandler =
            androidx.datastore.core.handlers
                .ReplaceFileCorruptionHandler { SerializableMusicBrain() },
    )

    suspend fun save(brain: MusicBrain) =
        withContext(Dispatchers.IO) {
            try {
                appContext.musicBrainDataStore.updateData { brain.toSerializable() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist music brain", e)
            }
        }

    suspend fun load(): MusicBrain? =
        withContext(Dispatchers.IO) {
            try {
                val stored = appContext.musicBrainDataStore.data.first()
                if (stored.hasAnyState()) stored.toMusicBrain() else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load music brain", e)
                null
            }
        }

    suspend fun exportToStream(out: OutputStream) =
        withContext(Dispatchers.IO) {
            val stored = appContext.musicBrainDataStore.data.first()
            out.write(exportJson.encodeToString(SerializableMusicBrain.serializer(), stored).encodeToByteArray())
        }

    suspend fun importFromStream(input: InputStream): MusicBrain =
        withContext(Dispatchers.IO) {
            val parsed = json.decodeFromString<SerializableMusicBrain>(input.readBytes().decodeToString())
            val brain = parsed.toMusicBrain()
            save(brain)
            brain
        }
}

private fun MusicBrainStorage.SerializableMusicBrain.hasAnyState(): Boolean =
    totalPlays > 0 ||
        artistAffinity.isNotEmpty() ||
        trackPlays.isNotEmpty() ||
        seenArtists.isNotEmpty() ||
        blockedArtists.isNotEmpty() ||
        backfilled

internal fun MusicBrain.toSerializable(): MusicBrainStorage.SerializableMusicBrain =
    MusicBrainStorage.SerializableMusicBrain(
        schemaVersion = schemaVersion,
        artistAffinity =
            artistAffinity.mapValues { (_, a) ->
                MusicBrainStorage.SerializableAffinity(a.plays, a.score, a.lastPlayed, a.liked, a.display)
            },
        genreAffinity = genreAffinity.toMap(),
        trackPlays = trackPlays.mapValues { it.value.toList() },
        trackMeta =
            trackMeta.mapValues { (_, m) ->
                MusicBrainStorage.SerializableTrackMeta(m.title, m.artist, m.artistKey, m.thumbnail)
            },
        recentRotation = recentRotation.toMap(),
        artistCooc = artistCooc.toMap(),
        artistRelated = artistRelated.mapValues { it.value.toList() },
        timeBuckets = timeBuckets.entries.associate { (bucket, genres) -> bucket.wireName to genres.toMap() },
        seenArtists = seenArtists.toList(),
        dislikedArtists = dislikedArtists.toMap(),
        blockedArtists = blockedArtists.toList(),
        discoveryAppetite = discoveryAppetite,
        totalPlays = totalPlays,
        lastRotationDecay = lastRotationDecay,
        backfilled = backfilled,
    )

internal fun MusicBrainStorage.SerializableMusicBrain.toMusicBrain(): MusicBrain {
    val brain = MusicBrain()
    brain.schemaVersion = MusicBrainParams.SCHEMA_VERSION
    artistAffinity.forEach { (k, a) ->
        brain.artistAffinity[k] = MusicAffinity(a.plays, a.score, a.lastPlayed, a.liked, a.display)
    }
    brain.genreAffinity.putAll(genreAffinity)
    trackPlays.forEach { (k, v) -> brain.trackPlays[k] = v.toMutableList() }
    trackMeta.forEach { (k, m) ->
        brain.trackMeta[k] = MusicTrackMeta(m.title, m.artist, m.artistKey, m.thumbnail)
    }
    brain.recentRotation.putAll(recentRotation)
    brain.artistCooc.putAll(artistCooc)
    artistRelated.forEach { (k, v) -> brain.artistRelated[k] = v.toMutableList() }
    timeBuckets.forEach { (wire, genres) ->
        MusicTimeBucket.fromWire(wire)?.let { bucket -> brain.timeBuckets[bucket] = genres.toMutableMap() }
    }
    brain.seenArtists.addAll(seenArtists)
    brain.dislikedArtists.putAll(dislikedArtists)
    brain.blockedArtists.addAll(blockedArtists)
    brain.discoveryAppetite = discoveryAppetite
    brain.totalPlays = totalPlays
    brain.lastRotationDecay = lastRotationDecay
    brain.backfilled = backfilled
    return brain
}
