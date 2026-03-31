/*
 * Copyright (C) 2025-2026 Flow | A-EDev
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
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.ln
import kotlin.math.log10

/**
 * Flow Neuro Engine (V10.0 — Channel Intelligence + Shorts Vector + Anti-Rec + Momentum)
 *
 * Client-side hybrid recommendation: Vector Space Model + Heuristic Rules.
 *
 * This file is the thin orchestrator that delegates all heavy logic to:
 * - NeuroModels.kt    — data classes and enums
 * - NeuroScoring.kt   — scoring factor calculators and constants
 * - NeuroVectorMath.kt — vector algebra (cosine similarity, vector adjustment)
 * - NeuroTokenizer.kt  — tokenization, IDF, feature extraction
 * - NeuroStorage.kt    — DataStore persistence, export/import, migration
 * - NeuroDiscovery.kt  — smart query generation (V2)
 */
class FlowNeuroEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "FlowNeuroEngine"

        // ── Orchestrator-only constants ──
        private const val FEATURE_CACHE_MAX = 150
        private const val SESSION_TOPIC_HISTORY_MAX = 50
        private const val SAVE_DEBOUNCE_MS = 5000L

        // ── Suppression constants ──
        /** How long a specific video stays hard-suppressed after "not interested" */
        private const val VIDEO_SUPPRESSION_DAYS = 30L
        /** How long a channel stays hard-suppressed before escalating to a full block */
        private const val CHANNEL_SUPPRESSION_DAYS = 14L
        /** Max suppressed video entries to prevent unbounded growth */
        private const val MAX_SUPPRESSED_VIDEOS = 500
        /** Max suppressed channel entries to prevent unbounded growth */
        private const val MAX_SUPPRESSED_CHANNELS = 100

        @Volatile
        private var instance: FlowNeuroEngine? = null

        fun getInstance(context: Context): FlowNeuroEngine {
            return instance ?: synchronized(this) {
                instance ?: FlowNeuroEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private fun requireInstance(): FlowNeuroEngine =
            instance ?: error("FlowNeuroEngine not initialized. Call initialize(context) first.")

        // ── Backward-compatible forwarding API ──

        suspend fun initialize(context: Context) = getInstance(context).initialize()

        suspend fun rank(candidates: List<Video>, userSubs: Set<String>): List<Video> =
            requireInstance().rank(candidates, userSubs)

        suspend fun generateDiscoveryQueries(): List<String> =
            requireInstance().generateDiscoveryQueries()

        suspend fun needsOnboarding(): Boolean = requireInstance().needsOnboarding()

        suspend fun getBrainSnapshot(): UserBrain = requireInstance().getBrainSnapshot()

        fun getPersona(brain: UserBrain): FlowPersona = requireInstance().getPersona(brain)

        suspend fun markNotInterested(video: Video) =
            requireInstance().markNotInterested(video)
        suspend fun markNotInterested(context: Context, video: Video) =
            getInstance(context).markNotInterested(video)

        suspend fun onVideoInteraction(
            video: Video,
            interactionType: InteractionType,
            percentWatched: Float = 0f
        ) = requireInstance().onVideoInteraction(video, interactionType, percentWatched)
        suspend fun onVideoInteraction(
            context: Context,
            video: Video,
            interactionType: InteractionType,
            percentWatched: Float = 0f
        ) = getInstance(context).onVideoInteraction(video, interactionType, percentWatched)

        suspend fun completeOnboarding(selectedTopics: Set<String>) =
            requireInstance().completeOnboarding(selectedTopics)
        suspend fun completeOnboarding(context: Context, selectedTopics: Set<String>) =
            getInstance(context).completeOnboarding(selectedTopics)

        suspend fun exportBrainToStream(output: OutputStream): Boolean =
            requireInstance().exportBrainToStream(output)
        suspend fun importBrainFromStream(input: InputStream): Boolean =
            requireInstance().importBrainFromStream(input)
        suspend fun importBrainFromStream(context: Context, input: InputStream): Boolean =
            getInstance(context).importBrainFromStream(input)

        suspend fun bootstrapFromSubscriptions(channelNames: List<String>) =
            requireInstance().bootstrapFromSubscriptions(channelNames)
        suspend fun bootstrapFromSubscriptions(context: Context, channelNames: List<String>) =
            getInstance(context).bootstrapFromSubscriptions(channelNames)

        suspend fun bootstrapFromWatchHistory(videos: List<Video>) =
            requireInstance().bootstrapFromWatchHistory(videos)
        suspend fun bootstrapFromWatchHistory(context: Context, videos: List<Video>) =
            getInstance(context).bootstrapFromWatchHistory(videos)

        suspend fun resetBrain() = requireInstance().resetBrain()
        suspend fun resetBrain(context: Context) = getInstance(context).resetBrain()

        suspend fun recordSeenShorts(shortIds: List<String>) =
            requireInstance().recordSeenShorts(shortIds)
        suspend fun getRecentlySeenShorts(): Set<String> =
            requireInstance().getRecentlySeenShorts()

        suspend fun getPreferredTopics(): Set<String> = requireInstance().getPreferredTopics()
        suspend fun getBlockedTopics(): Set<String> = requireInstance().getBlockedTopics()

        suspend fun addPreferredTopic(topic: String) = requireInstance().addPreferredTopic(topic)
        suspend fun addPreferredTopic(context: Context, topic: String) =
            getInstance(context).addPreferredTopic(topic)

        suspend fun removePreferredTopic(topic: String) =
            requireInstance().removePreferredTopic(topic)
        suspend fun removePreferredTopic(context: Context, topic: String) =
            getInstance(context).removePreferredTopic(topic)

        suspend fun addBlockedTopic(topic: String) = requireInstance().addBlockedTopic(topic)
        suspend fun addBlockedTopic(context: Context, topic: String) =
            getInstance(context).addBlockedTopic(topic)

        suspend fun removeBlockedTopic(topic: String) =
            requireInstance().removeBlockedTopic(topic)
        suspend fun removeBlockedTopic(context: Context, topic: String) =
            getInstance(context).removeBlockedTopic(topic)

        suspend fun unblockChannel(channelId: String) =
            requireInstance().unblockChannel(channelId)
        suspend fun unblockChannel(context: Context, channelId: String) =
            getInstance(context).unblockChannel(channelId)

        suspend fun blockChannel(channelId: String) =
            requireInstance().blockChannel(channelId)
        suspend fun blockChannel(context: Context, channelId: String) =
            getInstance(context).blockChannel(channelId)

        val TOPIC_CATEGORIES: List<TopicCategory>
            get() = instance?.TOPIC_CATEGORIES ?: emptyList()
    }

    // ── Module Instances ──
    private val tokenizer = NeuroTokenizer()
    private val storage = NeuroStorage(appContext)
    private val discovery by lazy { NeuroDiscovery(TOPIC_CATEGORIES, tokenizer) }

    // ── Concurrency ──
    private val brainMutex = Mutex()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSaveJob: Job? = null

    // Feature vector cache (LRU)
    private val featureCache = object : LinkedHashMap<String, ContentVector>(
        200, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ContentVector>?
        ): Boolean = size > FEATURE_CACHE_MAX
    }

    // Session tracking
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var sessionVideoCount: Int = 0
    private val sessionTopicHistory = mutableListOf<String>()
    private val recentInteractions = mutableListOf<MomentumEntry>()

    // IDF tracking — persisted in brain state
    private var idfWordFrequency = mutableMapOf<String, Int>()
    private var idfTotalDocuments = 0

    // Impression cache
    private val impressionCache = object : LinkedHashMap<String, ImpressionEntry>(
        NeuroScoring.IMPRESSION_CACHE_MAX + 50, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ImpressionEntry>?
        ): Boolean = size > NeuroScoring.IMPRESSION_CACHE_MAX
    }

    // Watch history
    private val watchHistory = LinkedHashMap<String, WatchEntry>(
        NeuroScoring.WATCH_HISTORY_MAX + 50, 0.75f, true
    )

    private var currentUserBrain: UserBrain = UserBrain()
    private var isInitialized = false

    // =================================================
    // PUBLIC API
    // =================================================

    suspend fun initialize() {
        brainMutex.withLock {
            if (isInitialized) return

            val loaded = storage.load()
            if (loaded != null) {
                currentUserBrain = loaded
            } else {
                val legacy = storage.migrateLegacy()
                if (legacy != null) {
                    currentUserBrain = legacy
                    Log.i(TAG, "Migrated legacy brain to DataStore")
                } else {
                    val previous = storage.tryMigrateFromPreviousDataStore()
                    if (previous != null) {
                        currentUserBrain = previous
                        Log.i(TAG, "Migrated previous DataStore brain")
                    }
                }
                storage.save(currentUserBrain)
                storage.deleteLegacyFile()
            }

            idfWordFrequency = currentUserBrain.idfWordFrequency.toMutableMap()
            idfTotalDocuments = currentUserBrain.idfTotalDocuments

            currentUserBrain.watchHistoryMap.forEach { (id, pct) ->
                watchHistory[id] = WatchEntry(pct, System.currentTimeMillis())
            }

            resetSessionInternal()
            isInitialized = true
        }
    }

    fun shutdown() {
        pendingSaveJob?.cancel()
        saveScope.cancel()
    }

    suspend fun getBrainSnapshot(): UserBrain =
        brainMutex.withLock { currentUserBrain }

    suspend fun resetBrain() {
        brainMutex.withLock {
            currentUserBrain = UserBrain()
            featureCache.clear()
            idfWordFrequency.clear()
            idfTotalDocuments = 0
            impressionCache.clear()
            watchHistory.clear()
            resetSessionInternal()
            storage.save(currentUserBrain)
        }
    }

    suspend fun resetSession() {
        brainMutex.withLock {
            resetSessionInternal()
        }
    }

    private fun resetSessionInternal() {
        sessionStartTime = System.currentTimeMillis()
        sessionVideoCount = 0
        sessionTopicHistory.clear()
        impressionCache.clear()
        recentInteractions.clear()
    }

    fun getSessionDurationMinutes(): Long =
        (System.currentTimeMillis() - sessionStartTime) / 60_000L

    private fun scheduleDebouncedSave() {
        pendingSaveJob?.cancel()
        pendingSaveJob = saveScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            brainMutex.withLock {
                storage.save(currentUserBrain)
            }
        }
    }

    // =================================================
    // BLOCKED TOPICS & CHANNELS API
    // =================================================

    suspend fun getBlockedTopics(): Set<String> =
        brainMutex.withLock { currentUserBrain.blockedTopics }

    suspend fun addBlockedTopic(topic: String) {
        val normalized = topic.trim().lowercase()
        if (normalized.isBlank()) return
        brainMutex.withLock {
            val lemma = tokenizer.normalizeLemma(normalized)

            val scrubbed = scrubTopicFromVector(
                currentUserBrain.globalVector, lemma, normalized
            )
            val scrubbedTimeVectors = currentUserBrain.timeVectors
                .mapValues { (_, vector) ->
                    scrubTopicFromVector(vector, lemma, normalized)
                }

            val cleanedPreferred = currentUserBrain.preferredTopics
                .filter { it.lowercase() != normalized }
                .toSet()

            currentUserBrain = currentUserBrain.copy(
                blockedTopics = currentUserBrain.blockedTopics + normalized,
                globalVector = scrubbed,
                timeVectors = scrubbedTimeVectors,
                preferredTopics = cleanedPreferred
            )
            storage.save(currentUserBrain)
        }
    }

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

    suspend fun removeBlockedTopic(topic: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedTopics = currentUserBrain.blockedTopics -
                    topic.lowercase()
            )
            storage.save(currentUserBrain)
        }
    }

    suspend fun getBlockedChannels(): Set<String> =
        brainMutex.withLock { currentUserBrain.blockedChannels }

    suspend fun blockChannel(channelId: String) {
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
            storage.save(currentUserBrain)
        }
    }

    suspend fun unblockChannel(channelId: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedChannels = currentUserBrain.blockedChannels - channelId
            )
            storage.save(currentUserBrain)
        }
    }

    // =================================================
    // ONBOARDING & PREFERRED TOPICS
    // =================================================

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

    suspend fun setPreferredTopics(topics: Set<String>) {
        brainMutex.withLock {
            val newTopics = currentUserBrain.globalVector.topics.toMutableMap()
            topics.forEach { topic ->
                newTopics[tokenizer.normalizeLemma(topic)] = 0.5
            }
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = topics,
                globalVector = currentUserBrain.globalVector.copy(
                    topics = newTopics
                )
            )
            storage.save(currentUserBrain)
        }
    }

    suspend fun addPreferredTopic(topic: String) {
        val normalized = topic.trim()
        if (normalized.isBlank()) return
        brainMutex.withLock {
            val newTopics = currentUserBrain.globalVector.topics.toMutableMap()
            newTopics[tokenizer.normalizeLemma(normalized)] = 0.5
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = currentUserBrain.preferredTopics + normalized,
                globalVector = currentUserBrain.globalVector.copy(
                    topics = newTopics
                )
            )
            storage.save(currentUserBrain)
        }
    }

    suspend fun removePreferredTopic(topic: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = currentUserBrain.preferredTopics - topic
            )
            storage.save(currentUserBrain)
        }
    }

    suspend fun completeOnboarding(selectedTopics: Set<String>) {
        brainMutex.withLock {
            val topicList = selectedTopics.toList()
            val newTopics = mutableMapOf<String, Double>()

            topicList.forEachIndexed { index, topic ->
                val weight = when {
                    index < 3 -> 0.55
                    index < 6 -> 0.40
                    else -> 0.30
                }
                newTopics[tokenizer.normalizeLemma(topic)] = weight
            }

            val affinities = mutableMapOf<String, Double>()
            val normalizedList = topicList.map { tokenizer.normalizeLemma(it) }
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
            storage.save(currentUserBrain)
            Log.i(TAG, "Onboarding: ${selectedTopics.size} topics")
        }
    }

    private fun makeAffinityKey(t1: String, t2: String): String {
        return if (t1 < t2) "$t1|$t2" else "$t2|$t1"
    }

    // =================================================
    // SUBSCRIPTION BOOTSTRAP
    // =================================================

    suspend fun bootstrapFromSubscriptions(channelNames: List<String>) {
        if (channelNames.isEmpty()) return

        brainMutex.withLock {
            if (currentUserBrain.totalInteractions > 5 &&
                currentUserBrain.globalVector.topics.isNotEmpty()
            ) {
                Log.i(TAG, "Bootstrap skipped: brain already has learned data")
                return
            }

            val topicWeights = mutableMapOf<String, Double>()
            val bootstrapWeight = 0.25

            channelNames.forEach { name ->
                val tokens = tokenizer.tokenize(name)
                tokens.forEach { token ->
                    val current = topicWeights[token] ?: 0.0
                    topicWeights[token] = (current + bootstrapWeight)
                        .coerceAtMost(0.60)
                }
            }

            if (topicWeights.isEmpty()) {
                Log.i(TAG, "Bootstrap: no usable keywords from ${channelNames.size} channels")
                return
            }

            val mergedTopics = currentUserBrain.globalVector.topics.toMutableMap()
            topicWeights.forEach { (key, weight) ->
                val existing = mergedTopics[key] ?: 0.0
                mergedTopics[key] = maxOf(existing, weight)
            }

            val topKeywords = topicWeights.entries
                .sortedByDescending { it.value }
                .take(15)
                .map { it.key }

            val newAffinities = currentUserBrain.topicAffinities.toMutableMap()
            for (i in topKeywords.indices) {
                for (j in i + 1 until topKeywords.size) {
                    val key = makeAffinityKey(topKeywords[i], topKeywords[j])
                    val current = newAffinities[key] ?: 0.0
                    newAffinities[key] = (current + 0.15)
                        .coerceAtMost(NeuroScoring.AFFINITY_MAX)
                }
            }

            val preferredFromSubs = topicWeights.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { it.key }
                .toSet()

            val mergedPreferred = currentUserBrain.preferredTopics + preferredFromSubs

            currentUserBrain = currentUserBrain.copy(
                globalVector = currentUserBrain.globalVector.copy(
                    topics = mergedTopics
                ),
                topicAffinities = newAffinities,
                preferredTopics = mergedPreferred,
                hasCompletedOnboarding = true
            )

            storage.save(currentUserBrain)
            Log.i(
                TAG,
                "Bootstrap: seeded ${topicWeights.size} topics from " +
                    "${channelNames.size} subscriptions " +
                    "(top: ${topKeywords.take(5).joinToString()})"
            )
        }
    }

    suspend fun bootstrapFromWatchHistory(videos: List<Video>) {
        if (videos.isEmpty()) return

        brainMutex.withLock {
            if (currentUserBrain.totalInteractions > 50 &&
                currentUserBrain.globalVector.topics.size > 10
            ) {
                Log.i(TAG, "History bootstrap skipped: brain already mature")
                return
            }

            val idfSnapshot = takeIdfSnapshot()
            var updatedBrain = currentUserBrain

            val historyLearningRate = 0.05
            val maxToProcess = 500

            val toProcess = videos.take(maxToProcess)

            toProcess.forEach { video ->
                val videoVector = tokenizer.extractFeatures(video, idfSnapshot)

                val newGlobal = NeuroVectorMath.adjustVector(
                    updatedBrain.globalVector, videoVector, historyLearningRate
                )

                val bucket = TimeBucket.current()
                val currentBucketVec = updatedBrain.timeVectors[bucket] ?: ContentVector()
                val newBucketVec = NeuroVectorMath.adjustVector(
                    currentBucketVec, videoVector, historyLearningRate * 0.5
                )

                val currentChScore = updatedBrain.channelScores[video.channelId] ?: 0.5
                val newChScore = (currentChScore * 0.95) + (1.0 * 0.05)
                val newChannelScores = updatedBrain.channelScores +
                    (video.channelId to newChScore)

                var newAffinities = updatedBrain.topicAffinities
                val topTopics = videoVector.topics.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }
                if (topTopics.size >= 2) {
                    val mutableAffinities = newAffinities.toMutableMap()
                    for (i in topTopics.indices) {
                        for (j in i + 1 until topTopics.size) {
                            val key = makeAffinityKey(topTopics[i], topTopics[j])
                            val current = mutableAffinities[key] ?: 0.0
                            mutableAffinities[key] = (current + 0.01)
                                .coerceAtMost(NeuroScoring.AFFINITY_MAX)
                        }
                    }
                    newAffinities = mutableAffinities
                }

                videoVector.topics.keys.forEach { word ->
                    idfWordFrequency[word] = (idfWordFrequency[word] ?: 0) + 1
                }
                idfTotalDocuments++

                updatedBrain = updatedBrain.copy(
                    globalVector = newGlobal,
                    timeVectors = updatedBrain.timeVectors + (bucket to newBucketVec),
                    channelScores = newChannelScores,
                    topicAffinities = newAffinities,
                    totalInteractions = updatedBrain.totalInteractions + 1
                )
            }

            toProcess.forEach { video ->
                watchHistory[video.id] = WatchEntry(0.5f, System.currentTimeMillis())
            }

            currentUserBrain = updatedBrain.copy(
                idfWordFrequency = idfWordFrequency.toMap(),
                idfTotalDocuments = idfTotalDocuments,
                watchHistoryMap = watchHistory.mapValues { it.value.percentWatched },
                hasCompletedOnboarding = true
            )

            if (idfTotalDocuments > 10000) {
                idfWordFrequency.replaceAll { _, v -> v / 2 }
                idfWordFrequency.entries.removeAll { it.value <= 0 }
                idfTotalDocuments /= 2
            }

            storage.save(currentUserBrain)
            featureCache.clear()

            Log.i(TAG, "History bootstrap: processed ${toProcess.size} videos, " +
                "${updatedBrain.globalVector.topics.size} topics learned")
        }
    }

    suspend fun markNotInterested(video: Video) {
        val videoVector = getOrExtractFeatures(video, takeIdfSnapshotSafe())

        brainMutex.withLock {
            val now = System.currentTimeMillis()

            // 1. Hard-suppress this specific video for VIDEO_SUPPRESSION_DAYS
            val newSuppressedVideos = currentUserBrain.suppressedVideoIds.toMutableMap()
            newSuppressedVideos[video.id] = now
            if (newSuppressedVideos.size > MAX_SUPPRESSED_VIDEOS) {
                val cutoff = now - (VIDEO_SUPPRESSION_DAYS * 86_400_000L)
                newSuppressedVideos.entries.removeAll { it.value < cutoff }
            }

            // 2. Channel suppression — escalate to permanent block on 2nd signal
            val newSuppressedChannels = currentUserBrain.suppressedChannels.toMutableMap()
            var updatedBlockedChannels = currentUserBrain.blockedChannels
            if (video.channelId.isNotBlank()) {
                if (video.channelId in newSuppressedChannels) {
                    updatedBlockedChannels = updatedBlockedChannels + video.channelId
                    newSuppressedChannels.remove(video.channelId)
                } else {
                    newSuppressedChannels[video.channelId] = now
                }
                if (newSuppressedChannels.size > MAX_SUPPRESSED_CHANNELS) {
                    val cutoff = now - (CHANNEL_SUPPRESSION_DAYS * 86_400_000L)
                    newSuppressedChannels.entries.removeAll { it.value < cutoff }
                }
            }

            // 3. Aggressive vector adjustment — halves matching topic scores instead of
            val newGlobal = adjustVectorAggressive(currentUserBrain.globalVector, videoVector)

            // 4. Channel score — halve the current score, floor at 0.01
            val newChannelScores = currentUserBrain.channelScores.toMutableMap()
            if (video.channelId.isNotBlank()) {
                val currentScore = newChannelScores[video.channelId] ?: 0.5
                newChannelScores[video.channelId] = (currentScore * 0.5).coerceAtLeast(0.01)
            }

            // 5. Time bucket adjustment
            val bucket = TimeBucket.current()
            val currentBucketVec = currentUserBrain.timeVectors[bucket] ?: ContentVector()
            val newBucketVec = NeuroVectorMath.adjustVector(
                currentBucketVec, videoVector, NeuroScoring.NOT_INTERESTED_TIME_RATE
            )

            // 6. Consecutive skips
            val newSkips = (currentUserBrain.consecutiveSkips +
                NeuroScoring.NOT_INTERESTED_SKIP_INCREMENT)
                .coerceAtMost(NeuroScoring.MAX_CONSECUTIVE_SKIPS)

            currentUserBrain = currentUserBrain.copy(
                globalVector = newGlobal,
                timeVectors = currentUserBrain.timeVectors + (bucket to newBucketVec),
                channelScores = newChannelScores,
                blockedChannels = updatedBlockedChannels,
                totalInteractions = currentUserBrain.totalInteractions + 1,
                consecutiveSkips = newSkips,
                suppressedVideoIds = newSuppressedVideos,
                suppressedChannels = newSuppressedChannels
            )
            storage.save(currentUserBrain)
        }
    }

    /**
     * Aggressive vector adjustment for "not interested" signals.
     * Halves every matching topic score instead of the gentle proportional decay
     */
    private fun adjustVectorAggressive(
        current: ContentVector,
        target: ContentVector
    ): ContentVector {
        val newTopics = current.topics.toMutableMap()
        target.topics.forEach { (key, _) ->
            val currentVal = newTopics[key] ?: 0.0
            if (currentVal > 0) {
                newTopics[key] = (currentVal * 0.5).coerceAtMost(0.3)
            }
        }
        newTopics.entries.removeAll { it.value < NeuroVectorMath.TOPIC_PRUNE_THRESHOLD }
        return current.copy(topics = newTopics)
    }

    // =================================================
    // DISCOVERY QUERY GENERATION
    // =================================================

    suspend fun generateDiscoveryQueries(): List<String> =
        withContext(Dispatchers.Default) { brainMutex.withLock {
            val brain = currentUserBrain
            val blocked = brain.blockedTopics

            // V3 discovery engine
            val discoveryQueries = discovery.generateQueries(brain) { b -> getPersona(b) }

            var candidates = if (discoveryQueries.isNotEmpty()) {
                discoveryQueries
                    .map { it.query }
                    .filter { query ->
                        !blocked.any { blockedTerm ->
                            query.lowercase().contains(blockedTerm)
                        }
                    }
            } else {
                val preferred = brain.preferredTopics.toList()
                if (preferred.isNotEmpty()) preferred.shuffled().take(5)
                else listOf("Music", "Science", "Technology", "Education", "Nature")
            }

            // ── Query rotation: filter queries too similar to recently used ones ──
            if (brain.recentQueryTokens.isNotEmpty() && candidates.size > 3) {
                val rotated = candidates.filter { query ->
                    val tokens = tokenizer.tokenize(query).toSet()
                    if (tokens.isEmpty()) return@filter true
                    brain.recentQueryTokens.none { recent ->
                        if (recent.isEmpty()) return@none false
                        val intersection = tokens.intersect(recent).size
                        val union = tokens.union(recent).size
                        intersection.toDouble() / union >
                            NeuroScoring.QUERY_OVERLAP_THRESHOLD
                    }
                }
                if (rotated.size >= candidates.size / 3) {
                    candidates = rotated
                }
            }

            // ── Track used queries for future rotation ──
            val newQueryTokens = candidates.map { tokenizer.tokenize(it).toSet() }
            val updatedRecentTokens = (brain.recentQueryTokens + newQueryTokens)
                .takeLast(NeuroScoring.RECENT_QUERY_TOKENS_MAX)

            currentUserBrain = currentUserBrain.copy(
                recentQueryTokens = updatedRecentTokens
            )
            scheduleDebouncedSave()

            candidates
        } }

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

    suspend fun rank(
        candidates: List<Video>,
        userSubs: Set<String>
    ): List<Video> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Session staleness auto-reset
        val sessionAgeMinutes = getSessionDurationMinutes()
        if (sessionAgeMinutes > NeuroScoring.SESSION_RESET_IDLE_MINUTES ||
            (sessionAgeMinutes > NeuroScoring.SESSION_RESET_EMPTY_MINUTES &&
                sessionVideoCount == 0)
        ) {
            brainMutex.withLock { resetSessionInternal() }
        }

        // Take consistent snapshots under the lock
        val brain: UserBrain
        val idfSnapshot: IdfSnapshot
        val sessionTopics: List<String>
        val impressionSnapshot: Map<String, ImpressionEntry>
        val watchHistorySnapshot: Map<String, WatchEntry>

        brainMutex.withLock {
            brain = currentUserBrain
            idfSnapshot = takeIdfSnapshot()
            sessionTopics = sessionTopicHistory.toList()
            impressionSnapshot = impressionCache.toMap()
            watchHistorySnapshot = watchHistory.toMap()
        }

        val random = java.util.Random()
        val now = System.currentTimeMillis()

        // Hard suppression sets (time-bounded)
        val videoSuppressionCutoff = now - (VIDEO_SUPPRESSION_DAYS * 24 * 60 * 60 * 1000L)
        val channelSuppressionCutoff = now - (CHANNEL_SUPPRESSION_DAYS * 24 * 60 * 60 * 1000L)
        val activeSuppressedVideos = brain.suppressedVideoIds
            .filter { (_, ts) -> ts > videoSuppressionCutoff }.keys
        val activeSuppressedChannels = brain.suppressedChannels
            .filter { (_, ts) -> ts > channelSuppressionCutoff }.keys

        // Precompute blocked topic expansion ONCE
        val expandedBlockedKeywords: Set<String> = brain.blockedTopics.flatMapTo(
            mutableSetOf()
        ) { blocked ->
            val blockedLower = blocked.lowercase()
            val matchingCategory = TOPIC_CATEGORIES.find { cat ->
                cat.topics.any { it.lowercase() == blockedLower }
            }
            val keywords = if (matchingCategory != null) {
                matchingCategory.topics.mapTo(mutableListOf()) { it.lowercase() }
                    .also { it.add(blockedLower) }
            } else {
                mutableListOf(blockedLower)
            }
            keywords
        }

        val expandedWithLemmas: Set<String> = expandedBlockedKeywords.flatMapTo(
            mutableSetOf()
        ) { keyword ->
            setOf(keyword, tokenizer.normalizeLemma(keyword))
        }

        // ── Feed overlap ratio (for adaptive jitter + feed history penalty) ──
        val feedOverlapRatio = if (candidates.isEmpty() || brain.feedHistory.isEmpty()) {
            0.0
        } else {
            val candidateIds = candidates.map { it.id }.toSet()
            val recentHistoryIds = brain.feedHistory
                .filter { (_, entry) ->
                    (now - entry.lastShown) < 48L * 60 * 60 * 1000
                }.keys
            val overlap = candidateIds.intersect(recentHistoryIds).size
            (overlap.toDouble() / candidateIds.size).coerceIn(0.0, 1.0)
        }

        // Pre-filter blocked content
        val filtered = candidates.filter { video ->
            if (video.id in activeSuppressedVideos) return@filter false
            if (video.channelId in activeSuppressedChannels) return@filter false
            if (brain.blockedChannels.contains(video.channelId)) {
                return@filter false
            }
            val titleLower = video.title.lowercase()
            val channelLower = video.channelName.lowercase()

            !expandedWithLemmas.any { blocked ->
                titleLower.contains(blocked) || channelLower.contains(blocked)
            }
        }

        if (filtered.isEmpty()) return@withContext emptyList()

        // Extract all features with consistent IDF snapshot
        val videoVectors = filtered.map { video ->
            val channelProfile = brain.channelTopicProfiles[video.channelId]
            video to getOrExtractFeatures(video, idfSnapshot, channelProfile)
        }

        // Vector-level topic blocking: remove videos whose top topics match blocked topics
        val blockedTopicLemmas = brain.blockedTopics.map { tokenizer.normalizeLemma(it) }.toSet()
        val vectorFiltered = if (blockedTopicLemmas.isEmpty()) videoVectors else {
            videoVectors.filter { (_, vector) ->
                val topVideoTopics = vector.topics.entries
                    .sortedByDescending { it.value }.take(3).map { it.key }
                !topVideoTopics.any { topic ->
                    blockedTopicLemmas.any { blocked ->
                        topic.contains(blocked) || blocked.contains(topic)
                    }
                }
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

        // Onboarding warmup factor
        val isColdStart = brain.totalInteractions < NeuroScoring.COLD_START_THRESHOLD
        val isOnboarding = brain.totalInteractions < NeuroScoring.ONBOARDING_WARMUP_INTERACTIONS
        val onboardingWarmup = if (isOnboarding) {
            1.0 - (brain.totalInteractions /
                NeuroScoring.ONBOARDING_WARMUP_INTERACTIONS.toDouble()) * 0.5
        } else 0.5

        val scored = vectorFiltered.map { (video, videoVector) ->
            val personalityScore = if (video.isShort && brain.shortsVector.topics.isNotEmpty()) {
                val globalSim = NeuroVectorMath.calculateCosineSimilarity(brain.globalVector, videoVector)
                val shortsSim = NeuroVectorMath.calculateCosineSimilarity(brain.shortsVector, videoVector)
                globalSim * 0.4 + shortsSim * 0.6
            } else {
                NeuroVectorMath.calculateCosineSimilarity(brain.globalVector, videoVector)
            }
            val contextScore = NeuroVectorMath.calculateCosineSimilarity(
                timeContextVector, videoVector
            )
            val noveltyScore = when {
                isColdStart -> 1.0 - personalityScore 
                personalityScore < NeuroScoring.NOVELTY_RELEVANCE_GATE -> 0.0
                else -> 1.0 - personalityScore
            }

            // ── Base similarity score ──
            var totalScore = (personalityScore * wPersonality) +
                (contextScore * wContext) +
                (noveltyScore * wNovelty)

            // ── Topic affinity boost ──
            val videoTopics = videoVector.topics.keys
                .map { NeuroScoring.stripDomainTag(it) }.distinct()
            var affinityBoost = 0.0
            for (i in videoTopics.indices) {
                for (j in i + 1 until videoTopics.size) {
                    val key = makeAffinityKey(videoTopics[i], videoTopics[j])
                    val affinity = brain.topicAffinities[key] ?: 0.0
                    affinityBoost += affinity * NeuroScoring.AFFINITY_BOOST_PER_PAIR
                }
            }
            totalScore += affinityBoost.coerceAtMost(NeuroScoring.AFFINITY_MAX_BOOST_PER_VIDEO)

            // ── Channel signal (sub boost + boredom) ──
            totalScore += NeuroScoring.calculateChannelSignal(video, brain, userSubs)

            // ── Serendipity ──
            totalScore += NeuroScoring.calculateSerendipity(noveltyScore, contextScore)

            // ── Cold-start popularity ──
            if (isColdStart && video.viewCount > 0) {
                val popularityBoost =
                    log10(1.0 + video.viewCount.toDouble()) / 10.0 * 0.05
                totalScore += popularityBoost
            }

            // ── Engagement quality ──
            totalScore *= NeuroScoring.calculateEngagementQuality(video, isColdStart)

            // ── Time decay ──
            val ageMultiplier = NeuroScoring.TimeDecay.calculateMultiplier(
                video.uploadDate, video.isLive
            )
            val isClassic = NeuroScoring.isVideoClassic(video.viewCount)
            val isSub = userSubs.contains(video.channelId)
            val finalAgeFactor = when {
                isClassic || isSub -> (ageMultiplier + 1.0) / 2.0
                else -> ageMultiplier
            }
            totalScore *= finalAgeFactor

            // ── Curiosity gap ──
            totalScore += NeuroScoring.calculateCuriosityBonus(
                personalityScore,
                brain.globalVector.complexity,
                videoVector.complexity
            )

            // ── Freshness (session fatigue + impressions + binge) ──
            totalScore *= NeuroScoring.calculateFreshness(
                video, videoVector, personalityScore,
                sessionTopics, sessionVideoCount,
                impressionSnapshot[video.id], now
            )

            // ── Onboarding warmup ──
            if (isOnboarding) {
                val hasPreferred = brain.preferredTopics.any { pref ->
                    videoVector.topics.containsKey(tokenizer.normalizeLemma(pref))
                }
                if (hasPreferred) {
                    totalScore += onboardingWarmup * NeuroScoring.ONBOARDING_MAX_BOOST
                }
            }

            // ── Already-watched penalty ──
            totalScore *= NeuroScoring.calculateWatchedPenalty(
                video, watchHistorySnapshot[video.id]
            )

            // ── Anti-recommendation penalty ──
            totalScore *= NeuroScoring.calculateAntiRecommendationPenalty(
                videoVector, video, brain
            )

            // ── Relevance floor (mature brains only) ──
            totalScore *= NeuroScoring.calculateRelevanceFloor(
                personalityScore, brain.totalInteractions, isSub
            )

            // ── Feed history penalty (persistent cross-session) ──
            totalScore *= NeuroScoring.calculateFeedHistoryPenalty(
                video.id, brain.feedHistory, now, filtered.size
            )

            // ── Implicit disinterest (shown many times, never watched) ──
            totalScore *= NeuroScoring.calculateImplicitDisinterestPenalty(
                video.id, brain.feedHistory, watchHistorySnapshot, now
            )

            // ── Engagement momentum boost ──
            totalScore += NeuroScoring.calculateMomentumBoost(videoVector, recentInteractions, personalityScore)

            // ── Seen Shorts penalty ──
            if (video.isShort) {
                val seenTimestamp = brain.seenShortsHistory[video.id]
                if (seenTimestamp != null) {
                    val daysSinceSeen = (now - seenTimestamp) / (24.0 * 60 * 60 * 1000)
                    if (daysSinceSeen < NeuroScoring.SEEN_SHORT_EXPIRY_DAYS) {
                        val recovery = (daysSinceSeen / NeuroScoring.SEEN_SHORT_EXPIRY_DAYS)
                            .coerceIn(0.0, 1.0)
                        val seenPenalty = NeuroScoring.SEEN_SHORT_PENALTY +
                            (1.0 - NeuroScoring.SEEN_SHORT_PENALTY) * recovery
                        totalScore *= seenPenalty
                    }
                }
            }

            // ── Adaptive Jitter ──
            val jitterMagnitude = NeuroScoring.calculateAdaptiveJitter(
                brain.totalInteractions, feedOverlapRatio
            )
            val jitter = random.nextDouble() * jitterMagnitude

            ScoredVideo(video, totalScore + jitter, videoVector)
        }.toMutableList()

        // Apply diversity reranking
        val result = NeuroScoring.applySmartDiversity(scored, tokenizer)

        // Log impressions + update persistent feed history for all returned videos
        brainMutex.withLock {
            result.forEach { video ->
                val existing = impressionCache[video.id]
                if (existing != null) {
                    existing.count++
                    existing.lastSeen = now
                } else {
                    impressionCache[video.id] =
                        ImpressionEntry(1, now)
                }
            }

            // ── Persistent feed history ──
            val updatedHistory = currentUserBrain.feedHistory.toMutableMap()
            result.forEach { video ->
                val prev = updatedHistory[video.id]
                updatedHistory[video.id] = FeedEntry(
                    lastShown = now,
                    showCount = (prev?.showCount ?: 0) + 1
                )
            }
            // Prune expired entries to keep map bounded
            val expiryCutoff = now - (NeuroScoring.FEED_HISTORY_EXPIRY_DAYS * 24 * 60 * 60 * 1000)
            val pruned = if (updatedHistory.size > NeuroScoring.FEED_HISTORY_MAX) {
                updatedHistory.entries
                    .filter { it.value.lastShown > expiryCutoff }
                    .sortedByDescending { it.value.lastShown }
                    .take(NeuroScoring.FEED_HISTORY_MAX)
                    .associate { it.key to it.value }
            } else {
                updatedHistory.filter { it.value.lastShown > expiryCutoff }
            }

            currentUserBrain = currentUserBrain.copy(feedHistory = pruned)
            scheduleDebouncedSave()
        }

        return@withContext result
    }

    // =================================================
    // LEARNING FUNCTION
    // =================================================

    suspend fun onVideoInteraction(
        video: Video,
        interactionType: InteractionType,
        percentWatched: Float = 0f
    ) {
        val idfSnapshot = brainMutex.withLock { takeIdfSnapshot() }
        val videoVector = getOrExtractFeatures(video, idfSnapshot)

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

        if (video.isShort) {
            learningRate *= NeuroScoring.SHORTS_LEARNING_PENALTY
        }

        // Maturity-scaled learning: slow down positive learning as brain matures.
        if (learningRate > 0) {
            val maturityDamping = 1.0 / (1.0 + ln(1.0 + currentUserBrain.totalInteractions / 50.0))
            learningRate *= maturityDamping.coerceIn(0.25, 1.0)
        }

        brainMutex.withLock {
            // 0. Watch velocity: adjust click learning rate based on impression timing
            if (interactionType == InteractionType.CLICK) {
                val impression = impressionCache[video.id]
                if (impression != null) {
                    val secondsSinceImpression =
                        (System.currentTimeMillis() - impression.lastSeen) / 1000.0
                    val clickVelocity = when {
                        secondsSinceImpression < 5.0 -> 1.5   // instant click
                        secondsSinceImpression < 30.0 -> 1.0  // normal
                        secondsSinceImpression < 120.0 -> 0.8 // delayed
                        else -> 0.6                            // much later
                    }
                    learningRate *= clickVelocity
                }
            }

            // 1. Update global vector
            val newGlobal = NeuroVectorMath.adjustVector(
                currentUserBrain.globalVector, videoVector, learningRate
            )

            // 1b. Shorts-specific vector (not dampened by SHORTS_LEARNING_PENALTY)
            val newShortsVector = if (video.isShort) {
                val shortsRate = when (interactionType) {
                    InteractionType.CLICK -> 0.08
                    InteractionType.LIKED -> 0.20
                    InteractionType.WATCHED -> 0.10 * percentWatched
                    InteractionType.SKIPPED -> -0.12
                    InteractionType.DISLIKED -> -0.30
                }
                NeuroVectorMath.adjustVector(
                    currentUserBrain.shortsVector, videoVector, shortsRate
                )
            } else {
                currentUserBrain.shortsVector
            }

            // 2. Update time bucket
            val bucket = TimeBucket.current()
            val currentBucketVec = currentUserBrain.timeVectors[bucket]
                ?: ContentVector()
            val newBucketVec = NeuroVectorMath.adjustVector(
                currentBucketVec, videoVector, learningRate * 1.2
            )

            // 3. Channel score
            val currentChScore =
                currentUserBrain.channelScores[video.channelId] ?: 0.5
            val outcome = if (learningRate > 0) 1.0 else 0.0
            val newChScore = (currentChScore * NeuroScoring.CHANNEL_EMA_DECAY) +
                (outcome * NeuroScoring.CHANNEL_EMA_ALPHA)
            var newChannelScores = currentUserBrain.channelScores +
                (video.channelId to newChScore)

            // Channel pruning
            if (newChannelScores.size > NeuroScoring.MAX_CHANNEL_SCORES) {
                val sorted = newChannelScores.entries.sortedBy { it.value }
                val keepLow = sorted.take(NeuroScoring.CHANNEL_KEEP_LOW)
                val keepHigh = sorted.takeLast(NeuroScoring.CHANNEL_KEEP_HIGH)
                val keepSet = (keepLow + keepHigh).map { it.key }.toSet()
                newChannelScores = newChannelScores
                    .filter { it.key in keepSet }
            }

            // 4. Consecutive skips
            val newSkips = when (interactionType) {
                InteractionType.CLICK, InteractionType.LIKED,
                InteractionType.WATCHED -> 0
                InteractionType.SKIPPED, InteractionType.DISLIKED ->
                    (currentUserBrain.consecutiveSkips + 1)
                        .coerceAtMost(NeuroScoring.MAX_CONSECUTIVE_SKIPS)
            }

            // 5. Topic co-occurrence
            var newAffinities = currentUserBrain.topicAffinities
            if (learningRate > 0) {
                val topTopics = videoVector.topics.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { NeuroScoring.stripDomainTag(it.key) }
                    .distinct()
                if (topTopics.size >= 2) {
                    val mutableAffinities = newAffinities.toMutableMap()
                    for (i in topTopics.indices) {
                        for (j in i + 1 until topTopics.size) {
                            val key = makeAffinityKey(
                                topTopics[i], topTopics[j]
                            )
                            val current = mutableAffinities[key] ?: 0.0
                            mutableAffinities[key] =
                                (current + NeuroScoring.AFFINITY_INCREMENT)
                                    .coerceAtMost(NeuroScoring.AFFINITY_MAX)
                        }
                    }
                    newAffinities = mutableAffinities
                        .filter { it.value > NeuroScoring.AFFINITY_PRUNE_THRESHOLD }
                    if (newAffinities.size > NeuroScoring.AFFINITY_MAX_ENTRIES) {
                        newAffinities = newAffinities.entries
                            .sortedByDescending { it.value }
                            .take(NeuroScoring.AFFINITY_KEEP_TOP)
                            .associate { it.key to it.value }
                    }
                }
            }

            // 6. Update IDF counters on interaction
            if (learningRate > 0) {
                videoVector.topics.keys.forEach { word ->
                    idfWordFrequency[word] =
                        (idfWordFrequency[word] ?: 0) + 1
                }
                idfTotalDocuments++

                if (idfTotalDocuments > 10000) {
                    idfWordFrequency.replaceAll { _, v -> v / 2 }
                    idfWordFrequency.entries.removeAll { it.value <= 0 }
                    idfTotalDocuments /= 2
                }

                if (idfTotalDocuments % 100 == 0) {
                    featureCache.clear()
                }
            }

            // 7. Persona tracking
            val rawPersona = getPersona(currentUserBrain)
            val lastPersonaName = currentUserBrain.lastPersona
            val newStability = if (rawPersona.name == lastPersonaName) {
                (currentUserBrain.personaStability + 1)
                    .coerceAtMost(NeuroScoring.PERSONA_MAX_STABILITY)
            } else 1

            // 8. Session tracking
            val primaryTopic = videoVector.topics
                .maxByOrNull { it.value }?.key
            if (primaryTopic != null) {
                sessionTopicHistory.add(primaryTopic)
                while (sessionTopicHistory.size > SESSION_TOPIC_HISTORY_MAX) {
                    sessionTopicHistory.removeFirst()
                }
            }
            sessionVideoCount++

            // 9. Channel topic profile update
            var newChannelProfiles = currentUserBrain.channelTopicProfiles
            if (learningRate > 0) {
                val channelId = video.channelId
                val existingProfile = newChannelProfiles[channelId]?.toMutableMap()
                    ?: mutableMapOf()

                videoVector.topics.forEach { (topic, weight) ->
                    val current = existingProfile[topic] ?: 0.0
                    existingProfile[topic] = current +
                        (weight - current) * NeuroScoring.CHANNEL_PROFILE_LEARNING_RATE
                }

                val profileIterator = existingProfile.iterator()
                while (profileIterator.hasNext()) {
                    val entry = profileIterator.next()
                    if (!videoVector.topics.containsKey(entry.key)) {
                        entry.setValue(entry.value * 0.98)
                    }
                    if (entry.value < NeuroScoring.CHANNEL_PROFILE_PRUNE_THRESHOLD) {
                        profileIterator.remove()
                    }
                }

                val pruned = if (existingProfile.size > NeuroScoring.CHANNEL_PROFILE_MAX_TOPICS) {
                    existingProfile.entries
                        .sortedByDescending { it.value }
                        .take(NeuroScoring.CHANNEL_PROFILE_MAX_TOPICS)
                        .associate { it.key to it.value }
                } else existingProfile.toMap()

                val mutableProfiles = newChannelProfiles.toMutableMap()
                mutableProfiles[channelId] = pruned

                if (mutableProfiles.size > NeuroScoring.CHANNEL_PROFILE_MAX_CHANNELS) {
                    val channelScoreMap = currentUserBrain.channelScores
                    val sorted = mutableProfiles.entries.sortedBy { (id, _) ->
                        channelScoreMap[id] ?: 0.0
                    }
                    val toRemove = sorted.take(
                        mutableProfiles.size - NeuroScoring.CHANNEL_PROFILE_MAX_CHANNELS
                    )
                    toRemove.forEach { mutableProfiles.remove(it.key) }
                }

                newChannelProfiles = mutableProfiles
            }

            // 10. Engagement momentum tracking
            if (primaryTopic != null) {
                recentInteractions.add(MomentumEntry(primaryTopic, learningRate > 0))
                while (recentInteractions.size > NeuroScoring.MOMENTUM_WINDOW) {
                    recentInteractions.removeFirst()
                }
            }

            if (learningRate > 0) {
                impressionCache.remove(video.id)
            }

            if (interactionType == InteractionType.WATCHED &&
                percentWatched > NeuroScoring.WATCHED_THRESHOLD_SAMPLED
            ) {
                val existing = watchHistory[video.id]
                if (existing == null ||
                    percentWatched > existing.percentWatched
                ) {
                    watchHistory[video.id] = WatchEntry(
                        percentWatched, System.currentTimeMillis()
                    )
                    while (watchHistory.size > NeuroScoring.WATCH_HISTORY_MAX) {
                        val oldestKey = watchHistory.keys.first()
                        watchHistory.remove(oldestKey)
                    }
                }
            }

            val watchHistoryMap = watchHistory.mapValues { (_, entry) ->
                entry.percentWatched
            }

            currentUserBrain = currentUserBrain.copy(
                globalVector = newGlobal,
                timeVectors = currentUserBrain.timeVectors +
                    (bucket to newBucketVec),
                channelScores = newChannelScores,
                topicAffinities = newAffinities,
                totalInteractions = currentUserBrain.totalInteractions + 1,
                consecutiveSkips = newSkips,
                lastPersona = rawPersona.name,
                personaStability = newStability,
                idfWordFrequency = idfWordFrequency.toMap(),
                idfTotalDocuments = idfTotalDocuments,
                watchHistoryMap = watchHistoryMap,
                channelTopicProfiles = newChannelProfiles,
                shortsVector = newShortsVector
            )

            scheduleDebouncedSave()
        }
    }

    // =================================================
    // FEATURE EXTRACTION (delegates to NeuroTokenizer)
    // =================================================

    private fun getOrExtractFeatures(
        video: Video,
        idfSnapshot: IdfSnapshot,
        channelProfile: Map<String, Double>? = null
    ): ContentVector {
        val cacheKey = video.id
        synchronized(featureCache) {
            featureCache[cacheKey]?.let { return it }
        }
        val vector = tokenizer.extractFeatures(video, idfSnapshot, channelProfile)
        synchronized(featureCache) {
            featureCache[cacheKey] = vector
        }
        return vector
    }

    private fun takeIdfSnapshot(): IdfSnapshot {
        return IdfSnapshot(
            wordFrequency = idfWordFrequency,
            totalDocs = idfTotalDocuments
        )
    }

    private suspend fun takeIdfSnapshotSafe(): IdfSnapshot {
        return brainMutex.withLock { takeIdfSnapshot() }
    }

    // =================================================
    // SEEN SHORTS
    // =================================================

    suspend fun recordSeenShorts(shortIds: List<String>) {
        if (shortIds.isEmpty()) return
        brainMutex.withLock {
            val now = System.currentTimeMillis()
            val updated = currentUserBrain.seenShortsHistory.toMutableMap()
            shortIds.forEach { id ->
                if (!updated.containsKey(id)) updated[id] = now
            }
            if (updated.size > NeuroScoring.SEEN_SHORTS_MAX) {
                val toRemove = updated.entries
                    .sortedBy { it.value }
                    .take(updated.size - NeuroScoring.SEEN_SHORTS_MAX)
                toRemove.forEach { updated.remove(it.key) }
            }
            currentUserBrain = currentUserBrain.copy(seenShortsHistory = updated)
            scheduleDebouncedSave()
        }
    }

    suspend fun getRecentlySeenShorts(): Set<String> {
        return brainMutex.withLock {
            val now = System.currentTimeMillis()
            val expiryMs = NeuroScoring.SEEN_SHORT_EXPIRY_DAYS * 24L * 60 * 60 * 1000
            currentUserBrain.seenShortsHistory
                .filter { (_, ts) -> (now - ts) < expiryMs }
                .keys
        }
    }

    // =================================================
    // EXPORT / IMPORT (delegates to NeuroStorage)
    // =================================================

    suspend fun exportBrainToStream(
        output: OutputStream
    ): Boolean {
        val brainCopy = brainMutex.withLock { currentUserBrain }
        return storage.exportToStream(brainCopy, output)
    }

    suspend fun importBrainFromStream(
        input: InputStream
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val finalBrain = storage.importFromStream(input)
                ?: return@withContext false

            brainMutex.withLock {
                currentUserBrain = finalBrain
                idfWordFrequency = finalBrain.idfWordFrequency.toMutableMap()
                idfTotalDocuments = finalBrain.idfTotalDocuments
                watchHistory.clear()
                finalBrain.watchHistoryMap.forEach { (id, pct) ->
                    watchHistory[id] = WatchEntry(pct, System.currentTimeMillis())
                }
                storage.save(currentUserBrain)
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

    // =================================================
    // PERSONA ENGINE
    // =================================================

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
            brain.totalInteractions > 500 &&
                v.pacing > 0.65 -> FlowPersona.BINGER
            v.complexity > 0.75 -> FlowPersona.SCHOLAR
            v.duration > 0.70 -> FlowPersona.DEEP_DIVER
            v.duration < 0.35 &&
                v.pacing > 0.60 -> FlowPersona.SKIMMER
            diversityIndex < 0.25 -> FlowPersona.SPECIALIST
            else -> FlowPersona.EXPLORER
        }

        val lastPersona = brain.lastPersona?.let { name ->
            FlowPersona.entries.find { it.name == name }
        }

        return if (lastPersona != null &&
            rawPersona != lastPersona &&
            brain.personaStability < NeuroScoring.PERSONA_STABILITY_THRESHOLD
        ) {
            lastPersona
        } else {
            rawPersona
        }
    }

}