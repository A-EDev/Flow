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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Home knows about each neighbour channel ([NeighbourChannels]): its latest uploads, when they
 * were fetched, and how often it was shown without a watch. Device-local, never synced or backed up.
 */
@Singleton
class NeuroNeighbourStore
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) {
        private companion object {
            const val TAG = "NeuroNeighbourStore"
            const val MAX_CHANNELS = 100
        }

        @Serializable
        data class StoredUpload(
            val id: String,
            val title: String,
            val thumbnailUrl: String = "",
            val duration: Int = 0,
            val publishedAt: Long = 0L,
            val uploadDate: String = "",
            val viewCount: Long = 0L,
        )

        @Serializable
        data class Entry(
            val name: String = "",
            val fetchedAt: Long = 0L,
            val uploads: List<StoredUpload> = emptyList(),
            val impressions: Int = 0,
            val dropped: Boolean = false,
        )

        @Serializable
        data class Index(
            val channels: Map<String, Entry> = emptyMap(),
        )

        private object IndexSerializer : Serializer<Index> {
            override val defaultValue: Index = Index()

            override suspend fun readFrom(input: InputStream): Index =
                try {
                    val text = input.bufferedReader().readText()
                    if (text.isBlank()) defaultValue else Json { ignoreUnknownKeys = true }.decodeFromString<Index>(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read neighbour index", e)
                    defaultValue
                }

            override suspend fun writeTo(
                t: Index,
                output: OutputStream,
            ) {
                output.write(Json.encodeToString(t).toByteArray())
            }
        }

        private val Context.neighbourDataStore: DataStore<Index>
            by dataStore(fileName = "flow_neuro_neighbours_v1.json", serializer = IndexSerializer)

        suspend fun entries(): Map<String, Entry> =
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.neighbourDataStore.data
                        .first()
                        .channels
                }.getOrDefault(emptyMap())
            }

        suspend fun update(transform: (Map<String, Entry>) -> Map<String, Entry>) =
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.neighbourDataStore.updateData { index ->
                        val updated = transform(index.channels)
                        Index(
                            if (updated.size <= MAX_CHANNELS) {
                                updated
                            } else {
                                updated.entries
                                    .sortedByDescending { it.value.fetchedAt }
                                    .take(MAX_CHANNELS)
                                    .associate { it.key to it.value }
                            },
                        )
                    }
                }.onFailure { Log.e(TAG, "Failed to update neighbour index", it) }
            }

        suspend fun clear() = update { emptyMap() }
    }
