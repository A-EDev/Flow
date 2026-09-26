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
 * Turns a typed search into phrase-level interests. A query is one interest,
 * so "lofi hip hop" plants its contiguous bigrams ("lofi hip", "hip hop"), the
 * same keys title extraction emits, rather than three unrelated words at the
 * same floor weight. Generic words still nudge the vector, far below the floor.
 */
internal object NeuroSearchLearning {
    private const val MAX_QUERY_TOKENS = 4
    private const val LEARNING_RATE = 0.05
    private const val EVIDENCE_SCORE = 0.5
    private const val EVIDENCE_SCORE_CAP = 50.0
    private const val WEAK_WORD_SHARE = 0.25

    data class QueryTopics(
        val phrases: List<String>,
        val weakWords: List<String>,
    )

    fun queryTopics(
        rawQuery: String,
        tokenizer: NeuroTokenizer,
        blockedTopics: Set<String> = emptySet(),
    ): QueryTopics {
        val tokens = tokenizer.tokenize(rawQuery.trim()).distinct().take(MAX_QUERY_TOKENS)
        val blocked = tokens.filter { token -> blockedTopics.any { token == it || token == tokenizer.normalizeLemma(it) } }.toSet()
        val usable = tokens.filterNot { it in blocked }
        if (usable.isEmpty()) return QueryTopics(emptyList(), emptyList())
        if (usable.size == 1) return QueryTopics(usable, emptyList())

        val bigrams =
            tokens
                .zipWithNext { a, b -> "$a $b" }
                .filter { bigram -> bigram.split(' ').none { it in blocked } && !tokenizer.isNoiseTopic(bigram) }
        val catalog = catalogTopics(tokenizer)
        val catalogWords = usable.filter { it in catalog }
        val phrases = (bigrams + catalogWords).take(NeuroScoring.TOPIC_ACQUISITION_TOP_K)
        return QueryTopics(phrases, usable.filterNot { it in phrases })
    }

    /** Applies a search to the brain; null when the query carries no usable topic. */
    fun learn(
        brain: UserBrain,
        rawQuery: String,
        tokenizer: NeuroTokenizer,
        now: Long,
    ): UserBrain? {
        val topics = queryTopics(rawQuery, tokenizer, brain.blockedTopics)
        if (topics.phrases.isEmpty()) return null

        val evidence = brain.topicEvidence.toMutableMap()
        topics.phrases.forEach { topic ->
            val existing = evidence[topic]
            evidence[topic] =
                TopicEvidence(
                    positiveSignals = (existing?.positiveSignals ?: 0) + 1,
                    negativeSignals = existing?.negativeSignals ?: 0,
                    watchSignals = existing?.watchSignals ?: 0,
                    explicitSignals = (existing?.explicitSignals ?: 0) + 1,
                    positiveScore = ((existing?.positiveScore ?: 0.0) + EVIDENCE_SCORE).coerceAtMost(EVIDENCE_SCORE_CAP),
                    videoIds = existing?.videoIds.orEmpty(),
                    channelIds = existing?.channelIds.orEmpty(),
                    firstSeenAt = existing?.firstSeenAt?.takeIf { it > 0L } ?: now,
                    lastSeenAt = now,
                )
        }

        val shares = topics.phrases.associateWith { 1.0 } + topics.weakWords.associateWith { WEAK_WORD_SHARE }
        val total = shares.values.sum()
        val learned =
            NeuroVectorMath.adjustVector(
                brain.globalVector,
                ContentVector(topics = shares.mapValues { it.value / total }),
                LEARNING_RATE,
            )
        val planted =
            NeuroVectorMath.plantKeys(learned, topics.phrases, NeuroScoring.TOPIC_ACQUISITION_FLOOR)
        return brain.copy(globalVector = planted, topicEvidence = evidence)
    }

    /** Catalog topics as the tokenizer keys them ("Hip Hop" becomes "hip hop"). */
    fun catalogTopics(tokenizer: NeuroTokenizer): Set<String> =
        NeuroTopicCatalog.TOPIC_CATEGORIES
            .flatMap { it.topics }
            .map { tokenizer.tokenize(it).joinToString(" ") }
            .filter { it.isNotEmpty() }
            .toSet()
}
