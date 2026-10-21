/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import kotlin.math.abs

/**
 * Schema 18 brings a brain to [NeuroKeySpec] 1 (#907). Keys written differently are read again and
 * merged ("tech's" joins "tech", "lo-fi" joins "lofi"). Word pairs learned by chance leave every
 * vector, profile and evidence record; document frequency keeps them, so they can still prove to be
 * phrases. The links onboarding drew between every two picks are removed.
 *
 * Document frequency keeps its old spellings: sync adds it up per device, so moving a count to a new
 * key would count it again once a peer sends the old one.
 */
internal object NeuroKeyMigration {
    const val SCHEMA_VERSION = 18

    /** A pair the viewer engaged with beyond one chance title weighs at least this much. */
    private const val KEPT_PAIR_WEIGHT = 0.30

    /** What onboarding gave every pair of picks; a link that grew past it has real co-watching behind it. */
    private const val ONBOARDING_EDGE = 0.3

    private const val WORD_PATTERN = "~"

    fun run(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain = run(brain, tokenizer, TopicEvidence::mergedWith, dropChancePairs = true)

    /**
     * The re-key once more after every sync merge, since a peer on an older key spec, or one the
     * sidecar still remembers, sends the old spellings again. Records that meet keep the stronger one,
     * so this can run any number of times without counting anything twice. Chance pairs are judged
     * only once, by the migration: today's counts are per video, and a single video's tag phrase is
     * not a chance pair.
     */
    fun afterSync(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain = run(brain, tokenizer, { a, b -> if (a.positiveScore >= b.positiveScore) a else b }, dropChancePairs = false)

    /**
     * Schema 20: every key read again by [NeuroKeySpec] 2, so "guitars" joins "guitar". Document
     * frequency is re-keyed too, summing what merges; sync re-keys its own per-device counts the same
     * way ([keyFunction]), so nothing is counted twice.
     */
    fun toKeySpec2(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        val rekey = Rekey(tokenizer, emptyMap())
        val forms = rekey.merged(brain.displayForms) { a, b -> if (a.votes >= b.votes) a else b }.toMutableMap()
        // A key that stems now reads as it did before ("Restoring Vintage Guitars", never "Restor Vintag Guitar"),
        // wherever a search or a label can reach it: a link's partner becomes search text too ("design claude").
        val linked = (brain.topicAffinities.keys + brain.tagAffinities.keys).flatMap { it.split('|') }
        val weighted =
            brain.globalVector.topics +
                (linked + brain.topicEvidence.keys).filterNot { it in brain.globalVector.topics }.associateWith { 0.0 }
        val spelledBefore = HashMap<String, Pair<String, Double>>()
        weighted.forEach { (key, weight) ->
            val base = NeuroScoring.stripDomainTag(key)
            rekey.keysFor(base).singleOrNull()?.takeIf { it != base && it !in forms }?.let { stemmed ->
                if (weight > (spelledBefore[stemmed]?.second ?: -1.0)) spelledBefore[stemmed] = base to weight
            }
        }
        val unchanged = weighted.keys.mapTo(HashSet(), NeuroScoring::stripDomainTag)
        spelledBefore.forEach { (stemmed, before) ->
            if (stemmed !in unchanged) forms[stemmed] = DisplayForm(NeuroDisplayForms.capitalized(before.first))
        }
        return run(brain, tokenizer, TopicEvidence::mergedWith, dropChancePairs = false, dropOnboardingEdges = false).copy(
            displayForms = forms,
            idfWordFrequency = rekey.merged(brain.idfWordFrequency, Int::plus),
        )
    }

    /** Schema 21: re-key by [NeuroKeySpec] 3; Hant spelling kept as [DisplayForm]; no fold for `ja` profiles. */
    fun toKeySpec3(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
    ): UserBrain {
        val rekey = Rekey(tokenizer, emptyMap())
        val forms = rekey.merged(brain.displayForms) { a, b -> if (a.votes >= b.votes) a else b }.toMutableMap()
        val linked = (brain.topicAffinities.keys + brain.tagAffinities.keys).flatMap { it.split('|') }
        val keys = brain.globalVector.topics.keys + brain.topicEvidence.keys + linked
        keys.mapTo(HashSet(), NeuroScoring::stripDomainTag).forEach { base ->
            val folded = rekey.keysFor(base).singleOrNull() ?: return@forEach
            if (folded != base && folded !in forms) forms[folded] = DisplayForm(base)
        }
        return run(brain, tokenizer, TopicEvidence::mergedWith, dropChancePairs = false, dropOnboardingEdges = false).copy(
            displayForms = forms,
            idfWordFrequency = rekey.merged(brain.idfWordFrequency, Int::plus),
            blockedTopics = brain.blockedTopics.mapTo(LinkedHashSet()) { rekey.keysFor(it).singleOrNull() ?: it },
        )
    }

    /** The keys [tokenizer] reads a stored key as: one, several when it splits, none when it goes. */
    fun keyFunction(tokenizer: NeuroTokenizer): (String) -> List<String> = Rekey(tokenizer, emptyMap())::keysFor

    private fun run(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
        mergeEvidence: (TopicEvidence, TopicEvidence) -> TopicEvidence,
        dropChancePairs: Boolean,
        dropOnboardingEdges: Boolean = true,
    ): UserBrain {
        // A pick was keyed by its whole label ("machine learning"); titles key it "machine learn".
        val pickForms =
            brain.preferredTopics.associate { label -> tokenizer.normalizeLemma(label) to tokenizer.keysOf(label) }.filter { (old, keys) ->
                keys != listOf(old)
            }
        val rekey = Rekey(tokenizer, pickForms)
        val rekeyed =
            brain.copy(
                globalVector = rekey.vector(brain.globalVector),
                shortsVector = rekey.vector(brain.shortsVector),
                recentVector = rekey.vector(brain.recentVector),
                timeVectors = brain.timeVectors.mapValues { rekey.vector(it.value) },
                topicAffinities = rekey.edges(brain.topicAffinities),
                tagAffinities = rekey.edges(brain.tagAffinities),
                channelTopicProfiles = brain.channelTopicProfiles.mapValues { rekey.merged(it.value, ::maxOf) },
                topicEvidence = rekey.merged(brain.topicEvidence, mergeEvidence),
                rejectionPatterns =
                    rekey.merged(brain.rejectionPatterns) { a, b ->
                        RejectionSignal(maxOf(a.count, b.count), maxOf(a.lastRejectedAt, b.lastRejectedAt))
                    },
            )
        val picks = brain.preferredTopics.flatMapTo(HashSet(), tokenizer::keysOf)
        val chance = if (dropChancePairs) chancePairs(rekeyed, tokenizer, picks) else emptySet()

        fun Map<String, Double>.withoutChance() = filterKeys { it !in chance }

        fun ContentVector.withoutChance() = copy(topics = topics.withoutChance())
        return rekeyed.copy(
            globalVector = rekeyed.globalVector.withoutChance(),
            shortsVector = rekeyed.shortsVector.withoutChance(),
            timeVectors = rekeyed.timeVectors.mapValues { it.value.withoutChance() },
            channelTopicProfiles = rekeyed.channelTopicProfiles.mapValues { it.value.withoutChance() },
            topicEvidence = rekeyed.topicEvidence.filterKeys { it !in chance },
            topicAffinities =
                rekeyed.topicAffinities.filter { (key, weight) ->
                    val ends = key.split('|')
                    ends.none { it in chance } && !(dropOnboardingEdges && ends.all { it in picks } && abs(weight - ONBOARDING_EDGE) < 1e-9)
                },
        )
    }

    /** Pairs that are no phrase by the viewer's own per-video counts and carry no other proof. */
    private fun chancePairs(
        brain: UserBrain,
        tokenizer: NeuroTokenizer,
        picks: Set<String>,
    ): Set<String> {
        // Earlier versions counted every event of a video, about two per video.
        val perVideo =
            IdfSnapshot(
                brain.idfWordFrequency.mapValues { (_, count) -> (count + 1) / 2 },
                (brain.idfTotalDocuments + 1) / 2,
            )
        val vectors = listOf(brain.globalVector, brain.shortsVector) + brain.timeVectors.values
        val weights = HashMap<String, Double>()
        vectors.forEach { vector -> vector.topics.forEach { (key, weight) -> weights.merge(key, weight, ::maxOf) } }
        // Older builds linked a video's top three keys, chance pairs among them, and the link can outlive the key.
        val linked = brain.topicAffinities.keys.flatMap { it.split('|') }
        val keys = weights.keys + brain.topicEvidence.keys + brain.channelTopicProfiles.values.flatMap { it.keys } + linked
        return keys.filterTo(HashSet()) { key ->
            val base = NeuroScoring.stripDomainTag(key)
            val evidence = brain.topicEvidence[base] ?: brain.topicEvidence[key]
            ' ' in base &&
                base !in picks &&
                !tokenizer.isKnownPhrase(base, perVideo) &&
                (evidence?.explicitSignals ?: 0) == 0 &&
                (evidence?.videoIds?.size ?: 0) < NeuroPhrases.MIN_COUNT &&
                (weights[key] ?: 0.0) < KEPT_PAIR_WEIGHT
        }
    }

    /**
     * Reads keys again by the tokenizer's [NeuroKeySpec]: a key holding punctuation or an expression is
     * chunked again, any other key is read word by word, which leaves a key already read unchanged.
     */
    private class Rekey(
        private val tokenizer: NeuroTokenizer,
        private val pickForms: Map<String, List<String>>,
    ) {
        private val cache = HashMap<String, List<String>>()

        private val foldHan = tokenizer.hanFolds

        fun keysFor(key: String): List<String> =
            cache.getOrPut(key) {
                // A word-level rejection pattern ("~guitar") keeps its mark.
                if (key.startsWith(WORD_PATTERN)) return@getOrPut keysFor(key.removePrefix(WORD_PATTERN)).map { WORD_PATTERN + it }
                val tag = key.substringAfter(':', "")
                val base = if (tag.isEmpty()) key else key.substringBefore(':')
                pickForms[key]?.let { return@getOrPut it }
                val read = if (foldHan) NeuroText.simplified(base) else base
                // A key left with no word ("m/v") goes.
                val parts =
                    if (needsRekey(read)) {
                        tokenizer.chunk(read).chunks.map { it.joinToString(" ") }
                    } else {
                        listOf(read.split(' ').joinToString(" ", transform = tokenizer::keyOf))
                    }
                if (tag.isEmpty()) parts else parts.map { "$it:$tag" }
            }

        private fun needsRekey(base: String): Boolean =
            base.any { it != ' ' && !NeuroText.isWordChar(it) } ||
                base in NeuroKeySpec.EXPRESSIONS ||
                NeuroKeySpec.EXPRESSIONS.keys.any { variant -> ' ' in variant && " $base ".contains(" $variant ") }

        fun <V : Any> merged(
            map: Map<String, V>,
            merge: (V, V) -> V,
        ): Map<String, V> {
            val out = LinkedHashMap<String, V>(map.size)
            map.forEach { (key, value) -> keysFor(key).forEach { out.merge(it, value, merge) } }
            return out
        }

        fun vector(vector: ContentVector): ContentVector = vector.copy(topics = merged(vector.topics, ::maxOf))

        fun edges(edges: Map<String, Double>): Map<String, Double> {
            val out = LinkedHashMap<String, Double>(edges.size)
            edges.forEach { (key, weight) ->
                val ends = key.split('|')
                if (ends.size != 2) return@forEach
                val left = keysFor(ends[0]).singleOrNull() ?: return@forEach
                val right = keysFor(ends[1]).singleOrNull() ?: return@forEach
                if (left != right) out.merge(NeuroScoring.makeAffinityKey(left, right), weight, ::maxOf)
            }
            return out
        }
    }
}
