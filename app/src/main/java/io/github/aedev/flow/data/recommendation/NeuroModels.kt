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

    // ── Feed repetition prevention ──

    val feedHistory: Map<String, FeedEntry> = emptyMap(),

    val recentQueryTokens: List<Set<String>> = emptyList(),

    val schemaVersion: Int = 12
)

// ── Interaction Types ──

enum class InteractionType {
    CLICK, LIKED, WATCHED, SKIPPED, DISLIKED
}

// ── Persona ──

enum class FlowPersona(
    val title: String,
    val description: String,
    val icon: String
) {
    INITIATE("The Initiate", "Just getting started. Your profile is still forming.", "🌱"),
    AUDIOPHILE("The Audiophile", "You use Flow mostly for Music. The vibe is everything.", "🎧"),
    LIVEWIRE("The Livewire", "You love the raw energy of Livestreams and premieres.", "🔴"),
    NIGHT_OWL("The Night Owl", "You thrive in the dark. Most watching happens after midnight.", "🦉"),
    BINGER("The Binger", "Once you start, you can't stop. Massive content waves.", "🍿"),
    SCHOLAR("The Scholar", "High-complexity content. Here to grow, not just be entertained.", "🎓"),
    DEEP_DIVER("The Deep Diver", "Long-form video essays and documentaries are your world.", "🤿"),
    SKIMMER("The Skimmer", "Fast-paced, short content. Dopamine on demand.", "⚡"),
    SPECIALIST("The Specialist", "Laser-focused on a few niches. You know what you like.", "🎯"),
    EXPLORER("The Explorer", "Chaotic and beautiful. A bit of everything.", "🧭")
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
