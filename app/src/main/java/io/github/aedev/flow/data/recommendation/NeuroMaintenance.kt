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
 *
 * V16 removes the single words that multi-word searches planted at the
 * acquisition floor ("lofi hip hop" became lofi, hip and hop at one weight).
 *
 * V17 estimates how many events each time bucket has seen, from how many topics it holds, so an
 * existing bucket keeps its weight in ranking only as far as it has earned it. It also re-keys
 * every topic through [NeuroText.fold], so "𝙥𝙝𝙤𝙣𝙠" learned before folding merges into "phonk".
 * Full-strength decay from every Short had hollowed vectors out again after V15, so a hollow
 * vector is rehydrated from channel knowledge once more, and blocked topics leave the vector.
 */
internal object NeuroMaintenance {
    const val TARGET_SCHEMA_VERSION = 17
    private const val V15_SCHEMA_VERSION = 15
    private const val V16_SCHEMA_VERSION = 16

    private const val REHYDRATE_MAX_TOPICS = 40
    private const val REHYDRATE_MAX_WEIGHT = 0.30
    private const val REHYDRATE_MIN_WEIGHT = 0.06
    private const val REHYDRATE_AFFINITY_SEED = 0.15
    private const val REHYDRATE_MIN_CHANNEL_QUALITY = 0.4

    /** Fewer topics than this at the developing tier or above means decay emptied the vector. */
    private const val HOLLOW_VECTOR_TOPICS = 5

    fun runIfNeeded(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        if (brain.schemaVersion >= TARGET_SCHEMA_VERSION) return brain
        var updated = brain
        if (updated.schemaVersion < V15_SCHEMA_VERSION) updated = runV15(updated, tokenizer)
        if (updated.schemaVersion < V16_SCHEMA_VERSION) updated = scrubSearchWordPlants(updated, tokenizer)
        updated = estimateTimeBucketCounts(updated)
        updated = refoldKeys(updated)
        updated = dropBlockedTopics(updated, tokenizer)
        updated = dropNoise(updated, tokenizer)
        if (isHollow(updated)) updated = rehydrateFromChannelProfiles(updated, tokenizer)
        return updated.copy(schemaVersion = TARGET_SCHEMA_VERSION)
    }

    private fun refoldTopic(key: String): String =
        if (key.all { it.code < 0x80 }) {
            key
        } else {
            key.split('|').map(NeuroText::fold).let { parts ->
                if (parts.size == 2) NeuroScoring.makeAffinityKey(parts[0], parts[1]) else parts.joinToString("|")
            }
        }

    private fun <V : Any> Map<String, V>.refold(merge: (V, V) -> V): Map<String, V> {
        if (keys.all { it.all { c -> c.code < 0x80 } }) return this
        val folded = LinkedHashMap<String, V>(size)
        forEach { (key, value) -> folded.merge(refoldTopic(key), value, merge) }
        return folded
    }

    private fun ContentVector.refold() = copy(topics = topics.refold(::maxOf))

    internal fun refoldKeys(brain: UserBrain): UserBrain =
        brain.copy(
            globalVector = brain.globalVector.refold(),
            shortsVector = brain.shortsVector.refold(),
            timeVectors = brain.timeVectors.mapValues { it.value.refold() },
            topicAffinities = brain.topicAffinities.refold(::maxOf),
            tagAffinities = brain.tagAffinities.refold(::maxOf),
            channelTopicProfiles = brain.channelTopicProfiles.mapValues { it.value.refold(::maxOf) },
            topicEvidence = brain.topicEvidence.refold { a, b -> if (a.positiveScore >= b.positiveScore) a else b },
            rejectionPatterns =
                brain.rejectionPatterns.refold { a, b ->
                    RejectionSignal(maxOf(a.count, b.count), maxOf(a.lastRejectedAt, b.lastRejectedAt))
                },
            idfWordFrequency = brain.idfWordFrequency.refold(Int::plus),
            preferredTopics = brain.preferredTopics.mapTo(LinkedHashSet(), ::refoldTopic),
            blockedTopics = brain.blockedTopics.mapTo(LinkedHashSet(), ::refoldTopic),
        )

    private fun isHollow(brain: UserBrain): Boolean =
        brain.globalVector.topics.values
            .count { it >= NeuroVectorMath.DEVELOPING_TOPIC_THRESHOLD } < HOLLOW_VECTOR_TOPICS

    private fun isBlocked(
        topic: String,
        blockedLemmas: Set<String>,
        tokenizer: NeuroTokenizer,
    ): Boolean {
        if (blockedLemmas.isEmpty()) return false
        val base = NeuroScoring.stripDomainTag(topic)
        return base in blockedLemmas || base.split(' ').any { tokenizer.normalizeLemma(it) in blockedLemmas }
    }

    /** Blocked topics, and with [withPhraseWords] the words of blocked phrases ("resident evil") too. */
    private fun blockedLemmas(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
        withPhraseWords: Boolean = false,
    ): Set<String> {
        val lemmas = brain.blockedTopics.mapTo(HashSet()) { tokenizer.normalizeLemma(NeuroText.fold(it).trim()) }
        if (withPhraseWords) lemmas.filter { ' ' in it }.forEach { lemmas += tokenizer.tokenize(it) }
        return lemmas
    }

    private fun dropBlockedTopics(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        val blocked = blockedLemmas(brain, tokenizer, withPhraseWords = true)
        if (blocked.isEmpty()) return brain

        fun Map<String, Double>.withoutBlockedEdges() = filterKeys { key -> key.split('|').none { isBlocked(it, blocked, tokenizer) } }
        return brain.copy(
            globalVector = brain.globalVector.copy(topics = brain.globalVector.topics.filterKeys { !isBlocked(it, blocked, tokenizer) }),
            topicAffinities = brain.topicAffinities.withoutBlockedEdges(),
            tagAffinities = brain.tagAffinities.withoutBlockedEdges(),
        )
    }

    /** Words that became filler after they were learned ("these", "did") leave the vector and its edges. */
    private fun dropNoise(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        fun Map<String, Double>.withoutNoisyEdges() = filterKeys { key -> key.split('|').none(tokenizer::isNoiseTopic) }
        return brain.copy(
            globalVector = brain.globalVector.copy(topics = brain.globalVector.topics.filterKeys { !tokenizer.isNoiseTopic(it) }),
            topicAffinities = brain.topicAffinities.withoutNoisyEdges(),
            tagAffinities = brain.tagAffinities.withoutNoisyEdges(),
        )
    }

    private fun estimateTimeBucketCounts(brain: UserBrain): UserBrain {
        val estimated =
            brain.timeVectors
                .filterKeys { it !in brain.timeBucketCounts }
                .mapValues { (_, vector) -> vector.topics.size.coerceAtMost(NeuroScoring.TIME_BUCKET_CONFIDENT_EVENTS) }
                .filterValues { it > 0 }
        return if (estimated.isEmpty()) brain else brain.copy(timeBucketCounts = brain.timeBucketCounts + estimated)
    }

    private fun runV15(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        val cleanedTopics =
            brain.globalVector.topics.filter { (topic, score) ->
                score >= NeuroVectorMath.TOPIC_PRUNE_THRESHOLD && !tokenizer.isNoiseTopic(topic)
            }
        val updated =
            brain.copy(globalVector = brain.globalVector.copy(topics = cleanedTopics))
        return rehydrateFromChannelProfiles(updated, tokenizer)
    }

    /**
     * A search writes evidence with no video or channel, and every word of one
     * query shares the same timestamp. Search-only single words that share a
     * first- or last-seen time came from one multi-word query. A single-word
     * search has no sibling, so it stays, as do catalog and chosen topics.
     */
    private fun scrubSearchWordPlants(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        val searchOnlyWords =
            brain.topicEvidence.filter { (topic, ev) ->
                ' ' !in topic &&
                    ev.positiveSignals > 0 &&
                    ev.explicitSignals == ev.positiveSignals &&
                    ev.watchSignals == 0 &&
                    ev.videoIds.isEmpty() &&
                    ev.channelIds.isEmpty()
            }
        if (searchOnlyWords.size < 2) return brain

        fun siblingTimes(time: (TopicEvidence) -> Long) =
            searchOnlyWords.values
                .map(time)
                .filter { it > 0L }
                .groupingBy { it }
                .eachCount()
                .filterValues { it >= 2 }
                .keys
        val sharedFirst = siblingTimes { it.firstSeenAt }
        val sharedLast = siblingTimes { it.lastSeenAt }
        val keep =
            NeuroSearchLearning.catalogTopics(tokenizer) +
                brain.preferredTopics.map { tokenizer.normalizeLemma(it) }
        val scrubbed =
            searchOnlyWords
                .filter { (topic, ev) ->
                    topic !in keep && (ev.firstSeenAt in sharedFirst || ev.lastSeenAt in sharedLast)
                }.keys
        if (scrubbed.isEmpty()) return brain

        return brain.copy(
            globalVector = brain.globalVector.copy(topics = brain.globalVector.topics - scrubbed),
            topicEvidence = brain.topicEvidence - scrubbed,
        )
    }

    private fun rehydrateFromChannelProfiles(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        if (brain.channelTopicProfiles.isEmpty()) return brain

        val blocked = blockedLemmas(brain, tokenizer, withPhraseWords = true)
        val aggregated = HashMap<String, Double>()
        brain.channelTopicProfiles.forEach { (channelId, profile) ->
            val quality = brain.channelScores[channelId] ?: 0.5
            if (quality < REHYDRATE_MIN_CHANNEL_QUALITY) return@forEach
            profile.forEach { (topic, weight) ->
                if (!tokenizer.isNoiseTopic(topic) && !isBlocked(topic, blocked, tokenizer)) {
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
                    .filter { !tokenizer.isNoiseTopic(it.key) && !isBlocked(it.key, blocked, tokenizer) }
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
