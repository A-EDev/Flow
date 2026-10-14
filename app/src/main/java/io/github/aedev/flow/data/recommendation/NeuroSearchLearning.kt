/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

/**
 * Turns a typed search into one interest (#907). The words a question wraps around its subject
 * ("how does X work", "what is X", "X explained") are dropped, the subject is read by [NeuroKeySpec],
 * and what gets planted is a phrase it holds that the engine knows, a catalog topic in it, or else the
 * subject itself. Its other words only nudge the vector, far below the floor.
 */
internal object NeuroSearchLearning {
    private const val MAX_QUERY_WORDS = 8
    private const val LEARNING_RATE = 0.05
    private const val EVIDENCE_SCORE = 0.5
    private const val EVIDENCE_SCORE_CAP = 50.0
    private const val WEAK_WORD_SHARE = 0.25

    private val Templates =
        listOf(
            Regex("^how (?:do|does|did|can|could|would|will|is|are) (.+?) (?:work|works|working|happen|happens)$"),
            Regex("^how (.+?) works?$"),
            Regex("^(?:what|who) (?:is|are|was|were) (.+)$"),
            Regex("^explain(?:ing)? (.+)$"),
            Regex("^(.+?) explained$"),
            Regex("^how to (.+)$"),
        )

    private val Whitespace = Regex("\\s+")

    data class QueryTopics(
        val phrases: List<String>,
        val weakWords: List<String>,
    )

    /** What a search asks about, without the words of the question around it. */
    fun subjectOf(query: String): String {
        val folded =
            NeuroText
                .fold(query)
                .trim()
                .trimEnd('?', '!', '.')
                .replace(Whitespace, " ")
        return Templates.firstNotNullOfOrNull { it.matchEntire(folded)?.groupValues?.get(1) } ?: folded
    }

    fun queryTopics(
        rawQuery: String,
        tokenizer: NeuroTokenizer,
        blockedTopics: Set<String> = emptySet(),
        idf: IdfSnapshot = IdfSnapshot(emptyMap(), 0),
    ): QueryTopics {
        val chunks = tokenizer.chunk(subjectOf(rawQuery))
        val words = chunks.words.distinct().take(MAX_QUERY_WORDS)
        val blockedKeys = blockedTopics.flatMapTo(HashSet()) { tokenizer.keysOf(it) }
        val usable = words.filterNot { it in blockedKeys }
        if (usable.isEmpty()) return QueryTopics(emptyList(), emptyList())
        if (usable.size == 1) return QueryTopics(usable, emptyList())
        val runs =
            chunks
                .runs()
                .filter { run -> chunks.chunks[run.chunk].subList(run.from, run.to).none { it in blockedKeys } }
                .map { it.key }
        val known = runs.filter { tokenizer.isKnownPhrase(it, idf) }
        val catalogWords = usable.filter { it in tokenizer.catalogKeys }
        val subject =
            chunks.chunks
                .singleOrNull()
                ?.takeIf { known.isEmpty() && catalogWords.isEmpty() && it.size >= 2 && it.none { word -> word in blockedKeys } }
                ?.joinToString(" ")
                ?.takeIf { it.count { c -> c == ' ' } < NeuroKeySpec.MAX_RUN_WORDS }
        val phrases = (known + catalogWords + listOfNotNull(subject)).distinct().take(NeuroScoring.TOPIC_ACQUISITION_TOP_K)
        return QueryTopics(phrases, usable.filterNot { it in phrases })
    }

    /** Applies a search to the brain; null when the query carries no usable topic. */
    fun learn(
        brain: UserBrain,
        rawQuery: String,
        tokenizer: NeuroTokenizer,
        now: Long,
    ): UserBrain? {
        val topics = queryTopics(rawQuery, tokenizer, brain.blockedTopics, IdfSnapshot(brain.idfWordFrequency, brain.idfTotalDocuments))
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
        val query = ContentVector(topics = shares.mapValues { it.value / total })
        val picks = brain.preferredTopics.flatMapTo(HashSet(), tokenizer::keysOf)

        fun learnOn(
            vector: ContentVector,
            horizon: NeuroVectorMath.Horizon,
        ) = NeuroVectorMath.learn(vector, query, LEARNING_RATE / NeuroVectorMath.DECAY_REFERENCE_RATE, horizon, LEARNING_RATE, picks)
        val planted =
            NeuroVectorMath.plantKeys(
                learnOn(brain.globalVector, NeuroVectorMath.LONG_TERM),
                topics.phrases,
                NeuroScoring.TOPIC_ACQUISITION_FLOOR,
            )
        return brain.copy(
            globalVector = planted,
            recentVector = learnOn(brain.recentVector, NeuroVectorMath.RECENT),
            topicEvidence = evidence,
        )
    }
}
