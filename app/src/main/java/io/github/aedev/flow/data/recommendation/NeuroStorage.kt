/*
 * Copyright (C) 2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * All persistence concerns: DataStore, serialization,
 * legacy migration, export/import.
 */
internal class NeuroStorage(private val appContext: Context) {

    companion object {
        private const val TAG = "FlowNeuroEngine"
        private const val BRAIN_FILENAME = "user_neuro_brain.json"
        private const val SCHEMA_VERSION = 10
    }

    // ── Serializable models ──

    @Serializable
    data class SerializableVector(
        val topics: Map<String, Double> = emptyMap(),
        val duration: Double = 0.5,
        val pacing: Double = 0.5,
        val complexity: Double = 0.5,
        val isLive: Double = 0.0
    )

    @Serializable
    data class SerializableBrain(
        val schemaVersion: Int = SCHEMA_VERSION,
        val timeVectors: Map<String, SerializableVector> = emptyMap(),
        val global: SerializableVector = SerializableVector(),
        val channelScores: Map<String, Double> = emptyMap(),
        val topicAffinities: Map<String, Double> = emptyMap(),
        val interactions: Int = 0,
        val consecutiveSkips: Int = 0,
        val blockedTopics: Set<String> = emptySet(),
        val blockedChannels: Set<String> = emptySet(),
        val preferredTopics: Set<String> = emptySet(),
        val hasCompletedOnboarding: Boolean = false,
        val lastPersona: String? = null,
        val personaStability: Int = 0,
        val idfWordFrequency: Map<String, Int> = emptyMap(),
        val idfTotalDocuments: Int = 0,
        val watchHistoryMap: Map<String, Float> = emptyMap(),
        val seenShortsHistory: Map<String, Long> = emptyMap(),
        val channelTopicProfiles: Map<String, Map<String, Double>> = emptyMap(),
        val shortsVector: SerializableVector = SerializableVector()
    )

    // ── DataStore setup ──

    private object BrainSerializer : Serializer<SerializableBrain> {
        override val defaultValue: SerializableBrain = SerializableBrain()

        override suspend fun readFrom(
            input: InputStream
        ): SerializableBrain {
            return try {
                val text = input.bufferedReader().readText()
                if (text.isBlank()) defaultValue
                else Json { ignoreUnknownKeys = true }
                    .decodeFromString<SerializableBrain>(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read brain", e)
                defaultValue
            }
        }

        override suspend fun writeTo(
            t: SerializableBrain,
            output: OutputStream
        ) {
            output.write(
                Json { encodeDefaults = true }
                    .encodeToString(t).toByteArray()
            )
        }
    }

    private val Context.brainDataStore: DataStore<SerializableBrain>
        by dataStore(
            fileName = "flow_neuro_brain_v10.json",
            serializer = BrainSerializer
        )

    // ── Conversion functions ──

    fun ContentVector.toSerializable() = SerializableVector(
        topics = topics, duration = duration, pacing = pacing,
        complexity = complexity, isLive = isLive
    )

    fun SerializableVector.toContentVector() = ContentVector(
        topics = topics, duration = duration, pacing = pacing,
        complexity = complexity, isLive = isLive
    )

    fun UserBrain.toSerializable() = SerializableBrain(
        schemaVersion = SCHEMA_VERSION,
        timeVectors = timeVectors.map { (k, v) ->
            k.name to v.toSerializable()
        }.toMap(),
        global = globalVector.toSerializable(),
        channelScores = channelScores,
        topicAffinities = topicAffinities,
        interactions = totalInteractions,
        consecutiveSkips = consecutiveSkips,
        blockedTopics = blockedTopics,
        blockedChannels = blockedChannels,
        preferredTopics = preferredTopics,
        hasCompletedOnboarding = hasCompletedOnboarding,
        lastPersona = lastPersona,
        personaStability = personaStability,
        idfWordFrequency = idfWordFrequency,
        idfTotalDocuments = idfTotalDocuments,
        watchHistoryMap = watchHistoryMap,
        seenShortsHistory = seenShortsHistory,
        channelTopicProfiles = channelTopicProfiles,
        shortsVector = shortsVector.toSerializable()
    )

    fun SerializableBrain.toUserBrain(): UserBrain {
        val vectors = TimeBucket.entries.associateWith { bucket ->
            val serialized = timeVectors[bucket.name]
            serialized?.toContentVector() ?: ContentVector()
        }
        return UserBrain(
            timeVectors = vectors,
            globalVector = global.toContentVector(),
            channelScores = channelScores,
            topicAffinities = topicAffinities,
            totalInteractions = interactions,
            consecutiveSkips = consecutiveSkips,
            blockedTopics = blockedTopics,
            blockedChannels = blockedChannels,
            preferredTopics = preferredTopics,
            hasCompletedOnboarding = hasCompletedOnboarding,
            lastPersona = lastPersona,
            personaStability = personaStability,
            idfWordFrequency = idfWordFrequency,
            idfTotalDocuments = idfTotalDocuments,
            watchHistoryMap = watchHistoryMap,
            seenShortsHistory = seenShortsHistory,
            channelTopicProfiles = channelTopicProfiles,
            shortsVector = shortsVector.toContentVector(),
            schemaVersion = schemaVersion
        )
    }

    // ── Persistence operations ──

    suspend fun save(brain: UserBrain) =
        withContext(Dispatchers.IO) {
            try {
                appContext.brainDataStore.updateData {
                    brain.toSerializable()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save brain", e)
            }
        }

    suspend fun load(): UserBrain? =
        withContext(Dispatchers.IO) {
            try {
                val data = appContext.brainDataStore.data.first()
                if (data.interactions > 0 ||
                    data.hasCompletedOnboarding ||
                    data.preferredTopics.isNotEmpty()
                ) {
                    val brain = data.toUserBrain()
                    Log.i(
                        TAG,
                        "Loaded brain v${data.schemaVersion}, " +
                            "${data.interactions} interactions"
                    )
                    return@withContext brain
                }
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load brain", e)
                return@withContext null
            }
        }

    // ── Export / Import ──

    suspend fun exportToStream(brain: UserBrain, output: OutputStream): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val brainCopy = brain.toSerializable()
                val jsonBytes = Json { encodeDefaults = true }
                    .encodeToString(brainCopy).toByteArray()
                output.write(jsonBytes)
                output.flush()
                Log.i(TAG, "Brain exported")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                false
            }
        }

    suspend fun importFromStream(input: InputStream): UserBrain? =
        withContext(Dispatchers.IO) {
            try {
                val text = input.bufferedReader().readText()
                val jsonParser = Json { ignoreUnknownKeys = true }

                val imported = jsonParser
                    .decodeFromString<SerializableBrain>(text)

                val hasTimeData = imported.timeVectors.any { (_, v) ->
                    v.topics.isNotEmpty()
                }

                val finalBrain = if (hasTimeData) {
                    imported.toUserBrain()
                } else {
                    migrateLegacyBackup(text, imported)
                }

                Log.i(
                    TAG,
                    "Brain imported (${finalBrain.totalInteractions} " +
                        "interactions, ${finalBrain.timeVectors.count {
                            it.value.topics.isNotEmpty()
                        }} active time buckets)"
                )
                finalBrain
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                null
            }
        }

    // ── Migration ──

    suspend fun migrateLegacy(): UserBrain? = withContext(Dispatchers.IO) {
        val legacyFile = File(appContext.filesDir, BRAIN_FILENAME)
        if (legacyFile.exists()) {
            try {
                Log.i(TAG, "Migrating legacy JSON brain...")
                val text = legacyFile.readText()
                val migrated = migrateLegacyBackup(
                    text, SerializableBrain()
                )
                Log.i(
                    TAG,
                    "Legacy brain migrated " +
                        "(${migrated.totalInteractions} interactions)"
                )
                return@withContext migrated
            } catch (e: Exception) {
                Log.e(TAG, "Legacy JSON migration failed", e)
            }
        }

        tryMigrateFromPreviousDataStore()
    }

    fun deleteLegacyFile() {
        val legacyFile = File(appContext.filesDir, BRAIN_FILENAME)
        if (legacyFile.exists()) {
            legacyFile.delete()
            Log.i(TAG, "Migrated legacy brain to DataStore")
        }
    }

    fun migrateLegacyBackup(
        rawJson: String,
        partialParse: SerializableBrain
    ): UserBrain {
        try {
            val jsonObj = JSONObject(rawJson)

            fun parseVector(key: String): ContentVector {
                val obj = jsonObj.optJSONObject(key)
                    ?: return ContentVector()
                return legacyJsonToVector(obj)
            }

            val morningVec = parseVector("morning")
            val afternoonVec = parseVector("afternoon")
            val eveningVec = parseVector("evening")
            val nightVec = parseVector("night")

            val hasLegacyData = listOf(
                morningVec, afternoonVec, eveningVec, nightVec
            ).any { it.topics.isNotEmpty() }

            val timeVectors: Map<TimeBucket, ContentVector> =
                if (hasLegacyData) {
                    mapOf(
                        TimeBucket.WEEKDAY_MORNING to morningVec,
                        TimeBucket.WEEKEND_MORNING to morningVec,
                        TimeBucket.WEEKDAY_AFTERNOON to afternoonVec,
                        TimeBucket.WEEKEND_AFTERNOON to afternoonVec,
                        TimeBucket.WEEKDAY_EVENING to eveningVec,
                        TimeBucket.WEEKEND_EVENING to eveningVec,
                        TimeBucket.WEEKDAY_NIGHT to nightVec,
                        TimeBucket.WEEKEND_NIGHT to nightVec
                    )
                } else {
                    val tvObj = jsonObj.optJSONObject("timeVectors")
                    if (tvObj != null) {
                        TimeBucket.entries.associateWith { bucket ->
                            val bucketObj = tvObj.optJSONObject(bucket.name)
                            if (bucketObj != null)
                                legacyJsonToVector(bucketObj)
                            else ContentVector()
                        }
                    } else {
                        TimeBucket.entries.associateWith { ContentVector() }
                    }
                }

            val globalVec = if (partialParse.global.topics.isNotEmpty()) {
                partialParse.global.toContentVector()
            } else {
                parseVector("global").let {
                    if (it.topics.isEmpty()) parseVector("longTerm") else it
                }
            }

            val channelScores = mutableMapOf<String, Double>()
            val scoresObj = jsonObj.optJSONObject("channelScores")
            scoresObj?.keys()?.forEach { key ->
                channelScores[key] = scoresObj.getDouble(key)
            }

            val affinities = mutableMapOf<String, Double>()
            val affObj = jsonObj.optJSONObject("topicAffinities")
            affObj?.keys()?.forEach { key ->
                affinities[key] = affObj.getDouble(key)
            }

            fun loadStringSet(key: String): Set<String> {
                val set = mutableSetOf<String>()
                val arr = jsonObj.optJSONArray(key)
                if (arr != null) {
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                }
                return set
            }

            val legacyIdfFreq = mutableMapOf<String, Int>()
            val idfObj = jsonObj.optJSONObject("idfWordFrequency")
            idfObj?.keys()?.forEach { key ->
                legacyIdfFreq[key] = idfObj.getInt(key)
            }
            val legacyIdfTotal = jsonObj.optInt("idfTotalDocuments", 0)

            return UserBrain(
                timeVectors = timeVectors,
                globalVector = globalVec,
                channelScores = if (channelScores.isNotEmpty())
                    channelScores
                else partialParse.channelScores,
                topicAffinities = if (affinities.isNotEmpty())
                    affinities
                else partialParse.topicAffinities,
                totalInteractions = if (partialParse.interactions > 0)
                    partialParse.interactions
                else jsonObj.optInt("interactions", 0),
                consecutiveSkips = partialParse.consecutiveSkips,
                blockedTopics = partialParse.blockedTopics.ifEmpty {
                    loadStringSet("blockedTopics")
                },
                blockedChannels = partialParse.blockedChannels.ifEmpty {
                    loadStringSet("blockedChannels")
                },
                preferredTopics = partialParse.preferredTopics.ifEmpty {
                    loadStringSet("preferredTopics")
                },
                hasCompletedOnboarding =
                partialParse.hasCompletedOnboarding ||
                    jsonObj.optBoolean("hasCompletedOnboarding", false),
                lastPersona = partialParse.lastPersona,
                personaStability = partialParse.personaStability,
                idfWordFrequency = if (legacyIdfFreq.isNotEmpty())
                    legacyIdfFreq
                else partialParse.idfWordFrequency,
                idfTotalDocuments = if (legacyIdfTotal > 0)
                    legacyIdfTotal
                else partialParse.idfTotalDocuments,
                watchHistoryMap = partialParse.watchHistoryMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Legacy backup migration failed", e)
            return partialParse.toUserBrain()
        }
    }

    suspend fun tryMigrateFromPreviousDataStore(): UserBrain? {
        val versions = listOf("v9", "v8", "v7")
        for (version in versions) {
            try {
                val file = File(
                    appContext.filesDir,
                    "datastore/flow_neuro_brain_$version.json"
                )
                if (!file.exists()) continue

                Log.i(TAG, "Found $version DataStore, migrating to V9...")
                val text = file.readText()
                if (text.isBlank()) continue

                val data = Json { ignoreUnknownKeys = true }
                    .decodeFromString<SerializableBrain>(text)

                if (data.interactions > 0 ||
                    data.hasCompletedOnboarding ||
                    data.preferredTopics.isNotEmpty()
                ) {
                    val brain = data.toUserBrain()
                    Log.i(
                        TAG,
                        "Migrated $version brain " +
                            "(${data.interactions} interactions)"
                    )
                    return brain
                }
            } catch (e: Exception) {
                Log.e(TAG, "$version migration failed", e)
            }
        }
        return null
    }

    fun legacyJsonToVector(jsonObj: JSONObject): ContentVector {
        val topicsMap = mutableMapOf<String, Double>()
        val topicsObj = jsonObj.optJSONObject("topics")
        topicsObj?.keys()?.forEach { key ->
            topicsMap[key] = topicsObj.getDouble(key)
        }
        return ContentVector(
            topics = topicsMap,
            duration = jsonObj.optDouble("duration", 0.5),
            pacing = jsonObj.optDouble("pacing", 0.5),
            complexity = jsonObj.optDouble("complexity", 0.5),
            isLive = jsonObj.optDouble("isLive", 0.0)
        )
    }
}
