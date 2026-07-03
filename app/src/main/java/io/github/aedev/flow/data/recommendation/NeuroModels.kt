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

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import java.util.Calendar

/**
 * Pure data definitions. No logic, no dependencies.
 * Every other file imports from here.
 */

// ── Content Vector ──

data class ContentVector(
    val topics: Map<String, Double> = emptyMap(),
    val duration: Double = 0.5,
    val pacing: Double = 0.5,
    val complexity: Double = 0.5,
    val isLive: Double = 0.0
)

// ── Time Buckets ──

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

// ── User Brain ──

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
    val channelTopicProfiles: Map<String, Map<String, Double>> = emptyMap(),
    val shortsVector: ContentVector = ContentVector(),
    /**
     * Hard suppression: video IDs that must NOT appear in ranked results.
     * Maps videoId → timestamp when suppression was applied.
     * Entries expire after VIDEO_SUPPRESSION_DAYS.
     */
    val suppressedVideoIds: Map<String, Long> = emptyMap(),
    /**
     * Hard channel suppression: channels the user explicitly marked not-interested on.
     * Maps channelId → timestamp. Escalates to blockedChannels on second signal.
     */
    val suppressedChannels: Map<String, Long> = emptyMap(),

    /**
     * Rejection pattern memory. Tracks topic patterns the user
     * repeatedly rejects via "not interested".
     */
    val rejectionPatterns: Map<String, RejectionSignal> = emptyMap(),

    // ── Feed repetition prevention ──

    val feedHistory: Map<String, FeedEntry> = emptyMap(),

    val recentQueryTokens: List<Set<String>> = emptyList(),


    val topicEvidence: Map<String, TopicEvidence> = emptyMap(),

    val schemaVersion: Int = 13
)

// ── Interaction Types ──

enum class InteractionType {
    CLICK, LIKED, WATCHED, SKIPPED, DISLIKED
}

// ── Persona ──

enum class FlowPersona(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: String
) {
    INITIATE(R.string.persona_initiate_title, R.string.persona_initiate_description, "🌱"),
    AUDIOPHILE(R.string.persona_audiophile_title, R.string.persona_audiophile_description, "🎧"),
    LIVEWIRE(R.string.persona_livewire_title, R.string.persona_livewire_description, "🔴"),
    NIGHT_OWL(R.string.persona_night_owl_title, R.string.persona_night_owl_description, "🦉"),
    BINGER(R.string.persona_binger_title, R.string.persona_binger_description, "🍿"),
    SCHOLAR(R.string.persona_scholar_title, R.string.persona_scholar_description, "🎓"),
    DEEP_DIVER(R.string.persona_deep_diver_title, R.string.persona_deep_diver_description, "🤿"),
    SKIMMER(R.string.persona_skimmer_title, R.string.persona_skimmer_description, "⚡"),
    SPECIALIST(R.string.persona_specialist_title, R.string.persona_specialist_description, "🎯"),
    EXPLORER(R.string.persona_explorer_title, R.string.persona_explorer_description, "🧭")
}

// ── Topic Category (Onboarding) ──

data class TopicCategory(
    val name: String,
    val icon: String,
    val topics: List<String>
)

// ── Discovery Query ──

data class DiscoveryQuery(
    val query: String,
    val strategy: QueryStrategy,
    val confidence: Double,
    val reasoning: String
)

enum class QueryStrategy {
    DEEP_DIVE,
    CROSS_TOPIC,
    TRENDING,
    ADJACENT_EXPLORATION,
    CHANNEL_DISCOVERY,
    CONTEXTUAL,
    FORMAT_DRIVEN
}

// ── Internal Tracking Structures ──

internal data class ScoredVideo(
    val video: io.github.aedev.flow.data.model.Video,
    var score: Double,
    val vector: ContentVector
)

data class RejectionSignal(
    val count: Int,
    val lastRejectedAt: Long
)

data class TopicEvidence(
    val positiveSignals: Int = 0,
    val negativeSignals: Int = 0,
    val watchSignals: Int = 0,
    val explicitSignals: Int = 0,
    val positiveScore: Double = 0.0,
    val videoIds: Set<String> = emptySet(),
    val channelIds: Set<String> = emptySet(),
    val firstSeenAt: Long = 0L,
    val lastSeenAt: Long = 0L
)

internal data class ImpressionEntry(var count: Int, var lastSeen: Long)

internal data class WatchEntry(val percentWatched: Float, val timestamp: Long)

internal data class MomentumEntry(val topic: String, val positive: Boolean)

data class FeedEntry(
    val lastShown: Long,
    val showCount: Int
)

internal data class IdfSnapshot(
    val wordFrequency: Map<String, Int>,
    val totalDocs: Int
)

/**
 * Immutable per-rank() snapshot consumed by NeuroScoring.scoreCandidate.
 * Bundles every shared input so candidate scoring is a pure, deterministic
 * function (no engine state, no Context) — testable and replayable offline.
 */
enum class GraphSeedSource {
    WATCH_HISTORY,
    LIKED,
    PLAYLIST
}

/** A candidate seed for related-graph retrieval, with enough context for one shared selector. */
data class GraphSeedInput(
    val id: String,
    val title: String,
    val channelId: String,
    val source: GraphSeedSource,
    val engagementWeight: Double,
    val timestamp: Long,
    val durationSec: Int,
    val percentWatched: Double,
    val isShort: Boolean = false
)

/** A seed annotated with its interest cluster, for diversified selection. */
internal data class SeedRank(
    val id: String,
    val clusterKey: String,
    val weight: Double
)

internal data class ScoringParams(
    val brain: UserBrain,
    val userSubs: Set<String>,
    val timeContextVector: ContentVector,
    val wPersonality: Double,
    val wContext: Double,
    val wNovelty: Double,
    val isColdStart: Boolean,
    val isOnboarding: Boolean,
    val onboardingWarmup: Double,
    val lemmatizedPreferred: Set<String>,
    val sessionTopics: List<String>,
    val sessionVideoCount: Int,
    val impressions: Map<String, ImpressionEntry>,
    val watchHistory: Map<String, WatchEntry>,
    val recentInteractions: List<MomentumEntry>,
    val candidatePoolSize: Int,
    val now: Long,
    val exploreWeight: Double = 0.0
)
