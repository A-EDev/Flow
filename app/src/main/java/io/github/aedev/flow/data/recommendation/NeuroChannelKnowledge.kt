/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

/**
 * Channel-level knowledge extraction — pure functions the engine applies.
 *
 * Channels carry two under-used signals: creator-declared keyword tags
 * (NewPipe ChannelInfo.tags — authored identity, fetched on subscribe) and
 * their recent upload titles (already downloaded for the subs lane, previously
 * discarded for learning). Both feed channelTopicProfiles, which drive
 * candidate priors, interest clustering, and brain rehydration.
 */
internal object NeuroChannelKnowledge {
    /** Seed weight for creator-declared tags — authored, so trusted more. */
    const val TAG_SEED_WEIGHT = 0.35

    /** Ceiling for passively observed upload-title topics — inferred, so modest. */
    const val PASSIVE_MAX_WEIGHT = 0.25

    const val MAX_TOKENS = 8

    /** Creator-declared channel keywords (+ description lead) → profile seed weights. */
    fun profileFromChannelTags(
        tags: List<String>,
        description: String?,
        tokenizer: NeuroTokenizer,
    ): Map<String, Double> {
        val fromTags = tags.flatMap { tokenizer.tokenize(it) }
        val fromDescription =
            description
                ?.lineSequence()
                ?.take(2)
                ?.joinToString(" ")
                ?.let { tokenizer.tokenize(it) }
                .orEmpty()
        val counts =
            (fromTags + fromDescription)
                .filterNot { tokenizer.isNoiseTopic(it) }
                .groupingBy { it }
                .eachCount()
        if (counts.isEmpty()) return emptyMap()
        val max = counts.values.max().toDouble()
        return counts.entries
            .sortedByDescending { it.value }
            .take(MAX_TOKENS)
            .associate { (token, count) -> token to TAG_SEED_WEIGHT * (0.6 + 0.4 * count / max) }
    }

    /**
     * Passive profile from a channel's recent upload titles. Tokens must repeat
     * across titles (when there are 3+) — a word from a single video is usually
     * video-specific, not channel identity.
     */
    fun profileFromUploadTitles(
        titles: List<String>,
        tokenizer: NeuroTokenizer,
    ): Map<String, Double> {
        if (titles.isEmpty()) return emptyMap()
        val counts =
            titles
                .flatMap { tokenizer.tokenize(it).distinct() }
                .filterNot { tokenizer.isNoiseTopic(it) }
                .groupingBy { it }
                .eachCount()
        if (counts.isEmpty()) return emptyMap()
        val minCount = if (titles.size >= 3) 2 else 1
        val identity = counts.filterValues { it >= minCount }
        if (identity.isEmpty()) return emptyMap()
        val max = identity.values.max().toDouble()
        return identity.entries
            .sortedByDescending { it.value }
            .take(MAX_TOKENS)
            .associate { (token, count) -> token to PASSIVE_MAX_WEIGHT * count / max }
    }

    /** Max-merge additions into a profile — never lowers learned weights. */
    fun mergeProfile(
        existing: Map<String, Double>,
        additions: Map<String, Double>,
    ): Map<String, Double> {
        if (additions.isEmpty()) return existing
        val merged = existing.toMutableMap()
        additions.forEach { (topic, weight) -> merged.merge(topic, weight, ::maxOf) }
        return if (merged.size > NeuroScoring.CHANNEL_PROFILE_MAX_TOPICS) {
            merged.entries
                .sortedByDescending { it.value }
                .take(NeuroScoring.CHANNEL_PROFILE_MAX_TOPICS)
                .associate { it.key to it.value }
        } else {
            merged
        }
    }
}
