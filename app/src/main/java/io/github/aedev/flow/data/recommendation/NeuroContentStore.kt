/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Persistent per-video content index. Tags are only available when a video is
 * OPENED (full StreamInfo) — feed candidates arrive with bare titles. This store
 * keeps the tag-rich topic vector computed at watch time so that when the same
 * video reappears as a candidate it is scored on real content, not its title.
 * Representations accumulate from engagement — the SimClusters tweet-embedding
 * idea, miniaturized.
 */
internal class NeuroContentStore(
    private val appContext: Context,
) {
    companion object {
        private const val TAG = "FlowNeuroEngine"
        private const val MAX_ENTRIES = 1500
        private const val TOPICS_PER_ENTRY = 12
    }

    @Serializable
    data class StoredVector(
        val topics: Map<String, Double> = emptyMap(),
        val at: Long = 0L,
    )

    @Serializable
    data class ContentIndex(
        val vectors: Map<String, StoredVector> = emptyMap(),
    )

    private object ContentSerializer : Serializer<ContentIndex> {
        override val defaultValue: ContentIndex = ContentIndex()

        override suspend fun readFrom(input: InputStream): ContentIndex =
            try {
                val text = input.bufferedReader().readText()
                if (text.isBlank()) {
                    defaultValue
                } else {
                    Json { ignoreUnknownKeys = true }.decodeFromString<ContentIndex>(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read content index", e)
                defaultValue
            }

        override suspend fun writeTo(
            t: ContentIndex,
            output: OutputStream,
        ) {
            output.write(Json.encodeToString(t).toByteArray())
        }
    }

    private val Context.contentDataStore: DataStore<ContentIndex>
        by dataStore(
            fileName = "flow_neuro_content_v1.json",
            serializer = ContentSerializer,
        )

    @Volatile
    private var vectors: Map<String, StoredVector> = emptyMap()

    @Volatile
    private var dirty = false

    suspend fun load() {
        withContext(Dispatchers.IO) {
            try {
                vectors =
                    appContext.contentDataStore.data
                        .first()
                        .vectors
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load content index", e)
            }
        }
    }

    /** Fast, non-suspending read for the ranking hot path. */
    fun topicsFor(videoId: String): Map<String, Double>? = vectors[videoId]?.topics

    fun size(): Int = vectors.size

    /** Records the tag-rich vector of an opened video, LRU-pruned by write time. */
    fun put(
        videoId: String,
        topics: Map<String, Double>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (videoId.isBlank() || topics.isEmpty()) return
        val trimmed =
            topics.entries
                .sortedByDescending { it.value }
                .take(TOPICS_PER_ENTRY)
                .associate { it.key to it.value }
        val updated = vectors.toMutableMap()
        updated[videoId] = StoredVector(trimmed, now)
        vectors =
            if (updated.size > MAX_ENTRIES) {
                updated.entries
                    .sortedByDescending { it.value.at }
                    .take(MAX_ENTRIES)
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
                val snapshot = vectors
                appContext.contentDataStore.updateData { ContentIndex(snapshot) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist content index", e)
            }
        }
    }

    suspend fun clear() {
        vectors = emptyMap()
        dirty = false
        withContext(Dispatchers.IO) {
            try {
                appContext.contentDataStore.updateData { ContentIndex() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear content index", e)
            }
        }
    }
}
