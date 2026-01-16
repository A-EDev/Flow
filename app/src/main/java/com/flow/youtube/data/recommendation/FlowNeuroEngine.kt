/*
 * Copyright (C) 2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property 
 * of the Flow project. Any use of this code in other projects must 
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package com.flow.youtube.data.recommendation

import android.content.Context
import android.util.Log
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.*

/**
 * 🧠 Flow Neuro Engine (V3 Final - Aggressive - Thread Safe)
 * 
 * A Client-Side Hybrid Recommendation System.
 * Combines Vector Space Models (Learning) with Heuristic Rules (Reliability).
 */
object FlowNeuroEngine {

    private const val TAG = "FlowNeuroEngine"
    private const val BRAIN_FILENAME = "user_neuro_brain.json"
    
    // Mutex to protect brain state access
    private val brainMutex = Mutex()
    
    // =================================================
    // 1. DATA MODELS (IMMUTABLE)
    // =================================================

    data class ContentVector(
        val topics: Map<String, Double> = emptyMap(),
        val duration: Double = 0.5,
        val pacing: Double = 0.5,
        val complexity: Double = 0.5,
        val isLive: Double = 0.0
    )

    data class UserBrain(
        val shortTermVector: ContentVector = ContentVector(),
        val longTermVector: ContentVector = ContentVector(),
        val totalInteractions: Int = 0
    )

    // Protected by Mutex
    private var currentUserBrain: UserBrain = UserBrain()
    private var isInitialized = false

    // =================================================
    // 2. PUBLIC API
    // =================================================

    suspend fun initialize(context: Context) {
        brainMutex.withLock {
            if (isInitialized) return
            loadBrain(context)
            isInitialized = true
        }
    }

    suspend fun getBrainSnapshot(): UserBrain {
        return brainMutex.withLock { currentUserBrain }
    }

    suspend fun resetBrain(context: Context) {
        brainMutex.withLock {
            currentUserBrain = UserBrain()
            saveBrain(context)
        }
    }

    /**
     * MAIN FUNCTION: Rank videos based on User Brain + Random Jitter
     */
    suspend fun rank(
        candidates: List<Video>,
        userSubs: Set<String>
    ): List<Video> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Capture a clean snapshot of the brain to avoid locking during the heavy loop
        val brainSnapshot = brainMutex.withLock { currentUserBrain }

        // 1. Session Entropy (The "Freshness" Factor)
        val random = java.util.Random()

        val scoredCandidates = candidates.map { video ->
            val videoVector = extractFeatures(video)
            
            // A. AI Score (Cosine Similarity)
            // 60% Short Term (Current Mood), 40% Long Term (Personality)
            val shortTermScore = calculateCosineSimilarity(brainSnapshot.shortTermVector, videoVector)
            val longTermScore = calculateCosineSimilarity(brainSnapshot.longTermVector, videoVector)
            val aiScore = (shortTermScore * 0.6) + (longTermScore * 0.4)
            
            // B. Heuristic Boosts
            var boost = 0.0
            
            // Boost 1: Subscribed Channels (TRUST)
            if (userSubs.contains(video.channelId)) {
                boost += 0.10 
            }
            
            // Boost 2: Viral Velocity
            if (video.viewCount > 1_000_000) {
                boost += 0.05
            }

            // Boost 3: Live Content (Freshness)
            if (video.isLive) {
                boost += 0.08
            }

            // C. The "Cold Start" Jitter
            val jitterRange = if (brainSnapshot.totalInteractions < 10) 0.1 else 0.01
            val jitter = random.nextDouble() * jitterRange

            ScoredVideo(video, aiScore + boost + jitter, videoVector)
        }.toMutableList()

        // 2. Diversity Re-ranking
        return@withContext applyDiversityReranking(scoredCandidates)
    }

    /**
     * LEARNING FUNCTION: Aggressive Learning Mode
     */
    suspend fun onVideoInteraction(context: Context, video: Video, interactionType: InteractionType, percentWatched: Float = 0f) {
        val videoVector = extractFeatures(video)
        
        // UPDATED: Aggressive Learning Rates
        val learningRate = when (interactionType) {
            InteractionType.CLICK -> 0.10
            InteractionType.LIKED -> 0.30
            InteractionType.WATCHED -> 0.15 * percentWatched
            InteractionType.SKIPPED -> -0.15
            InteractionType.DISLIKED -> -0.40
        }

        brainMutex.withLock {
            // Create NEW derived vectors (Functional Style)
            val newShortTerm = adjustVector(currentUserBrain.shortTermVector, videoVector, learningRate * 2.0)
            val newLongTerm = adjustVector(currentUserBrain.longTermVector, videoVector, learningRate * 1.0)
            
            currentUserBrain = currentUserBrain.copy(
                shortTermVector = newShortTerm,
                longTermVector = newLongTerm,
                totalInteractions = currentUserBrain.totalInteractions + 1
            )
            
            saveBrain(context)
        }
    }

    enum class InteractionType { CLICK, LIKED, WATCHED, SKIPPED, DISLIKED }

    // =================================================
    // 3. INTERNAL MATH ENGINE
    // =================================================

    private data class ScoredVideo(val video: Video, var score: Double, val vector: ContentVector)

    private fun extractFeatures(video: Video): ContentVector {
        val topicsMap = mutableMapOf<String, Double>()
        
        // Improved Tokenizer for Arabic/French/Unicode
        val rawText = "${video.title} ${video.channelName}".lowercase()
        val tokens = rawText.split("\\s+".toRegex())
            .map { word -> word.trim { !it.isLetterOrDigit() } }
            .filter { word -> word.length > 2 && !STOP_WORDS.contains(word) }
        
        tokens.forEach { token ->
            topicsMap[token] = (topicsMap[token] ?: 0.0) + 0.2
        }
        
        // Clamp keys
        topicsMap.replaceAll { _, v -> v.coerceAtMost(1.0) }

        val durationSecs = if (video.duration > 0) video.duration else if (video.isLive) 3600 else 300
        val durScore = (ln(durationSecs.toDouble() + 1) / ln(3600.0)).coerceIn(0.0, 1.0)
        val paceScore = if (durationSecs < 180) 0.8 else 0.3
        val liveScore = if (video.isLive) 1.0 else 0.0
        
        return ContentVector(
            topics = topicsMap,
            duration = durScore,
            pacing = paceScore,
            isLive = liveScore
        )
    }

    private fun calculateCosineSimilarity(user: ContentVector, content: ContentVector): Double {
        var dotProduct = 0.0
        var magA = 0.0
        var magB = 0.0
        
        // Intersection of keys
        val commonKeys = user.topics.keys.intersect(content.topics.keys)
        
        if (commonKeys.isEmpty()) {
            val distDur = 1.0 - abs(user.duration - content.duration)
            return distDur * 0.3
        }

        commonKeys.forEach { key ->
            val u = user.topics[key]!!
            val v = content.topics[key]!!
            dotProduct += u * v
        }
        
        user.topics.values.forEach { magA += it * it }
        content.topics.values.forEach { magB += it * it }
        
        return if (magA > 0 && magB > 0) dotProduct / (sqrt(magA) * sqrt(magB)) else 0.0
    }

    private fun adjustVector(current: ContentVector, target: ContentVector, rate: Double): ContentVector {
        val newTopics = current.topics.toMutableMap()
        
        // 1. Move towards target
        target.topics.forEach { (key, targetVal) ->
            val currentVal = newTopics[key] ?: 0.0
            newTopics[key] = (currentVal + (targetVal - currentVal) * rate).coerceIn(0.0, 1.0)
        }
        
        // 2. Decay logic
        val decay = if (rate > 0) 0.98 else 1.0
        if (decay < 1.0) {
            val iterator = newTopics.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                entry.setValue(entry.value * decay)
                if (entry.value < 0.05) iterator.remove()
            }
        }

        return current.copy(
            topics = newTopics, // It's a copy, safe to be immutable now
            duration = current.duration + (target.duration - current.duration) * rate,
            pacing = current.pacing + (target.pacing - current.pacing) * rate,
            complexity = current.complexity + (target.complexity - current.complexity) * rate
        )
    }

    private fun applyDiversityReranking(candidates: MutableList<ScoredVideo>): List<Video> {
        val finalPlaylist = mutableListOf<Video>()
        val maxItems = candidates.size
        
        while (candidates.isNotEmpty() && finalPlaylist.size < maxItems) {
            // Sort by current dynamic score
            candidates.sortByDescending { it.score }
            val best = candidates.removeAt(0)
            finalPlaylist.add(best.video)
            
            // Penalize similar videos remaining in the pool
            candidates.forEach { candidate ->
                val similarity = calculateCosineSimilarity(best.vector, candidate.vector)
                if (similarity > 0.7) candidate.score *= 0.5
            }
        }
        return finalPlaylist
    }

    // =================================================
    // 4. STORAGE
    // =================================================

    private fun saveBrain(context: Context) {
        try {
            val json = JSONObject()
            json.put("shortTerm", vectorToJson(currentUserBrain.shortTermVector))
            json.put("longTerm", vectorToJson(currentUserBrain.longTermVector))
            json.put("interactions", currentUserBrain.totalInteractions)
            
            val file = File(context.filesDir, BRAIN_FILENAME)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save brain", e)
        }
    }

    private fun loadBrain(context: Context) {
        try {
            val file = File(context.filesDir, BRAIN_FILENAME)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            currentUserBrain = UserBrain(
                shortTermVector = jsonToVector(json.getJSONObject("shortTerm")),
                longTermVector = jsonToVector(json.getJSONObject("longTerm")),
                totalInteractions = json.optInt("interactions", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load brain", e)
        }
    }

    private fun vectorToJson(vector: ContentVector): JSONObject {
        val obj = JSONObject()
        obj.put("duration", vector.duration)
        obj.put("pacing", vector.pacing)
        val topicsObj = JSONObject()
        vector.topics.forEach { (k, v) -> topicsObj.put(k, v) }
        obj.put("topics", topicsObj)
        return obj
    }

    private fun jsonToVector(json: JSONObject): ContentVector {
        val duration = json.optDouble("duration", 0.5)
        val pacing = json.optDouble("pacing", 0.5)
        
        val topicsMap = mutableMapOf<String, Double>()
        val topicsObj = json.optJSONObject("topics")
        topicsObj?.keys()?.forEach { key ->
            topicsMap[key] = topicsObj.getDouble(key)
        }
        
        return ContentVector(
            topics = topicsMap,
            duration = duration,
            pacing = pacing
        )
    }


    // =================================================
    // 5. PERSONA ENGINE
    // =================================================

    enum class FlowPersona(
        val title: String,
        val description: String,
        val icon: String // Placeholder for icon name or emoji
    ) {
        DEEP_DIVER("The Deep Diver", "You prefer long-form, complex content. You're here to learn.", "🤿"),
        SKIMMER("The Skimmer", "Fast-paced and efficient. You want the highlights, now.", "⚡"),
        SPECIALIST("The Specialist", "Laser-focused on specific topics. You know what you like.", "🎯"),
        EXPLORER("The Explorer", "A balanced appetite for everything. You love discovering new things.", "🧭"),
        INITIATE("The Initiate", "Just getting started. Your profile is still forming.", "🌱")
    }

    fun getPersona(brain: UserBrain): FlowPersona {
        if (brain.totalInteractions < 10) return FlowPersona.INITIATE

        val v = brain.longTermVector
        val sortedTopics = v.topics.values.sortedDescending()
        val topScore = sortedTopics.firstOrNull() ?: 0.0
        val diversity = if (sortedTopics.size >= 3) {
            sortedTopics.take(3).average() / topScore
        } else 1.0

        return when {
            v.duration > 0.65 && v.complexity > 0.6 -> FlowPersona.DEEP_DIVER
            v.duration < 0.4 && v.pacing > 0.6 -> FlowPersona.SKIMMER
            topScore > 0.8 && diversity < 0.5 -> FlowPersona.SPECIALIST
            else -> FlowPersona.EXPLORER
        }
    }

    private val STOP_WORDS = setOf(
        "the", "and", "for", "that", "this", "with", "you", "video", "how", "what", "video", "official", "channel"
    )
}