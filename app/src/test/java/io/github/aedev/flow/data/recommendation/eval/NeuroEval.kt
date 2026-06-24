/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.ContentVector
import io.github.aedev.flow.data.recommendation.FeedEntry
import io.github.aedev.flow.data.recommendation.ImpressionEntry
import io.github.aedev.flow.data.recommendation.MomentumEntry
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.ScoringParams
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.recommendation.WatchEntry
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Offline evaluation harness for the ranking core. Scores candidates through
 * the same deterministic NeuroScoring.scoreCandidate the engine uses (no jitter,
 * no Context), then computes ranking-quality metrics for before/after comparison.
 */
internal object NeuroEval {

    /** Fixed clock so every metric is reproducible across runs/machines. */
    const val FIXED_NOW = 1_700_000_000_000L

    data class Labeled(val video: Video, val vector: ContentVector, val relevance: Double)

    data class Metrics(
        val ndcg: Double,
        val ild: Double,
        val coverage: Double,
        val selfSuppression: Double
    )

    // ── Fixture builders (shared by all recommendation tests) ──

    fun video(
        id: String,
        title: String = "video $id",
        channelId: String = "ch_$id",
        channelName: String = "Channel $id",
        duration: Int = 600,
        viewCount: Long = 50_000L,
        likeCount: Long = 0L,
        uploadDate: String = "2 weeks ago",
        isLive: Boolean = false,
        isShort: Boolean = false
    ) = Video(
        id = id,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = "",
        duration = duration,
        viewCount = viewCount,
        likeCount = likeCount,
        uploadDate = uploadDate,
        isLive = isLive,
        isShort = isShort
    )

    fun vec(
        vararg topics: Pair<String, Double>,
        duration: Double = 0.5,
        pacing: Double = 0.5,
        complexity: Double = 0.5,
        isLive: Double = 0.0
    ) = ContentVector(topics.toMap(), duration, pacing, complexity, isLive)

    /**
     * Builds scoring params at a fixed operating point (no boredom, no session
     * fatigue) so metrics isolate ranking quality rather than runtime noise.
     */
    fun params(
        brain: UserBrain,
        userSubs: Set<String> = emptySet(),
        preferredLemmas: Set<String> = emptySet(),
        poolSize: Int = 20,
        impressions: Map<String, ImpressionEntry> = emptyMap(),
        watchHistory: Map<String, WatchEntry> = emptyMap(),
        recentInteractions: List<MomentumEntry> = emptyList(),
        now: Long = FIXED_NOW
    ) = ScoringParams(
        brain = brain,
        userSubs = userSubs,
        timeContextVector = ContentVector(),
        wPersonality = 0.4,
        wContext = 0.4,
        wNovelty = 0.2,
        isColdStart = brain.totalInteractions < NeuroScoring.COLD_START_THRESHOLD,
        isOnboarding = brain.totalInteractions < NeuroScoring.ONBOARDING_WARMUP_INTERACTIONS,
        onboardingWarmup = 0.5,
        lemmatizedPreferred = preferredLemmas,
        sessionTopics = emptyList(),
        sessionVideoCount = 0,
        impressions = impressions,
        watchHistory = watchHistory,
        recentInteractions = recentInteractions,
        candidatePoolSize = poolSize,
        now = now
    )

    fun rankByScore(items: List<Labeled>, params: ScoringParams): List<Labeled> =
        items.sortedByDescending { NeuroScoring.scoreCandidate(it.video, it.vector, params) }

    // ── Metrics ──

    /** Normalised Discounted Cumulative Gain of the scorer's order vs. ideal. */
    fun ndcg(ranked: List<Labeled>, k: Int): Double {
        val dcg = dcg(ranked.take(k).map { it.relevance })
        val idcg = dcg(ranked.map { it.relevance }.sortedDescending().take(k))
        return if (idcg > 0.0) dcg / idcg else 0.0
    }

    private fun dcg(rels: List<Double>): Double =
        rels.mapIndexed { i, rel -> rel / log2(i + 2.0) }.sum()

    /** Intra-list diversity: mean pairwise topical distance over the top-k. */
    fun ild(ranked: List<Labeled>, k: Int): Double {
        val top = ranked.take(k)
        if (top.size < 2) return 0.0
        var sum = 0.0
        var pairs = 0
        for (i in top.indices) for (j in i + 1 until top.size) {
            sum += 1.0 - topicCosine(top[i].vector, top[j].vector)
            pairs++
        }
        return if (pairs > 0) sum / pairs else 0.0
    }

    /** Distinct primary topics surfaced in the top-k over those in the pool. */
    fun coverage(ranked: List<Labeled>, k: Int): Double {
        val poolTopics = ranked.mapNotNull { primaryTopic(it.vector) }.toSet()
        if (poolTopics.isEmpty()) return 0.0
        val shownTopics = ranked.take(k).mapNotNull { primaryTopic(it.vector) }.toSet()
        return shownTopics.size.toDouble() / poolTopics.size
    }

    /**
     * Average score mass lost to repetition penalties on never-watched items.
     * High = the ranker is suppressing content the user never engaged with —
     * the pathology I-1 (viewport-based impressions) is meant to reduce.
     */
    fun selfSuppression(brain: UserBrain, unseen: List<Labeled>, now: Long = FIXED_NOW): Double {
        if (unseen.isEmpty()) return 0.0
        return unseen.map { item ->
            val feed = NeuroScoring.calculateFeedHistoryPenalty(
                item.video.id, brain.feedHistory, now, unseen.size
            )
            val implicit = NeuroScoring.calculateImplicitDisinterestPenalty(
                item.video.id, brain.feedHistory, emptyMap(), now
            )
            1.0 - (feed * implicit)
        }.average()
    }

    fun evaluate(pool: List<Labeled>, params: ScoringParams, k: Int = 10): Metrics {
        val ranked = rankByScore(pool, params)
        return Metrics(
            ndcg = ndcg(ranked, k),
            ild = ild(ranked, k),
            coverage = coverage(ranked, k),
            selfSuppression = selfSuppression(params.brain, pool, params.now)
        )
    }

    // ── helpers ──

    private fun log2(x: Double) = ln(x) / ln(2.0)

    private fun primaryTopic(v: ContentVector): String? =
        v.topics.maxByOrNull { it.value }?.key?.substringBefore(':')

    private fun topicCosine(a: ContentVector, b: ContentVector): Double {
        if (a.topics.isEmpty() || b.topics.isEmpty()) return 0.0
        var dot = 0.0
        for ((k, v) in a.topics) dot += v * (b.topics[k] ?: 0.0)
        val magA = sqrt(a.topics.values.sumOf { it * it })
        val magB = sqrt(b.topics.values.sumOf { it * it })
        return if (magA > 0 && magB > 0) dot / (magA * magB) else 0.0
    }
}
