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
 * One-time brain maintenance migrations. Pure — no Context, no state — so the
 * engine, offline diagnostics, and tests all run the identical code.
 *
 * V15 repairs two long-standing learning defects: the affinity instant-prune
 * bug deleted every organic co-watch edge, and the acquisition wall kept new
 * topics out of mature vectors — leaving the vector a decayed copy of the
 * onboarding seeds while channelTopicProfiles (which learn undamped) kept the
 * user's REAL interests. This scrubs junk and rehydrates the vector and
 * affinity edges from those profiles, once.
 */
internal object NeuroMaintenance {
    const val TARGET_SCHEMA_VERSION = 15

    private const val REHYDRATE_MAX_TOPICS = 40
    private const val REHYDRATE_MAX_WEIGHT = 0.30
    private const val REHYDRATE_MIN_WEIGHT = 0.06
    private const val REHYDRATE_AFFINITY_SEED = 0.15
    private const val REHYDRATE_MIN_CHANNEL_QUALITY = 0.4

    fun runV15IfNeeded(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        if (brain.schemaVersion >= TARGET_SCHEMA_VERSION) return brain

        val cleanedTopics =
            brain.globalVector.topics.filter { (topic, score) ->
                score >= NeuroVectorMath.TOPIC_PRUNE_THRESHOLD && !tokenizer.isNoiseTopic(topic)
            }
        var updated =
            brain.copy(globalVector = brain.globalVector.copy(topics = cleanedTopics))

        updated = rehydrateFromChannelProfiles(updated, tokenizer)

        return updated.copy(schemaVersion = TARGET_SCHEMA_VERSION)
    }

    private fun rehydrateFromChannelProfiles(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        if (brain.channelTopicProfiles.isEmpty()) return brain

        val aggregated = HashMap<String, Double>()
        brain.channelTopicProfiles.forEach { (channelId, profile) ->
            val quality = brain.channelScores[channelId] ?: 0.5
            if (quality < REHYDRATE_MIN_CHANNEL_QUALITY) return@forEach
            profile.forEach { (topic, weight) ->
                if (!tokenizer.isNoiseTopic(topic)) {
                    aggregated.merge(topic, weight * quality, Double::plus)
                }
            }
        }
        if (aggregated.isEmpty()) return brain
        val maxAggregate = aggregated.values.max()
        if (maxAggregate <= 0.0) return brain

        // Max-merge topic seeds: never lowers an existing score, tops out below
        // established interests so rehydrated topics still need reinforcement.
        val topics = brain.globalVector.topics.toMutableMap()
        aggregated.entries
            .sortedByDescending { it.value }
            .take(REHYDRATE_MAX_TOPICS)
            .forEach { (topic, aggregate) ->
                val seeded =
                    (aggregate / maxAggregate * REHYDRATE_MAX_WEIGHT)
                        .coerceAtLeast(REHYDRATE_MIN_WEIGHT)
                topics[topic] = maxOf(topics[topic] ?: 0.0, seeded)
            }

        // Seed affinity edges from co-taught topics per channel so clustering
        // has real structure immediately (organic edges were all lost to the
        // instant-prune bug).
        val affinities = brain.topicAffinities.toMutableMap()
        brain.channelTopicProfiles.forEach { (channelId, profile) ->
            val quality = brain.channelScores[channelId] ?: 0.5
            if (quality < REHYDRATE_MIN_CHANNEL_QUALITY) return@forEach
            val top =
                profile.entries
                    .filter { !tokenizer.isNoiseTopic(it.key) }
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { NeuroScoring.stripDomainTag(it.key) }
                    .distinct()
            for (i in top.indices) {
                for (j in i + 1 until top.size) {
                    val key = NeuroScoring.makeAffinityKey(top[i], top[j])
                    affinities[key] = maxOf(affinities[key] ?: 0.0, REHYDRATE_AFFINITY_SEED)
                }
            }
        }

        return brain.copy(
            globalVector = brain.globalVector.copy(topics = topics),
            topicAffinities = affinities,
        )
    }
}
