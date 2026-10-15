/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.pow

/**
 * Channels watched together (#907), from the related lists YouTube shows beside videos the viewer
 * really watched or liked. One list is noise: a pair of channels counts only once two different
 * videos list it, or the viewer has watched both. Device-local, never synced or backed up.
 */
internal class NeuroCoWatchStore(
    private val appContext: Context,
) {
    companion object {
        private const val TAG = "FlowNeuroEngine"
        const val MAX_SOURCES = 200
        const val CHANNELS_PER_SOURCE = 10
        const val HALF_LIFE_MS = 45L * 24L * 60L * 60L * 1000L

        /** Fresh sources that make a pair as strong as it gets. */
        private const val FULL_STRENGTH_SOURCES = 2.0

        fun pairKey(
            a: String,
            b: String,
        ) = if (a < b) "$a|$b" else "$b|$a"

        /**
         * Confirmed channel pairs ("a|b") and their strength from 0 to 1: the decayed number of source
         * videos listing them, over [FULL_STRENGTH_SOURCES]. [isWatched] confirms a pair seen once.
         */
        fun edges(
            sources: Collection<Source>,
            isWatched: (channelId: String) -> Boolean,
            now: Long,
        ): Map<String, Double> {
            val weight = HashMap<String, Double>()
            val seen = HashMap<String, Int>()
            sources.forEach { source ->
                val decay = 0.5.pow((now - source.at).coerceAtLeast(0L).toDouble() / HALF_LIFE_MS)
                source.related.forEach { channel ->
                    val key = pairKey(source.channelId, channel)
                    weight.merge(key, decay, Double::plus)
                    seen.merge(key, 1, Int::plus)
                }
            }
            return weight
                .filter { (key, _) ->
                    (seen[key] ?: 0) >= 2 || key.split('|').all(isWatched)
                }.mapValues { (_, value) -> (value / FULL_STRENGTH_SOURCES).coerceAtMost(1.0) }
        }

        /** For each channel next to one the viewer watches, the strongest such pair. */
        fun neighbours(
            edges: Map<String, Double>,
            isWatched: (channelId: String) -> Boolean,
        ): Map<String, Double> {
            val strongest = HashMap<String, Double>()
            edges.forEach { (key, strength) ->
                val (a, b) = key.split('|')
                if (isWatched(a)) strongest.merge(b, strength, ::maxOf)
                if (isWatched(b)) strongest.merge(a, strength, ::maxOf)
            }
            return strongest
        }
    }

    @Serializable
    data class Source(
        val channelId: String = "",
        val related: List<String> = emptyList(),
        val at: Long = 0L,
        /** The related channels' names, so a neighbour channel can be fetched and named (#907). */
        val names: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class CoWatchIndex(
        val sources: Map<String, Source> = emptyMap(),
    )

    private object CoWatchSerializer : Serializer<CoWatchIndex> {
        override val defaultValue: CoWatchIndex = CoWatchIndex()

        override suspend fun readFrom(input: InputStream): CoWatchIndex =
            try {
                val text = input.bufferedReader().readText()
                if (text.isBlank()) defaultValue else Json { ignoreUnknownKeys = true }.decodeFromString<CoWatchIndex>(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read co-watch index", e)
                defaultValue
            }

        override suspend fun writeTo(
            t: CoWatchIndex,
            output: OutputStream,
        ) {
            output.write(Json.encodeToString(t).toByteArray())
        }
    }

    private val Context.coWatchDataStore: DataStore<CoWatchIndex>
        by dataStore(
            fileName = "flow_neuro_cowatch_v1.json",
            serializer = CoWatchSerializer,
        )

    @Volatile
    private var sources: Map<String, Source> = emptyMap()

    @Volatile
    private var dirty = false

    suspend fun load() {
        withContext(Dispatchers.IO) {
            try {
                sources =
                    appContext.coWatchDataStore.data
                        .first()
                        .sources
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load co-watch index", e)
            }
        }
    }

    fun sources(): Collection<Source> = sources.values

    fun index(): CoWatchIndex = CoWatchIndex(sources)

    fun channelNames(): Map<String, String> = sources.values.fold(HashMap()) { names, source -> names.apply { putAll(source.names) } }

    /** Replaces the in-memory index without persisting it, for replaying a recorded Home load. */
    fun restore(index: CoWatchIndex) {
        sources = index.sources
    }

    /** Records the channels of [relatedVideos], the list beside [videoId], a video of [channelId] the viewer took to or follows. */
    fun record(
        videoId: String,
        channelId: String,
        relatedVideos: List<Video>,
        now: Long = System.currentTimeMillis(),
    ) {
        val related =
            relatedVideos
                .map { it.channelId }
                .filter { it.isNotBlank() && it != channelId }
                .distinct()
                .take(CHANNELS_PER_SOURCE)
        if (videoId.isBlank() || channelId.isBlank() || related.isEmpty()) return
        val names =
            relatedVideos
                .filter { it.channelId in related && it.channelName.isNotBlank() }
                .associate { it.channelId to it.channelName }
        val updated = sources + (videoId to Source(channelId, related, now, names))
        sources =
            if (updated.size > MAX_SOURCES) {
                updated.entries
                    .sortedByDescending { it.value.at }
                    .take(MAX_SOURCES)
                    .associate { it.key to it.value }
            } else {
                updated
            }
        dirty = true
    }

    /** Persists the in-memory index if it changed; callers debounce. */
    suspend fun persistIfDirty() {
        if (!dirty) return
        dirty = false
        withContext(Dispatchers.IO) {
            try {
                val snapshot = sources
                appContext.coWatchDataStore.updateData { CoWatchIndex(snapshot) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist co-watch index", e)
            }
        }
    }

    suspend fun clear() {
        sources = emptyMap()
        dirty = false
        withContext(Dispatchers.IO) {
            try {
                appContext.coWatchDataStore.updateData { CoWatchIndex() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear co-watch index", e)
            }
        }
    }
}
