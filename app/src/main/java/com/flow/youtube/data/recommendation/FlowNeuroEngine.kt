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
import kotlinx.coroutines.cancel
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
 * Flow Neuro Engine (V9.3 — Polysemy Fix + Smooth Scoring + Smart Descriptions)
 *
 * Client-side hybrid recommendation: Vector Space Model + Heuristic Rules.
 *
 * V9.3 Changes over V9.1:
 * - Fix 1: Context-aware bigram priority — polysemous words (train, model, stream, etc.)
 *   are suppressed as unigrams when they appear in a meaningful bigram. Eliminates
 *   cross-topic contamination (e.g. "train AI" no longer matches locomotive content).
 * - Fix 2: Lemma disambiguation — removed dangerous lemma mappings for polysemous words.
 *   "training", "streaming", "building", "running", "playing", "filming", "reacting",
 *   "modeling", "planting" now stay as-is instead of collapsing to ambiguous roots.
 * - Fix 3: Scoring factor consolidation — merged 19 scoring factors into ~12 grouped
 *   helper functions (calculateFreshness, calculateEngagementQuality, calculateChannelSignal).
 *   Cleaner rank() body, easier debugging, fewer interaction surprises.
 * - Fix 4: Cliff effect elimination — replaced hard thresholds with smooth functions
 *   (exponential decay for session fatigue, sigmoid for channel boredom, graduated ramps
 *   for curiosity gap and serendipity, linear interpolation for engagement floor).
 * - Fix 5: Description sponsor skip — filters out sponsor copy, social media links,
 *   hashtag lines, and URLs before extracting description keywords. First 5 content-
 *   relevant lines instead of first 200 chars.
 * - Fix 6: Blocked topic expansion precomputation — category expansion and lemmatization
 *   computed once before the filter loop instead of per-video. Includes lemmatized forms
 *   to catch morphological variants.
 * - All V9.1 fixes carried forward (impression fatigue, already-watched penalty,
 *   engagement floor, pre-compiled regex, export mutex reduction, IDF pruning).
 */
class FlowNeuroEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "FlowNeuroEngine"
        private const val BRAIN_FILENAME = "user_neuro_brain.json"
        private const val SCHEMA_VERSION = 9

        // ── Pre-compiled Regex ──
        private val WHITESPACE_REGEX = Regex("\\s+")

        // ── Scoring Weight Constants ──

        /** Subscription boost: subscribers are trusted curators, mild boost */
        private const val SUBSCRIPTION_BOOST = 0.15

        /** Serendipity bonus: novel content that still fits time context */
        private const val SERENDIPITY_BONUS = 0.10

        /** Curiosity gap: familiar topic at unexpected complexity */
        private const val CURIOSITY_GAP_BONUS = 0.10

        // V9.3: Channel boredom constants retained for sigmoid curve parameters
        /** Not-interested channel floor: recoverable but penalized */
        private const val NOT_INTERESTED_CHANNEL_FLOOR = 0.20

        /** Channel EMA alpha: slow-moving average (α=0.05) */
        private const val CHANNEL_EMA_ALPHA = 0.05
        private const val CHANNEL_EMA_DECAY = 1.0 - CHANNEL_EMA_ALPHA

        /** Max channel scores to retain (prune mediocre middle) */
        private const val MAX_CHANNEL_SCORES = 500
        private const val CHANNEL_KEEP_LOW = 50
        private const val CHANNEL_KEEP_HIGH = 200

        /** Topic vector decay: non-relevant topics decay by 3% per positive interaction */
        private const val TOPIC_DECAY_RATE = 0.97

        /** Topic floor: topics below this threshold are pruned */
        private const val TOPIC_PRUNE_THRESHOLD = 0.03

        /** Shorts learning rate multiplier: short-form engagement is noisy */
        private const val SHORTS_LEARNING_PENALTY = 0.01

        /** Max consecutive skips tracked before capping */
        private const val MAX_CONSECUTIVE_SKIPS = 30

        /** Session auto-reset thresholds */
        private const val SESSION_RESET_IDLE_MINUTES = 120L
        private const val SESSION_RESET_EMPTY_MINUTES = 30L

        /** Cold-start threshold: below this, popularity and jitter are amplified */
        private const val COLD_START_THRESHOLD = 30

        /** Onboarding warmup threshold: preferred topic boost decays over this many interactions */
        private const val ONBOARDING_WARMUP_INTERACTIONS = 50

        /** Onboarding warmup max boost */
        private const val ONBOARDING_MAX_BOOST = 0.15

        /** Engagement rate normalization: 5% like/view ratio = max boost */
        private const val ENGAGEMENT_RATE_BASELINE = 0.05
        private const val ENGAGEMENT_MAX_BOOST = 0.05
        private const val ENGAGEMENT_MIN_VIEWS = 1000L

        /** Engagement rate floor — clickbait filter for high-view low-engagement videos. */
        private const val ENGAGEMENT_FLOOR_RATE = 0.01
        private const val ENGAGEMENT_FLOOR_MIN_VIEWS = 50_000L
        private const val ENGAGEMENT_FLOOR_PENALTY = 0.2

        /** Cold-start engagement floor — tighter thresholds during first 30 interactions. */
        private const val COLD_START_ENGAGEMENT_FLOOR_RATE = 0.02
        private const val COLD_START_ENGAGEMENT_FLOOR_MIN_VIEWS = 10_000L

        /** Binge detection: after this many videos, novelty gets extra weight */
        private const val BINGE_THRESHOLD = 20
        private const val BINGE_NOVELTY_FACTOR = 0.15

        /** Jitter: randomness to prevent deterministic ordering */
        private const val JITTER_COLD_START = 0.20
        private const val JITTER_NORMAL = 0.02

        /** Title similarity threshold for duplicate detection */
        private const val TITLE_SIMILARITY_STRICT = 0.55
        private const val TITLE_SIMILARITY_RELAXED = 0.60

        /** Feature cache max size */
        private const val FEATURE_CACHE_MAX = 150

        /** IDF cold-start threshold: below this, IDF weighting is disabled */
        private const val IDF_COLD_START_DOCS = 30
        private const val IDF_MIN_WEIGHT = 0.15
        private const val IDF_MAX_WEIGHT = 1.0

        /** Session topic history max size */
        private const val SESSION_TOPIC_HISTORY_MAX = 50

        /** Topic affinity cap per video and total storage */
        private const val AFFINITY_INCREMENT = 0.02
        private const val AFFINITY_MAX = 1.0
        private const val AFFINITY_PRUNE_THRESHOLD = 0.05
        private const val AFFINITY_MAX_ENTRIES = 500
        private const val AFFINITY_KEEP_TOP = 300
        private const val AFFINITY_MAX_BOOST_PER_VIDEO = 0.15
        private const val AFFINITY_BOOST_PER_PAIR = 0.05

        /** Cosine similarity blend weights */
        private const val TOPIC_SIMILARITY_WEIGHT = 0.70
        private const val DURATION_SIMILARITY_WEIGHT = 0.10
        private const val PACING_SIMILARITY_WEIGHT = 0.10
        private const val COMPLEXITY_SIMILARITY_WEIGHT = 0.10

        /** Vector adjustment: saturation, compression, negative feedback */
        private const val NEGATIVE_PROPORTIONAL_EXPONENT = 1.5
        private const val NEGATIVE_FLOOR_FACTOR = 0.3
        private const val NEGATIVE_SCALAR_PROPORTIONAL = 0.3
        private const val NEGATIVE_SCALAR_FLOOR = 0.1
        private const val COMPRESSION_THRESHOLD = 0.6
        private const val COMPRESSION_CEILING = 0.5
        private const val COMPRESSION_FACTOR = 0.7

        /** Classic video threshold (views) */
        private const val CLASSIC_VIEW_THRESHOLD = 5_000_000L

        /** Diversity phase 1 target size */
        private const val DIVERSITY_PHASE1_TARGET = 20

        /** Complexity feature weights */
        private const val COMPLEXITY_TITLE_LEN_MAX = 80.0
        private const val COMPLEXITY_TITLE_LEN_WEIGHT = 0.4
        private const val COMPLEXITY_WORD_LEN_DIVISOR = 8.0
        private const val COMPLEXITY_WORD_LEN_WEIGHT = 0.4
        private const val COMPLEXITY_CHAPTER_BONUS = 0.2

        /** Description extraction limits */
        private const val DESCRIPTION_MIN_LENGTH = 20
        private const val DESCRIPTION_TAKE_WORDS = 15
        private const val DESCRIPTION_TAKE_LINES = 5
        private const val DESCRIPTION_LINE_MIN_LENGTH = 15
        private const val DESCRIPTION_WORD_WEIGHT = 0.2

        /** Channel keyword weight */
        private const val CHANNEL_KEYWORD_WEIGHT = 1.0

        /** Title keyword weight */
        private const val TITLE_KEYWORD_WEIGHT = 0.5

        /** Bigram weight */
        private const val BIGRAM_WEIGHT = 0.75

        // V9.3 Fix 1: Priority weight for bigrams that disambiguate polysemous words
        private const val BIGRAM_PRIORITY_WEIGHT = 1.2

        /** Not-interested learning rates */
        private const val NOT_INTERESTED_GLOBAL_RATE = -0.35
        private const val NOT_INTERESTED_TIME_RATE = -0.25
        private const val NOT_INTERESTED_SKIP_INCREMENT = 3

        /** Persona stability threshold for hysteresis */
        private const val PERSONA_STABILITY_THRESHOLD = 3
        private const val PERSONA_MAX_STABILITY = 10

        /** Exploration: max score for a topic to be considered unexplored */
        private const val EXPLORATION_SCORE_THRESHOLD = 0.1

        /** Debounce delay for saving brain state */
        private const val SAVE_DEBOUNCE_MS = 5000L

        /** Timestamp pattern for chapter detection */
        private val TIMESTAMP_PATTERN = Regex("""\d{1,2}:\d{2}""")
        private const val CHAPTER_TIMESTAMP_MIN = 3

        // ── Seen Shorts History Constants ──
        /** Max Short IDs to keep in persistent seen history */
        private const val SEEN_SHORTS_MAX = 3000

        /** Multiplier applied to Shorts seen very recently (effectively hides them) */
        private const val SEEN_SHORT_PENALTY = 0.05

        /** Days after which a seen Short fully recovers and can reappear */
        private const val SEEN_SHORT_EXPIRY_DAYS = 7

        // ── Impression Fatigue Constants ──
        private const val IMPRESSION_CACHE_MAX = 500
        private const val IMPRESSION_DECAY_RATE = 0.1
        private const val IMPRESSION_PENALTY_HEAVY = 0.05
        private const val IMPRESSION_PENALTY_MEDIUM = 0.30
        private const val IMPRESSION_PENALTY_LIGHT = 0.85
        private const val IMPRESSION_THRESHOLD_DROP = 5
        private const val IMPRESSION_THRESHOLD_HEAVY = 3
        private const val IMPRESSION_THRESHOLD_LIGHT = 1

        // ── Already-Watched Constants ──
        private const val MUSIC_REWATCH_MAX_DURATION = 480
        private const val WATCHED_PENALTY_FULL = 0.02
        private const val WATCHED_PENALTY_HALF = 0.30
        private const val WATCHED_PENALTY_SAMPLED = 0.70
        private const val WATCHED_THRESHOLD_FULL = 0.85f
        private const val WATCHED_THRESHOLD_HALF = 0.50f
        private const val WATCHED_THRESHOLD_SAMPLED = 0.15f
        private const val WATCH_HISTORY_MAX = 2000

        // ══════════════════════════════════════════════
        // V9.3 Fix 1: Polysemy Protection Data
        // ══════════════════════════════════════════════

        /**
         * Words with multiple unrelated meanings in YouTube context.
         * When these appear in a bigram, the bigram takes priority
         * and the unigram is suppressed from the topic vector.
         *
         * Example: "train" can mean "training/learning" or "locomotive".
         * In "train ai", the bigram "train ai" captures the ML meaning,
         * and the bare "train" unigram is NOT added to prevent locomotive
         * content from matching.
         */
        private val POLYSEMOUS_WORDS = hashSetOf(
            "train", "model", "build", "plant", "stream",
            "react", "design", "film", "run", "play",
            "cook", "fire", "spring", "match", "cell",
            "power", "drive", "board", "frame", "scale",
            "lead", "light", "block", "drop", "track",
            "craft", "host", "mine", "pitch", "wave",
            "bass", "bow", "clip", "dart", "fan",
            "gear", "jam", "kit", "lab", "log",
            "net", "pad", "port", "rig", "set",
            "tap", "tip", "web"
        )

        /**
         * Known compound terms where the bigram meaning differs
         * significantly from the sum of its parts. These always
         * suppress their constituent unigrams.
         */
        private val COMPOUND_TERMS = hashSetOf(
            // AI/ML
            "train model", "train ai", "machine learn",
            "deep learn", "neural network", "train data",
            "fine tune", "train network", "train system",
            "build model", "build ai", "build network",
            "model train", "model ai", "model build",
            // Programming
            "react native", "react component", "react hook",
            "react app", "react tutorial", "react project",
            "build system", "build tool", "build project",
            "run test", "run code", "run server", "run script",
            "design pattern", "design system", "web design",
            "game design", "sound design",
            // Specific disambiguations
            "power plant", "plant base", "plant based",
            "fire base", "fire wall", "fire fight",
            "spring boot", "spring framework", "spring board",
            "stream deck", "stream lab", "stream setup",
            "film make", "film edit", "film score",
            "scale model",
            "block chain", "block list",
            "host server", "host name",
            "web development", "web app", "web site",
            "net work", "net worth",
            // Music disambiguations
            "bass guitar", "bass boost", "bass drop",
            "drum track", "sound track", "race track",
            // Gaming
            "speed run", "play through",
            "build guide", "build order",
            "craft recipe", "mine craft"
        )

        // ══════════════════════════════════════════════
        // V9.3 Fix 5: Sponsor/Meta Line Detection
        // ══════════════════════════════════════════════

        /**
         * Patterns that indicate a description line is sponsor copy,
         * social media links, or call-to-action filler rather than
         * actual content description. Lines matching any of these
         * are skipped during keyword extraction.
         */
        private val SPONSOR_LINE_PATTERNS = listOf(
            "use code ", "% off", "free trial", "link in",
            "sponsored by", "brought to you", "check out",
            "sign up", "discount", "promo code", "coupon",
            "affiliate", "partner", "merch", "merchandise",
            "patreon", "ko-fi", "buymeacoffee", "buy me a coffee",
            "subscribe", "follow me", "social media",
            "instagram", "twitter", "tiktok", "discord",
            "join the", "become a member", "membership",
            "business inquiries", "business email", "contact:",
            "►", "→", "⬇", "⇩", "👇",
            "timestamps:", "chapters:"
        )

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

    // IDF tracking — persisted in brain state
    private var idfWordFrequency = mutableMapOf<String, Int>()
    private var idfTotalDocuments = 0

    // ── Impression Fatigue Cache ──
    private data class ImpressionEntry(var count: Int, var lastSeen: Long)

    private val impressionCache = object : LinkedHashMap<String, ImpressionEntry>(
        IMPRESSION_CACHE_MAX + 50, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ImpressionEntry>?
        ): Boolean = size > IMPRESSION_CACHE_MAX
    }

    private data class WatchEntry(val percentWatched: Float, val timestamp: Long)

    private val watchHistory = LinkedHashMap<String, WatchEntry>(
        WATCH_HISTORY_MAX + 50, 0.75f, true
    )

    private var currentUserBrain: UserBrain = UserBrain()
    private var isInitialized = false

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

        fun isOlderThan24Hours(dateText: String): Boolean {
            val text = dateText.lowercase()
            return when {
                text.contains("second") || text.contains("minute") ||
                    text.contains("hour") -> false
                text.contains("day") || text.contains("week") ||
                    text.contains("month") || text.contains("year") -> true
                else -> true
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
        "animating" to "animation", "animated" to "animation",
        "animations" to "animation", "animator" to "animation",
        // Fitness
        "workouts" to "workout", "exercising" to "exercise",
        "exercises" to "exercise", "exercised" to "exercise",
        "learning" to "learn", "learned" to "learn",
        "learner" to "learn", "learners" to "learn",
        "teaching" to "teach", "teacher" to "teach",
        "teachers" to "teach", "taught" to "teach",
        "studying" to "study", "studies" to "study",
        "studied" to "study", "tutorials" to "tutorial",
        "making" to "make", "maker" to "make", "makers" to "make",
        "makes" to "make", "made" to "make",
        "reviewing" to "review", "reviewed" to "review",
        "reviews" to "review", "reviewer" to "review",
        "testing" to "test", "tested" to "test", "tests" to "test",
        "tester" to "test",
        "editing" to "edit", "edited" to "edit", "edits" to "edit",
        "editor" to "edit",
        "traveling" to "travel", "travelled" to "travel",
        "travels" to "travel", "traveler" to "travel",
        "vlogging" to "vlog", "vlogs" to "vlog", "vlogger" to "vlog",
        "vloggers" to "vlog",
        "reactions" to "reaction",
        "compilations" to "compilation",
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
        "animals" to "animal",
        // Lifestyle
        "recipes" to "recipe", "baking" to "bake", "baked" to "bake",
        "baker" to "bake",
        "gardening" to "garden", "gardens" to "garden",
        "photographing" to "photography",
        "photographs" to "photography",
        "photographer" to "photography",
        // Common verbs
        "explained" to "explain", "explains" to "explain",
        "explaining" to "explain",
        "created" to "create", "creates" to "create",
        "creating" to "create", "creator" to "create",
        "creators" to "create",
        "discovered" to "discover", "discovers" to "discover",
        "discovering" to "discover",
        "exploring" to "explore", "explored" to "explore",
        "explores" to "explore",
        "comparing" to "compare", "compared" to "compare",
        "compares" to "compare", "comparison" to "compare",
        "comparisons" to "compare",
        // Plurals
        "videos" to "video", "channels" to "channel",
        "episodes" to "episode",
        "movies" to "movie", "documentaries" to "documentary",
        "podcasts" to "podcast", "interviews" to "interview",
        "challenges" to "challenge",
        "montages" to "montage"
    )

    private fun normalizeLemma(word: String): String =
        LEMMA_MAP[word.lowercase()] ?: word.lowercase()

    // MUSIC DETECTION

    private val MUSIC_KEYWORDS = setOf(
        "music", "song", "lyrics", "remix", "lofi", "lo-fi",
        "playlist", "official audio", "official video",
        "music video", "feat", "ft.", "acoustic", "cover",
        "karaoke", "instrumental", "beat", "rap", "hip hop",
        "pop", "rock", "jazz", "classical", "edm", "mix"
    )

    private fun isMusicTrack(video: Video): Boolean {
        if (video.duration > MUSIC_REWATCH_MAX_DURATION) return false
        val titleLower = video.title.lowercase()
        val channelLower = video.channelName.lowercase()
        return MUSIC_KEYWORDS.any { keyword ->
            titleLower.contains(keyword) || channelLower.contains(keyword)
        }
    }

    // =================================================
    // TITLE SIMILARITY
    // =================================================

    private fun calculateTitleSimilarity(
        title1: String,
        title2: String
    ): Double {
        val words1 = tokenizeForSimilarity(title1)
        val words2 = tokenizeForSimilarity(title2)
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return if (union > 0) intersection.toDouble() / union else 0.0
    }

    private fun tokenizeForSimilarity(text: String): Set<String> {
        return text.lowercase()
            .split(WHITESPACE_REGEX)
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length > 2 }
            .map { normalizeLemma(it) }
            .filter { !STOP_WORDS.contains(it) }
            .toSet()
    }

    private fun isVideoClassic(viewCount: Long): Boolean =
        viewCount >= CLASSIC_VIEW_THRESHOLD

    // =================================================
    // IDF WEIGHT CALCULATION
    // =================================================

    private data class IdfSnapshot(
        val wordFrequency: Map<String, Int>,
        val totalDocs: Int
    )

    private fun takeIdfSnapshot(): IdfSnapshot {
        return IdfSnapshot(
            wordFrequency = idfWordFrequency.toMap(),
            totalDocs = idfTotalDocuments
        )
    }

    private fun calculateIdfWeight(
        word: String,
        baseWeight: Double,
        idfSnapshot: IdfSnapshot
    ): Double {
        if (idfSnapshot.totalDocs < IDF_COLD_START_DOCS) return baseWeight

        val df = idfSnapshot.wordFrequency[word] ?: 0
        val idf = ln(1.0 + idfSnapshot.totalDocs.toDouble() / (df + 1.0))
        val maxIdf = ln(1.0 + idfSnapshot.totalDocs.toDouble())
        val normalizedIdf = (idf / maxIdf).coerceIn(IDF_MIN_WEIGHT, IDF_MAX_WEIGHT)

        return baseWeight * normalizedIdf
    }

    // =================================================
    // PACING KEYWORD SETS
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
        val pacing: Double = 0.5,
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
        val idfWordFrequency: Map<String, Int> = emptyMap(),
        val idfTotalDocuments: Int = 0,
        val watchHistoryMap: Map<String, Float> = emptyMap(),
        val seenShortsHistory: Map<String, Long> = emptyMap(),
        val schemaVersion: Int = SCHEMA_VERSION
    )

    // =================================================
    // 2. PUBLIC API
    // =================================================

    suspend fun initialize() {
        brainMutex.withLock {
            if (isInitialized) return

            val loaded = loadBrainFromDataStore()
            if (!loaded) {
                loadLegacyBrain()
                saveBrainToDataStore()
                val legacyFile = File(appContext.filesDir, BRAIN_FILENAME)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                    Log.i(TAG, "Migrated legacy brain to DataStore")
                }
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
            saveBrainToDataStore()
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
    }

    fun getSessionDurationMinutes(): Long =
        (System.currentTimeMillis() - sessionStartTime) / 60_000L

    private fun scheduleDebouncedSave() {
        pendingSaveJob?.cancel()
        pendingSaveJob = saveScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            brainMutex.withLock {
                saveBrainToDataStore()
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
            val lemma = normalizeLemma(normalized)

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
            saveBrainToDataStore()
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
            saveBrainToDataStore()
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
            saveBrainToDataStore()
        }
    }

    suspend fun unblockChannel(channelId: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                blockedChannels = currentUserBrain.blockedChannels - channelId
            )
            saveBrainToDataStore()
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

    suspend fun setPreferredTopics(topics: Set<String>) {
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
            saveBrainToDataStore()
        }
    }

    suspend fun addPreferredTopic(topic: String) {
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
            saveBrainToDataStore()
        }
    }

    suspend fun removePreferredTopic(topic: String) {
        brainMutex.withLock {
            currentUserBrain = currentUserBrain.copy(
                preferredTopics = currentUserBrain.preferredTopics - topic
            )
            saveBrainToDataStore()
        }
    }

    suspend fun completeOnboarding(selectedTopics: Set<String>) {
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
            saveBrainToDataStore()
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
                val tokens = tokenize(name)
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
                    newAffinities[key] = (current + 0.15).coerceAtMost(AFFINITY_MAX)
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

            saveBrainToDataStore()
            Log.i(
                TAG,
                "Bootstrap: seeded ${topicWeights.size} topics from " +
                    "${channelNames.size} subscriptions " +
                    "(top: ${topKeywords.take(5).joinToString()})"
            )
        }
    }

    // V9.4: Bootstrap the recommendation brain from imported watch history.
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
                val videoVector = extractFeatures(video, idfSnapshot)

                val newGlobal = adjustVector(
                    updatedBrain.globalVector, videoVector, historyLearningRate
                )

                val bucket = TimeBucket.current()
                val currentBucketVec = updatedBrain.timeVectors[bucket] ?: ContentVector()
                val newBucketVec = adjustVector(
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
                                .coerceAtMost(AFFINITY_MAX)
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

            saveBrainToDataStore()
            featureCache.clear()

            Log.i(TAG, "History bootstrap: processed ${toProcess.size} videos, " +
                "${updatedBrain.globalVector.topics.size} topics learned")
        }
    }

    suspend fun markNotInterested(video: Video) {
        val videoVector = getOrExtractFeatures(video, takeIdfSnapshotSafe())

        brainMutex.withLock {
            val newGlobal = adjustVector(
                currentUserBrain.globalVector, videoVector,
                NOT_INTERESTED_GLOBAL_RATE
            )

            val newChannelScores = currentUserBrain.channelScores.toMutableMap()
            newChannelScores[video.channelId] = NOT_INTERESTED_CHANNEL_FLOOR

            val bucket = TimeBucket.current()
            val currentBucketVec = currentUserBrain.timeVectors[bucket]
                ?: ContentVector()
            val newBucketVec = adjustVector(
                currentBucketVec, videoVector, NOT_INTERESTED_TIME_RATE
            )

            val newSkips = (currentUserBrain.consecutiveSkips +
                NOT_INTERESTED_SKIP_INCREMENT)
                .coerceAtMost(MAX_CONSECUTIVE_SKIPS)

            currentUserBrain = currentUserBrain.copy(
                globalVector = newGlobal,
                timeVectors = currentUserBrain.timeVectors +
                    (bucket to newBucketVec),
                channelScores = newChannelScores,
                totalInteractions = currentUserBrain.totalInteractions + 1,
                consecutiveSkips = newSkips
            )
            saveBrainToDataStore()
        }
    }

    // =================================================
    // DISCOVERY QUERY GENERATION
    // =================================================

    suspend fun generateDiscoveryQueries(): List<String> =
        brainMutex.withLock {
            val interests = currentUserBrain.globalVector.topics
            val queries = mutableListOf<String>()

            val sortedInterests = interests.entries
                .sortedByDescending { it.value }
                .take(6)
                .map { it.key }

            queries.addAll(sortedInterests.take(2))

            val timeJitter = listOf(
                "", "2024", "2025", "new", "best", "top"
            ).random()

            if (sortedInterests.size >= 3) {
                queries.add(
                    "${sortedInterests[0]} ${sortedInterests[1]} $timeJitter"
                        .trim()
                )
                queries.add("${sortedInterests[0]} ${sortedInterests[2]}")
                queries.add("${sortedInterests[1]} ${sortedInterests[2]}")
            } else if (sortedInterests.size >= 2) {
                queries.add(
                    "${sortedInterests[0]} ${sortedInterests[1]} $timeJitter"
                        .trim()
                )
            }

            currentUserBrain.topicAffinities.entries
                .sortedByDescending { it.value }
                .take(2)
                .forEach { (key, _) ->
                    val parts = key.split("|")
                    if (parts.size == 2) {
                        queries.add("${parts[0]} ${parts[1]}")
                    }
                }

            val bucket = TimeBucket.current()
            val currentBucketVec = currentUserBrain.timeVectors[bucket]
                ?: ContentVector()
            currentBucketVec.topics.maxByOrNull { it.value }?.key
                ?.let { queries.add(it) }

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

            getExplorationQueries(currentUserBrain).forEach {
                queries.add(it)
            }

            if (queries.isEmpty()) {
                val preferred = currentUserBrain.preferredTopics.toList()
                if (preferred.isNotEmpty()) {
                    return@withLock preferred.shuffled().take(5)
                }
                return@withLock listOf(
                    "Music", "Science", "Technology",
                    "Education", "Nature"
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

    fun getSnowballSeeds(
        recentlyWatched: List<Video>,
        count: Int = 3
    ): List<String> {
        return recentlyWatched
            .take(count)
            .map { it.id }
    }

    /**
     * Unified channel signal combining subscription boost and channel boredom.
     *
     * V9.3 Fix 4: Channel boredom uses a sigmoid curve instead of a hard
     * threshold. Smooth transition: 0% click rate → 0.4x, 5% → 0.7x,
     * 10% → 0.9x, 20%+ → ~1.0x.
     */
    private fun calculateChannelSignal(
        video: Video,
        brain: UserBrain,
        userSubs: Set<String>
    ): Double {
        var signal = 0.0

        // Subscription boost
        val isSub = userSubs.contains(video.channelId)
        if (isSub) {
            val isShort = video.isShort || (video.duration in 1..120 && !video.isLive)
            signal += if (isShort) SUBSCRIPTION_BOOST * 3.0 else SUBSCRIPTION_BOOST
        }

        // V9.3 Fix 4: Sigmoid channel boredom
        if (brain.channelScores.containsKey(video.channelId)) {
            val channelClickRate = brain.channelScores[video.channelId] ?: 0.5
            // Sigmoid: 0% → 0.4x, 5% → ~0.7x, 10% → ~0.9x, 20%+ → ~1.0x
            val channelQuality = 1.0 / (1.0 + exp(-80.0 * (channelClickRate - 0.06)))
            val channelMultiplier = 0.4 + 0.6 * channelQuality
            // Encode as additive penalty/bonus relative to 1.0
            signal += (channelMultiplier - 1.0)
        }

        return signal
    }

    /**
     * Unified engagement quality signal combining positive boost and
     * clickbait floor filter.
     *
     * V9.3 Fix 3: Merged from two separate factors.
     * V9.3 Fix 4: Engagement floor uses graduated linear interpolation
     * instead of a cliff (rate=0 → 0.2x, rate=floorRate → 1.0x).
     *
     * Returns a multiplier:
     *   >1.0 for high-engagement content (boost)
     *   1.0 for normal/unknown engagement
     *   <1.0 for suspiciously low engagement (clickbait filter)
     */
    private fun calculateEngagementQuality(
        video: Video,
        isColdStart: Boolean
    ): Double {
        val views = video.viewCount
        val likes = video.likeCount

        if (views < ENGAGEMENT_MIN_VIEWS || likes < 0) return 1.0

        val rate = likes.toDouble() / views.toDouble()

        // ── Floor: clickbait filter ──
        val floorMinViews = if (isColdStart) COLD_START_ENGAGEMENT_FLOOR_MIN_VIEWS
                            else ENGAGEMENT_FLOOR_MIN_VIEWS
        val floorRate = if (isColdStart) COLD_START_ENGAGEMENT_FLOOR_RATE
                        else ENGAGEMENT_FLOOR_RATE

        if (views > floorMinViews &&
            TimeDecay.isOlderThan24Hours(video.uploadDate) &&
            rate < floorRate
        ) {
            // V9.3 Fix 4: Graduated penalty instead of cliff
            // rate=0 → ENGAGEMENT_FLOOR_PENALTY, rate=floorRate → 1.0
            return (ENGAGEMENT_FLOOR_PENALTY +
                (1.0 - ENGAGEMENT_FLOOR_PENALTY) * (rate / floorRate))
                .coerceIn(ENGAGEMENT_FLOOR_PENALTY, 1.0)
        }

        // ── Boost: high engagement ──
        val boost = (rate / ENGAGEMENT_RATE_BASELINE)
            .coerceIn(0.0, 1.0) * ENGAGEMENT_MAX_BOOST
        return 1.0 + boost
    }

    /**
     * Unified freshness factor combining:
     * - Session topic repetition (fatigue)
     * - Impression fatigue (exponentially decaying)
     * - Session momentum (positive for 1-2 repeats, negative for 3+)
     * - Binge novelty injection (when session is long)
     *
     * V9.3 Fix 3: Merged from 4 separate factors (session fatigue,
     * session affinity, binge detection, impression fatigue).
     * V9.3 Fix 4: Session fatigue uses exponential decay instead of
     * stepped thresholds.
     *
     * Returns a multiplier in [0.05, 1.3] range.
     */
    private fun calculateFreshness(
        video: Video,
        videoVector: ContentVector,
        personalityScore: Double,
        sessionTopics: List<String>,
        sessionVideoCount: Int,
        impressionEntry: ImpressionEntry?,
        now: Long
    ): Double {
        val primaryTopic = videoVector.topics.maxByOrNull { it.value }?.key ?: ""

        // ── Session topic momentum ──
        // V9.3 Fix 4: Exponential decay replaces stepped thresholds
        // count=0→1.0, count=1→0.85, count=3→0.61, count=5→0.44, count=8→0.27
        val topicSessionCount = if (primaryTopic.isNotEmpty()) {
            sessionTopics.count { it == primaryTopic }
        } else 0

        val momentumFactor = if (topicSessionCount > 0 && primaryTopic.isNotEmpty()) {
            exp(-0.16 * topicSessionCount).coerceIn(0.15, 1.0)
        } else 1.0

        // ── Impression decay ──
        val impressionFactor = if (impressionEntry != null) {
            val hoursSince = (now - impressionEntry.lastSeen) / 3_600_000.0
            val decayedCount = (impressionEntry.count *
                exp(-IMPRESSION_DECAY_RATE * hoursSince)).toInt()
            when {
                decayedCount >= IMPRESSION_THRESHOLD_DROP -> IMPRESSION_PENALTY_HEAVY
                decayedCount >= IMPRESSION_THRESHOLD_HEAVY -> IMPRESSION_PENALTY_MEDIUM
                decayedCount >= IMPRESSION_THRESHOLD_LIGHT -> IMPRESSION_PENALTY_LIGHT
                else -> 1.0
            }
        } else 1.0

        // ── Binge novelty injection ──
        val bingeFactor = if (sessionVideoCount > BINGE_THRESHOLD) {
            val noveltyScore = 1.0 - personalityScore
            1.0 + (noveltyScore * BINGE_NOVELTY_FACTOR)
        } else 1.0

        return (momentumFactor * impressionFactor * bingeFactor)
            .coerceIn(0.05, 1.3)
    }

    /**
     * V9.3 Fix 4: Smooth curiosity gap with graduated ramps
     * instead of binary threshold.
     *
     * Ramps from 0 at complexityDiff=0.2 to full bonus at 0.5.
     * Scaled by topic safety (personalityScore 0.5→0, 0.8→full).
     */
    private fun calculateCuriosityBonus(
        personalityScore: Double,
        brainComplexity: Double,
        videoComplexity: Double
    ): Double {
        if (personalityScore <= 0.5) return 0.0

        val complexityDiff = abs(brainComplexity - videoComplexity)
        if (complexityDiff <= 0.2) return 0.0

        // Ramps: 0 at diff=0.2, full at diff=0.5
        val curiosityRamp = ((complexityDiff - 0.2) / 0.3).coerceIn(0.0, 1.0)
        // Scale by how safe the topic match is (0.5→0, 0.8→full)
        val topicSafety = ((personalityScore - 0.5) / 0.3).coerceIn(0.0, 1.0)
        return CURIOSITY_GAP_BONUS * curiosityRamp * topicSafety
    }

    /**
     * V9.3 Fix 4: Smooth serendipity with graduated ramps
     * instead of binary threshold.
     *
     * Both novelty and context contribute proportionally.
     */
    private fun calculateSerendipity(
        noveltyScore: Double,
        contextScore: Double
    ): Double {
        // Ramps: novelty 0.4→0, 0.8→full; context 0.3→0, 0.7→full
        val noveltyRamp = ((noveltyScore - 0.4) / 0.4).coerceIn(0.0, 1.0)
        val contextRamp = ((contextScore - 0.3) / 0.4).coerceIn(0.0, 1.0)
        return SERENDIPITY_BONUS * noveltyRamp * contextRamp
    }

    /**
     * Already-watched penalty with music exception.
     * Returns a multiplier in [0.02, 1.0].
     */
    private fun calculateWatchedPenalty(
        video: Video,
        watchEntry: WatchEntry?
    ): Double {
        if (watchEntry == null) return 1.0

        val isMusic = isMusicTrack(video)
        return when {
            isMusic && watchEntry.percentWatched > WATCHED_THRESHOLD_HALF -> 1.0
            watchEntry.percentWatched > WATCHED_THRESHOLD_FULL -> WATCHED_PENALTY_FULL
            watchEntry.percentWatched > WATCHED_THRESHOLD_HALF -> WATCHED_PENALTY_HALF
            watchEntry.percentWatched > WATCHED_THRESHOLD_SAMPLED -> WATCHED_PENALTY_SAMPLED
            else -> 1.0
        }
    }

    // =================================================
    // MAIN RANKING FUNCTION
    // V9.3 Fix 3: Consolidated scoring — ~12 grouped factors
    // V9.3 Fix 4: All smooth scoring functions
    // V9.3 Fix 6: Blocked topic expansion precomputed
    // =================================================

    suspend fun rank(
        candidates: List<Video>,
        userSubs: Set<String>
    ): List<Video> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Session staleness auto-reset
        val sessionAgeMinutes = getSessionDurationMinutes()
        if (sessionAgeMinutes > SESSION_RESET_IDLE_MINUTES ||
            (sessionAgeMinutes > SESSION_RESET_EMPTY_MINUTES &&
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

        // ══════════════════════════════════════════════
        // V9.3 Fix 6: Precompute blocked topic expansion ONCE
        // before the filter loop. Includes lemmatized forms.
        // ══════════════════════════════════════════════
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
            setOf(keyword, normalizeLemma(keyword))
        }

        // Pre-filter blocked content
        val filtered = candidates.filter { video ->
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
            video to getOrExtractFeatures(video, idfSnapshot)
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
        val isColdStart = brain.totalInteractions < COLD_START_THRESHOLD
        val isOnboarding = brain.totalInteractions < ONBOARDING_WARMUP_INTERACTIONS
        val onboardingWarmup = if (isOnboarding) {
            1.0 - (brain.totalInteractions /
                ONBOARDING_WARMUP_INTERACTIONS.toDouble()) * 0.5
        } else 0.5

        val scored = videoVectors.map { (video, videoVector) ->
            val personalityScore = calculateCosineSimilarity(
                brain.globalVector, videoVector
            )
            val contextScore = calculateCosineSimilarity(
                timeContextVector, videoVector
            )
            val noveltyScore = 1.0 - personalityScore

            // ── Base similarity score ──
            var totalScore = (personalityScore * wPersonality) +
                (contextScore * wContext) +
                (noveltyScore * wNovelty)

            // ── Topic affinity boost ──
            val videoTopics = videoVector.topics.keys.toList()
            var affinityBoost = 0.0
            for (i in videoTopics.indices) {
                for (j in i + 1 until videoTopics.size) {
                    val key = makeAffinityKey(videoTopics[i], videoTopics[j])
                    val affinity = brain.topicAffinities[key] ?: 0.0
                    affinityBoost += affinity * AFFINITY_BOOST_PER_PAIR
                }
            }
            totalScore += affinityBoost.coerceAtMost(AFFINITY_MAX_BOOST_PER_VIDEO)

            // ── V9.3 Fix 3: Channel signal (sub boost + boredom) ──
            val channelSignal = calculateChannelSignal(video, brain, userSubs)
            totalScore += channelSignal

            // ── V9.3 Fix 4: Smooth serendipity ──
            totalScore += calculateSerendipity(noveltyScore, contextScore)

            // ── Cold-start popularity ──
            if (isColdStart && video.viewCount > 0) {
                val popularityBoost =
                    log10(1.0 + video.viewCount.toDouble()) / 10.0 * 0.05
                totalScore += popularityBoost
            }

            // ── V9.3 Fix 3+4: Unified engagement quality ──
            val engagementQuality = calculateEngagementQuality(video, isColdStart)
            totalScore *= engagementQuality

            // ── Time decay ──
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

            // ── V9.3 Fix 4: Smooth curiosity gap ──
            totalScore += calculateCuriosityBonus(
                personalityScore,
                brain.globalVector.complexity,
                videoVector.complexity
            )

            // ── V9.3 Fix 3+4: Unified freshness (session fatigue + impressions + binge) ──
            val freshness = calculateFreshness(
                video, videoVector, personalityScore,
                sessionTopics, sessionVideoCount,
                impressionSnapshot[video.id], now
            )
            totalScore *= freshness

            // ── Onboarding warmup (applied AFTER freshness, additive) ──
            if (isOnboarding) {
                val hasPreferred = brain.preferredTopics.any { pref ->
                    videoVector.topics.containsKey(normalizeLemma(pref))
                }
                if (hasPreferred) {
                    totalScore += onboardingWarmup * ONBOARDING_MAX_BOOST
                }
            }

            // ── Already-watched penalty ──
            val watchedPenalty = calculateWatchedPenalty(
                video, watchHistorySnapshot[video.id]
            )
            totalScore *= watchedPenalty

            // ── Seen Shorts penalty ──
            if (video.isShort) {
                val seenTimestamp = brain.seenShortsHistory[video.id]
                if (seenTimestamp != null) {
                    val daysSinceSeen = (now - seenTimestamp) / (24.0 * 60 * 60 * 1000)
                    if (daysSinceSeen < SEEN_SHORT_EXPIRY_DAYS) {
                        val recovery = (daysSinceSeen / SEEN_SHORT_EXPIRY_DAYS).coerceIn(0.0, 1.0)
                        val seenPenalty = SEEN_SHORT_PENALTY + (1.0 - SEEN_SHORT_PENALTY) * recovery
                        totalScore *= seenPenalty
                    }
                }
            }

            // ── Jitter ──
            val jitter = if (brain.totalInteractions <
                ONBOARDING_WARMUP_INTERACTIONS
            ) {
                random.nextDouble() * JITTER_COLD_START
            } else {
                random.nextDouble() * JITTER_NORMAL
            }

            ScoredVideo(video, totalScore + jitter, videoVector)
        }.toMutableList()

        // Apply diversity reranking
        val result = applySmartDiversity(scored)

        // Log impressions for all returned videos
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
            learningRate *= SHORTS_LEARNING_PENALTY
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
            val newChScore = (currentChScore * CHANNEL_EMA_DECAY) +
                (outcome * CHANNEL_EMA_ALPHA)
            var newChannelScores = currentUserBrain.channelScores +
                (video.channelId to newChScore)

            // Channel pruning
            if (newChannelScores.size > MAX_CHANNEL_SCORES) {
                val sorted = newChannelScores.entries.sortedBy { it.value }
                val keepLow = sorted.take(CHANNEL_KEEP_LOW)
                val keepHigh = sorted.takeLast(CHANNEL_KEEP_HIGH)
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
                        .coerceAtMost(MAX_CONSECUTIVE_SKIPS)
            }

            // 5. Topic co-occurrence
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
                                (current + AFFINITY_INCREMENT)
                                    .coerceAtMost(AFFINITY_MAX)
                        }
                    }
                    newAffinities = mutableAffinities
                        .filter { it.value > AFFINITY_PRUNE_THRESHOLD }
                    if (newAffinities.size > AFFINITY_MAX_ENTRIES) {
                        newAffinities = newAffinities.entries
                            .sortedByDescending { it.value }
                            .take(AFFINITY_KEEP_TOP)
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
                    .coerceAtMost(PERSONA_MAX_STABILITY)
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

            if (learningRate > 0) {
                impressionCache.remove(video.id)
            }

            if (interactionType == InteractionType.WATCHED &&
                percentWatched > WATCHED_THRESHOLD_SAMPLED
            ) {
                val existing = watchHistory[video.id]
                if (existing == null ||
                    percentWatched > existing.percentWatched
                ) {
                    watchHistory[video.id] = WatchEntry(
                        percentWatched, System.currentTimeMillis()
                    )
                    while (watchHistory.size > WATCH_HISTORY_MAX) {
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
                watchHistoryMap = watchHistoryMap
            )

            scheduleDebouncedSave()
        }
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

    private fun getOrExtractFeatures(
        video: Video,
        idfSnapshot: IdfSnapshot
    ): ContentVector {
        val cacheKey = video.id
        synchronized(featureCache) {
            featureCache[cacheKey]?.let { return it }
        }
        val vector = extractFeatures(video, idfSnapshot)
        synchronized(featureCache) {
            featureCache[cacheKey] = vector
        }
        return vector
    }

    private suspend fun takeIdfSnapshotSafe(): IdfSnapshot {
        return brainMutex.withLock { takeIdfSnapshot() }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(WHITESPACE_REGEX)
            .map { word -> word.trim { !it.isLetterOrDigit() } }
            .filter { it.length > 2 }
            .map { normalizeLemma(it) }
            .filter { !STOP_WORDS.contains(it) }
    }

    // ══════════════════════════════════════════════════
    // V9.3 Fix 1: Context-Aware Feature Extraction
    // Bigrams containing polysemous words suppress their
    // constituent unigrams to prevent cross-topic contamination.
    // ══════════════════════════════════════════════════

    private fun extractFeatures(
        video: Video,
        idfSnapshot: IdfSnapshot
    ): ContentVector {
        val topics = mutableMapOf<String, Double>()

        val titleWords = tokenize(video.title)
        val chWords = tokenize(video.channelName)

        // Channel keywords (unchanged — channel names rarely have polysemy issues)
        chWords.forEach {
            topics[it] = calculateIdfWeight(
                it, CHANNEL_KEYWORD_WEIGHT, idfSnapshot
            )
        }

        // ── V9.3 Fix 1: Extract bigrams FIRST, track which unigram indices are "claimed" ──
        val claimedByBigram = mutableSetOf<Int>()

        if (titleWords.size >= 2) {
            for (i in 0 until titleWords.size - 1) {
                val bigram = "${titleWords[i]} ${titleWords[i + 1]}"

                val isMeaningful = COMPOUND_TERMS.contains(bigram) ||
                    POLYSEMOUS_WORDS.contains(titleWords[i]) ||
                    POLYSEMOUS_WORDS.contains(titleWords[i + 1])

                if (isMeaningful) {
                    topics[bigram] = calculateIdfWeight(
                        bigram, BIGRAM_PRIORITY_WEIGHT, idfSnapshot
                    )
                    claimedByBigram.add(i)
                    claimedByBigram.add(i + 1)
                } else {
                    topics[bigram] = calculateIdfWeight(
                        bigram, BIGRAM_WEIGHT, idfSnapshot
                    )
                }
            }
        }

        // ── Title unigrams: SKIP words claimed by meaningful bigrams ──
        titleWords.forEachIndexed { index, word ->
            if (index !in claimedByBigram) {
                topics[word] = (topics.getOrDefault(word, 0.0) +
                    calculateIdfWeight(word, TITLE_KEYWORD_WEIGHT, idfSnapshot))
            }
        }

        // ── V9.3 Fix 5: Smart description extraction ──
        val descriptionTopics = extractDescriptionKeywords(
            video.description, idfSnapshot
        )
        descriptionTopics.forEach { (word, weight) ->
            topics[word] = (topics.getOrDefault(word, 0.0) + weight)
        }

        // Normalize topic vector
        val normalized = if (topics.isNotEmpty()) {
            var magnitude = 0.0
            topics.values.forEach { magnitude += it * it }
            magnitude = sqrt(magnitude)
            if (magnitude > 0) topics.mapValues { (_, v) -> v / magnitude }
            else topics
        } else topics

        val durationSec = when {
            video.duration > 0 -> video.duration
            video.isLive -> 3600
            else -> 300
        }
        val durationScore = (ln(1.0 + durationSec) /
            ln(1.0 + 7200.0)).coerceIn(0.0, 1.0)

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

        val description = video.description
        val hasChapters = if (!description.isNullOrBlank()) {
            TIMESTAMP_PATTERN.findAll(description).count() >= CHAPTER_TIMESTAMP_MIN
        } else false

        val rawTitleWords = video.title.split(WHITESPACE_REGEX)
            .filter { it.length > 1 }

        val complexityScore = run {
            val titleLenFactor = (video.title.length /
                COMPLEXITY_TITLE_LEN_MAX).coerceIn(0.0, COMPLEXITY_TITLE_LEN_WEIGHT)
            val avgWordLen = if (rawTitleWords.isNotEmpty()) {
                rawTitleWords.map { it.length }.average()
            } else 4.0
            val wordLenFactor = (avgWordLen /
                COMPLEXITY_WORD_LEN_DIVISOR).coerceIn(0.0, COMPLEXITY_WORD_LEN_WEIGHT)
            val chapterBonus = if (hasChapters) COMPLEXITY_CHAPTER_BONUS else 0.0
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

    // ══════════════════════════════════════════════════
    // V9.3 Fix 5: Smart Description Keyword Extraction
    // Filters out sponsor copy, social media links,
    // hashtag lines, and URLs before extracting keywords.
    // ══════════════════════════════════════════════════

    private fun extractDescriptionKeywords(
        description: String?,
        idfSnapshot: IdfSnapshot
    ): Map<String, Double> {
        if (description.isNullOrBlank() || description.length < DESCRIPTION_MIN_LENGTH) {
            return emptyMap()
        }

        val contentLines = description.lines()
            .filter { line ->
                val lower = line.lowercase().trim()
                line.trim().length > DESCRIPTION_LINE_MIN_LENGTH &&
                !SPONSOR_LINE_PATTERNS.any { pattern -> lower.contains(pattern) } &&
                !lower.contains("http") &&
                !lower.trimStart().startsWith("#") &&
                !(line.trim().length > 5 && line.trim() == line.trim().uppercase())
            }
            .take(DESCRIPTION_TAKE_LINES)
            .joinToString(" ")

        if (contentLines.isBlank()) return emptyMap()

        val words = tokenize(contentLines).take(DESCRIPTION_TAKE_WORDS)
        val result = mutableMapOf<String, Double>()
        words.forEach { word ->
            result[word] = (result.getOrDefault(word, 0.0) +
                calculateIdfWeight(word, DESCRIPTION_WORD_WEIGHT, idfSnapshot))
        }
        return result
    }

    private fun calculateCosineSimilarity(
        user: ContentVector,
        content: ContentVector
    ): Double {
        val (smallMap, largeMap) = if (
            user.topics.size <= content.topics.size
        ) user.topics to content.topics
        else content.topics to user.topics

        val durationSim = 1.0 - abs(user.duration - content.duration)
        val pacingSim = 1.0 - abs(user.pacing - content.pacing)
        val complexitySim = 1.0 - abs(user.complexity - content.complexity)
        val scalarScore = (durationSim * DURATION_SIMILARITY_WEIGHT) +
            (pacingSim * PACING_SIMILARITY_WEIGHT) +
            (complexitySim * COMPLEXITY_SIMILARITY_WEIGHT)

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

        var magnitudeA = 0.0
        var magnitudeB = 0.0
        user.topics.values.forEach { magnitudeA += it * it }
        content.topics.values.forEach { magnitudeB += it * it }

        val topicSim = if (magnitudeA > 0 && magnitudeB > 0) {
            dotProduct / (sqrt(magnitudeA) * sqrt(magnitudeB))
        } else 0.0

        return (topicSim * TOPIC_SIMILARITY_WEIGHT) + scalarScore
    }

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
                val proportional = currentVal *
                    currentVal.pow(NEGATIVE_PROPORTIONAL_EXPONENT) * baseRate
                val absoluteFloor = baseRate * NEGATIVE_FLOOR_FACTOR
                minOf(proportional, absoluteFloor)
            } else {
                val saturationPenalty = (1.0 - currentVal).pow(2)
                val effectiveRate = baseRate * saturationPenalty
                (targetVal - currentVal) * effectiveRate
            }

            newTopics[key] = (currentVal + delta).coerceIn(0.0, 1.0)
        }

        val decay = if (baseRate > 0) TOPIC_DECAY_RATE else 1.0
        val iterator = newTopics.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!target.topics.containsKey(entry.key)) {
                entry.setValue(entry.value * decay)
            }
            if (entry.value < TOPIC_PRUNE_THRESHOLD) iterator.remove()
        }

        if (isNegative && newTopics.isNotEmpty()) {
            val totalMagnitude = newTopics.values.sum()
            val maxScore = newTopics.values.maxOrNull() ?: 0.0

            if (totalMagnitude > 0 &&
                maxScore / totalMagnitude > COMPRESSION_THRESHOLD
            ) {
                val compressed = newTopics.mapValues { (_, v) ->
                    if (v > COMPRESSION_CEILING)
                        COMPRESSION_CEILING +
                            (v - COMPRESSION_CEILING) * COMPRESSION_FACTOR
                    else v
                }
                newTopics.clear()
                newTopics.putAll(compressed)
            }
        }

        fun updateScalar(
            currentScalar: Double,
            targetScalar: Double
        ): Double {
            return if (isNegative) {
                val proportional = currentScalar * baseRate *
                    NEGATIVE_SCALAR_PROPORTIONAL
                val floor = baseRate * NEGATIVE_SCALAR_FLOOR
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

    private fun applySmartDiversity(
        candidates: MutableList<ScoredVideo>
    ): List<Video> {
        val finalPlaylist = mutableListOf<Video>()
        val channelWindow = mutableListOf<String>()
        val topicWindow = mutableListOf<String>()

        candidates.sortByDescending { it.score }

        val uniqueTopics = candidates
            .mapNotNull {
                it.vector.topics.maxByOrNull { e -> e.value }?.key
            }
            .distinct()
        val topicDiversity = uniqueTopics.size

        val maxPerTopic = when {
            topicDiversity <= 4 -> 2
            topicDiversity <= 7 -> 3
            else -> 3
        }

        val explorationSlots = when {
            topicDiversity <= 2 -> 6
            topicDiversity <= 4 -> 4
            else -> 2
        }

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
        val deferredHighQuality = mutableListOf<ScoredVideo>()
        val phase1Candidates = candidates.toMutableList()
        val phase1Iterator = phase1Candidates.iterator()
        var explorationCount = 0
        val topScore = candidates.firstOrNull()?.score ?: 0.0

        while (phase1Iterator.hasNext() &&
            finalPlaylist.size < DIVERSITY_PHASE1_TARGET
        ) {
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
                    ) > TITLE_SIMILARITY_STRICT
                }

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
            } else if (topScore > 0 &&
                current.score > (topScore * 0.8)
            ) {
                deferredHighQuality.add(current)
                phase1Iterator.remove()
            }
        }

        // Phase 2: Deferred quality
        deferredHighQuality.sortByDescending { it.score }
        for (scored in deferredHighQuality) {
            val recentChannels = finalPlaylist.takeLast(7)
                .map { it.channelId }
            val channelOk = recentChannels
                .count { it == scored.video.channelId } < 2
            val titleOk = finalPlaylist.takeLast(5)
                .none { existing ->
                    calculateTitleSimilarity(
                        scored.video.title, existing.title
                    ) > TITLE_SIMILARITY_RELAXED
                }
            if (channelOk && titleOk) {
                finalPlaylist.add(scored.video)
            }
        }

        // Phase 3: Relaxed fill
        phase1Candidates.sortByDescending { it.score }
        for (scored in phase1Candidates) {
            val recentChannels = finalPlaylist.takeLast(5)
                .map { it.channelId }
            val channelSpam = recentChannels
                .count { it == scored.video.channelId } >= 2
            val titleSimilar = finalPlaylist.takeLast(5)
                .any { existing ->
                    calculateTitleSimilarity(
                        scored.video.title, existing.title
                    ) > TITLE_SIMILARITY_RELAXED
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
        val personaStability: Int = 0,
        val idfWordFrequency: Map<String, Int> = emptyMap(),
        val idfTotalDocuments: Int = 0,
        val watchHistoryMap: Map<String, Float> = emptyMap(),
        val seenShortsHistory: Map<String, Long> = emptyMap()
    )

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
            fileName = "flow_neuro_brain_v9.json",
            serializer = BrainSerializer
        )

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
        personaStability = personaStability,
        idfWordFrequency = idfWordFrequency,
        idfTotalDocuments = idfTotalDocuments,
        watchHistoryMap = watchHistoryMap,
        seenShortsHistory = seenShortsHistory
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
            idfWordFrequency = idfWordFrequency,
            idfTotalDocuments = idfTotalDocuments,
            watchHistoryMap = watchHistoryMap,
            seenShortsHistory = seenShortsHistory,
            schemaVersion = schemaVersion
        )
    }

    /**
     * Records a batch of Short video IDs as "seen" by the user.
     * Called after rank() returns results to the discovery engine.
     * Persisted across app restarts so seen Shorts aren't immediately reshown.
     */
    suspend fun recordSeenShorts(shortIds: List<String>) {
        if (shortIds.isEmpty()) return
        brainMutex.withLock {
            val now = System.currentTimeMillis()
            val updated = currentUserBrain.seenShortsHistory.toMutableMap()
            shortIds.forEach { id ->
                if (!updated.containsKey(id)) updated[id] = now
            }
            if (updated.size > SEEN_SHORTS_MAX) {
                val toRemove = updated.entries
                    .sortedBy { it.value }
                    .take(updated.size - SEEN_SHORTS_MAX)
                toRemove.forEach { updated.remove(it.key) }
            }
            currentUserBrain = currentUserBrain.copy(seenShortsHistory = updated)
            scheduleDebouncedSave()
        }
    }

    /**
     * Returns IDs of Shorts seen within the expiry window.
     * Shorts older than SEEN_SHORT_EXPIRY_DAYS are eligible to reappear.
     */
    suspend fun getRecentlySeenShorts(): Set<String> {
        return brainMutex.withLock {
            val now = System.currentTimeMillis()
            val expiryMs = SEEN_SHORT_EXPIRY_DAYS * 24L * 60 * 60 * 1000
            currentUserBrain.seenShortsHistory
                .filter { (_, ts) -> (now - ts) < expiryMs }
                .keys
        }
    }

    private suspend fun saveBrainToDataStore() =
        withContext(Dispatchers.IO) {
            try {
                appContext.brainDataStore.updateData {
                    currentUserBrain.toSerializable()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save brain", e)
            }
        }

    private suspend fun loadBrainFromDataStore(): Boolean =
        withContext(Dispatchers.IO) {
            try {
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
            val brainCopy = brainMutex.withLock {
                currentUserBrain.toSerializable()
            }
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

    suspend fun importBrainFromStream(
        input: InputStream
    ): Boolean = withContext(Dispatchers.IO) {
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

            brainMutex.withLock {
                currentUserBrain = finalBrain
                idfWordFrequency = finalBrain.idfWordFrequency.toMutableMap()
                idfTotalDocuments = finalBrain.idfTotalDocuments
                watchHistory.clear()
                finalBrain.watchHistoryMap.forEach { (id, pct) ->
                    watchHistory[id] = WatchEntry(pct, System.currentTimeMillis())
                }
                saveBrainToDataStore()
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

    private suspend fun loadLegacyBrain() = withContext(Dispatchers.IO) {
        val legacyFile = File(appContext.filesDir, BRAIN_FILENAME)
        if (legacyFile.exists()) {
            try {
                Log.i(TAG, "Migrating legacy JSON brain...")
                val text = legacyFile.readText()
                val migrated = migrateLegacyBackup(
                    text, SerializableBrain()
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

        tryMigrateFromPreviousDataStore()
    }

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

    private suspend fun tryMigrateFromPreviousDataStore() {
        val versions = listOf("v8", "v7")
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
                    currentUserBrain = data.toUserBrain()
                    Log.i(
                        TAG,
                        "Migrated $version brain " +
                            "(${data.interactions} interactions)"
                    )
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "$version migration failed", e)
            }
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
            brain.personaStability < PERSONA_STABILITY_THRESHOLD
        ) {
            lastPersona
        } else {
            rawPersona
        }
    }

    // =================================================
    // 6. EXPLORATION ENGINE
    // =================================================

    private val MACRO_CATEGORY_CLEAN_REGEX = Regex("[^a-zA-Z ]")

    private val MACRO_CATEGORIES: List<String> by lazy {
        TOPIC_CATEGORIES.flatMap { category ->
            listOf(
                category.name
                    .replace(MACRO_CATEGORY_CLEAN_REGEX, "")
                    .trim()
            ) + category.topics.take(3)
        }.distinct()
    }

    private fun getExplorationQueries(brain: UserBrain): List<String> {
        val blocked = brain.blockedTopics

        return MACRO_CATEGORIES
            .filter { category ->
                val normalized = category.lowercase()
                val lemma = normalizeLemma(normalized)
                !blocked.any { blockedTerm ->
                    normalized.contains(blockedTerm) ||
                        lemma.contains(blockedTerm)
                }
            }
            .map { category ->
                val score = brain.globalVector
                    .topics[normalizeLemma(category)] ?: 0.0
                category to score
            }
            .filter { it.second < EXPLORATION_SCORE_THRESHOLD }
            .sortedBy { it.second }
            .take(2)
            .map { it.first }
    }

    // =================================================
    // STOP WORDS
    // =================================================

    private val STOP_WORDS = hashSetOf(
        // Grammatical
        "the", "and", "for", "that", "this", "with", "you", "how",
        "what", "when", "mom", "types", "your", "which", "can",
        "make", "seen", "most", "into", "best", "from", "just",
        "about", "more", "some", "will", "one", "all", "would",
        "there", "their", "out", "not", "but", "have", "has",
        "been", "being", "was", "were", "are",
        // YouTube meta
        "video", "official", "channel", "review", "reaction",
        "full", "episode", "part", "new", "latest", "update",
        "hdr", "uhd", "fps", "live", "stream",
        "watch", "subscribe", "like", "comment",
        "share", "click", "link", "description", "below", "check",
        "dont", "miss", "must", "now",
        // Resolution tags
        "1080p", "720p", "480p", "360p", "240p", "144p",
        // Lemma-consistent
        "compilation", "montage",
        "reupload", "reup", "reuploaded"
    )
}