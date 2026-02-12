/*
 * Copyright (C) 2025 Flow | A-EDev
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
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.*

/**
 * 🧠 Flow Neuro Engine (V6 - Battle-Tested & Reliable)
 * 
 * A Client-Side Hybrid Recommendation System.
 * Combines Vector Space Models (Learning) with Heuristic Rules (Reliability).
 * 
 * V6 Changes:
 * - Fixed: Negative feedback (skip/dislike/not-interested) now properly reduces topic scores
 * - Fixed: Replaced broken Porter stemmer with predictable lemma dictionary  
 * - Fixed: Topic saturation diversity check (Set→List) now actually works
 * - Fixed: complexity & isLive fields now persist across restarts
 * - Improved: Cosine similarity now blends scalar features (duration/pacing/complexity)
 * - Improved: Phase 2 diversity re-ranking with channel + title constraints  
 * - Improved: Complexity heuristic uses avg word length instead of just title length
 * - Improved: Duration normalization uses logarithmic scale (distinguishes 20min vs 3hr)
 * - Added: Cold-start popularity boost using view count  
 * - Added: Debounced disk writes (max once per 5s instead of every interaction)
 * - Added: Schema versioning for future brain migrations
 * - Added: Channel scores pruning (capped at 500 entries)
 * - Added: Persona hysteresis (prevents rapid persona flipping)
 * - Migrated: Storage from raw JSON file to Jetpack DataStore (atomic writes, crash-safe)
 */
object FlowNeuroEngine {

    private const val TAG = "FlowNeuroEngine"
    private const val BRAIN_FILENAME = "user_neuro_brain.json" // Legacy, kept for migration
    private const val SCHEMA_VERSION = 6
    
    private val brainMutex = Mutex()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSaveJob: Job? = null

    // =================================================
    // V5: TIME DECAY ENGINE
    // =================================================
    
    /**
     * Applies a recency bias multiplier based on upload date.
     * Fresh content gets a boost, ancient content gets buried (unless it's a classic).
     */
    private object TimeDecay {
        
        fun calculateMultiplier(dateText: String, isLive: Boolean): Double {
            val text = dateText.lowercase()
            
            // Live streams get a slight recency bias (news/events)
            if (isLive) return 1.15
            
            return when {
                // Freshness Boost (Breaking/Trending)
                text.contains("second") || text.contains("minute") || text.contains("hour") -> 1.15
                text.contains("day") -> 1.12
                text.contains("week") -> 1.08
                
                // Standard (Recent enough)
                text.contains("month") -> 1.0
                
                // The Decay Curve (Older content)
                text.contains("year") -> {
                    // Extract the number: "2 years ago" -> 2
                    val years = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                    
                    // Formula: 1 / (1 + 0.35 * years)
                    // 1 year  = 0.74
                    // 3 years = 0.49
                    // 5 years = 0.36
                    // 8 years = 0.26 (Effectively buried unless perfect match)
                    1.0 / (1.0 + (0.35 * years))
                }
                
                else -> 0.85 
            }
        }
    }

    // =================================================
    // V6: LEMMA DICTIONARY (Replaces broken Porter Stemmer)
    // =================================================
    
    /**
     * Predictable word normalization via dictionary lookup.
     * Unlike the V5 stemmer, this produces real words that:
     * 1. Match onboarding preferences correctly
     * 2. Generate valid YouTube search queries
     * 3. Are consistent across all subsystems
     */
    private val LEMMA_MAP = mapOf(
        // Gaming
        "gaming" to "game", "games" to "game", "gamer" to "game", "gamers" to "game",
        "gameplay" to "game", "gamed" to "game",
        // Coding / Programming
        "coding" to "code", "coder" to "code", "coders" to "code", "codes" to "code", "coded" to "code",
        "programming" to "program", "programmer" to "program", "programmers" to "program",
        "programs" to "program", "programmed" to "program",
        // Cooking
        "cooking" to "cook", "cooked" to "cook", "cooks" to "cook", "cooker" to "cook",
        // Music
        "songs" to "song", "singing" to "sing", "singer" to "sing", "singers" to "sing",
        "musics" to "music", "musical" to "music", "musician" to "music", "musicians" to "music",
        // Technology  
        "technologies" to "technology", "technological" to "technology",
        "computers" to "computer", "computing" to "computer", "computed" to "computer",
        // Art
        "drawing" to "draw", "drawings" to "draw", "drawn" to "draw",
        "painting" to "paint", "paintings" to "paint", "painted" to "paint", "painter" to "paint",
        "designing" to "design", "designs" to "design", "designer" to "design", "designed" to "design",
        "animating" to "animation", "animated" to "animation", "animations" to "animation", "animator" to "animation",
        // Fitness
        "workouts" to "workout", "exercising" to "exercise", "exercises" to "exercise", "exercised" to "exercise",
        "running" to "run", "runner" to "run", "runners" to "run",
        "training" to "train", "trained" to "train", "trainer" to "train", "trainers" to "train",
        // Education
        "learning" to "learn", "learned" to "learn", "learner" to "learn", "learners" to "learn",
        "teaching" to "teach", "teacher" to "teach", "teachers" to "teach", "taught" to "teach",
        "studying" to "study", "studies" to "study", "studied" to "study",
        "tutorials" to "tutorial",
        // Common
        "playing" to "play", "played" to "play", "player" to "play", "players" to "play",
        "building" to "build", "builder" to "build", "builders" to "build", "builds" to "build", "built" to "build",
        "making" to "make", "maker" to "make", "makers" to "make", "makes" to "make", "made" to "make",
        "reviewing" to "review", "reviewed" to "review", "reviews" to "review", "reviewer" to "review",
        "testing" to "test", "tested" to "test", "tests" to "test", "tester" to "test",
        "streaming" to "stream", "streamed" to "stream", "streams" to "stream", "streamer" to "stream",
        "editing" to "edit", "edited" to "edit", "edits" to "edit", "editor" to "edit",
        "filming" to "film", "filmed" to "film", "films" to "film", "filmmaker" to "film",
        "traveling" to "travel", "travelled" to "travel", "travels" to "travel", "traveler" to "travel",
        "vlogging" to "vlog", "vlogs" to "vlog", "vlogger" to "vlog", "vloggers" to "vlog",
        "reacting" to "react", "reacted" to "react", "reacts" to "react", "reactions" to "reaction",
        // Science
        "experiments" to "experiment", "experimenting" to "experiment", "experimental" to "experiment",
        "sciences" to "science", "scientific" to "science", "scientist" to "science",
        "engineering" to "engineer", "engineered" to "engineer", "engineers" to "engineer",
        "inventions" to "invention", "inventing" to "invention", "invented" to "invention",
        // Nature
        "animals" to "animal", "plants" to "plant", "planting" to "plant",
        // Lifestyle
        "recipes" to "recipe", "baking" to "bake", "baked" to "bake", "baker" to "bake",
        "gardening" to "garden", "gardens" to "garden",
        "photographing" to "photography", "photographs" to "photography", "photographer" to "photography",
        // Plurals / common suffixes
        "videos" to "video", "channels" to "channel", "episodes" to "episode",
        "movies" to "movie", "documentaries" to "documentary",
        "podcasts" to "podcast", "interviews" to "interview",
        "challenges" to "challenge", "compilations" to "compilation"
    )

    private fun normalizeLemma(word: String): String = LEMMA_MAP[word.lowercase()] ?: word.lowercase()

    // =================================================
    // V5: TITLE SIMILARITY (Anti-Clustering)
    // =================================================
    
    /**
     * Calculates normalized Levenshtein-like similarity (0.0 to 1.0).
     * Uses word-level comparison for efficiency.
     */
    private fun calculateTitleSimilarity(title1: String, title2: String): Double {
        val words1 = title1.lowercase().split(" ").filter { it.length > 2 }.toSet()
        val words2 = title2.lowercase().split(" ").filter { it.length > 2 }.toSet()
        
        if (words1.isEmpty() || words2.isEmpty()) return 0.0
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union > 0) intersection.toDouble() / union else 0.0
    }


    private fun isVideoClassic(viewCount: Long): Boolean {
        return viewCount >= 5_000_000L
    }
    
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
        val totalInteractions: Int = 0,
        val consecutiveSkips: Int = 0,  
        val blockedTopics: Set<String> = emptySet(),  
        val blockedChannels: Set<String> = emptySet(), 
        val preferredTopics: Set<String> = emptySet(), 
        val hasCompletedOnboarding: Boolean = false,
        val lastPersona: String? = null,        // V6: Persona hysteresis
        val personaStability: Int = 0,           // V6: Consecutive checks returning same persona
        val schemaVersion: Int = SCHEMA_VERSION  // V6: Schema versioning for future migrations
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
            
            // V6: Try loading from DataStore first, then migrate from legacy JSON if needed
            val loaded = loadBrainFromDataStore(context)
            if (!loaded) {
                // Attempt legacy JSON migration
                loadLegacyBrain(context)
                // Persist to DataStore immediately after migration
                saveBrainToDataStore(context)
                // Delete legacy file after successful migration
                val legacyFile = File(context.filesDir, BRAIN_FILENAME)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                    Log.i(TAG, "Migrated legacy JSON brain to DataStore and deleted old file")
                }
            }
            isInitialized = true
        }
    }

    suspend fun getBrainSnapshot(): UserBrain {
        return brainMutex.withLock { currentUserBrain }
    }

    suspend fun resetBrain(context: Context) {
        brainMutex.withLock {
            currentUserBrain = UserBrain()
            saveBrainToDataStore(context)
        }
    }

    /**
     * V6: Debounced save - writes at most once every 5 seconds.
     * Prevents excessive I/O from rapid interactions (click, skip, skip, skip...).
     * Critical state changes (resetBrain, markNotInterested, onboarding) still save immediately.
     */
    private fun scheduleDebouncedSave(context: Context) {
        pendingSaveJob?.cancel()
        pendingSaveJob = saveScope.launch {
            delay(5000L)
            brainMutex.withLock {
                saveBrainToDataStore(context)
            }
        }
    }

    // =================================================
    // V5: BLOCKED TOPICS & CHANNELS API
    // =================================================

    /**
     * Get the current list of blocked topics.
     */
    suspend fun getBlockedTopics(): Set<String> {
        return brainMutex.withLock { currentUserBrain.blockedTopics }
    }

    /**
     * Add a topic to the blocked list.
     * Topics are stemmed and lowercased for matching.
     */
    suspend fun addBlockedTopic(context: Context, topic: String) {
        val normalizedTopic = topic.trim().lowercase()
        if (normalizedTopic.isBlank()) return
        
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedTopics = currentUserBrain.blockedTopics + normalizedTopic
            )
            saveBrainToDataStore(context) // Immediate - user expects instant effect
        }
    }

    /**
     * Remove a topic from the blocked list.
     */
    suspend fun removeBlockedTopic(context: Context, topic: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedTopics = currentUserBrain.blockedTopics - topic.lowercase()
            )
            saveBrainToDataStore(context)
        }
    }

    /**
     * Get the current list of blocked channel IDs.
     */
    suspend fun getBlockedChannels(): Set<String> {
        return brainMutex.withLock { currentUserBrain.blockedChannels }
    }

    /**
     * Block a channel by ID.
     */
    suspend fun blockChannel(context: Context, channelId: String) {
        if (channelId.isBlank()) return
        
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedChannels = currentUserBrain.blockedChannels + channelId
            )
            saveBrainToDataStore(context) // Immediate
        }
    }

    /**
     * Unblock a channel by ID.
     */
    suspend fun unblockChannel(context: Context, channelId: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedChannels = currentUserBrain.blockedChannels - channelId
            )
            saveBrainToDataStore(context)
        }
    }

    // =================================================
    // V5: ONBOARDING & PREFERRED TOPICS API
    // =================================================

    /**
     * Comprehensive topic categories for onboarding and preferences.
     * Organized by category with emoji icons for better UX. (i may change emojies later if i find suitable icons)
     */
    data class TopicCategory(
        val name: String,
        val icon: String,
        val topics: List<String>
    )

    val TOPIC_CATEGORIES = listOf(
        TopicCategory("🎮 Gaming", "🎮", listOf(
            "Gaming", "Minecraft", "Fortnite", "GTA", "Call of Duty", "Valorant", 
            "League of Legends", "Pokemon", "Nintendo", "PlayStation", "Xbox",
            "PC Gaming", "Esports", "Speedruns", "Game Reviews", "Indie Games",
            "Retro Gaming", "Mobile Games", "Roblox", "Apex Legends", "FIFA"
        )),
        TopicCategory("🎵 Music", "🎵", listOf(
            "Music", "Pop Music", "Hip Hop", "R&B", "Rock", "Metal", "Jazz",
            "Classical", "Electronic", "EDM", "Lo-Fi", "K-Pop", "J-Pop",
            "Country", "Indie Music", "Music Production", "Guitar", "Piano",
            "Singing", "Music Theory", "Album Reviews", "Concerts", "DJ"
        )),
        TopicCategory("💻 Technology", "💻", listOf(
            "Technology", "Programming", "Coding", "Web Development", "App Development",
            "AI", "Machine Learning", "Cybersecurity", "Linux", "Apple", "Android",
            "Smartphones", "Laptops", "PC Building", "Tech Reviews", "Gadgets",
            "Software", "Cloud Computing", "Blockchain", "Crypto", "Startups"
        )),
        TopicCategory("🎬 Entertainment", "🎬", listOf(
            "Movies", "TV Shows", "Netflix", "Anime", "Marvel", "DC", "Star Wars",
            "Disney", "Comedy", "Stand-up Comedy", "Drama", "Horror", "Sci-Fi",
            "Documentary", "Film Analysis", "Movie Reviews", "Behind the Scenes",
            "Celebrities", "Award Shows", "Trailers", "Fan Theories"
        )),
        TopicCategory("📚 Education", "📚", listOf(
            "Science", "Physics", "Chemistry", "Biology", "Mathematics", "History",
            "Geography", "Psychology", "Philosophy", "Economics", "Finance",
            "Investing", "Business", "Marketing", "Language Learning", "English",
            "Spanish", "Study Tips", "College", "University", "Tutorials"
        )),
        TopicCategory("🏋️ Health & Fitness", "🏋️", listOf(
            "Fitness", "Workout", "Gym", "Yoga", "Running", "CrossFit", "Bodybuilding",
            "Weight Loss", "Nutrition", "Healthy Eating", "Mental Health", "Meditation",
            "Self Improvement", "Productivity", "Motivation", "Sports", "Basketball",
            "Football", "Soccer", "MMA", "Boxing", "Tennis", "Golf"
        )),
        TopicCategory("🍳 Lifestyle", "🍳", listOf(
            "Cooking", "Recipes", "Baking", "Food", "Restaurants", "Travel",
            "Vlogging", "Daily Vlog", "Fashion", "Style", "Beauty", "Skincare",
            "Home Decor", "Interior Design", "DIY", "Crafts", "Gardening",
            "Pets", "Dogs", "Cats", "Cars", "Motorcycles", "Photography"
        )),
        TopicCategory("🎨 Creative", "🎨", listOf(
            "Art", "Drawing", "Painting", "Digital Art", "Animation", "3D Modeling",
            "Graphic Design", "Video Editing", "Filmmaking", "Photography",
            "Music Production", "Writing", "Storytelling", "Architecture",
            "Fashion Design", "Crafts", "Woodworking", "Sculpture"
        )),
        TopicCategory("🔬 Science & Nature", "🔬", listOf(
            "Space", "Astronomy", "NASA", "Physics", "Nature", "Animals", "Wildlife",
            "Ocean", "Marine Life", "Environment", "Climate", "Geology",
            "Paleontology", "Dinosaurs", "Engineering", "Inventions", "Experiments"
        )),
        TopicCategory("📰 News & Current Events", "📰", listOf(
            "News", "Politics", "World News", "Tech News", "Sports News",
            "Entertainment News", "Business News", "Analysis", "Commentary",
            "Podcasts", "Interviews", "Debates", "Current Events"
        ))
    )

    /**
     * Check if user needs onboarding (cold start detection).
     */
    suspend fun needsOnboarding(): Boolean {
        return brainMutex.withLock {
            !currentUserBrain.hasCompletedOnboarding && 
            currentUserBrain.totalInteractions < 5 &&
            currentUserBrain.preferredTopics.isEmpty()
        }
    }

    /**
     * Check if onboarding has been completed.
     */
    suspend fun hasCompletedOnboarding(): Boolean {
        return brainMutex.withLock { currentUserBrain.hasCompletedOnboarding }
    }

    /**
     * Get current preferred topics.
     */
    suspend fun getPreferredTopics(): Set<String> {
        return brainMutex.withLock { currentUserBrain.preferredTopics }
    }

    /**
     * Set preferred topics from onboarding or preferences screen.
     * Also seeds the global vector with initial weights for better cold start.
     */
    suspend fun setPreferredTopics(context: Context, topics: Set<String>) {
        brainMutex.withLock {
            // Seed the global vector with preferred topics
            // V6: Use normalizeLemma for consistency with extractFeatures
            val newTopics = currentUserBrain.globalVector.topics.toMutableMap()
            topics.forEach { topic ->
                val normalizedTopic = normalizeLemma(topic)
                newTopics[normalizedTopic] = 0.5 
            }
            
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = topics,
                globalVector = currentUserBrain.globalVector.copy(topics = newTopics)
            )
            saveBrainToDataStore(context)
        }
    }

    /**
     * Add a single preferred topic.
     */
    suspend fun addPreferredTopic(context: Context, topic: String) {
        val normalizedTopic = topic.trim()
        if (normalizedTopic.isBlank()) return
        
        brainMutex.withLock {
            // V6: Use normalizeLemma for consistency
            val newTopics = currentUserBrain.globalVector.topics.toMutableMap()
            newTopics[normalizeLemma(normalizedTopic)] = 0.5
            
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = currentUserBrain.preferredTopics + normalizedTopic,
                globalVector = currentUserBrain.globalVector.copy(topics = newTopics)
            )
            saveBrainToDataStore(context)
        }
    }

    /**
     * Remove a preferred topic.
     */
    suspend fun removePreferredTopic(context: Context, topic: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = currentUserBrain.preferredTopics - topic
            )
            saveBrainToDataStore(context)
        }
    }

    /**
     * Complete onboarding process.
     * V6: Seeds topics using normalizeLemma for proper matching with video features.
     */
    suspend fun completeOnboarding(context: Context, selectedTopics: Set<String>) {
        brainMutex.withLock {
            // Seed the global vector with selected topics using lemma normalization
            val newTopics = mutableMapOf<String, Double>()
            selectedTopics.forEach { topic ->
                newTopics[normalizeLemma(topic)] = 0.6 
            }
            
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = selectedTopics,
                globalVector = currentUserBrain.globalVector.copy(topics = newTopics),
                hasCompletedOnboarding = true
            )
            saveBrainToDataStore(context) // Immediate - critical state change
            Log.i(TAG, "Onboarding completed with ${selectedTopics.size} topics: $selectedTopics")
        }
    }

    /**
     * "Not Interested" - Nuclear option for content the user doesn't want.
     * This:
     * 1. Strongly penalizes the video's topics in the user's brain
     * 2. Lowers the channel score significantly
     * 3. Extracts keywords and adds them as mild negative signals
     */
    suspend fun markNotInterested(context: Context, video: Video) {
        val videoVector = extractFeatures(video)
        
        brainMutex.withLock {
            // 1. Strong negative learning on the video's topics
            val newGlobal = adjustVector(currentUserBrain.globalVector, videoVector, -0.35)
            
            // 2. Heavily penalize the channel (set to very low score)
            val newChannelScores = currentUserBrain.channelScores.toMutableMap()
            newChannelScores[video.channelId] = 0.05 
            
            // 3. Update the current time bucket as well
            val currentBucket = getCurrentTimeBucket(currentUserBrain)
            val newBucketVector = adjustVector(currentBucket, videoVector, -0.25)
            
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            
            currentUserBrain = currentUserBrain.copy(
                globalVector = newGlobal,
                morningVector = if (hour in 6..11) newBucketVector else currentUserBrain.morningVector,
                afternoonVector = if (hour in 12..17) newBucketVector else currentUserBrain.afternoonVector,
                eveningVector = if (hour in 18..23) newBucketVector else currentUserBrain.eveningVector,
                nightVector = if (hour !in 6..23) newBucketVector else currentUserBrain.nightVector,
                channelScores = newChannelScores,
                totalInteractions = currentUserBrain.totalInteractions + 1,
                consecutiveSkips = (currentUserBrain.consecutiveSkips + 1).coerceAtMost(30)
            )
            
            saveBrainToDataStore(context) // Immediate - critical user intent
            Log.d(TAG, "Marked not interested: ${video.title} from ${video.channelName}")
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
        // 5. FALLBACK: Use preferred topics from onboarding, or defaults
        // ==============================================================
        if (queries.isEmpty()) {
            val preferredTopics = currentUserBrain.preferredTopics.toList()
            if (preferredTopics.isNotEmpty()) {
                return@withLock preferredTopics.shuffled().take(5)
            }
            return@withLock listOf("Trending", "Music", "Gaming", "Technology", "Science")
        }

        return@withLock queries.distinct().shuffled()
    }

    /**
     * MAIN FUNCTION: Rank videos based on User Brain + Random Jitter
     * 
     * V5 Enhancements:
     * - Dynamic Temperature: Weights adapt based on consecutive skips
     * - Time Decay: Recency bias with classic video exceptions
     * - Curiosity Gap: Boost for challenging complexity within safe topics
     * - Blocked Topics/Channels: Filter out unwanted content
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
        
        // =====================================================
        // V5: PRE-FILTER - Remove blocked channels and topics
        // =====================================================
        val filteredCandidates = candidates.filter { video ->
            // Check if channel is blocked
            if (brain.blockedChannels.contains(video.channelId)) {
                return@filter false
            }
            
            // Check if any blocked topic appears in title or channel name
            val titleLower = video.title.lowercase()
            val channelLower = video.channelName.lowercase()
            val hasBlockedTopic = brain.blockedTopics.any { blockedTopic ->
                titleLower.contains(blockedTopic) || channelLower.contains(blockedTopic)
            }
            
            !hasBlockedTopic
        }
        
        if (filteredCandidates.isEmpty()) return@withContext emptyList()
        
        // 1. Identify the Context (What time is it?)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeContextVector = when (hour) {
            in 6..11 -> brain.morningVector
            in 12..17 -> brain.afternoonVector
            in 18..23 -> brain.eveningVector
            else -> brain.nightVector
        }

        // =====================================================
        // V5: DYNAMIC TEMPERATURE (Boredom Detection)
        // =====================================================
        // If user has ignored many recommendations, boost novelty dynamically
        val boredomFactor = (brain.consecutiveSkips / 20.0).coerceIn(0.0, 0.5)
        
        val wPersonality = 0.4 - (boredomFactor * 0.5) // Drops to ~0.15 if very bored
        val wContext     = 0.4 - (boredomFactor * 0.5) // Drops to ~0.15 if very bored
        val wNovelty     = 0.2 + boredomFactor         // Rises to ~0.70 if very bored

        val scoredCandidates = filteredCandidates.map { video ->
            val videoVector = extractFeatures(video)

            // A. The "Global Personality" Score
            val personalityScore = calculateCosineSimilarity(brain.globalVector, videoVector)

            // B. The "Time Context" Score
            val contextScore = calculateCosineSimilarity(timeContextVector, videoVector)

            // C. The "Discovery" Score (Novelty)
            val noveltyScore = 1.0 - personalityScore

            // V5: Dynamic Weighted Average (adapts to user behavior)
            var totalScore = (personalityScore * wPersonality) + 
                             (contextScore * wContext) + 
                             (noveltyScore * wNovelty)

            // --- BOOSTS & PENALTIES ---

            // Boost: Subscription
            if (userSubs.contains(video.channelId)) totalScore += 0.15

            // Boost: Serendipity
            if (noveltyScore > 0.6 && contextScore > 0.5) totalScore += 0.10 

            // =====================================================
            // V6: COLD-START POPULARITY BOOST
            // =====================================================
            // Use view count as a quality signal during cold start
            if (brain.totalInteractions < 30 && video.viewCount > 0) {
                val popularityBoost = log10(1.0 + video.viewCount.toDouble()) / 10.0 * 0.05
                totalScore += popularityBoost
            } 

            // =====================================================
            // V5: TIME DECAY (Recency Bias)
            // =====================================================
            val ageMultiplier = TimeDecay.calculateMultiplier(video.uploadDate, video.isLive)
            
            // Exceptions: Soften decay for subscriptions and viral classics
            val isClassic = isVideoClassic(video.viewCount)
            val isSubscription = userSubs.contains(video.channelId)
            
            val finalAgeFactor = when {
                isClassic || isSubscription -> (ageMultiplier + 1.0) / 2.0 // Soften: 0.26 -> 0.63
                else -> ageMultiplier
            }
            totalScore *= finalAgeFactor

            // =====================================================
            // V5: CURIOSITY GAP (Challenge the User)
            // =====================================================
            // If topic matches strongly BUT complexity is very different, give a growth boost
            val isTopicSafe = personalityScore > 0.65
            val complexityDiff = abs(brain.globalVector.complexity - videoVector.complexity)
            val isChallenging = complexityDiff > 0.35
            
            if (isTopicSafe && isChallenging) {
                totalScore += 0.10 // The "Growth" boost - surface documentaries for casual viewers
            }

            // IMPLICIT FEEDBACK CHECK (V5: Fixed threshold 0.2 -> 0.05)
            // YouTube CTR is typically 2-10%, so 0.2 was too aggressive
            val channelClickRate = brain.channelScores[video.channelId] ?: 0.5
            val channelBoredomPenalty = if (brain.channelScores.containsKey(video.channelId) && channelClickRate < 0.05) 0.5 else 1.0
            
            totalScore *= channelBoredomPenalty

            // 🧠 SESSION FATIGUE PENALTY
            // If the user JUST watched this topic, temporarily lower its score
            // V6: Normalize lastWatchedTopics with lemma for consistent matching
            val normalizedLastWatched = lastWatchedTopics.map { normalizeLemma(it) }
            val videoPrimaryTopic = videoVector.topics.maxByOrNull { it.value }?.key ?: ""
            val fatigueMultiplier = when {
                videoPrimaryTopic.isEmpty() -> 1.0
                normalizedLastWatched.count { it == videoPrimaryTopic } >= 3 -> 0.4 
                normalizedLastWatched.contains(videoPrimaryTopic) -> 0.7
                else -> 1.0
            }
            totalScore *= fatigueMultiplier

            // Jitter for Cold Start
            val jitter = if (brain.totalInteractions < 50) random.nextDouble() * 0.2 else random.nextDouble() * 0.02

            ScoredVideo(video, totalScore + jitter, videoVector)
        }.toMutableList()

        // 2. Diversity Re-ranking (V5: Now includes title similarity check)
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
     * 
     * V5: Now tracks consecutive skips for Dynamic Temperature feature.
     */
    suspend fun onVideoInteraction(context: Context, video: Video, interactionType: InteractionType, percentWatched: Float = 0f) {
        val videoVector = extractFeatures(video)
        
        var learningRate = when (interactionType) {
            InteractionType.CLICK -> 0.10
            InteractionType.LIKED -> 0.30
            InteractionType.WATCHED -> 0.15 * percentWatched
            InteractionType.SKIPPED -> -0.15
            InteractionType.DISLIKED -> -0.40
        }

        // --- SHORTS PENALTY ---
        // Reduce the learning impact of shorts to 1% of normal.
        // Shorts are consumed rapidly and often impulsively (dopamine loop), 
        // so they shouldn't skew the user's "deep" usage personality too much.
        if (video.isShort) {
            learningRate *= 0.01
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
            var newChannelScores = currentUserBrain.channelScores + (video.channelId to newChScore)
            
            // V6: Prune channel scores to prevent unbounded growth
            if (newChannelScores.size > 500) {
                newChannelScores = newChannelScores.entries
                    .sortedByDescending { it.value }
                    .take(300)
                    .associate { it.key to it.value }
            }
            
            // V5: Update consecutive skips for Dynamic Temperature
            // Reset on positive interaction, increment on skip/dislike
            val newConsecutiveSkips = when (interactionType) {
                InteractionType.CLICK, InteractionType.LIKED, InteractionType.WATCHED -> 0
                InteractionType.SKIPPED, InteractionType.DISLIKED -> (currentUserBrain.consecutiveSkips + 1).coerceAtMost(30)
            }
            
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            
            currentUserBrain = currentUserBrain.copy(
                globalVector = newGlobal,
                morningVector = if (hour in 6..11) newBucketVector else currentUserBrain.morningVector,
                afternoonVector = if (hour in 12..17) newBucketVector else currentUserBrain.afternoonVector,
                eveningVector = if (hour in 18..23) newBucketVector else currentUserBrain.eveningVector,
                nightVector = if (hour !in 6..23) newBucketVector else currentUserBrain.nightVector,
                channelScores = newChannelScores,
                totalInteractions = currentUserBrain.totalInteractions + 1,
                consecutiveSkips = newConsecutiveSkips
            )
            
            // V6: Debounced save - interactions happen frequently, no need to save every single one
            scheduleDebouncedSave(context)
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
     * 
     * V4: Now normalizes the topic vector to unit length for proper cosine similarity.
     * V6: Replaced stemmer with lemma dictionary for reliable normalization.
     *     Improved complexity heuristic (avg word length + title length).
     *     Logarithmic duration scale (distinguishes 20min vs 3hr content).
     */
    private fun extractFeatures(video: Video): ContentVector {
        val topics = mutableMapOf<String, Double>()
        
        // Helper: Clean, split, and NORMALIZE text (V6: Lemma dictionary replaces stemmer)
        fun tokenize(text: String): List<String> {
            return text.lowercase()
                .split("\\s+".toRegex())
                .map { word -> word.trim { !it.isLetterOrDigit() } }
                .filter { it.length > 2 && !STOP_WORDS.contains(it) }
                .map { normalizeLemma(it) } 
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
        
        // V6: Logarithmic duration scale — distinguishes 20min, 1hr, 3hr content
        // 5min=0.46, 20min=0.71, 60min=0.87, 2hrs=1.0
        val durationScore = (ln(1.0 + durationSec) / ln(1.0 + 7200.0)).coerceIn(0.0, 1.0)
        val pacingScore = 1.0 - durationScore
        
        // V6: Improved complexity — blend title length with average word length
        // Long average word length ("thermodynamics") > short spam words ("OMG WOW!!!")
        val rawTitleWords = video.title.split("\\s+".toRegex()).filter { it.length > 1 }
        val complexityScore = run {
            val titleLenFactor = (video.title.length / 80.0).coerceIn(0.0, 0.5)
            val avgWordLen = if (rawTitleWords.isNotEmpty()) rawTitleWords.map { it.length }.average() else 4.0
            val wordLenFactor = (avgWordLen / 8.0).coerceIn(0.0, 0.5)
            (titleLenFactor + wordLenFactor).coerceIn(0.0, 1.0)
        }
        val liveScore = if (video.isLive) 1.0 else 0.0
        
        return ContentVector(
            topics = normalizedTopics,
            duration = durationScore,
            pacing = pacingScore,
            complexity = complexityScore,
            isLive = liveScore
        )
    }

    /**
     * V6: Now blends topic cosine similarity with scalar feature distance.
     * Duration, pacing, and complexity preferences actually affect ranking.
     */
    private fun calculateCosineSimilarity(user: ContentVector, content: ContentVector): Double {
        // Optimization: Iterate over the smaller map to avoid Set allocation
        val (smallMap, largeMap) = if (user.topics.size <= content.topics.size) 
            user.topics to content.topics 
        else 
            content.topics to user.topics
        
        // Scalar similarity (inverse distance) - V6: These are now factored in
        val durationSim = 1.0 - abs(user.duration - content.duration)
        val pacingSim = 1.0 - abs(user.pacing - content.pacing)
        val complexitySim = 1.0 - abs(user.complexity - content.complexity)
        val scalarScore = (durationSim * 0.1) + (pacingSim * 0.1) + (complexitySim * 0.1)
        
        // Early exit if either map is empty
        if (smallMap.isEmpty()) {
            return scalarScore
        }

        var dotProduct = 0.0
        var hasIntersection = false
        
        for ((key, smallVal) in smallMap) {
            val largeVal = largeMap[key]
            if (largeVal != null) {
                dotProduct += smallVal * largeVal
                hasIntersection = true
            }
        }
        
        if (!hasIntersection) {
            return scalarScore
        }
        
        // Calculate magnitudes
        var magA = 0.0
        var magB = 0.0
        user.topics.values.forEach { magA += it * it }
        content.topics.values.forEach { magB += it * it }
        
        val topicSim = if (magA > 0 && magB > 0) dotProduct / (sqrt(magA) * sqrt(magB)) else 0.0
        
        // Weighted blend: 70% topics, 30% scalar features
        return (topicSim * 0.7) + scalarScore
    }

    /**
     * V6 FIX: Split positive/negative feedback handling.
     * 
     * POSITIVE: Diminishing returns (harder to increase high scores - anti echo chamber)
     * NEGATIVE: Proportional reduction (high scores get penalized MORE, not less)
     * 
     * The V5 formula applied positive diminishing returns to negative feedback,
     * which meant disliking a topic you already liked would BOOST it. Now fixed.
     */
    private fun adjustVector(current: ContentVector, target: ContentVector, baseRate: Double): ContentVector {
        val newTopics = current.topics.toMutableMap()
        val isNegative = baseRate < 0
        
        // 1. Move towards target with corrected feedback
        target.topics.forEach { (key, targetVal) ->
            val currentVal = newTopics[key] ?: 0.0
            
            val delta = if (isNegative) {
                // V6 FIX: For negative feedback, penalty scales WITH current value
                // If you strongly like gaming (0.8) and dislike a gaming video,
                // the penalty should be STRONG (0.8^1.5 * -0.15 = -0.107), not weak
                currentVal * (currentVal.pow(1.5) * baseRate)
            } else {
                // For positive feedback: diminishing returns as value approaches 1.0
                val saturationPenalty = (1.0 - currentVal).pow(2)
                val effectiveRate = baseRate * saturationPenalty
                (targetVal - currentVal) * effectiveRate
            }
            
            newTopics[key] = (currentVal + delta).coerceIn(0.0, 1.0)
        }
        
        // 2. Aggressive Decay for Non-Relevant Topics (The "Clean-up")
        // If we are boosting "Cats", we must slightly punish "Cars" to keep the vector normalized
        val decay = if (baseRate > 0) 0.97 else 1.0 
        
        val iterator = newTopics.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            
            // Don't decay the topics we just boosted
            if (!target.topics.containsKey(entry.key)) {
                entry.setValue(entry.value * decay)
            }
            
            if (entry.value < 0.05) iterator.remove()
        }

        // Apply feedback to scalar values
        // V6: Negative feedback uses proportional reduction for scalars too
        val newDuration = if (isNegative) {
            current.duration + current.duration * baseRate * 0.3
        } else {
            val durationSaturation = (1.0 - current.duration).pow(2)
            current.duration + (target.duration - current.duration) * baseRate * durationSaturation
        }
        
        val newPacing = if (isNegative) {
            current.pacing + current.pacing * baseRate * 0.3
        } else {
            val pacingSaturation = (1.0 - current.pacing).pow(2)
            current.pacing + (target.pacing - current.pacing) * baseRate * pacingSaturation
        }
        
        val newComplexity = if (isNegative) {
            current.complexity + current.complexity * baseRate * 0.3
        } else {
            val complexitySaturation = (1.0 - current.complexity).pow(2)
            current.complexity + (target.complexity - current.complexity) * baseRate * complexitySaturation
        }
        
        // V6 FIX: isLive is now learned and persisted
        val newIsLive = if (isNegative) {
            current.isLive + current.isLive * baseRate * 0.3
        } else {
            val liveSaturation = (1.0 - current.isLive).pow(2)
            current.isLive + (target.isLive - current.isLive) * baseRate * liveSaturation
        }

        return current.copy(
            topics = newTopics,
            duration = newDuration.coerceIn(0.0, 1.0),
            pacing = newPacing.coerceIn(0.0, 1.0),
            complexity = newComplexity.coerceIn(0.0, 1.0),
            isLive = newIsLive.coerceIn(0.0, 1.0)
        )
    }

    /**
     * V6: Fixed & Improved Diversity Logic
     * - Fixed: usedTopics is now a List (was Set, making count >= 3 impossible)
     * - Improved: Phase 2 now applies lighter channel + title constraints
     */
    private fun applySmartDiversity(candidates: MutableList<ScoredVideo>): List<Video> {
        val finalPlaylist = mutableListOf<Video>()
        val usedChannels = mutableSetOf<String>()
        val usedTopics = mutableListOf<String>()  // V6 FIX: List, not Set (count needs dupes)

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
            
            // V5: Title Similarity Check (Anti-Clustering)
            // Prevent 3 videos about the exact same phone release
            val isTitleTooSimilar = finalPlaylist.takeLast(5).any { existing ->
                calculateTitleSimilarity(current.video.title, existing.title) > 0.55
            }

            if (!isChannelRepeated && !isTopicSaturated && !isTitleTooSimilar) {
                finalPlaylist.add(current.video)
                usedChannels.add(current.video.channelId)
                if (primaryTopic.isNotEmpty()) usedTopics.add(primaryTopic)
                iterator.remove() 
            }
        }

        // PHASE 2: V6 - Relaxed but meaningful diversity constraints
        // No longer a free-for-all dump of remaining videos
        candidates.sortedByDescending { it.score }.forEach { scoredVideo ->
            val recentChannels = finalPlaylist.takeLast(5).map { it.channelId }
            val isChannelSpam = recentChannels.count { it == scoredVideo.video.channelId } >= 2
            val isTitleTooSimilar = finalPlaylist.takeLast(5).any { existing ->
                calculateTitleSimilarity(scoredVideo.video.title, existing.title) > 0.60
            }
            if (!isChannelSpam && !isTitleTooSimilar) {
                finalPlaylist.add(scoredVideo.video)
            }
        }

        return finalPlaylist
    }

    // =================================================
    // 4. STORAGE (V6: Jetpack DataStore - Atomic, Crash-Safe)
    // =================================================

    // V6: Serializable models for DataStore
    @Serializable
    private data class SerializableVector(
        val topics: Map<String, Double> = emptyMap(),
        val duration: Double = 0.5,
        val pacing: Double = 0.5,
        val complexity: Double = 0.5,
        val isLive: Double = 0.0
    )
    
    @Serializable
    private data class SerializableBrain(
        val schemaVersion: Int = SCHEMA_VERSION,
        val morning: SerializableVector = SerializableVector(),
        val afternoon: SerializableVector = SerializableVector(),
        val evening: SerializableVector = SerializableVector(),
        val night: SerializableVector = SerializableVector(),
        val global: SerializableVector = SerializableVector(),
        val channelScores: Map<String, Double> = emptyMap(),
        val interactions: Int = 0,
        val consecutiveSkips: Int = 0,
        val blockedTopics: Set<String> = emptySet(),
        val blockedChannels: Set<String> = emptySet(),
        val preferredTopics: Set<String> = emptySet(),
        val hasCompletedOnboarding: Boolean = false,
        val lastPersona: String? = null,
        val personaStability: Int = 0
    )
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    // V6: DataStore Serializer
    private object BrainSerializer : Serializer<SerializableBrain> {
        override val defaultValue: SerializableBrain = SerializableBrain()
        
        override suspend fun readFrom(input: InputStream): SerializableBrain {
            return try {
                val text = input.bufferedReader().readText()
                if (text.isBlank()) defaultValue
                else Json { ignoreUnknownKeys = true }.decodeFromString<SerializableBrain>(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read brain from DataStore", e)
                defaultValue
            }
        }
        
        override suspend fun writeTo(t: SerializableBrain, output: OutputStream) {
            output.write(
                Json { encodeDefaults = true }.encodeToString(t).toByteArray()
            )
        }
    }
    
    // DataStore extension property
    private val Context.brainDataStore: DataStore<SerializableBrain> by dataStore(
        fileName = "flow_neuro_brain.json",
        serializer = BrainSerializer
    )
    
    // Conversion helpers
    private fun ContentVector.toSerializable() = SerializableVector(
        topics = topics, duration = duration, pacing = pacing,
        complexity = complexity, isLive = isLive
    )
    
    private fun SerializableVector.toContentVector() = ContentVector(
        topics = topics, duration = duration, pacing = pacing,
        complexity = complexity, isLive = isLive
    )
    
    private fun UserBrain.toSerializable() = SerializableBrain(
        schemaVersion = SCHEMA_VERSION,
        morning = morningVector.toSerializable(),
        afternoon = afternoonVector.toSerializable(),
        evening = eveningVector.toSerializable(),
        night = nightVector.toSerializable(),
        global = globalVector.toSerializable(),
        channelScores = channelScores,
        interactions = totalInteractions,
        consecutiveSkips = consecutiveSkips,
        blockedTopics = blockedTopics,
        blockedChannels = blockedChannels,
        preferredTopics = preferredTopics,
        hasCompletedOnboarding = hasCompletedOnboarding,
        lastPersona = lastPersona,
        personaStability = personaStability
    )
    
    private fun SerializableBrain.toUserBrain() = UserBrain(
        morningVector = morning.toContentVector(),
        afternoonVector = afternoon.toContentVector(),
        eveningVector = evening.toContentVector(),
        nightVector = night.toContentVector(),
        globalVector = global.toContentVector(),
        channelScores = channelScores,
        totalInteractions = interactions,
        consecutiveSkips = consecutiveSkips,
        blockedTopics = blockedTopics,
        blockedChannels = blockedChannels,
        preferredTopics = preferredTopics,
        hasCompletedOnboarding = hasCompletedOnboarding,
        lastPersona = lastPersona,
        personaStability = personaStability,
        schemaVersion = schemaVersion
    )
    
    /**
     * V6: Save brain to DataStore (atomic write, crash-safe).
     */
    private suspend fun saveBrainToDataStore(context: Context) = withContext(Dispatchers.IO) {
        try {
            context.brainDataStore.updateData { currentUserBrain.toSerializable() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save brain to DataStore", e)
        }
    }
    
    /**
     * V6: Load brain from DataStore.
     * Returns true if data was loaded successfully, false if DataStore is empty/new.
     */
    private suspend fun loadBrainFromDataStore(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = context.brainDataStore.data.first()
            // Check if this is actually saved data (not just defaults)
            if (data.interactions > 0 || data.hasCompletedOnboarding || data.preferredTopics.isNotEmpty()) {
                currentUserBrain = data.toUserBrain()
                Log.i(TAG, "Loaded brain from DataStore (v${data.schemaVersion}, ${data.interactions} interactions)")
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load brain from DataStore", e)
            return@withContext false
        }
    }

    /**
     * V6: Legacy JSON migration - loads old brain format and converts to new model.
     * This preserves all user data from V3/V4/V5 brain files.
     */
    private suspend fun loadLegacyBrain(context: Context) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, BRAIN_FILENAME)
            if (!file.exists()) return@withContext
            
            Log.i(TAG, "Found legacy brain file, migrating...")
            val jsonObj = JSONObject(file.readText())
            
            val scoresMap = mutableMapOf<String, Double>()
            val scoresJson = jsonObj.optJSONObject("channelScores")
            scoresJson?.keys()?.forEach { key ->
                scoresMap[key] = scoresJson.getDouble(key)
            }

            // --- MIGRATION LOGIC ---
            val hasOldData = jsonObj.has("longTerm") && !jsonObj.has("global")
            
            val globalVec = if (hasOldData) {
                Log.i(TAG, "Migrating Legacy V3 Brain...")
                legacyJsonToVector(jsonObj.getJSONObject("longTerm"))
            } else {
                legacyJsonToVector(jsonObj.optJSONObject("global") ?: JSONObject())
            }
            
            // Load blocked topics
            val blockedTopicsSet = mutableSetOf<String>()
            val blockedTopicsJson = jsonObj.optJSONArray("blockedTopics")
            if (blockedTopicsJson != null) {
                for (i in 0 until blockedTopicsJson.length()) {
                    blockedTopicsSet.add(blockedTopicsJson.getString(i))
                }
            }
            
            // Load blocked channels
            val blockedChannelsSet = mutableSetOf<String>()
            val blockedChannelsJson = jsonObj.optJSONArray("blockedChannels")
            if (blockedChannelsJson != null) {
                for (i in 0 until blockedChannelsJson.length()) {
                    blockedChannelsSet.add(blockedChannelsJson.getString(i))
                }
            }
            
            // Load preferred topics
            val preferredTopicsSet = mutableSetOf<String>()
            val preferredTopicsJson = jsonObj.optJSONArray("preferredTopics")
            if (preferredTopicsJson != null) {
                for (i in 0 until preferredTopicsJson.length()) {
                    preferredTopicsSet.add(preferredTopicsJson.getString(i))
                }
            }
            
            val hasCompletedOnboarding = jsonObj.optBoolean("hasCompletedOnboarding", false)

            currentUserBrain = UserBrain(
                morningVector = legacyJsonToVector(jsonObj.optJSONObject("morning") ?: JSONObject()),
                afternoonVector = legacyJsonToVector(jsonObj.optJSONObject("afternoon") ?: JSONObject()),
                eveningVector = legacyJsonToVector(jsonObj.optJSONObject("evening") ?: JSONObject()),
                nightVector = legacyJsonToVector(jsonObj.optJSONObject("night") ?: JSONObject()),
                globalVector = globalVec, 
                channelScores = scoresMap,
                totalInteractions = jsonObj.optInt("interactions", 0),
                consecutiveSkips = jsonObj.optInt("consecutiveSkips", 0),
                blockedTopics = blockedTopicsSet,
                blockedChannels = blockedChannelsSet,
                preferredTopics = preferredTopicsSet,
                hasCompletedOnboarding = hasCompletedOnboarding
            )
            
            Log.i(TAG, "Legacy brain migrated successfully (${currentUserBrain.totalInteractions} interactions)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load legacy brain", e)
        }
    }

    /**
     * Parse a ContentVector from legacy JSON format (V3-V5).
     * V6: Now reads complexity and isLive if present, defaults gracefully.
     */
    private fun legacyJsonToVector(jsonObj: JSONObject): ContentVector {
        val duration = jsonObj.optDouble("duration", 0.5)
        val pacing = jsonObj.optDouble("pacing", 0.5)
        val complexity = jsonObj.optDouble("complexity", 0.5)
        val isLive = jsonObj.optDouble("isLive", 0.0)
        
        val topicsMap = mutableMapOf<String, Double>()
        val topicsObj = jsonObj.optJSONObject("topics")
        topicsObj?.keys()?.forEach { key ->
            topicsMap[key] = topicsObj.getDouble(key)
        }
        
        return ContentVector(
            topics = topicsMap,
            duration = duration,
            pacing = pacing,
            complexity = complexity,
            isLive = isLive
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

        val rawPersona = when {
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
        
        // V6: Persona Hysteresis - require 3+ consistent evaluations before changing
        // Prevents the persona from flipping between states due to minor fluctuations
        val lastPersona = brain.lastPersona?.let { name -> 
            FlowPersona.entries.find { it.name == name } 
        }
        
        return if (lastPersona != null && rawPersona != lastPersona && brain.personaStability < 3) {
            lastPersona // Keep the old persona until signal is consistent
        } else {
            rawPersona
        }
    }
    
    /**
     * V6: Update persona stability tracking.
     * Call this after getPersona() to track how stable the evaluation is.
     */
    suspend fun updatePersonaTracking(context: Context, evaluatedPersona: FlowPersona) {
        brainMutex.withLock {
            val lastPersona = currentUserBrain.lastPersona
            val newStability = if (evaluatedPersona.name == lastPersona) {
                (currentUserBrain.personaStability + 1).coerceAtMost(10)
            } else {
                1 // Reset counter for new persona
            }
            
            currentUserBrain = currentUserBrain.copy(
                lastPersona = evaluatedPersona.name,
                personaStability = newStability
            )
            scheduleDebouncedSave(context)
        }
    }

    // =================================================
    // 6. ANTI-GRAVITY EXPLORATION ENGINE
    // =================================================

    /**
     * Macro categories for exploration.
     * V5: Now dynamically derived from TOPIC_CATEGORIES for consistency.
     */
    private val MACRO_CATEGORIES: List<String> by lazy {
        TOPIC_CATEGORIES.flatMap { category ->
            listOf(category.name.replace(Regex("[^a-zA-Z ]"), "").trim()) + 
            category.topics.take(3)
        }.distinct()
    }

    /**
     * Finds a macro category the user has low or zero interaction with.
     * This breaks the echo chamber by deliberately exploring new areas.
     * V6: Now uses normalizeLemma for consistent matching with stored topic keys.
     */
    private fun getExplorationQuery(brain: UserBrain): String? {
        // Find a category user has minimal interaction with
        return MACRO_CATEGORIES.shuffled().firstOrNull { category ->
            val score = brain.globalVector.topics[normalizeLemma(category)] ?: 0.0
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
        
        // YouTube-specific fluff
        "video", "official", "channel", "review", "reaction",
        "full", "episode", "part", "new", "latest", "update", "updates",
        "hdr", "uhd", "fps", "live", "stream", "streaming", "watch",
        "subscribe", "like", "comment", "share", "click", "link",
        "description", "below", "check", "dont", "miss", "must", "now",
        
        // Quality indicators (don't indicate topic interest)
        "1080p", "720p", "480p", "360p", "240p", "144p",
        
        // Common YouTube title patterns
        "reupload", "reup", "reuploaded", "compilation", "montage"
    )
}