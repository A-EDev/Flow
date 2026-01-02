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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.*

/**
 * 🧠 Flow Neuro Engine (V3 Final - Aggressive)
 * 
 * A Client-Side Hybrid Recommendation System.
 * Combines Vector Space Models (Learning) with Heuristic Rules (Reliability).
 */
object FlowNeuroEngine {

    private const val TAG = "FlowNeuroEngine"
    private const val BRAIN_FILENAME = "user_neuro_brain.json"
    
    // =================================================
    // 1. DATA MODELS
    // =================================================

    data class ContentVector(
        val topics: MutableMap<String, Double> = mutableMapOf(),
        var duration: Double = 0.5,
        var pacing: Double = 0.5,
        var complexity: Double = 0.5,
        var isLive: Double = 0.0
    )

    data class UserBrain(
        var shortTermVector: ContentVector = ContentVector(),
        var longTermVector: ContentVector = ContentVector(),
        var totalInteractions: Int = 0
    )

    private var currentUserBrain: UserBrain = UserBrain()
    private var isInitialized = false

    // =================================================
    // 2. PUBLIC API
    // =================================================

    suspend fun initialize(context: Context) {
        if (isInitialized) return
        loadBrain(context)
        isInitialized = true
    }

    /**
     * MAIN FUNCTION: Rank videos based on User Brain + Random Jitter
     */
    suspend fun rank(
        candidates: List<Video>,
        userSubs: Set<String>
    ): List<Video> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // 1. Session Entropy (The "Freshness" Factor)
        // We use a random generator to add slight chaos to the scores.
        // This ensures that even if scores are identical, the order changes every time.
        val random = java.util.Random()

        val scoredCandidates = candidates.map { video ->
            val videoVector = extractFeatures(video)
            
            // A. AI Score (Cosine Similarity)
            // 60% Short Term (Current Mood), 40% Long Term (Personality)
            val shortTermScore = calculateCosineSimilarity(currentUserBrain.shortTermVector, videoVector)
            val longTermScore = calculateCosineSimilarity(currentUserBrain.longTermVector, videoVector)
            val aiScore = (shortTermScore * 0.6) + (longTermScore * 0.4)
            
            // B. Heuristic Boosts
            var boost = 0.0
            
            // Boost 1: Subscribed Channels (TRUST)
            // WAS: 0.25 (Too strong, overrides AI)
            // NOW: 0.10 (Gentle nudge, but AI wins if topic is irrelevant)
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
            // If brain is new (< 10 interactions), add random noise (0.0 - 0.1)
            // If brain is mature, add micro noise (0.0 - 0.01) to keep it feeling fresh
            val jitterRange = if (currentUserBrain.totalInteractions < 10) 0.1 else 0.01
            val jitter = random.nextDouble() * jitterRange

            ScoredVideo(video, aiScore + boost + jitter, videoVector)
        }.toMutableList()

        // 2. Diversity Re-ranking
        return@withContext applyDiversityReranking(scoredCandidates)
    }

    /**
     * LEARNING FUNCTION: Aggressive Learning Mode
     */
    fun onVideoInteraction(context: Context, video: Video, interactionType: InteractionType, percentWatched: Float = 0f) {
        val videoVector = extractFeatures(video)
        
        // UPDATED: Aggressive Learning Rates
        // We increased these values so the algorithm adapts INSTANTLY
        val learningRate = when (interactionType) {
            InteractionType.CLICK -> 0.10      // Was 0.05
            InteractionType.LIKED -> 0.30      // Was 0.15 (Big jump!)
            InteractionType.WATCHED -> 0.15 * percentWatched // Was 0.05
            InteractionType.SKIPPED -> -0.15   // Was -0.05 (Learn what they HATE fast)
            InteractionType.DISLIKED -> -0.40  // Was -0.20
        }

        // Apply changes
        currentUserBrain.shortTermVector = adjustVector(currentUserBrain.shortTermVector, videoVector, learningRate * 2.0)
        currentUserBrain.longTermVector = adjustVector(currentUserBrain.longTermVector, videoVector, learningRate * 1.0)
        
        currentUserBrain.totalInteractions++
        saveBrain(context)
    }

    enum class InteractionType { CLICK, LIKED, WATCHED, SKIPPED, DISLIKED }

    // =================================================
    // 3. INTERNAL MATH ENGINE
    // =================================================

    private data class ScoredVideo(val video: Video, var score: Double, val vector: ContentVector)

    private fun extractFeatures(video: Video): ContentVector {
        val vector = ContentVector()
        
        // Improved Tokenizer for Arabic/French/Unicode
        val rawText = "${video.title} ${video.channelName}".lowercase()
        val tokens = rawText.split("\\s+".toRegex())
            .map { word -> word.trim { !it.isLetterOrDigit() } }
            .filter { word -> word.length > 2 && !STOP_WORDS.contains(word) }
        
        tokens.forEach { token ->
            // Higher initial weight (0.2) so keywords stick immediately
            vector.topics[token] = (vector.topics[token] ?: 0.0) + 0.2
        }
        
        vector.topics.replaceAll { _, v -> v.coerceAtMost(1.0) }

        val durationSecs = if (video.duration > 0) video.duration else if (video.isLive) 3600 else 300
        vector.duration = (ln(durationSecs.toDouble() + 1) / ln(3600.0)).coerceIn(0.0, 1.0)
        vector.pacing = if (durationSecs < 180) 0.8 else 0.3
        vector.isLive = if (video.isLive) 1.0 else 0.0
        
        return vector
    }

    private fun calculateCosineSimilarity(user: ContentVector, content: ContentVector): Double {
        var dotProduct = 0.0
        var magA = 0.0
        var magB = 0.0
        
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
        target.topics.forEach { (key, targetVal) ->
            val currentVal = newTopics[key] ?: 0.0
            newTopics[key] = (currentVal + (targetVal - currentVal) * rate).coerceIn(0.0, 1.0)
        }
        
        // Decay to forget old topics
        val decay = if (rate > 0) 0.98 else 1.0
        if (decay < 1.0) {
            val iterator = newTopics.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                entry.setValue(entry.value * decay)
                if (entry.value < 0.05) iterator.remove()
            }
        }

        return ContentVector(
            topics = newTopics,
            duration = current.duration + (target.duration - current.duration) * rate,
            pacing = current.pacing + (target.pacing - current.pacing) * rate,
            complexity = current.complexity + (target.complexity - current.complexity) * rate
        )
    }

    private fun applyDiversityReranking(candidates: MutableList<ScoredVideo>): List<Video> {
        val finalPlaylist = mutableListOf<Video>()
        val maxItems = candidates.size
        
        while (candidates.isNotEmpty() && finalPlaylist.size < maxItems) {
            candidates.sortByDescending { it.score }
            val best = candidates.removeAt(0)
            finalPlaylist.add(best.video)
            
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
            // Use Internal Storage for Production
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
        val vector = ContentVector()
        vector.duration = json.optDouble("duration", 0.5)
        vector.pacing = json.optDouble("pacing", 0.5)
        val topicsObj = json.optJSONObject("topics")
        topicsObj?.keys()?.forEach { key ->
            vector.topics[key] = topicsObj.getDouble(key)
        }
        return vector
    }

    private val STOP_WORDS = setOf(
        "the", "and", "for", "that", "this", "with", "you", "video", "how", "what", "video", "official", "channel"
    )
}