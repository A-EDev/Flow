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
 * 🧠 Flow Neuro Engine (V4 - Balanced & Thread Safe)
 * 
 * A Client-Side Hybrid Recommendation System.
 * Combines Vector Space Models (Learning) with Heuristic Rules (Reliability).
 * 
 * V4 Changes:
 * - Fixed "Echo Chamber" issue with diminishing returns (saturation penalty)
 * - Added session-based fatigue penalty for topic variety
 * - Normalized feature vectors for proper cosine similarity
 * - Moved file I/O to IO dispatcher for UI smoothness
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
        val morningVector: ContentVector = ContentVector(),   // 06:00 - 12:00
        val afternoonVector: ContentVector = ContentVector(), // 12:00 - 18:00
        val eveningVector: ContentVector = ContentVector(),   // 18:00 - 00:00
        val nightVector: ContentVector = ContentVector(),     // 00:00 - 06:00
        val globalVector: ContentVector = ContentVector(),    // The "Core Personality"
        val channelScores: Map<String, Double> = emptyMap(),  // Track specific channel affinity
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
     * 🕵️‍♂️ NEURO-SEARCH: Generates search queries based on user interests.
     * The App should use these to fetch "For You" content from the network.
     * 
     * V4: Enhanced with Bridge, Persona Suffix, and Anti-Gravity strategies
     * for infinite content discovery.
     */
    suspend fun generateDiscoveryQueries(): List<String> = brainMutex.withLock {
        val interests = currentUserBrain.globalVector.topics
        val queries = mutableListOf<String>()
        
        // ==============================================================
        // 1. BRIDGE METHOD: Combine top topics for niche discovery
        // ==============================================================
        val sortedInterests = interests.entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
        
        // Strategy A: Direct Interest (Top 2)
        queries.addAll(sortedInterests.take(2))
        
        // Strategy B: "Bridge" Queries - Combine interests for niches
        if (sortedInterests.size >= 3) {
            // Combine #0 + #1 (e.g., "Coding" + "Music" -> "Coding Music")
            queries.add("${sortedInterests[0]} ${sortedInterests[1]}")
            // Combine #0 + #2 (e.g., "Coding" + "Tutorial")
            queries.add("${sortedInterests[0]} ${sortedInterests[2]}")
            // Combine #1 + #2 for more variety
            queries.add("${sortedInterests[1]} ${sortedInterests[2]}")
        } else if (sortedInterests.size >= 2) {
            queries.add("${sortedInterests[0]} ${sortedInterests[1]}")
        }

        // ==============================================================
        // 2. TIME CONTEXT: The "Obsession" (what you like RIGHT NOW)
        // ==============================================================
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val currentBucket = when (hour) {
             in 6..11 -> currentUserBrain.morningVector
             in 12..17 -> currentUserBrain.afternoonVector
             in 18..23 -> currentUserBrain.eveningVector
             else -> currentUserBrain.nightVector
        }
        val obsession = currentBucket.topics.entries
            .maxByOrNull { it.value }?.key
        obsession?.let { queries.add(it) }

        // ==============================================================
        // 3. PERSONA SUFFIX: Format-based queries tied to user behavior
        // ==============================================================
        val persona = getPersona(currentUserBrain)
        val suffix = when(persona) {
            FlowPersona.DEEP_DIVER -> "documentary"
            FlowPersona.SCHOLAR -> "analysis explained"
            FlowPersona.AUDIOPHILE -> "playlist mix"
            FlowPersona.LIVEWIRE -> "live stream"
            FlowPersona.BINGER -> "full movie"
            FlowPersona.SKIMMER -> "shorts compilation"
            else -> null
        }
        if (suffix != null && sortedInterests.isNotEmpty()) {
            queries.add("${sortedInterests[0]} $suffix")
        }

        // ==============================================================
        // 4. ANTI-GRAVITY: Exploration of untouched categories
        // ==============================================================
        getExplorationQuery(currentUserBrain)?.let { queries.add(it) }

        // ==============================================================
        // 5. FALLBACK for cold start
        // ==============================================================
        if (queries.isEmpty()) {
            return@withLock listOf("New Trending", "Music", "Gaming", "Technology", "Science") 
        }

        return@withLock queries.distinct().shuffled()
    }

    /**
     * MAIN FUNCTION: Rank videos based on User Brain + Random Jitter
     * 
     * @param candidates List of videos to rank
     * @param userSubs Set of channel IDs the user is subscribed to
     * @param lastWatchedTopics List of primary topics from recently watched videos (for session fatigue)
     */
    suspend fun rank(
        candidates: List<Video>,
        userSubs: Set<String>,
        lastWatchedTopics: List<String> = emptyList()
    ): List<Video> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        val brain = brainMutex.withLock { currentUserBrain }
        val random = java.util.Random()
        
        // 1. Identify the Context (What time is it?)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeContextVector = when (hour) {
            in 6..11 -> brain.morningVector
            in 12..17 -> brain.afternoonVector
            in 18..23 -> brain.eveningVector
            else -> brain.nightVector
        }

        val scoredCandidates = candidates.map { video ->
            val videoVector = extractFeatures(video)

            // A. The "Global Personality" Score - 40% Weight
            val personalityScore = calculateCosineSimilarity(brain.globalVector, videoVector)

            // B. The "Time Context" Score - 40% Weight
            val contextScore = calculateCosineSimilarity(timeContextVector, videoVector)

            // C. The "Discovery" Score (Novelty) - 20% Weight
            val noveltyScore = 1.0 - personalityScore

            // Weighted Average
            var totalScore = (personalityScore * 0.4) + (contextScore * 0.4) + (noveltyScore * 0.2)

            // --- BOOSTS & PENALTIES ---

            // Boost: Subscription
            if (userSubs.contains(video.channelId)) totalScore += 0.15

            // Boost: Serendipity
            if (noveltyScore > 0.6 && contextScore > 0.5) totalScore += 0.10 

            // 🔥 IMPLICIT FEEDBACK CHECK
            val channelClickRate = brain.channelScores[video.channelId] ?: 0.5
            val boredomPenalty = if (brain.channelScores.containsKey(video.channelId) && channelClickRate < 0.2) 0.5 else 1.0
            
            totalScore *= boredomPenalty

            // 🧠 SESSION FATIGUE PENALTY (New in V4)
            // If the user JUST watched this topic, temporarily lower its score
            // This allows the "Personality" (long term) to surface other interests
            val videoPrimaryTopic = videoVector.topics.maxByOrNull { it.value }?.key ?: ""
            val fatigueMultiplier = when {
                videoPrimaryTopic.isEmpty() -> 1.0
                lastWatchedTopics.count { it == videoPrimaryTopic } >= 3 -> 0.4 // Heavy repetition
                lastWatchedTopics.contains(videoPrimaryTopic) -> 0.7 // Some repetition
                else -> 1.0
            }
            totalScore *= fatigueMultiplier

            // Jitter for Cold Start
            val jitter = if (brain.totalInteractions < 50) random.nextDouble() * 0.2 else random.nextDouble() * 0.02

            ScoredVideo(video, totalScore + jitter, videoVector)
        }.toMutableList()

        // 2. Diversity Re-ranking
        return@withContext applySmartDiversity(scoredCandidates)
    }

    private fun getCurrentTimeBucket(brain: UserBrain): ContentVector {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> brain.morningVector
            in 12..17 -> brain.afternoonVector
            in 18..23 -> brain.eveningVector
            else -> brain.nightVector
        }
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
            // 1. Update Global Vector
            val newGlobal = adjustVector(currentUserBrain.globalVector, videoVector, learningRate)
            
            // 2. Update Time-Specific Vector
            val currentBucket = getCurrentTimeBucket(currentUserBrain)
            val newBucketVector = adjustVector(currentBucket, videoVector, learningRate * 1.5)

            // 3. Update Channel Score (Implicit Feedback)
            val currentChScore = currentUserBrain.channelScores[video.channelId] ?: 0.5
            val outcome = if (learningRate > 0) 1.0 else 0.0
            val newChScore = (currentChScore * 0.95) + (outcome * 0.05) // Slow moving average
            val newChannelScores = currentUserBrain.channelScores + (video.channelId to newChScore)
            
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            
            currentUserBrain = currentUserBrain.copy(
                globalVector = newGlobal,
                morningVector = if (hour in 6..11) newBucketVector else currentUserBrain.morningVector,
                afternoonVector = if (hour in 12..17) newBucketVector else currentUserBrain.afternoonVector,
                eveningVector = if (hour in 18..23) newBucketVector else currentUserBrain.eveningVector,
                nightVector = if (hour !in 6..23) newBucketVector else currentUserBrain.nightVector,
                channelScores = newChannelScores,
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

    /**
     * Extracts features from a video into a ContentVector.
     * Uses Bigram tokenization for better context understanding.
     */
    /**
     * Extracts features from a video into a ContentVector.
     * Uses Bigram tokenization for better context understanding.
     * 
     * V4: Now normalizes the topic vector to unit length for proper cosine similarity.
     */
    private fun extractFeatures(video: Video): ContentVector {
        val topics = mutableMapOf<String, Double>()
        
        // Helper: Clean and split text
        fun tokenize(text: String): List<String> {
            return text.lowercase()
                .split("\\s+".toRegex())
                .map { word -> word.trim { !it.isLetterOrDigit() } }
                .filter { it.length > 2 && !STOP_WORDS.contains(it) }
        }

        val titleWords = tokenize(video.title)
        val chWords = tokenize(video.channelName)
        
        // 1. Channel Weight (Reduced from 2.0 to 1.0 for normalization safety)
        chWords.forEach { word -> topics[word] = 1.0 }
        
        // 2. Title Keywords
        titleWords.forEach { word -> 
            topics[word] = (topics.getOrDefault(word, 0.0) + 0.5)
        }

        // 3. Bigram Extraction (Context)
        // "Machine Learning" vs "Washing Machine"
        if (titleWords.size >= 2) {
            for (i in 0 until titleWords.size - 1) {
                val bigram = "${titleWords[i]} ${titleWords[i+1]}"
                topics[bigram] = 0.75
            }
        }
        
        // 4. NORMALIZE VECTOR (Crucial for proper cosine similarity - V4 fix)
        // Ensure the vector magnitude is 1.0 so it doesn't overpower the User Brain
        val normalizedTopics = if (topics.isNotEmpty()) {
            var magnitude = 0.0
            topics.values.forEach { magnitude += it * it }
            magnitude = sqrt(magnitude)
            
            if (magnitude > 0) {
                topics.mapValues { (_, v) -> v / magnitude }
            } else topics
        } else topics
        
        // 5. Heuristics
        val durationSec = if (video.duration > 0) video.duration else if (video.isLive) 3600 else 300
        val durationScore = (durationSec / 1200.0).coerceIn(0.0, 1.0) // 20 mins = 1.0
        val pacingScore = 1.0 - durationScore
        val complexityScore = ((video.title.length / 60.0).coerceIn(0.0, 1.0))
        val liveScore = if (video.isLive) 1.0 else 0.0
        
        return ContentVector(
            topics = normalizedTopics,
            duration = durationScore,
            pacing = pacingScore,
            complexity = complexityScore,
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

    /**
     * Adjusts the current vector towards the target vector.
     * 
     * V4 FIX: Implements DIMINISHING RETURNS (Sigmoid Saturation)
     * The higher a score already is, the harder it is to increase further.
     * This prevents the "Echo Chamber" effect where 3 likes dominate the entire profile.
     */
    private fun adjustVector(current: ContentVector, target: ContentVector, baseRate: Double): ContentVector {
        val newTopics = current.topics.toMutableMap()
        
        // 1. Move towards target with Diminishing Returns
        target.topics.forEach { (key, targetVal) ->
            val currentVal = newTopics[key] ?: 0.0
            
            // V4 FIX: Calculate dynamic rate based on saturation
            // If currentVal is already high (0.8), effective rate drops significantly
            // saturationPenalty approaches 0 as currentVal approaches 1
            val saturationPenalty = (1.0 - currentVal).pow(2)
            val effectiveRate = baseRate * saturationPenalty
            
            // Apply update with diminishing returns
            val delta = (targetVal - currentVal) * effectiveRate
            newTopics[key] = (currentVal + delta).coerceIn(0.0, 1.0)
        }
        
        // 2. Aggressive Decay for Non-Relevant Topics (The "Clean-up")
        // If we are boosting "Cats", we must slightly punish "Cars" to keep the vector normalized
        val decay = if (baseRate > 0) 0.97 else 1.0 // Increased decay from 0.98 to 0.97
        
        val iterator = newTopics.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            
            // Don't decay the topics we just boosted
            if (!target.topics.containsKey(entry.key)) {
                entry.setValue(entry.value * decay)
            }
            
            // Remove very low scores to prevent vector bloat
            if (entry.value < 0.05) iterator.remove()
        }

        // Apply diminishing returns to scalar values too
        val durationSaturation = (1.0 - current.duration).pow(2)
        val pacingSaturation = (1.0 - current.pacing).pow(2)
        val complexitySaturation = (1.0 - current.complexity).pow(2)

        return current.copy(
            topics = newTopics,
            duration = current.duration + (target.duration - current.duration) * baseRate * durationSaturation,
            pacing = current.pacing + (target.pacing - current.pacing) * baseRate * pacingSaturation,
            complexity = current.complexity + (target.complexity - current.complexity) * baseRate * complexitySaturation
        )
    }

    // Improved Diversity Logic
    private fun applySmartDiversity(candidates: MutableList<ScoredVideo>): List<Video> {
        val finalPlaylist = mutableListOf<Video>()
        val usedChannels = mutableSetOf<String>()
        val usedTopics = mutableSetOf<String>()

        // Sort by Score (Best content first)
        candidates.sortByDescending { it.score }

        val iterator = candidates.iterator()
        
        // PHASE 1: High Quality Diversity (The first ~20 videos)
        // We are strict here. We don't want the user to see 5 videos from the same guy immediately.
        while (iterator.hasNext() && finalPlaylist.size < 20) {
            val current = iterator.next()
            val primaryTopic = current.vector.topics.maxByOrNull { it.value }?.key ?: ""
            
            val isChannelRepeated = usedChannels.contains(current.video.channelId)
            val isTopicSaturated = usedTopics.count { it == primaryTopic } >= 3

            if (!isChannelRepeated && !isTopicSaturated) {
                finalPlaylist.add(current.video)
                usedChannels.add(current.video.channelId)
                if (primaryTopic.isNotEmpty()) usedTopics.add(primaryTopic)
                iterator.remove() // Remove from candidate pool so we don't duplicate
            }
        }

        // PHASE 2: The "Filler" (Unlimited)
        // After the top 20, we relax the rules. Just give the user the rest of the high-scored videos.
        // We just map the remaining ScoredVideo objects back to normal Video objects.
        candidates.forEach { scoredVideo ->
            finalPlaylist.add(scoredVideo.video)
        }

        return finalPlaylist
    }

    // =================================================
    // 4. STORAGE
    // =================================================

    /**
     * V4: File I/O now runs on IO dispatcher to prevent UI jank
     * as the brain grows larger with more topics.
     */
    private suspend fun saveBrain(context: Context) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("morning", vectorToJson(currentUserBrain.morningVector))
            json.put("afternoon", vectorToJson(currentUserBrain.afternoonVector))
            json.put("evening", vectorToJson(currentUserBrain.eveningVector))
            json.put("night", vectorToJson(currentUserBrain.nightVector))
            json.put("global", vectorToJson(currentUserBrain.globalVector))
            
            val scoresJson = JSONObject()
            currentUserBrain.channelScores.forEach { (k, v) -> scoresJson.put(k, v) }
            json.put("channelScores", scoresJson)

            json.put("interactions", currentUserBrain.totalInteractions)
            
            val file = File(context.filesDir, BRAIN_FILENAME)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save brain", e)
        }
    }

    /**
     * V4: File I/O now runs on IO dispatcher to prevent UI jank at startup.
     */
    private suspend fun loadBrain(context: Context) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, BRAIN_FILENAME)
            if (!file.exists()) return@withContext
            
            val json = JSONObject(file.readText())
            
            val scoresMap = mutableMapOf<String, Double>()
            val scoresJson = json.optJSONObject("channelScores")
            scoresJson?.keys()?.forEach { key ->
                scoresMap[key] = scoresJson.getDouble(key)
            }

            // --- MIGRATION LOGIC START ---
            // Check if we have the old "longTerm" key but missing the new "global" key
            val hasOldData = json.has("longTerm") && !json.has("global")
            
            val globalVec = if (hasOldData) {
                Log.i(TAG, "Migrating Legacy Brain to V4...")
                jsonToVector(json.getJSONObject("longTerm"))
            } else {
                jsonToVector(json.optJSONObject("global") ?: JSONObject())
            }
            // --- MIGRATION LOGIC END ---

            currentUserBrain = UserBrain(
                morningVector = jsonToVector(json.optJSONObject("morning") ?: JSONObject()),
                afternoonVector = jsonToVector(json.optJSONObject("afternoon") ?: JSONObject()),
                eveningVector = jsonToVector(json.optJSONObject("evening") ?: JSONObject()),
                nightVector = jsonToVector(json.optJSONObject("night") ?: JSONObject()),
                globalVector = globalVec, // Use the migrated or loaded vector
                channelScores = scoresMap,
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
        val icon: String
    ) {
        // Tier 1: The Newbie
        INITIATE("The Initiate", "Just getting started. Your profile is still forming.", "🌱"),

        // Tier 2: Content Specific
        AUDIOPHILE("The Audiophile", "You use Flow mostly for Music. The vibe is everything.", "🎧"),
        LIVEWIRE("The Livewire", "You love the raw energy of Livestreams and premieres.", "🔴"),
        
        // Tier 3: Context Specific
        NIGHT_OWL("The Night Owl", "You thrive in the dark. Most of your watching happens after midnight.", "🦉"),
        BINGER("The Binger", "Once you start, you can't stop. You consume content in massive waves.", "🍿"),

        // Tier 4: Intellectual Style
        SCHOLAR("The Scholar", "High-complexity content. You aren't here to be entertained, you're here to grow.", "🎓"),
        DEEP_DIVER("The Deep Diver", "You prefer long-form video essays and documentaries.", "🤿"),
        
        // Tier 5: Attention Span
        SKIMMER("The Skimmer", "Fast-paced, short content. You want the dopamine, now.", "⚡"),
        
        // Tier 6: Breadth
        SPECIALIST("The Specialist", "Laser-focused on a few niches. You know exactly what you like.", "🎯"),
        EXPLORER("The Explorer", "Chaotic and beautiful. You watch a bit of everything.", "🧭")
    }

    fun getPersona(brain: UserBrain): FlowPersona {
        // 1. The "Cold Start" Check
        if (brain.totalInteractions < 15) return FlowPersona.INITIATE

        val v = brain.globalVector
        
        // --- PRE-CALCULATIONS ---
        
        // A. Diversity Score (How scattered are your interests?)
        val sortedTopics = v.topics.values.sortedDescending()
        val topScore = sortedTopics.firstOrNull() ?: 0.0
        val diversityIndex = if (sortedTopics.size >= 5) {
            // Compare the strength of the 5th topic to the 1st topic
            sortedTopics[4] / topScore
        } else 0.0

        // B. Music Detection (Do you mostly listen to audio?)
        val musicKeywords = setOf("music", "song", "lyrics", "remix", "lofi", "playlist", "official audio")
        val musicScore = v.topics.entries
            .filter { musicKeywords.contains(it.key) || it.key.contains("feat") }
            .sumOf { it.value }

        // C. Time Dominance (Are you nocturnal?)
        // Calculate magnitude of vectors to see which time slot is most active
        fun mag(cv: ContentVector) = cv.topics.values.sum()
        val morningMag = mag(brain.morningVector)
        val nightMag = mag(brain.nightVector)
        val isNocturnal = nightMag > (morningMag * 1.5) && nightMag > 5.0

        // --- THE DECISION WATERFALL ---

        return when {
            // 2. Content Type Overrides (Strongest indicators)
            musicScore > (v.topics.values.sum() * 0.4) -> FlowPersona.AUDIOPHILE
            v.isLive > 0.6 -> FlowPersona.LIVEWIRE

            // 3. Behavioral Overrides
            isNocturnal -> FlowPersona.NIGHT_OWL
            brain.totalInteractions > 500 && v.pacing > 0.6 -> FlowPersona.BINGER // Lots of videos + fast pacing

            // 4. Intellectual Style
            v.complexity > 0.75 -> FlowPersona.SCHOLAR // High complexity text/topics
            v.duration > 0.70 -> FlowPersona.DEEP_DIVER // Very long videos (>20mins avg)

            // 5. Attention Style
            v.duration < 0.35 && v.pacing > 0.65 -> FlowPersona.SKIMMER // Shorts & fast clips

            // 6. Breadth (The Fallback)
            diversityIndex < 0.25 -> FlowPersona.SPECIALIST // Top topic dominates everything
            else -> FlowPersona.EXPLORER // Balanced profile
        }
    }

    // =================================================
    // 6. ANTI-GRAVITY EXPLORATION ENGINE
    // =================================================

    /**
     * Macro categories for exploration.
     * These are high-level topics to test when the user hasn't engaged with them.
     */
    private val MACRO_CATEGORIES = listOf(
        "Technology", "Travel", "Cooking", "History", "Art", "Fitness",
        "Comedy", "DIY", "Space", "Psychology", "Cinema", "Nature",
        "Photography", "Philosophy", "Economics", "Sports", "Fashion",
        "Architecture", "Music", "Science", "Cars", "Gadgets", "Anime"
    )

    /**
     * Finds a macro category the user has low or zero interaction with.
     * This breaks the echo chamber by deliberately exploring new areas.
     */
    private fun getExplorationQuery(brain: UserBrain): String? {
        // Find a category user has minimal interaction with
        return MACRO_CATEGORIES.shuffled().firstOrNull { category ->
            val score = brain.globalVector.topics[category.lowercase()] ?: 0.0
            score < 0.1 // Low score means "Explore this"
        }
    }

    /**
     * V4: Expanded stop words to filter out YouTube fluff that doesn't indicate interest.
     */
    private val STOP_WORDS = setOf(
        // Common English
        "the", "and", "for", "that", "this", "with", "you", "how", "what", 
        "when", "mom", "types", "your", "which", "can", "make", "seen", 
        "most", "into", "best", "from", "just", "about", "more", "some",
        "will", "one", "all", "would", "there", "their", "out", "not",
        "but", "have", "has", "been", "being", "was", "were", "are",
        
        // YouTube-specific fluff (new in V4)
        "video", "official", "channel", "recap", "review", "reaction",
        "full", "episode", "part", "new", "latest", "update", "updates",
        "hdr", "uhd", "fps", "live", "stream", "streaming", "watch",
        "subscribe", "like", "comment", "share", "click", "link",
        "description", "below", "check", "dont", "miss", "must", "now",
        
        // Quality indicators (don't indicate topic interest)
        "1080p", "720p", "480p", "360p", "240p", "144p",
        
        // Common YouTube title patterns
        "reupload", "reup", "reuploaded", "compilation", "montage",
        "highlights", "explained", "tutorial", "guide", "tips", "tricks"
    )
}