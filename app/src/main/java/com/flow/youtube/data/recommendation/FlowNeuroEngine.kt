/*
 * Copyright (C) 2025 Flow | A-EDev
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
import java.util.Calendar
import kotlin.math.*

/**
 * 🧠 Flow Neuro Engine (V8 — "The Rabbit Hole")
 *
 * Client-side hybrid recommendation: Vector Space Model + Heuristic Rules.
 *
 * V8 Changes over V7:
 * - Added: Independent pacing dimension derived from title/metadata keywords
 *   (no longer redundant with duration)
 * - Added: Local IDF weighting — common words like "gameplay" get reduced
 *   weight over time so niche words drive recommendations
 * - Added: Description timestamp/chapter detection for complexity heuristic
 * - Added: Title similarity now uses lemma normalization (prevents cluster spam
 *   from inflected forms like "Playing"/"Play"/"Played")
 * - Added: Onboarding warmup — preferred topics get decaying boost over first
 *   50 interactions so organic learning gradually takes over
 * - Added: Query temporal jitter to prevent search result stagnation
 * - Added: Mild session affinity boost (sustained interest detection, NOT
 *   aggressive flooding)
 * - Added: Snowball candidate generation API for repository layer
 *   (related videos graph traversal)
 * - Improved: Feature cache invalidation when IDF weights shift
 * - Improved: Pacing now factored into cosine similarity scalar blend
 * - Fixed: All V7 fixes carried forward (negative feedback floor,
 *   channel pruning, persona auto-update, DataStore applicationContext)
 */
object FlowNeuroEngine {

    private const val TAG = "FlowNeuroEngine"
    private const val BRAIN_FILENAME = "user_neuro_brain.json"
    private const val SCHEMA_VERSION = 8

    private val brainMutex = Mutex()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSaveJob: Job? = null

    // V7: Feature vector cache (LRU)
    private val featureCache = object : LinkedHashMap<String, ContentVector>(
        200, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ContentVector>?
        ): Boolean = size > 150
    }

    // V7: Session tracking
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var sessionVideoCount: Int = 0
    private val sessionTopicHistory = mutableListOf<String>()

    // V8: Local IDF tracking (not persisted — rebuilt from usage)
    private val wordDocumentFrequency = mutableMapOf<String, Int>()
    private var totalDocumentsRanked: Int = 0

    // =================================================
    // TIME DECAY ENGINE
    // =================================================

    private object TimeDecay {

        fun calculateMultiplier(dateText: String, isLive: Boolean): Double {
            val text = dateText.lowercase()
            if (isLive) return 1.15

            return when {
                text.contains("second") || text.contains("minute") ||
                    text.contains("hour") -> 1.15
                text.contains("day") -> 1.12
                text.contains("week") -> 1.08
                text.contains("month") -> {
                    val months = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                    (1.0 / (1.0 + 0.08 * months)).coerceAtLeast(0.75)
                }
                text.contains("year") -> {
                    val years = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                    1.0 / (1.0 + (0.35 * years))
                }
                else -> 0.85
            }
        }
    }

    // =================================================
    // LEMMA DICTIONARY
    // =================================================

    private val LEMMA_MAP = mapOf(
        // Gaming
        "gaming" to "game", "games" to "game", "gamer" to "game",
        "gamers" to "game", "gameplay" to "game", "gamed" to "game",
        // Coding / Programming
        "coding" to "code", "coder" to "code", "coders" to "code",
        "codes" to "code", "coded" to "code",
        "programming" to "program", "programmer" to "program",
        "programmers" to "program", "programs" to "program",
        "programmed" to "program",
        // Cooking
        "cooking" to "cook", "cooked" to "cook", "cooks" to "cook",
        "cooker" to "cook",
        // Music
        "songs" to "song", "singing" to "sing", "singer" to "sing",
        "singers" to "sing", "musics" to "music", "musical" to "music",
        "musician" to "music", "musicians" to "music",
        // Technology
        "technologies" to "technology", "technological" to "technology",
        "computers" to "computer", "computing" to "computer",
        "computed" to "computer",
        // Art
        "drawing" to "draw", "drawings" to "draw", "drawn" to "draw",
        "painting" to "paint", "paintings" to "paint",
        "painted" to "paint", "painter" to "paint",
        "designing" to "design", "designs" to "design",
        "designer" to "design", "designed" to "design",
        "animating" to "animation", "animated" to "animation",
        "animations" to "animation", "animator" to "animation",
        // Fitness
        "workouts" to "workout", "exercising" to "exercise",
        "exercises" to "exercise", "exercised" to "exercise",
        "running" to "run", "runner" to "run", "runners" to "run",
        "training" to "train", "trained" to "train",
        "trainer" to "train", "trainers" to "train",
        // Education
        "learning" to "learn", "learned" to "learn",
        "learner" to "learn", "learners" to "learn",
        "teaching" to "teach", "teacher" to "teach",
        "teachers" to "teach", "taught" to "teach",
        "studying" to "study", "studies" to "study",
        "studied" to "study", "tutorials" to "tutorial",
        // Common
        "playing" to "play", "played" to "play", "player" to "play",
        "players" to "play",
        "building" to "build", "builder" to "build",
        "builders" to "build", "builds" to "build", "built" to "build",
        "making" to "make", "maker" to "make", "makers" to "make",
        "makes" to "make", "made" to "make",
        "reviewing" to "review", "reviewed" to "review",
        "reviews" to "review", "reviewer" to "review",
        "testing" to "test", "tested" to "test", "tests" to "test",
        "tester" to "test",
        "streaming" to "stream", "streamed" to "stream",
        "streams" to "stream", "streamer" to "stream",
        "editing" to "edit", "edited" to "edit", "edits" to "edit",
        "editor" to "edit",
        "filming" to "film", "filmed" to "film", "films" to "film",
        "filmmaker" to "film",
        "traveling" to "travel", "travelled" to "travel",
        "travels" to "travel", "traveler" to "travel",
        "vlogging" to "vlog", "vlogs" to "vlog", "vlogger" to "vlog",
        "vloggers" to "vlog",
        "reacting" to "react", "reacted" to "react",
        "reacts" to "react", "reactions" to "reaction",
        // Science
        "experiments" to "experiment", "experimenting" to "experiment",
        "experimental" to "experiment",
        "sciences" to "science", "scientific" to "science",
        "scientist" to "science",
        "engineering" to "engineer", "engineered" to "engineer",
        "engineers" to "engineer",
        "inventions" to "invention", "inventing" to "invention",
        "invented" to "invention",
        // Nature
        "animals" to "animal", "plants" to "plant",
        "planting" to "plant",
        // Lifestyle
        "recipes" to "recipe", "baking" to "bake", "baked" to "bake",
        "baker" to "bake",
        "gardening" to "garden", "gardens" to "garden",
        "photographing" to "photography",
        "photographs" to "photography",
        "photographer" to "photography",
        // Plurals / common suffixes
        "videos" to "video", "channels" to "channel",
        "episodes" to "episode",
        "movies" to "movie", "documentaries" to "documentary",
        "podcasts" to "podcast", "interviews" to "interview",
        "challenges" to "challenge", "compilations" to "compilation"
    )

    private fun normalizeLemma(word: String): String =
        LEMMA_MAP[word.lowercase()] ?: word.lowercase()

    // =================================================
    // V8: TITLE SIMILARITY (Now uses lemma normalization)
    // =================================================

    private fun calculateTitleSimilarity(
        title1: String,
        title2: String
    ): Double {
        fun tokenizeForSimilarity(text: String): Set<String> {
            return text.lowercase()
                .split("\\s+".toRegex())
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { it.length > 2 && !STOP_WORDS.contains(it) }
                .map { normalizeLemma(it) }
                .toSet()
        }

        val words1 = tokenizeForSimilarity(title1)
        val words2 = tokenizeForSimilarity(title2)
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return if (union > 0) intersection.toDouble() / union else 0.0
    }

    private fun isVideoClassic(viewCount: Long): Boolean =
        viewCount >= 5_000_000L

    // =================================================
    // V8: IDF WEIGHT CALCULATION
    // =================================================

    /**
     * TF-IDF inspired weight adjustment.
     * Words that appear across many ranked videos get reduced weight,
     * ensuring niche-specific words like "Subnautica" or "Elden" drive
     * recommendations rather than generic words like "gameplay" or "part".
     *
     * During cold start (<30 documents ranked), returns base weight unchanged.
     */
    private fun calculateIdfWeight(word: String, baseWeight: Double): Double {
        if (totalDocumentsRanked < 30) return baseWeight

        val df = wordDocumentFrequency[word] ?: 0
        // Smoothed IDF: rare words ≈ 1.0, ubiquitous words ≈ 0.15
        val idf = ln(1.0 + totalDocumentsRanked.toDouble() / (df + 1.0))
        val maxIdf = ln(1.0 + totalDocumentsRanked.toDouble())
        val normalizedIdf = (idf / maxIdf).coerceIn(0.15, 1.0)

        return baseWeight * normalizedIdf
    }

    // =================================================
    // V8: PACING KEYWORD SETS
    // =================================================

    private val HIGH_PACING_WORDS = setOf(
        "compilation", "tiktok", "tiktoks", "highlights",
        "speedrun", "trailer", "shorts", "montage", "moments",
        "best of", "try not to", "memes", "funny", "fails",
        "rapid", "fast", "quick", "minute", "seconds",
        "top 10", "top 5", "ranked", "tier list", "versus"
    )

    private val LOW_PACING_WORDS = setOf(
        "podcast", "essay", "ambient", "explained", "study",
        "meditation", "sleep", "asmr", "relaxing", "calm",
        "deep dive", "analysis", "lecture", "course",
        "documentary", "interview", "conversation",
        "discussion", "breakdown", "walkthrough"
    )

    // =================================================
    // 1. DATA MODELS
    // =================================================

    data class ContentVector(
        val topics: Map<String, Double> = emptyMap(),
        val duration: Double = 0.5,
        val pacing: Double = 0.5,      // V8: Independent, derived from title keywords
        val complexity: Double = 0.5,
        val isLive: Double = 0.0
    )

    enum class TimeBucket {
        WEEKDAY_MORNING,
        WEEKDAY_AFTERNOON,
        WEEKDAY_EVENING,
        WEEKDAY_NIGHT,
        WEEKEND_MORNING,
        WEEKEND_AFTERNOON,
        WEEKEND_EVENING,
        WEEKEND_NIGHT;

        companion object {
            fun current(): TimeBucket {
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val isWeekend = dayOfWeek == Calendar.SATURDAY ||
                    dayOfWeek == Calendar.SUNDAY

                return when {
                    isWeekend && hour in 6..11 -> WEEKEND_MORNING
                    isWeekend && hour in 12..17 -> WEEKEND_AFTERNOON
                    isWeekend && hour in 18..23 -> WEEKEND_EVENING
                    isWeekend -> WEEKEND_NIGHT
                    hour in 6..11 -> WEEKDAY_MORNING
                    hour in 12..17 -> WEEKDAY_AFTERNOON
                    hour in 18..23 -> WEEKDAY_EVENING
                    else -> WEEKDAY_NIGHT
                }
            }
        }
    }

    data class UserBrain(
        val timeVectors: Map<TimeBucket, ContentVector> = TimeBucket.entries
            .associateWith { ContentVector() },
        val globalVector: ContentVector = ContentVector(),
        val channelScores: Map<String, Double> = emptyMap(),
        val topicAffinities: Map<String, Double> = emptyMap(),
        val totalInteractions: Int = 0,
        val consecutiveSkips: Int = 0,
        val blockedTopics: Set<String> = emptySet(),
        val blockedChannels: Set<String> = emptySet(),
        val preferredTopics: Set<String> = emptySet(),
        val hasCompletedOnboarding: Boolean = false,
        val lastPersona: String? = null,
        val personaStability: Int = 0,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    private var currentUserBrain: UserBrain = UserBrain()
    private var isInitialized = false

    // =================================================
    // 2. PUBLIC API
    // =================================================

    suspend fun initialize(context: Context) {
        brainMutex.withLock {
            if (isInitialized) return

            val appContext = context.applicationContext
            val loaded = loadBrainFromDataStore(appContext)
            if (!loaded) {
                loadLegacyBrain(appContext)
                saveBrainToDataStore(appContext)
                val legacyFile = File(appContext.filesDir, BRAIN_FILENAME)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                    Log.i(TAG, "Migrated legacy brain to DataStore")
                }
            }
            resetSession()
            isInitialized = true
        }
    }

    suspend fun getBrainSnapshot(): UserBrain =
        brainMutex.withLock { currentUserBrain }

    suspend fun resetBrain(context: Context) {
        brainMutex.withLock {
            currentUserBrain = UserBrain()
            featureCache.clear()
            wordDocumentFrequency.clear()
            totalDocumentsRanked = 0
            resetSession()
            saveBrainToDataStore(context.applicationContext)
        }
    }

    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
        sessionVideoCount = 0
        synchronized(sessionTopicHistory) {
            sessionTopicHistory.clear()
        }
    }

    fun getSessionDurationMinutes(): Long =
        (System.currentTimeMillis() - sessionStartTime) / 60_000L

    private fun scheduleDebouncedSave(context: Context) {
        pendingSaveJob?.cancel()
        pendingSaveJob = saveScope.launch {
            delay(5000L)
            brainMutex.withLock {
                saveBrainToDataStore(context.applicationContext)
            }
        }
    }

    // =================================================
    // BLOCKED TOPICS & CHANNELS API
    // =================================================

    suspend fun getBlockedTopics(): Set<String> =
        brainMutex.withLock { currentUserBrain.blockedTopics }

    suspend fun addBlockedTopic(context: Context, topic: String) {
        val normalized = topic.trim().lowercase()
        if (normalized.isBlank()) return
        brainMutex.withLock {
            // V8 FIX: Also remove from global vector and time vectors
            val lemma = normalizeLemma(normalized)

            val scrubbed = scrubTopicFromVector(
                currentUserBrain.globalVector, lemma, normalized
            )
            val scrubbedTimeVectors = currentUserBrain.timeVectors
                .mapValues { (_, vector) ->
                    scrubTopicFromVector(vector, lemma, normalized)
                }

            // Also remove from preferred topics if present
            val cleanedPreferred = currentUserBrain.preferredTopics
                .filter { it.lowercase() != normalized }
                .toSet()

            currentUserBrain = currentUserBrain.copy(
                blockedTopics = currentUserBrain.blockedTopics + normalized,
                globalVector = scrubbed,
                timeVectors = scrubbedTimeVectors,
                preferredTopics = cleanedPreferred
            )
            saveBrainToDataStore(context.applicationContext)
        }
    }

    /**
     * Removes all topic entries whose key contains the blocked term
     * or its lemma. e.g. blocking "minecraft" also removes
     * "minecraft build", "play minecraft" etc.
     */
    private fun scrubTopicFromVector(
        vector: ContentVector,
        lemma: String,
        raw: String
    ): ContentVector {
        val cleaned = vector.topics.filter { (key, _) ->
            !key.contains(lemma) && !key.contains(raw)
        }
        return vector.copy(topics = cleaned)
    }

    suspend fun removeBlockedTopic(context: Context, topic: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedTopics = currentUserBrain.blockedTopics -
                    topic.lowercase()
            )
            saveBrainToDataStore(context.applicationContext)
        }
    }

    suspend fun getBlockedChannels(): Set<String> =
        brainMutex.withLock { currentUserBrain.blockedChannels }

    suspend fun blockChannel(context: Context, channelId: String) {
        if (channelId.isBlank()) return
        brainMutex.withLock {
            val cleanedScores = currentUserBrain.channelScores
                .toMutableMap()
            cleanedScores.remove(channelId)

            currentUserBrain = currentUserBrain.copy(
                blockedChannels = currentUserBrain.blockedChannels +
                    channelId,
                channelScores = cleanedScores
            )
            saveBrainToDataStore(context.applicationContext)
        }
    }

    suspend fun unblockChannel(context: Context, channelId: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedChannels = currentUserBrain.blockedChannels - channelId
            )
            saveBrainToDataStore(context.applicationContext)
        }
    }

    // =================================================
    // ONBOARDING & PREFERRED TOPICS
    // =================================================

    data class TopicCategory(
        val name: String,
        val icon: String,
        val topics: List<String>
    )

    val TOPIC_CATEGORIES = listOf(
        TopicCategory("🎮 Gaming", "🎮", listOf(
            "Gaming", "Minecraft", "Fortnite", "GTA", "Call of Duty",
            "Valorant", "League of Legends", "Pokemon", "Nintendo",
            "PlayStation", "Xbox", "PC Gaming", "Esports", "Speedruns",
            "Game Reviews", "Indie Games", "Retro Gaming", "Mobile Games",
            "Roblox", "Apex Legends", "FIFA"
        )),
        TopicCategory("🎵 Music", "🎵", listOf(
            "Music", "Pop Music", "Hip Hop", "R&B", "Rock", "Metal",
            "Jazz", "Classical", "Electronic", "EDM", "Lo-Fi", "K-Pop",
            "J-Pop", "Country", "Indie Music", "Music Production",
            "Guitar", "Piano", "Singing", "Music Theory", "Album Reviews",
            "Concerts", "DJ"
        )),
        TopicCategory("💻 Technology", "💻", listOf(
            "Technology", "Programming", "Coding", "Web Development",
            "App Development", "AI", "Machine Learning", "Cybersecurity",
            "Linux", "Apple", "Android", "Smartphones", "Laptops",
            "PC Building", "Tech Reviews", "Gadgets", "Software",
            "Cloud Computing", "Blockchain", "Crypto", "Startups"
        )),
        TopicCategory("🎬 Entertainment", "🎬", listOf(
            "Movies", "TV Shows", "Netflix", "Anime", "Marvel", "DC",
            "Star Wars", "Disney", "Comedy", "Stand-up Comedy", "Drama",
            "Horror", "Sci-Fi", "Documentary", "Film Analysis",
            "Movie Reviews", "Behind the Scenes", "Celebrities",
            "Award Shows", "Trailers", "Fan Theories"
        )),
        TopicCategory("📚 Education", "📚", listOf(
            "Science", "Physics", "Chemistry", "Biology", "Mathematics",
            "History", "Geography", "Psychology", "Philosophy",
            "Economics", "Finance", "Investing", "Business", "Marketing",
            "Language Learning", "English", "Spanish", "Study Tips",
            "College", "University", "Tutorials"
        )),
        TopicCategory("🏋️ Health & Fitness", "🏋️", listOf(
            "Fitness", "Workout", "Gym", "Yoga", "Running", "CrossFit",
            "Bodybuilding", "Weight Loss", "Nutrition", "Healthy Eating",
            "Mental Health", "Meditation", "Self Improvement",
            "Productivity", "Motivation", "Sports", "Basketball",
            "Football", "Soccer", "MMA", "Boxing", "Tennis", "Golf"
        )),
        TopicCategory("🍳 Lifestyle", "🍳", listOf(
            "Cooking", "Recipes", "Baking", "Food", "Restaurants",
            "Travel", "Vlogging", "Daily Vlog", "Fashion", "Style",
            "Beauty", "Skincare", "Home Decor", "Interior Design", "DIY",
            "Crafts", "Gardening", "Pets", "Dogs", "Cats", "Cars",
            "Motorcycles", "Photography"
        )),
        TopicCategory("🎨 Creative", "🎨", listOf(
            "Art", "Drawing", "Painting", "Digital Art", "Animation",
            "3D Modeling", "Graphic Design", "Video Editing", "Filmmaking",
            "Photography", "Music Production", "Writing", "Storytelling",
            "Architecture", "Fashion Design", "Crafts", "Woodworking",
            "Sculpture"
        )),
        TopicCategory("🔬 Science & Nature", "🔬", listOf(
            "Space", "Astronomy", "NASA", "Physics", "Nature", "Animals",
            "Wildlife", "Ocean", "Marine Life", "Environment", "Climate",
            "Geology", "Paleontology", "Dinosaurs", "Engineering",
            "Inventions", "Experiments"
        )),
        TopicCategory("📰 News & Current Events", "📰", listOf(
            "News", "Politics", "World News", "Tech News", "Sports News",
            "Entertainment News", "Business News", "Analysis",
            "Commentary", "Podcasts", "Interviews", "Debates",
            "Current Events"
        ))
    )

    suspend fun needsOnboarding(): Boolean = brainMutex.withLock {
        !currentUserBrain.hasCompletedOnboarding &&
            currentUserBrain.totalInteractions < 5 &&
            currentUserBrain.preferredTopics.isEmpty()
    }

    suspend fun hasCompletedOnboarding(): Boolean =
        brainMutex.withLock { currentUserBrain.hasCompletedOnboarding }

    suspend fun getPreferredTopics(): Set<String> =
        brainMutex.withLock { currentUserBrain.preferredTopics }

    suspend fun setPreferredTopics(
        context: Context,
        topics: Set<String>
    ) {
        brainMutex.withLock {
            val newTopics = currentUserBrain.globalVector.topics.toMutableMap()
            topics.forEach { topic ->
                newTopics[normalizeLemma(topic)] = 0.5
            }
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = topics,
                globalVector = currentUserBrain.globalVector.copy(
                    topics = newTopics
                )
            )
            saveBrainToDataStore(context.applicationContext)
        }
    }

    suspend fun addPreferredTopic(context: Context, topic: String) {
        val normalized = topic.trim()
        if (normalized.isBlank()) return
        brainMutex.withLock {
            val newTopics = currentUserBrain.globalVector.topics.toMutableMap()
            newTopics[normalizeLemma(normalized)] = 0.5
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = currentUserBrain.preferredTopics + normalized,
                globalVector = currentUserBrain.globalVector.copy(
                    topics = newTopics
                )
            )
            saveBrainToDataStore(context.applicationContext)
        }
    }

    suspend fun removePreferredTopic(context: Context, topic: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = currentUserBrain.preferredTopics - topic
            )
            saveBrainToDataStore(context.applicationContext)
        }
    }

    /**
     * V8: Onboarding seeds with differentiated weights.
     * First 3 selected topics get higher weight (user picked them first =
     * stronger signal). Also builds initial topic affinity matrix.
     */
    suspend fun completeOnboarding(
        context: Context,
        selectedTopics: Set<String>
    ) {
        brainMutex.withLock {
            val topicList = selectedTopics.toList()
            val newTopics = mutableMapOf<String, Double>()

            topicList.forEachIndexed { index, topic ->
                val weight = when {
                    index < 3 -> 0.75
                    index < 6 -> 0.60
                    else -> 0.45
                }
                newTopics[normalizeLemma(topic)] = weight
            }

            // Build initial topic affinities from co-selected topics
            val affinities = mutableMapOf<String, Double>()
            val normalizedList = topicList.map { normalizeLemma(it) }
            for (i in normalizedList.indices) {
                for (j in i + 1 until normalizedList.size) {
                    val key = makeAffinityKey(
                        normalizedList[i], normalizedList[j]
                    )
                    affinities[key] = 0.3
                }
            }

            currentUserBrain = currentUserBrain.copy(
                preferredTopics = selectedTopics,
                globalVector = currentUserBrain.globalVector.copy(
                    topics = newTopics
                ),
                topicAffinities = affinities,
                hasCompletedOnboarding = true
            )
            saveBrainToDataStore(context.applicationContext)
            Log.i(TAG, "Onboarding: ${selectedTopics.size} topics")
        }
    }

    private fun makeAffinityKey(t1: String, t2: String): String {
        return if (t1 < t2) "$t1|$t2" else "$t2|$t1"
    }

    /**
     * "Not Interested" — strong negative signal.
     */
    suspend fun markNotInterested(context: Context, video: Video) {
        val videoVector = getOrExtractFeatures(video)

        brainMutex.withLock {
            val newGlobal = adjustVector(
                currentUserBrain.globalVector, videoVector, -0.35
            )

            val newChannelScores = currentUserBrain.channelScores.toMutableMap()
            newChannelScores[video.channelId] = 0.05

            val bucket = TimeBucket.current()
            val currentBucketVec = currentUserBrain.timeVectors[bucket]
                ?: ContentVector()
            val newBucketVec = adjustVector(
                currentBucketVec, videoVector, -0.25
            )

            // V8 FIX: not-interested is a stronger signal than a skip;
            val newSkips = (currentUserBrain.consecutiveSkips + 3)
                .coerceAtMost(30)

            currentUserBrain = currentUserBrain.copy(
                globalVector = newGlobal,
                timeVectors = currentUserBrain.timeVectors +
                    (bucket to newBucketVec),
                channelScores = newChannelScores,
                totalInteractions = currentUserBrain.totalInteractions + 1,
                consecutiveSkips = newSkips
            )
            saveBrainToDataStore(context.applicationContext)
        }
    }

    // =================================================
    // DISCOVERY QUERY GENERATION
    // =================================================

    /**
     * V8: Added query temporal jitter to prevent YouTube returning
     * identical results for the same query day after day.
     * Added affinity-based queries for co-occurring topic combinations.
     */
    suspend fun generateDiscoveryQueries(): List<String> =
        brainMutex.withLock {
            val interests = currentUserBrain.globalVector.topics
            val queries = mutableListOf<String>()

            val sortedInterests = interests.entries
                .sortedByDescending { it.value }
                .take(6)
                .map { it.key }

            // Direct interest (top 2)
            queries.addAll(sortedInterests.take(2))

            // Bridge queries with V8 temporal jitter
            val timeJitter = listOf(
                "", "2024", "2025", "new", "best", "top"
            ).random()

            if (sortedInterests.size >= 3) {
                queries.add(
                    "${sortedInterests[0]} ${sortedInterests[1]} $timeJitter"
                        .trim()
                )
                queries.add(
                    "${sortedInterests[0]} ${sortedInterests[2]}"
                )
                queries.add(
                    "${sortedInterests[1]} ${sortedInterests[2]}"
                )
            } else if (sortedInterests.size >= 2) {
                queries.add(
                    "${sortedInterests[0]} ${sortedInterests[1]} $timeJitter"
                        .trim()
                )
            }

            // Affinity-based queries
            currentUserBrain.topicAffinities.entries
                .sortedByDescending { it.value }
                .take(2)
                .forEach { (key, _) ->
                    val parts = key.split("|")
                    if (parts.size == 2) {
                        queries.add("${parts[0]} ${parts[1]}")
                    }
                }

            // Time context obsession
            val bucket = TimeBucket.current()
            val currentBucketVec = currentUserBrain.timeVectors[bucket]
                ?: ContentVector()
            currentBucketVec.topics.maxByOrNull { it.value }?.key
                ?.let { queries.add(it) }

            // Persona suffix
            val persona = getPersona(currentUserBrain)
            val suffix = when (persona) {
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

            // Exploration queries (weakest macro categories)
            getExplorationQueries(currentUserBrain).forEach {
                queries.add(it)
            }

            // Fallback
            if (queries.isEmpty()) {
                val preferred = currentUserBrain.preferredTopics.toList()
                if (preferred.isNotEmpty()) {
                    return@withLock preferred.shuffled().take(5)
                }
                return@withLock listOf(
                    "Trending", "Music", "Gaming",
                    "Technology", "Science"
                )
            }

            val blocked = currentUserBrain.blockedTopics
            return@withLock queries
                .distinct()
                .filter { query ->
                    !blocked.any { blockedTerm ->
                        query.lowercase().contains(blockedTerm)
                    }
                }
                .shuffled()
        }

    /**
     * V8: Provides seed video IDs for snowball candidate generation.
     * The repository layer should fetch related videos for these IDs
     * and add them to the candidate pool before calling rank().
     *
     * Returns up to [count] video IDs from recent positive interactions
     * that would make good seeds for related video expansion.
     */
    fun getSnowballSeeds(
        recentlyWatched: List<Video>,
        count: Int = 3
    ): List<String> {
        return recentlyWatched
            .take(count)
            .map { it.id }
    }

    // =================================================
    // MAIN RANKING FUNCTION
    // =================================================

    /**
     * V8 Changes:
     * - IDF-weighted feature extraction (common words penalized)
     * - Onboarding warmup boost (preferred topics decay over 50 interactions)
     * - Mild session affinity (sustained interest, not flooding)
     * - Independent pacing factored into scoring
     * - Feature cache invalidation when IDF weights shift
     * - Engagement rate signal from like/view ratio
     */
    suspend fun rank(
        candidates: List<Video>,
        userSubs: Set<String>
    ): List<Video> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Session staleness auto-reset — prevents morning context
        // contaminating an evening session after the app was idle.
        val sessionAgeMinutes = getSessionDurationMinutes()
        if (sessionAgeMinutes > 120 ||
            (sessionAgeMinutes > 30 && sessionVideoCount == 0)
        ) {
            resetSession()
        }

        val brain = brainMutex.withLock { currentUserBrain }
        val random = java.util.Random()

        // Pre-filter blocked content
        val filtered = candidates.filter { video ->
            if (brain.blockedChannels.contains(video.channelId)) {
                return@filter false
            }
            val titleLower = video.title.lowercase()
            val channelLower = video.channelName.lowercase()
            !brain.blockedTopics.any { blocked ->
                titleLower.contains(blocked) ||
                    channelLower.contains(blocked)
            }
        }

        if (filtered.isEmpty()) return@withContext emptyList()

        // V8: Update IDF counters before scoring
        filtered.forEach { video ->
            val vector = getOrExtractFeatures(video)
            vector.topics.keys.forEach { word ->
                wordDocumentFrequency[word] =
                    (wordDocumentFrequency[word] ?: 0) + 1
            }
            totalDocumentsRanked++
        }

        // V8: Prevent unbounded IDF growth (logarithmic forgetting)
        if (totalDocumentsRanked > 10000) {
            wordDocumentFrequency.entries.forEach { entry ->
                entry.setValue(entry.value / 2)
            }
            totalDocumentsRanked /= 2
        }

        // V8: Invalidate feature cache periodically as IDF weights shift
        if (totalDocumentsRanked % 500 == 0 && totalDocumentsRanked > 0) {
            synchronized(featureCache) {
                featureCache.clear()
            }
        }

        // Time context
        val bucket = TimeBucket.current()
        val timeContextVector = brain.timeVectors[bucket] ?: ContentVector()

        // Dynamic temperature (boredom detection)
        val boredomFactor = (brain.consecutiveSkips / 20.0)
            .coerceIn(0.0, 0.5)
        val wPersonality = 0.4 - (boredomFactor * 0.5)
        val wContext = 0.4 - (boredomFactor * 0.5)
        val wNovelty = 0.2 + boredomFactor

        // V8: Onboarding warmup — preferred topics get decaying boost
        val onboardingWarmup = if (brain.totalInteractions < 50) {
            1.0 - (brain.totalInteractions / 50.0) * 0.5
        } else 0.5

        // Session fatigue from internal tracking
        val sessionTopics = synchronized(sessionTopicHistory) {
            sessionTopicHistory.toList()
        }

        val scored = filtered.map { video ->
            val videoVector = getOrExtractFeatures(video)

            val personalityScore = calculateCosineSimilarity(
                brain.globalVector, videoVector
            )
            val contextScore = calculateCosineSimilarity(
                timeContextVector, videoVector
            )
            val noveltyScore = 1.0 - personalityScore

            var totalScore = (personalityScore * wPersonality) +
                (contextScore * wContext) +
                (noveltyScore * wNovelty)

            // V8: Onboarding warmup boost
            if (brain.totalInteractions < 50) {
                val videoTopics = videoVector.topics.keys
                val hasPreferred = brain.preferredTopics.any { pref ->
                    videoTopics.contains(normalizeLemma(pref))
                }
                if (hasPreferred) {
                    totalScore += onboardingWarmup * 0.15
                }
            }

            // Topic affinity boost
            val videoTopics = videoVector.topics.keys.toList()
            var affinityBoost = 0.0
            for (i in videoTopics.indices) {
                for (j in i + 1 until videoTopics.size) {
                    val key = makeAffinityKey(videoTopics[i], videoTopics[j])
                    val affinity = brain.topicAffinities[key] ?: 0.0
                    affinityBoost += affinity * 0.05
                }
            }
            totalScore += affinityBoost.coerceAtMost(0.15)

            // Subscription boost
            if (userSubs.contains(video.channelId)) {
                totalScore += 0.15
            }

            // Serendipity
            if (noveltyScore > 0.6 && contextScore > 0.5) {
                totalScore += 0.10
            }

            // Cold-start popularity
            if (brain.totalInteractions < 30 && video.viewCount > 0) {
                val popularityBoost =
                    log10(1.0 + video.viewCount.toDouble()) / 10.0 * 0.05
                totalScore += popularityBoost
            }

            // V8: Engagement rate signal
            if (video.viewCount > 1000 && video.likeCount > 0) {
                val engagementRate = video.likeCount.toDouble() /
                    video.viewCount.toDouble()
                val engagementBoost = (engagementRate / 0.05)
                    .coerceIn(0.0, 1.0) * 0.05
                totalScore += engagementBoost
            }

            // Time decay
            val ageMultiplier = TimeDecay.calculateMultiplier(
                video.uploadDate, video.isLive
            )
            val isClassic = isVideoClassic(video.viewCount)
            val isSub = userSubs.contains(video.channelId)
            val finalAgeFactor = when {
                isClassic || isSub -> (ageMultiplier + 1.0) / 2.0
                else -> ageMultiplier
            }
            totalScore *= finalAgeFactor

            // Curiosity gap
            val isTopicSafe = personalityScore > 0.65
            val complexityDiff = abs(
                brain.globalVector.complexity - videoVector.complexity
            )
            if (isTopicSafe && complexityDiff > 0.35) {
                totalScore += 0.10
            }

            // Channel boredom
            val channelClickRate =
                brain.channelScores[video.channelId] ?: 0.5
            if (brain.channelScores.containsKey(video.channelId) &&
                channelClickRate < 0.05
            ) {
                totalScore *= 0.5
            }

            // Session fatigue (internal tracking)
            val videoPrimaryTopic = videoVector.topics
                .maxByOrNull { it.value }?.key ?: ""
            val topicSessionCount = sessionTopics
                .count { it == videoPrimaryTopic }
            val fatigueMultiplier = when {
                videoPrimaryTopic.isEmpty() -> 1.0
                topicSessionCount >= 5 -> 0.3
                topicSessionCount >= 3 -> 0.5
                topicSessionCount >= 1 -> 0.8
                else -> 1.0
            }
            totalScore *= fatigueMultiplier

            // V8: Mild session affinity boost (NOT aggressive flooding)
            // Only boosts topics the user has shown SUSTAINED interest in
            // during this session (3+ videos on same topic)
            if (sessionTopics.isNotEmpty() && videoPrimaryTopic.isNotEmpty()) {
                val recentTopics = sessionTopics.takeLast(5)
                val recentCount = recentTopics.count { it == videoPrimaryTopic }
                val sessionAffinityBoost = when {
                    recentCount >= 3 -> 0.08
                    recentCount >= 2 -> 0.04
                    else -> 0.0
                }
                totalScore += sessionAffinityBoost
            }

            // Binge detection
            if (sessionVideoCount > 20) {
                val bingeNoveltyBoost = noveltyScore * 0.15
                totalScore += bingeNoveltyBoost
            }

            // Jitter
            val jitter = if (brain.totalInteractions < 50) {
                random.nextDouble() * 0.2
            } else {
                random.nextDouble() * 0.02
            }

            ScoredVideo(video, totalScore + jitter, videoVector)
        }.toMutableList()

        return@withContext applySmartDiversity(scored)
    }

    // =================================================
    // LEARNING FUNCTION
    // =================================================

    /**
     * V8: All V7 learning features plus IDF cache invalidation awareness.
     */
    suspend fun onVideoInteraction(
        context: Context,
        video: Video,
        interactionType: InteractionType,
        percentWatched: Float = 0f
    ) {
        val videoVector = getOrExtractFeatures(video)

        // Watch-time weighting
        val absoluteMinutesWatched = if (
            interactionType == InteractionType.WATCHED && video.duration > 0
        ) {
            (video.duration * percentWatched / 60.0).coerceAtLeast(0.0)
        } else 0.0

        var learningRate = when (interactionType) {
            InteractionType.CLICK -> 0.10
            InteractionType.LIKED -> 0.30
            InteractionType.WATCHED -> {
                val baseWatchRate = 0.15 * percentWatched
                val timeBonus = (ln(1.0 + absoluteMinutesWatched) /
                    ln(1.0 + 60.0) * 0.08)
                baseWatchRate + timeBonus
            }
            InteractionType.SKIPPED -> -0.15
            InteractionType.DISLIKED -> -0.40
        }

        // Shorts penalty
        if (video.isShort) {
            learningRate *= 0.01
        }

        brainMutex.withLock {
            // 1. Update global vector
            val newGlobal = adjustVector(
                currentUserBrain.globalVector, videoVector, learningRate
            )

            // 2. Update time bucket
            val bucket = TimeBucket.current()
            val currentBucketVec = currentUserBrain.timeVectors[bucket]
                ?: ContentVector()
            val newBucketVec = adjustVector(
                currentBucketVec, videoVector, learningRate * 1.5
            )

            // 3. Channel score
            val currentChScore =
                currentUserBrain.channelScores[video.channelId] ?: 0.5
            val outcome = if (learningRate > 0) 1.0 else 0.0
            val newChScore = (currentChScore * 0.95) + (outcome * 0.05)
            var newChannelScores = currentUserBrain.channelScores +
                (video.channelId to newChScore)

            // Channel pruning — keep extremes, prune mediocre middle
            if (newChannelScores.size > 500) {
                val sorted = newChannelScores.entries.sortedBy { it.value }
                val keepLow = sorted.take(50)
                val keepHigh = sorted.takeLast(200)
                val keepSet = (keepLow + keepHigh).map { it.key }.toSet()
                newChannelScores = newChannelScores.filter { it.key in keepSet }
            }

            // 4. Consecutive skips
            val newSkips = when (interactionType) {
                InteractionType.CLICK, InteractionType.LIKED,
                InteractionType.WATCHED -> 0
                InteractionType.SKIPPED, InteractionType.DISLIKED ->
                    (currentUserBrain.consecutiveSkips + 1).coerceAtMost(30)
            }

            // 5. Topic co-occurrence (positive interactions only)
            var newAffinities = currentUserBrain.topicAffinities
            if (learningRate > 0) {
                val topTopics = videoVector.topics.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { it.key }
                if (topTopics.size >= 2) {
                    val mutableAffinities = newAffinities.toMutableMap()
                    for (i in topTopics.indices) {
                        for (j in i + 1 until topTopics.size) {
                            val key = makeAffinityKey(
                                topTopics[i], topTopics[j]
                            )
                            val current = mutableAffinities[key] ?: 0.0
                            mutableAffinities[key] =
                                (current + 0.02).coerceAtMost(1.0)
                        }
                    }
                    newAffinities = mutableAffinities.filter { it.value > 0.05 }
                    if (newAffinities.size > 500) {
                        newAffinities = newAffinities.entries
                            .sortedByDescending { it.value }
                            .take(300)
                            .associate { it.key to it.value }
                    }
                }
            }

            // 6. Auto-update persona tracking
            val rawPersona = getPersona(currentUserBrain)
            val lastPersonaName = currentUserBrain.lastPersona
            val newStability = if (rawPersona.name == lastPersonaName) {
                (currentUserBrain.personaStability + 1).coerceAtMost(10)
            } else 1

            currentUserBrain = currentUserBrain.copy(
                globalVector = newGlobal,
                timeVectors = currentUserBrain.timeVectors +
                    (bucket to newBucketVec),
                channelScores = newChannelScores,
                topicAffinities = newAffinities,
                totalInteractions = currentUserBrain.totalInteractions + 1,
                consecutiveSkips = newSkips,
                lastPersona = rawPersona.name,
                personaStability = newStability
            )

            scheduleDebouncedSave(context)
        }

        // Update session tracking (outside mutex — not persisted)
        val primaryTopic = videoVector.topics
            .maxByOrNull { it.value }?.key
        if (primaryTopic != null) {
            synchronized(sessionTopicHistory) {
                sessionTopicHistory.add(primaryTopic)
                while (sessionTopicHistory.size > 50) {
                    sessionTopicHistory.removeFirst()
                }
            }
        }
        sessionVideoCount++
    }

    enum class InteractionType {
        CLICK, LIKED, WATCHED, SKIPPED, DISLIKED
    }

    // =================================================
    // 3. MATH ENGINE
    // =================================================

    private data class ScoredVideo(
        val video: Video,
        var score: Double,
        val vector: ContentVector
    )

    /**
     * V8: Cached feature extraction with IDF awareness.
     */
    private fun getOrExtractFeatures(video: Video): ContentVector {
        val cacheKey = video.id
        synchronized(featureCache) {
            featureCache[cacheKey]?.let { return it }
        }
        val vector = extractFeatures(video)
        synchronized(featureCache) {
            featureCache[cacheKey] = vector
        }
        return vector
    }

    /**
     * V8: Feature extraction with:
     * - IDF-weighted keywords (common words penalized)
     * - Independent pacing from title/metadata keywords
     * - Description timestamp detection for complexity
     * - Description keyword extraction
     */
    private fun extractFeatures(video: Video): ContentVector {
        val topics = mutableMapOf<String, Double>()

        fun tokenize(text: String): List<String> {
            return text.lowercase()
                .split("\\s+".toRegex())
                .map { word -> word.trim { !it.isLetterOrDigit() } }
                .filter { it.length > 2 && !STOP_WORDS.contains(it) }
                .map { normalizeLemma(it) }
        }

        val titleWords = tokenize(video.title)
        val chWords = tokenize(video.channelName)

        // Channel keywords (IDF-weighted)
        chWords.forEach {
            topics[it] = calculateIdfWeight(it, 1.0)
        }

        // Title keywords (IDF-weighted)
        titleWords.forEach { word ->
            topics[word] = (topics.getOrDefault(word, 0.0) +
                calculateIdfWeight(word, 0.5))
        }

        // Bigrams (IDF-weighted)
        if (titleWords.size >= 2) {
            for (i in 0 until titleWords.size - 1) {
                val bigram = "${titleWords[i]} ${titleWords[i + 1]}"
                topics[bigram] = calculateIdfWeight(bigram, 0.75)
            }
        }

        // Description extraction (lower weight, capped)
        val description = video.description
        if (!description.isNullOrBlank() && description.length > 20) {
            val descWords = tokenize(description.take(200)).take(15)
            descWords.forEach { word ->
                topics[word] = (topics.getOrDefault(word, 0.0) +
                    calculateIdfWeight(word, 0.2))
            }
        }

        // Normalize vector
        val normalized = if (topics.isNotEmpty()) {
            var magnitude = 0.0
            topics.values.forEach { magnitude += it * it }
            magnitude = sqrt(magnitude)
            if (magnitude > 0) topics.mapValues { (_, v) -> v / magnitude }
            else topics
        } else topics

        // === Scalar features ===

        // Duration (logarithmic scale)
        val durationSec = when {
            video.duration > 0 -> video.duration
            video.isLive -> 3600
            else -> 300
        }
        val durationScore = (ln(1.0 + durationSec) /
            ln(1.0 + 7200.0)).coerceIn(0.0, 1.0)

        // V8: Independent pacing from title/metadata keywords
        val pacingScore = run {
            val titleLower = video.title.lowercase()

            val highCount = HIGH_PACING_WORDS.count {
                titleLower.contains(it)
            }
            val lowCount = LOW_PACING_WORDS.count {
                titleLower.contains(it)
            }

            when {
                highCount > lowCount -> (0.6 + highCount * 0.1)
                    .coerceAtMost(0.95)
                lowCount > highCount -> (0.4 - lowCount * 0.1)
                    .coerceAtLeast(0.05)
                video.isShort -> 0.85
                else -> 0.5
            }
        }

        // V8: Complexity with description timestamp detection
        val rawTitleWords = video.title.split("\\s+".toRegex())
            .filter { it.length > 1 }

        val hasChapters = run {
            val timestampPattern = Regex("""\d{1,2}:\d{2}""")
            timestampPattern.findAll(description).count() >= 3
        }

        val complexityScore = run {
            val titleLenFactor =
                (video.title.length / 80.0).coerceIn(0.0, 0.4)
            val avgWordLen = if (rawTitleWords.isNotEmpty()) {
                rawTitleWords.map { it.length }.average()
            } else 4.0
            val wordLenFactor = (avgWordLen / 8.0).coerceIn(0.0, 0.4)
            val chapterBonus = if (hasChapters) 0.2 else 0.0
            (titleLenFactor + wordLenFactor + chapterBonus)
                .coerceIn(0.0, 1.0)
        }

        return ContentVector(
            topics = normalized,
            duration = durationScore,
            pacing = pacingScore,
            complexity = complexityScore,
            isLive = if (video.isLive) 1.0 else 0.0
        )
    }

    /**
     * V8: Cosine similarity with independent pacing dimension.
     */
    private fun calculateCosineSimilarity(
        user: ContentVector,
        content: ContentVector
    ): Double {
        val (smallMap, largeMap) = if (
            user.topics.size <= content.topics.size
        ) user.topics to content.topics
        else content.topics to user.topics

        // Scalar similarity — V8: pacing is now independent and meaningful
        val durationSim = 1.0 - abs(user.duration - content.duration)
        val pacingSim = 1.0 - abs(user.pacing - content.pacing)
        val complexitySim = 1.0 - abs(user.complexity - content.complexity)
        val scalarScore = (durationSim * 0.10) +
            (pacingSim * 0.10) +
            (complexitySim * 0.10)

        if (smallMap.isEmpty()) return scalarScore

        var dotProduct = 0.0
        var hasIntersection = false

        for ((key, smallVal) in smallMap) {
            val largeVal = largeMap[key]
            if (largeVal != null) {
                dotProduct += smallVal * largeVal
                hasIntersection = true
            }
        }

        if (!hasIntersection) return scalarScore

        var magA = 0.0
        var magB = 0.0
        user.topics.values.forEach { magA += it * it }
        content.topics.values.forEach { magB += it * it }

        val topicSim = if (magA > 0 && magB > 0) {
            dotProduct / (sqrt(magA) * sqrt(magB))
        } else 0.0

        // 70% topics, 30% scalar features
        return (topicSim * 0.7) + scalarScore
    }

    /**
     * V7/V8: Fixed negative feedback with absolute floor reduction.
     */
    private fun adjustVector(
        current: ContentVector,
        target: ContentVector,
        baseRate: Double
    ): ContentVector {
        val newTopics = current.topics.toMutableMap()
        val isNegative = baseRate < 0

        target.topics.forEach { (key, targetVal) ->
            val currentVal = newTopics[key] ?: 0.0

            val delta = if (isNegative) {
                val proportional =
                    currentVal * currentVal.pow(1.5) * baseRate
                val absoluteFloor = baseRate * 0.3
                minOf(proportional, absoluteFloor)
            } else {
                val saturationPenalty = (1.0 - currentVal).pow(2)
                val effectiveRate = baseRate * saturationPenalty
                (targetVal - currentVal) * effectiveRate
            }

            newTopics[key] = (currentVal + delta).coerceIn(0.0, 1.0)
        }

        // Decay non-relevant topics on positive feedback
        val decay = if (baseRate > 0) 0.97 else 1.0
        val iterator = newTopics.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!target.topics.containsKey(entry.key)) {
                entry.setValue(entry.value * decay)
            }
            if (entry.value < 0.03) iterator.remove()
        }

        // V8 FIX: After negative feedback, if the vector has become
        // too concentrated (top topic >60% of total magnitude),
        // apply gentle compression to prevent domination.
        if (isNegative && newTopics.isNotEmpty()) {
            val totalMagnitude = newTopics.values.sum()
            val maxScore = newTopics.values.maxOrNull() ?: 0.0

            if (totalMagnitude > 0 && maxScore / totalMagnitude > 0.6) {
                val compressed = newTopics.mapValues { (_, v) ->
                    if (v > 0.5) 0.5 + (v - 0.5) * 0.7 else v
                }
                newTopics.clear()
                newTopics.putAll(compressed)
            }
        }

        // Scalar updates with corrected negative feedback
        fun updateScalar(
            currentScalar: Double,
            targetScalar: Double
        ): Double {
            return if (isNegative) {
                val proportional = currentScalar * baseRate * 0.3
                val floor = baseRate * 0.1
                currentScalar + minOf(proportional, floor)
            } else {
                val saturation = (1.0 - currentScalar).pow(2)
                currentScalar + (targetScalar - currentScalar) *
                    baseRate * saturation
            }.coerceIn(0.0, 1.0)
        }

        return current.copy(
            topics = newTopics,
            duration = updateScalar(current.duration, target.duration),
            pacing = updateScalar(current.pacing, target.pacing),
            complexity = updateScalar(
                current.complexity, target.complexity
            ),
            isLive = updateScalar(current.isLive, target.isLive)
        )
    }

    /**
     * V7/V8: Diversity re-ranking with sliding window.
     */
    private fun applySmartDiversity(
        candidates: MutableList<ScoredVideo>
    ): List<Video> {
        val finalPlaylist = mutableListOf<Video>()
        val channelWindow = mutableListOf<String>()
        val topicWindow = mutableListOf<String>()

        candidates.sortByDescending { it.score }

        // V8 FIX: Calculate how many unique strong topics exist.
        // If only 2-3 topics dominate, tighten the per-topic cap.
        val uniqueTopics = candidates
            .mapNotNull {
                it.vector.topics.maxByOrNull { e -> e.value }?.key
            }
            .distinct()
        val topicDiversity = uniqueTopics.size

        // Adaptive cap: more unique topics → more allowed per topic
        val maxPerTopic = when {
            topicDiversity <= 4 -> 2
            topicDiversity <= 7 -> 3
            else -> 3
        }

        // Reserve exploration slots when diversity is low
        val explorationSlots = when {
            topicDiversity <= 2 -> 6
            topicDiversity <= 4 -> 4
            else -> 2
        }

        // Pre-compute user's top 3 topics from all candidates
        val userTopTopics = candidates
            .flatMap { it.vector.topics.entries }
            .groupBy { it.key }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
            .toSet()

        // Phase 1: Strict diversity
        val phase1Candidates = candidates.toMutableList()
        val phase1Iterator = phase1Candidates.iterator()
        var explorationCount = 0

        while (phase1Iterator.hasNext() && finalPlaylist.size < 20) {
            val current = phase1Iterator.next()
            val primaryTopic = current.vector.topics
                .maxByOrNull { it.value }?.key ?: ""

            val channelCount = channelWindow
                .count { it == current.video.channelId }
            val topicCount = topicWindow.count { it == primaryTopic }

            val isTitleSimilar = finalPlaylist.takeLast(5)
                .any { existing ->
                    calculateTitleSimilarity(
                        current.video.title, existing.title
                    ) > 0.55
                }

            // Novel topics (not in top 3) get a slightly relaxed cap
            val isNovelTopic = primaryTopic.isNotEmpty() &&
                !userTopTopics.contains(primaryTopic)
            val effectiveTopicCap = if (isNovelTopic &&
                explorationCount < explorationSlots
            ) maxPerTopic + 1 else maxPerTopic

            if (channelCount == 0 &&
                topicCount < effectiveTopicCap &&
                !isTitleSimilar
            ) {
                finalPlaylist.add(current.video)
                channelWindow.add(current.video.channelId)
                if (primaryTopic.isNotEmpty()) {
                    topicWindow.add(primaryTopic)
                }
                if (isNovelTopic) explorationCount++
                phase1Iterator.remove()
            }
        }

        // Phase 2: Relaxed with sliding window
        phase1Candidates.sortedByDescending { it.score }
            .forEach { scored ->
                val recentChannels = finalPlaylist.takeLast(5)
                    .map { it.channelId }
                val channelSpam = recentChannels
                    .count { it == scored.video.channelId } >= 2
                val titleSimilar = finalPlaylist.takeLast(5)
                    .any { existing ->
                        calculateTitleSimilarity(
                            scored.video.title, existing.title
                        ) > 0.60
                    }
                if (!channelSpam && !titleSimilar) {
                    finalPlaylist.add(scored.video)
                }
            }

        return finalPlaylist
    }

    // =================================================
    // 4. STORAGE (DataStore)
    // =================================================

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
        val personaStability: Int = 0
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

    // V8: New filename to prevent corruption from V7 schema differences
    private val Context.brainDataStore: DataStore<SerializableBrain>
        by dataStore(
            fileName = "flow_neuro_brain_v8.json",
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
        personaStability = personaStability
    )

    private fun SerializableBrain.toUserBrain(): UserBrain {
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
            schemaVersion = schemaVersion
        )
    }

    private suspend fun saveBrainToDataStore(
        context: Context
    ) = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            appContext.brainDataStore.updateData {
                currentUserBrain.toSerializable()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save brain", e)
        }
    }

    private suspend fun loadBrainFromDataStore(
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val data = appContext.brainDataStore.data.first()
            if (data.interactions > 0 ||
                data.hasCompletedOnboarding ||
                data.preferredTopics.isNotEmpty()
            ) {
                currentUserBrain = data.toUserBrain()
                Log.i(
                    TAG,
                    "Loaded brain v${data.schemaVersion}, " +
                        "${data.interactions} interactions"
                )
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load brain", e)
            return@withContext false
        }
    }

    // =================================================
    // EXPORT / IMPORT
    // =================================================

    suspend fun exportBrainToStream(
        output: OutputStream
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            brainMutex.withLock {
                val serializable = currentUserBrain.toSerializable()
                val jsonBytes = Json { encodeDefaults = true }
                    .encodeToString(serializable).toByteArray()
                output.write(jsonBytes)
                output.flush()
            }
            Log.i(TAG, "Brain exported")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            false
        }
    }

    suspend fun importBrainFromStream(
        context: Context,
        input: InputStream
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = input.bufferedReader().readText()
            val jsonParser = Json { ignoreUnknownKeys = true }

            // First try: direct V8 format
            val imported = jsonParser
                .decodeFromString<SerializableBrain>(text)

            // Check if timeVectors actually has data
            val hasTimeData = imported.timeVectors.any { (_, v) ->
                v.topics.isNotEmpty()
            }

            val finalBrain = if (hasTimeData) {
                // V8 native format — use directly
                imported.toUserBrain()
            } else {
                // Likely V6/V7 format — parse legacy fields manually
                migrateLegacyBackup(text, imported)
            }

            brainMutex.withLock {
                currentUserBrain = finalBrain
                saveBrainToDataStore(context.applicationContext)
            }
            Log.i(
                TAG,
                "Brain imported (${finalBrain.totalInteractions} " +
                    "interactions, ${finalBrain.timeVectors.count {
                        it.value.topics.isNotEmpty()
                    }} active time buckets)"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            false
        }
    }

    /**
     * Legacy migration — reads V3-V8 brain formats.
     * Delegates to migrateLegacyBackup for unified format handling.
     */
    private suspend fun loadLegacyBrain(
        context: Context
    ) = withContext(Dispatchers.IO) {
        val legacyFile = File(context.filesDir, BRAIN_FILENAME)
        if (legacyFile.exists()) {
            try {
                Log.i(TAG, "Migrating legacy JSON brain...")
                val text = legacyFile.readText()
                val migrated = migrateLegacyBackup(
                    text,
                    SerializableBrain() 
                )
                currentUserBrain = migrated
                Log.i(
                    TAG,
                    "Legacy brain migrated " +
                        "(${migrated.totalInteractions} interactions)"
                )
                return@withContext
            } catch (e: Exception) {
                Log.e(TAG, "Legacy JSON migration failed", e)
            }
        }

        // Try V7 DataStore file
        tryMigrateFromV7DataStore(context)
    }

    /**
     * Parses a backup JSON that may use V6 field names
     * (morning/afternoon/evening/night) or V7 timeVectors with
     * string keys, and maps them to V8's 8-bucket ContentVector map.
     */
    private fun migrateLegacyBackup(
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

            val morningVec   = parseVector("morning")
            val afternoonVec = parseVector("afternoon")
            val eveningVec   = parseVector("evening")
            val nightVec     = parseVector("night")

            val hasLegacyData = listOf(
                morningVec, afternoonVec, eveningVec, nightVec
            ).any { it.topics.isNotEmpty() }

            val timeVectors: Map<TimeBucket, ContentVector> =
                if (hasLegacyData) {
                    // V6 format: 4 buckets → duplicate to weekday + weekend
                    mapOf(
                        TimeBucket.WEEKDAY_MORNING   to morningVec,
                        TimeBucket.WEEKEND_MORNING   to morningVec,
                        TimeBucket.WEEKDAY_AFTERNOON to afternoonVec,
                        TimeBucket.WEEKEND_AFTERNOON to afternoonVec,
                        TimeBucket.WEEKDAY_EVENING   to eveningVec,
                        TimeBucket.WEEKEND_EVENING   to eveningVec,
                        TimeBucket.WEEKDAY_NIGHT     to nightVec,
                        TimeBucket.WEEKEND_NIGHT     to nightVec
                    )
                } else {
                    // Try V7 format: timeVectors with string keys
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

            // Global vector
            val globalVec = if (partialParse.global.topics.isNotEmpty()) {
                partialParse.global.toContentVector()
            } else {
                parseVector("global").let {
                    if (it.topics.isEmpty()) parseVector("longTerm") else it
                }
            }

            // Channel scores
            val channelScores = mutableMapOf<String, Double>()
            val scoresObj = jsonObj.optJSONObject("channelScores")
            scoresObj?.keys()?.forEach { key ->
                channelScores[key] = scoresObj.getDouble(key)
            }

            // Topic affinities (V7+)
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
                personaStability = partialParse.personaStability
            )
        } catch (e: Exception) {
            Log.e(TAG, "Legacy backup migration failed", e)
            return partialParse.toUserBrain()
        }
    }

    /**
     * V8: Migrate from V7 DataStore format.
     * V7 used "flow_neuro_brain_v7.json" — if it exists, read it
     * and convert (pacing defaults to 0.5 for existing vectors).
     */
    private suspend fun tryMigrateFromV7DataStore(context: Context) {
        try {
            // V7 DataStore file location
            val v7File = File(
                context.applicationContext.filesDir,
                "datastore/flow_neuro_brain_v7.json"
            )
            if (!v7File.exists()) return

            Log.i(TAG, "Found V7 DataStore, migrating to V8...")
            val text = v7File.readText()
            if (text.isBlank()) return

            val v7Data = Json { ignoreUnknownKeys = true }
                .decodeFromString<SerializableBrain>(text)

            // V7 SerializableBrain is compatible — pacing will default
            // to 0.5 via ignoreUnknownKeys + default values
            if (v7Data.interactions > 0 ||
                v7Data.hasCompletedOnboarding ||
                v7Data.preferredTopics.isNotEmpty()
            ) {
                currentUserBrain = v7Data.toUserBrain()
                Log.i(
                    TAG,
                    "Migrated V7 brain (${v7Data.interactions} interactions)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "V7 migration failed", e)
        }
    }

    private fun legacyJsonToVector(jsonObj: JSONObject): ContentVector {
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

    // =================================================
    // 5. PERSONA ENGINE
    // =================================================

    enum class FlowPersona(
        val title: String,
        val description: String,
        val icon: String
    ) {
        INITIATE(
            "The Initiate",
            "Just getting started. Your profile is still forming.",
            "🌱"
        ),
        AUDIOPHILE(
            "The Audiophile",
            "You use Flow mostly for Music. The vibe is everything.",
            "🎧"
        ),
        LIVEWIRE(
            "The Livewire",
            "You love the raw energy of Livestreams and premieres.",
            "🔴"
        ),
        NIGHT_OWL(
            "The Night Owl",
            "You thrive in the dark. Most watching happens after midnight.",
            "🦉"
        ),
        BINGER(
            "The Binger",
            "Once you start, you can't stop. Massive content waves.",
            "🍿"
        ),
        SCHOLAR(
            "The Scholar",
            "High-complexity content. Here to grow, not just be entertained.",
            "🎓"
        ),
        DEEP_DIVER(
            "The Deep Diver",
            "Long-form video essays and documentaries are your world.",
            "🤿"
        ),
        SKIMMER(
            "The Skimmer",
            "Fast-paced, short content. Dopamine on demand.",
            "⚡"
        ),
        SPECIALIST(
            "The Specialist",
            "Laser-focused on a few niches. You know what you like.",
            "🎯"
        ),
        EXPLORER(
            "The Explorer",
            "Chaotic and beautiful. A bit of everything.",
            "🧭"
        )
    }

    /**
     * V8: Persona detection with independent pacing dimension.
     * Uses title-keyword-derived pacing for Skimmer/Binger detection
     * instead of the old redundant `1 - duration`.
     */
    fun getPersona(brain: UserBrain): FlowPersona {
        if (brain.totalInteractions < 15) return FlowPersona.INITIATE

        val v = brain.globalVector

        val sortedTopics = v.topics.values.sortedDescending()
        val topScore = sortedTopics.firstOrNull() ?: 0.0
        val diversityIndex = if (sortedTopics.size >= 5 && topScore > 0) {
            sortedTopics[4] / topScore
        } else 0.0

        val musicKeywords = setOf(
            "music", "song", "lyrics", "remix", "lofi",
            "playlist", "official audio"
        )
        val musicScore = v.topics.entries
            .filter {
                musicKeywords.contains(it.key) ||
                    it.key.contains("feat")
            }
            .sumOf { it.value }
        val totalScore = v.topics.values.sum()

        fun mag(cv: ContentVector) = cv.topics.values.sum()
        val nightMag = (
            mag(brain.timeVectors[TimeBucket.WEEKDAY_NIGHT]
                ?: ContentVector()) +
                mag(brain.timeVectors[TimeBucket.WEEKEND_NIGHT]
                    ?: ContentVector())
            )
        val morningMag = (
            mag(brain.timeVectors[TimeBucket.WEEKDAY_MORNING]
                ?: ContentVector()) +
                mag(brain.timeVectors[TimeBucket.WEEKEND_MORNING]
                    ?: ContentVector())
            )
        val isNocturnal = nightMag > (morningMag * 1.5) && nightMag > 5.0

        val rawPersona = when {
            totalScore > 0 &&
                musicScore > (totalScore * 0.4) -> FlowPersona.AUDIOPHILE
            v.isLive > 0.6 -> FlowPersona.LIVEWIRE
            isNocturnal -> FlowPersona.NIGHT_OWL
            // V8: Binger uses high interaction count + high pacing preference
            brain.totalInteractions > 500 &&
                v.pacing > 0.65 -> FlowPersona.BINGER
            v.complexity > 0.75 -> FlowPersona.SCHOLAR
            v.duration > 0.70 -> FlowPersona.DEEP_DIVER
            // V8: Skimmer uses short duration + high pacing (both independent)
            v.duration < 0.35 &&
                v.pacing > 0.60 -> FlowPersona.SKIMMER
            diversityIndex < 0.25 -> FlowPersona.SPECIALIST
            else -> FlowPersona.EXPLORER
        }

        // Persona hysteresis
        val lastPersona = brain.lastPersona?.let { name ->
            FlowPersona.entries.find { it.name == name }
        }

        return if (lastPersona != null &&
            rawPersona != lastPersona &&
            brain.personaStability < 3
        ) {
            lastPersona
        } else {
            rawPersona
        }
    }

    // =================================================
    // 6. EXPLORATION ENGINE
    // =================================================

    private val MACRO_CATEGORIES: List<String> by lazy {
        TOPIC_CATEGORIES.flatMap { category ->
            listOf(
                category.name
                    .replace(Regex("[^a-zA-Z ]"), "")
                    .trim()
            ) + category.topics.take(3)
        }.distinct()
    }

    /**
     * V8: Returns the 2 weakest macro categories for targeted exploration.
     * Sorted by score so the least-covered areas get explored first.
     */
    private fun getExplorationQueries(brain: UserBrain): List<String> {
        return MACRO_CATEGORIES
            .map { category ->
                val score = brain.globalVector
                    .topics[normalizeLemma(category)] ?: 0.0
                category to score
            }
            .filter { it.second < 0.1 }
            .sortedBy { it.second }
            .take(2)
            .map { it.first }
    }

    // =================================================
    // STOP WORDS
    // =================================================

    private val STOP_WORDS = setOf(
        "the", "and", "for", "that", "this", "with", "you", "how",
        "what", "when", "mom", "types", "your", "which", "can",
        "make", "seen", "most", "into", "best", "from", "just",
        "about", "more", "some", "will", "one", "all", "would",
        "there", "their", "out", "not", "but", "have", "has",
        "been", "being", "was", "were", "are",
        "video", "official", "channel", "review", "reaction",
        "full", "episode", "part", "new", "latest", "update",
        "updates", "hdr", "uhd", "fps", "live", "stream",
        "streaming", "watch", "subscribe", "like", "comment",
        "share", "click", "link", "description", "below", "check",
        "dont", "miss", "must", "now",
        "1080p", "720p", "480p", "360p", "240p", "144p",
        "reupload", "reup", "reuploaded", "compilation", "montage"
    )
}