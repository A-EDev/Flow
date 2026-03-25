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

import io.github.aedev.flow.data.model.Video
import kotlin.math.*

/**
 * Scoring factor calculators. Each function computes one
 * signal and returns a multiplier or additive bonus.
 * All functions are pure — no state mutation.
 */
internal object NeuroScoring {

    // ── Scoring Weight Constants ──
    const val SUBSCRIPTION_BOOST = 0.15
    const val SERENDIPITY_BONUS = 0.10
    const val CURIOSITY_GAP_BONUS = 0.10
    const val NOT_INTERESTED_CHANNEL_FLOOR = 0.20
    const val CHANNEL_EMA_ALPHA = 0.05
    const val CHANNEL_EMA_DECAY = 1.0 - CHANNEL_EMA_ALPHA
    const val MAX_CHANNEL_SCORES = 500
    const val CHANNEL_KEEP_LOW = 50
    const val CHANNEL_KEEP_HIGH = 200
    const val SHORTS_LEARNING_PENALTY = 0.01
    const val MAX_CONSECUTIVE_SKIPS = 30
    const val SESSION_RESET_IDLE_MINUTES = 120L
    const val SESSION_RESET_EMPTY_MINUTES = 30L
    const val COLD_START_THRESHOLD = 30
    const val ONBOARDING_WARMUP_INTERACTIONS = 50
    const val ONBOARDING_MAX_BOOST = 0.15
    const val ENGAGEMENT_RATE_BASELINE = 0.05
    const val ENGAGEMENT_MAX_BOOST = 0.05
    const val ENGAGEMENT_MIN_VIEWS = 1000L
    const val ENGAGEMENT_FLOOR_RATE = 0.01
    const val ENGAGEMENT_FLOOR_MIN_VIEWS = 50_000L
    const val ENGAGEMENT_FLOOR_PENALTY = 0.2
    const val COLD_START_ENGAGEMENT_FLOOR_RATE = 0.02
    const val COLD_START_ENGAGEMENT_FLOOR_MIN_VIEWS = 10_000L
    const val BINGE_THRESHOLD = 20
    const val BINGE_NOVELTY_FACTOR = 0.15
    const val JITTER_COLD_START = 0.20
    const val JITTER_NORMAL = 0.02
    const val TITLE_SIMILARITY_STRICT = 0.55
    const val TITLE_SIMILARITY_RELAXED = 0.60
    const val CLASSIC_VIEW_THRESHOLD = 5_000_000L
    const val DIVERSITY_PHASE1_TARGET = 20
    const val ANTI_REC_PENALTY_THRESHOLD = 0.6
    const val ANTI_REC_PENALTY = 0.4
    const val MOMENTUM_WINDOW = 10
    const val MOMENTUM_BOOST = 0.12
    const val MOMENTUM_THRESHOLD = 3
    const val IMPRESSION_CACHE_MAX = 500
    const val IMPRESSION_DECAY_RATE = 0.1
    const val IMPRESSION_PENALTY_HEAVY = 0.05
    const val IMPRESSION_PENALTY_MEDIUM = 0.30
    const val IMPRESSION_PENALTY_LIGHT = 0.85
    const val IMPRESSION_THRESHOLD_DROP = 5
    const val IMPRESSION_THRESHOLD_HEAVY = 3
    const val IMPRESSION_THRESHOLD_LIGHT = 1
    const val MUSIC_REWATCH_MAX_DURATION = 480
    const val WATCHED_PENALTY_FULL = 0.02
    const val WATCHED_PENALTY_HALF = 0.30
    const val WATCHED_PENALTY_SAMPLED = 0.70
    const val WATCHED_THRESHOLD_FULL = 0.85f
    const val WATCHED_THRESHOLD_HALF = 0.50f
    const val WATCHED_THRESHOLD_SAMPLED = 0.15f
    const val WATCH_HISTORY_MAX = 2000
    const val SEEN_SHORTS_MAX = 3000
    const val SEEN_SHORT_PENALTY = 0.05
    const val SEEN_SHORT_EXPIRY_DAYS = 7
    const val AFFINITY_INCREMENT = 0.02
    const val AFFINITY_MAX = 1.0
    const val AFFINITY_PRUNE_THRESHOLD = 0.05
    const val AFFINITY_MAX_ENTRIES = 500
    const val AFFINITY_KEEP_TOP = 300
    const val AFFINITY_MAX_BOOST_PER_VIDEO = 0.15
    const val AFFINITY_BOOST_PER_PAIR = 0.05
    const val CHANNEL_PROFILE_LEARNING_RATE = 0.1
    const val CHANNEL_PROFILE_MAX_TOPICS = 15
    const val CHANNEL_PROFILE_PRUNE_THRESHOLD = 0.05
    const val CHANNEL_PROFILE_MAX_CHANNELS = 200
    const val CHANNEL_PROFILE_BLEND_WEIGHT = 0.3
    const val CHANNEL_PROFILE_MIN_VIDEOS = 3
    const val NOT_INTERESTED_GLOBAL_RATE = -0.35
    const val NOT_INTERESTED_TIME_RATE = -0.25
    const val NOT_INTERESTED_SKIP_INCREMENT = 3
    const val PERSONA_STABILITY_THRESHOLD = 3
    const val PERSONA_MAX_STABILITY = 10
    const val EXPLORATION_SCORE_THRESHOLD = 0.1

    // ── Time Decay Engine ──

    object TimeDecay {
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

    // ── Music Detection ──

    private val MUSIC_KEYWORDS = setOf(
        "music", "song", "lyrics", "remix", "lofi", "lo-fi",
        "playlist", "official audio", "official video",
        "music video", "feat", "ft.", "acoustic", "cover",
        "karaoke", "instrumental", "beat", "rap", "hip hop",
        "pop", "rock", "jazz", "classical", "edm", "mix"
    )

    fun isMusicTrack(video: Video): Boolean {
        if (video.duration > MUSIC_REWATCH_MAX_DURATION) return false
        val titleLower = video.title.lowercase()
        val channelLower = video.channelName.lowercase()
        return MUSIC_KEYWORDS.any { keyword ->
            titleLower.contains(keyword) || channelLower.contains(keyword)
        }
    }

    fun isVideoClassic(viewCount: Long): Boolean =
        viewCount >= CLASSIC_VIEW_THRESHOLD

    /**
     * Unified channel signal combining subscription boost and channel boredom.
     *
     * V9.3 Fix 4: Channel boredom uses a sigmoid curve instead of a hard
     * threshold. Smooth transition: 0% click rate → 0.4x, 5% → 0.7x,
     * 10% → 0.9x, 20%+ → ~1.0x.
     */
    fun calculateChannelSignal(
        video: Video,
        brain: UserBrain,
        userSubs: Set<String>
    ): Double {
        var signal = 0.0

        // Subscription boost with freshness amplifier
        val isSub = userSubs.contains(video.channelId)
        if (isSub) {
            val isShort = video.isShort || (video.duration in 1..120 && !video.isLive)
            val subBoost = if (isShort) SUBSCRIPTION_BOOST * 3.0 else SUBSCRIPTION_BOOST

            val freshnessMultiplier = when {
                !TimeDecay.isOlderThan24Hours(video.uploadDate) -> 2.0
                video.uploadDate.lowercase().let { text ->
                    text.contains("day") &&
                    (text.filter { it.isDigit() }.toIntOrNull() ?: 99) <= 2
                } -> 1.5
                video.uploadDate.lowercase().let { text ->
                    text.contains("week") &&
                    (text.filter { it.isDigit() }.toIntOrNull() ?: 99) <= 1
                } -> 1.2
                else -> 1.0
            }

            signal += subBoost * freshnessMultiplier
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
     */
    fun calculateEngagementQuality(
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
     */
    fun calculateFreshness(
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
     * V9.3 Fix 4: Smooth curiosity gap with graduated ramps.
     */
    fun calculateCuriosityBonus(
        personalityScore: Double,
        brainComplexity: Double,
        videoComplexity: Double
    ): Double {
        if (personalityScore <= 0.5) return 0.0

        val complexityDiff = abs(brainComplexity - videoComplexity)
        if (complexityDiff <= 0.2) return 0.0

        val curiosityRamp = ((complexityDiff - 0.2) / 0.3).coerceIn(0.0, 1.0)
        val topicSafety = ((personalityScore - 0.5) / 0.3).coerceIn(0.0, 1.0)
        return CURIOSITY_GAP_BONUS * curiosityRamp * topicSafety
    }

    /**
     * V9.3 Fix 4: Smooth serendipity with graduated ramps.
     */
    fun calculateSerendipity(
        noveltyScore: Double,
        contextScore: Double
    ): Double {
        val noveltyRamp = ((noveltyScore - 0.4) / 0.4).coerceIn(0.0, 1.0)
        val contextRamp = ((contextScore - 0.3) / 0.4).coerceIn(0.0, 1.0)
        return SERENDIPITY_BONUS * noveltyRamp * contextRamp
    }

    /**
     * Already-watched penalty with music exception.
     */
    fun calculateWatchedPenalty(
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

    /**
     * Anti-recommendation penalty for videos matching negatively-rated channel profiles.
     */
    fun calculateAntiRecommendationPenalty(
        videoVector: ContentVector,
        video: Video,
        brain: UserBrain
    ): Double {
        val negativeChannels = brain.channelScores
            .filter { (_, score) -> score < NOT_INTERESTED_CHANNEL_FLOOR }
            .keys

        if (negativeChannels.isEmpty()) return 1.0

        val negativeProfiles = negativeChannels.mapNotNull { channelId ->
            brain.channelTopicProfiles[channelId]
        }

        if (negativeProfiles.isEmpty()) return 1.0

        var maxSimilarity = 0.0
        negativeProfiles.forEach { negProfile ->
            val negVector = ContentVector(topics = negProfile)
            val similarity = NeuroVectorMath.calculateCosineSimilarity(negVector, videoVector)
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity
            }
        }

        return if (maxSimilarity > ANTI_REC_PENALTY_THRESHOLD) {
            val penaltyStrength = ((maxSimilarity - ANTI_REC_PENALTY_THRESHOLD) /
                (1.0 - ANTI_REC_PENALTY_THRESHOLD)).coerceIn(0.0, 1.0)
            1.0 - (penaltyStrength * (1.0 - ANTI_REC_PENALTY))
        } else 1.0
    }

    /**
     * Session-level engagement momentum boost.
     */
    fun calculateMomentumBoost(
        videoVector: ContentVector,
        interactions: List<MomentumEntry>
    ): Double {
        if (interactions.size < MOMENTUM_THRESHOLD) return 0.0

        val primaryTopic = videoVector.topics.maxByOrNull { it.value }?.key
            ?: return 0.0

        val recentPositiveCount = interactions
            .takeLast(MOMENTUM_WINDOW)
            .count { it.topic == primaryTopic && it.positive }

        return if (recentPositiveCount >= MOMENTUM_THRESHOLD) {
            (recentPositiveCount.toDouble() / MOMENTUM_WINDOW * MOMENTUM_BOOST)
                .coerceAtMost(MOMENTUM_BOOST)
        } else 0.0
    }

    /**
     * Smart diversity re-ranking across channels and topics.
     */
    fun applySmartDiversity(
        candidates: MutableList<ScoredVideo>,
        tokenizer: NeuroTokenizer
    ): List<Video> {
        val finalPlaylist = mutableListOf<Video>()
        val channelWindow = mutableListOf<String>()
        val topicWindow = mutableListOf<String>()
        val tokenCache = HashMap<String, Set<String>>(candidates.size * 2)

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
                    val tokens1 = tokenCache.getOrPut(current.video.title) { tokenizer.tokenizeForSimilarity(current.video.title) }
                    val tokens2 = tokenCache.getOrPut(existing.title) { tokenizer.tokenizeForSimilarity(existing.title) }
                    NeuroVectorMath.calculateTitleSimilarity(tokens1, tokens2) >
                        TITLE_SIMILARITY_STRICT
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
                    val tokens1 = tokenCache.getOrPut(scored.video.title) { tokenizer.tokenizeForSimilarity(scored.video.title) }
                    val tokens2 = tokenCache.getOrPut(existing.title) { tokenizer.tokenizeForSimilarity(existing.title) }
                    NeuroVectorMath.calculateTitleSimilarity(tokens1, tokens2) >
                        TITLE_SIMILARITY_RELAXED
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
                    val tokens1 = tokenCache.getOrPut(scored.video.title) { tokenizer.tokenizeForSimilarity(scored.video.title) }
                    val tokens2 = tokenCache.getOrPut(existing.title) { tokenizer.tokenizeForSimilarity(existing.title) }
                    NeuroVectorMath.calculateTitleSimilarity(tokens1, tokens2) >
                        TITLE_SIMILARITY_RELAXED
                }
            if (!channelSpam && !titleSimilar) {
                finalPlaylist.add(scored.video)
            }
        }

        return finalPlaylist
    }
}
