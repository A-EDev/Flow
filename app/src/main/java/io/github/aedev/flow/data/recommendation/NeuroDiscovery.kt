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
import java.util.Calendar

/**
 * Smart Discovery Query Engine V2.
 *
 * Generates search queries across 7 strategies to maximize
 * content discovery while staying relevant to user interests.
 *
 * Stateless — takes a brain snapshot and returns queries.
 * Can be updated independently of the scoring/learning pipeline.
 */
internal class NeuroDiscovery(
    private val topicCategories: List<TopicCategory>,
    private val tokenizer: NeuroTokenizer
) {

    // ── Query quality helpers ──

    /**
     * Words that have no value as topic signals and should never appear
     * as raw tokens in a generated query (years, AI meta-words, etc.).
     */
    private val QUERY_NOISE_WORDS = hashSetOf(
        // Year tokens
        "2020", "2021", "2022", "2023", "2024",
        "2025", "2026", "2027", "2028", "2029", "2030",
        // AI / prompt meta-words
        "prompt", "prompts", "prompting",
        // Pure noise verbs that get into topics
        "use", "used"
    )

    /**
     * Deduplicates words, strips year tokens and QUERY_NOISE_WORDS.
     * Returns null when the surviving tokens are too few to make a
     * meaningful search query (< 2 meaningful words).
     */
    private fun sanitizeQuery(raw: String): String? {
        val words = raw.trim().split(NeuroTokenizer.WHITESPACE_REGEX)
        val deduped = LinkedHashSet(words) 
        val cleaned = deduped.filter { word ->
            val lower = word.lowercase()
            lower.isNotEmpty() &&
                lower !in QUERY_NOISE_WORDS &&
                !lower.matches(Regex("\\d{4}")) 
        }
        return if (cleaned.size >= 2) cleaned.joinToString(" ") else null
    }

    /**
     * Returns false for topics that are meta-words, year tokens, or
     * too short — they should not drive query templates.
     */
    private fun isSubstantialTopic(topic: String): Boolean {
        if (topic.length < 3) return false
        val lower = topic.lowercase()
        if (lower in QUERY_NOISE_WORDS) return false
        if (lower.matches(Regex("\\d{4}"))) return false
        return true
    }

    /**
     * Full query generation with metadata — useful for the transparency UI
     * so users can see WHY each query was generated.
     */
    fun generateQueries(
        brain: UserBrain,
        personaProvider: (UserBrain) -> FlowPersona
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()
        val blocked = brain.blockedTopics

        // ── Precompute shared data ──
        val rankedTopics = brain.globalVector.topics.entries
            .sortedByDescending { it.value }
        val topTopics = rankedTopics.take(8).map { it.key }.filter { isSubstantialTopic(it) }
        val midTopics = rankedTopics.drop(8).take(10).map { it.key }.filter { isSubstantialTopic(it) }

        val bucket = TimeBucket.current()
        val timeVector = brain.timeVectors[bucket] ?: ContentVector()
        val timeTopics = timeVector.topics.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .filter { isSubstantialTopic(it) }

        val topChannels = brain.channelScores.entries
            .sortedByDescending { it.value }
            .take(10)

        val persona = personaProvider(brain)
        val isColdStart = brain.totalInteractions < NeuroScoring.COLD_START_THRESHOLD

        // ── Strategy 1: Deep Dive ──
        queries.addAll(generateDeepDiveQueries(topTopics, persona, brain))

        // ── Strategy 2: Cross-Topic ──
        queries.addAll(
            generateCrossTopicQueries(
                topTopics, midTopics, brain.topicAffinities
            )
        )

        // ── Strategy 3: Trending ──
        queries.addAll(generateTrendingQueries(topTopics, timeTopics))

        // ── Strategy 4: Adjacent Exploration ──
        queries.addAll(
            generateAdjacentQueries(topTopics, brain, isColdStart)
        )

        // ── Strategy 5: Channel Discovery ──
        queries.addAll(
            generateChannelDiscoveryQueries(
                topChannels, brain.channelTopicProfiles
            )
        )

        // ── Strategy 6: Contextual ──
        queries.addAll(
            generateContextualQueries(timeTopics, bucket, persona)
        )

        // ── Strategy 7: Format-Driven ──
        queries.addAll(generateFormatQueries(topTopics, persona, brain))

        // ── Filter, deduplicate, and balance ──
        val filtered = queries.filter { q ->
            !blocked.any { b -> q.query.lowercase().contains(b) }
        }.mapNotNull { q ->
            sanitizeQuery(q.query)?.let { q.copy(query = it) }
        }

        val balanced = balanceQueryStrategies(filtered)

        // ── Fallback ──
        if (balanced.isEmpty()) {
            val preferred = brain.preferredTopics.toList()
            return if (preferred.isNotEmpty()) {
                preferred.shuffled().take(5).map { topic ->
                    DiscoveryQuery(
                        topic,
                        QueryStrategy.DEEP_DIVE,
                        0.5,
                        "Fallback to preferred topic"
                    )
                }
            } else {
                listOf("Music", "Science", "Technology", "Education", "Nature")
                    .map { topic ->
                        DiscoveryQuery(
                            topic,
                            QueryStrategy.ADJACENT_EXPLORATION,
                            0.3,
                            "Cold start default"
                        )
                    }
            }
        }

        return balanced
    }

    // ══════════════════════════════════════════════
    // Strategy 1: DEEP DIVE
    // Natural-language queries that deepen top interests.
    // ══════════════════════════════════════════════

    private fun generateDeepDiveQueries(
        topTopics: List<String>,
        persona: FlowPersona,
        brain: UserBrain
    ): List<DiscoveryQuery> {
        if (topTopics.isEmpty()) return emptyList()

        val queries = mutableListOf<DiscoveryQuery>()
        val primary = topTopics.first()

        // ── Natural phrasing templates ──
        val depthTemplates = listOf(
            "$primary explained",
            "$primary for beginners",
            "$primary advanced techniques",
            "$primary deep dive",
            "$primary masterclass",
            "understanding $primary",
            "how $primary actually works",
            "$primary tips you didn't know",
            "best $primary channels",
            "$primary in depth"
        )

        // ── Persona-aware template selection ──
        val personaTemplates = when (persona) {
            FlowPersona.SCHOLAR -> listOf(
                "$primary lecture",
                "$primary research explained",
                "$primary academic perspective",
                "$primary analysis essay"
            )
            FlowPersona.DEEP_DIVER -> listOf(
                "$primary documentary",
                "$primary full breakdown",
                "$primary the complete story",
                "$primary long form"
            )
            FlowPersona.SKIMMER -> listOf(
                "$primary in 10 minutes",
                "$primary quick guide",
                "$primary shorts",
                "$primary highlights"
            )
            FlowPersona.AUDIOPHILE -> listOf(
                "$primary playlist",
                "$primary mix",
                "$primary album review",
                "best $primary songs"
            )
            FlowPersona.BINGER -> listOf(
                "$primary marathon",
                "$primary all episodes",
                "$primary complete series",
                "$primary binge watch"
            )
            FlowPersona.LIVEWIRE -> listOf(
                "$primary live",
                "$primary livestream",
                "$primary premiere",
                "$primary live discussion"
            )
            else -> emptyList()
        }

        // Pick 1 depth template + 1 persona template for primary topic
        depthTemplates.shuffled().take(1).forEach { template ->
            queries.add(
                DiscoveryQuery(
                    template, QueryStrategy.DEEP_DIVE, 0.9,
                    "Deepen primary interest: $primary"
                )
            )
        }

        personaTemplates.shuffled().take(1).forEach { template ->
            queries.add(
                DiscoveryQuery(
                    template, QueryStrategy.DEEP_DIVE, 0.85,
                    "Persona-matched depth: ${persona.title} + $primary"
                )
            )
        }

        // Secondary topic gets a simpler depth query
        if (topTopics.size >= 2) {
            val secondary = topTopics[1]
            queries.add(
                DiscoveryQuery(
                    "$secondary explained",
                    QueryStrategy.DEEP_DIVE,
                    0.8,
                    "Deepen secondary interest: $secondary"
                )
            )
        }

        return queries
    }

    // ══════════════════════════════════════════════
    // Strategy 2: CROSS-TOPIC
    // Intelligent combination of related interests.
    // Only combines topics that have affinity evidence
    // or belong to the same macro category.
    // ══════════════════════════════════════════════

    private fun generateCrossTopicQueries(
        topTopics: List<String>,
        midTopics: List<String>,
        affinities: Map<String, Double>
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()

        // ── Affinity-backed combinations ──
        val topAffinities = affinities.entries
            .sortedByDescending { it.value }
            .take(5)

        topAffinities.forEach { (key, score) ->
            val parts = key.split("|")
            if (parts.size == 2) {
                val (t1, t2) = parts

                val templates = listOf(
                    "$t1 meets $t2",
                    "$t1 and $t2",
                    "$t1 for $t2 fans",
                    "$t2 inspired $t1"
                )

                templates.shuffled().take(1).forEach { template ->
                    queries.add(
                        DiscoveryQuery(
                            template, QueryStrategy.CROSS_TOPIC,
                            0.7 + (score * 0.2),
                            "Affinity pair (strength: ${
                                "%.2f".format(score)
                            }): $t1 + $t2"
                        )
                    )
                }
            }
        }

        // ── Category-coherent combinations ──
        if (topTopics.size >= 2) {
            for (i in topTopics.indices) {
                for (j in i + 1 until minOf(i + 3, topTopics.size)) {
                    val t1 = topTopics[i]
                    val t2 = topTopics[j]

                    val sameCategory = topicCategories.any { cat ->
                        val catTopics = cat.topics.map { tokenizer.normalizeLemma(it) }
                        catTopics.contains(t1) && catTopics.contains(t2)
                    }

                    if (sameCategory) {
                        queries.add(
                            DiscoveryQuery(
                                "$t1 vs $t2",
                                QueryStrategy.CROSS_TOPIC,
                                0.65,
                                "Same-category pair: $t1 + $t2"
                            )
                        )
                    }
                }
            }
        }

        // ── Bridge query: strong topic + weaker interest ──
        if (topTopics.isNotEmpty() && midTopics.isNotEmpty()) {
            val strong = topTopics.first()
            val emerging = midTopics.shuffled().first()
            queries.add(
                DiscoveryQuery(
                    "$strong $emerging",
                    QueryStrategy.CROSS_TOPIC,
                    0.55,
                    "Bridge: strong($strong) + emerging($emerging)"
                )
            )
        }

        return queries
    }

    // ══════════════════════════════════════════════
    // Strategy 3: TRENDING
    // Find fresh/current content in known interests.
    // Uses temporal markers that YouTube search
    // interprets as recency signals.
    // ══════════════════════════════════════════════

    private fun generateTrendingQueries(
        topTopics: List<String>,
        timeTopics: List<String>
    ): List<DiscoveryQuery> {
        if (topTopics.isEmpty()) return emptyList()

        val queries = mutableListOf<DiscoveryQuery>()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonth = Calendar.getInstance().getDisplayName(
            Calendar.MONTH, Calendar.LONG, java.util.Locale.ENGLISH
        ) ?: ""

        val primary = topTopics.first()

        val trendingTemplates = listOf(
            "$primary $currentYear",
            "$primary $currentMonth $currentYear",
            "$primary latest",
            "$primary this week",
            "new $primary",
            "$primary news today",
            "$primary update $currentYear",
            "best $primary $currentYear"
        )

        trendingTemplates.shuffled().take(2).forEach { template ->
            queries.add(
                DiscoveryQuery(
                    template, QueryStrategy.TRENDING, 0.75,
                    "Fresh content in primary interest"
                )
            )
        }

        // ── Time-context trending ──
        val timeTop = timeTopics.firstOrNull()
        if (timeTop != null && timeTop != primary) {
            queries.add(
                DiscoveryQuery(
                    "$timeTop $currentYear new",
                    QueryStrategy.TRENDING,
                    0.70,
                    "Trending in time-context topic: $timeTop"
                )
            )
        }

        return queries
    }

    // ══════════════════════════════════════════════
    // Strategy 4: ADJACENT EXPLORATION
    // Find topics that are RELATED to user interests
    // but not yet explored. Uses category graph
    // proximity instead of random macro categories.
    // ══════════════════════════════════════════════

    private fun generateAdjacentQueries(
        topTopics: List<String>,
        brain: UserBrain,
        isColdStart: Boolean
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()

        // ── Find categories the user IS active in ──
        val activeCategories = mutableSetOf<TopicCategory>()
        topTopics.forEach { topic ->
            topicCategories.forEach { cat ->
                if (cat.topics.any { tokenizer.normalizeLemma(it) == topic }) {
                    activeCategories.add(cat)
                }
            }
        }

        // ── Find unexplored topics WITHIN active categories ──
        val unexploredAdjacent = mutableListOf<Pair<String, String>>()

        activeCategories.forEach { category ->
            category.topics.forEach { topic ->
                val normalized = tokenizer.normalizeLemma(topic)
                val score = brain.globalVector.topics[normalized] ?: 0.0
                if (score < NeuroScoring.EXPLORATION_SCORE_THRESHOLD) {
                    unexploredAdjacent.add(topic to category.name)
                }
            }
        }

        // Pick adjacent unexplored topics
        unexploredAdjacent.shuffled().take(2).forEach { (topic, category) ->
            val anchor = topTopics.firstOrNull { anchorTopic ->
                topicCategories.any { cat ->
                    cat.name == category &&
                        cat.topics.any { tokenizer.normalizeLemma(it) == anchorTopic }
                }
            }

            val query = if (anchor != null) {
                "$topic for $anchor fans"
            } else {
                "$topic beginner guide"
            }

            queries.add(
                DiscoveryQuery(
                    query, QueryStrategy.ADJACENT_EXPLORATION, 0.55,
                    "Adjacent to active category: $category"
                )
            )
        }

        // ── One truly novel category (only if not cold-start) ──
        if (!isColdStart) {
            val novelCategories = topicCategories.filter { cat ->
                cat !in activeCategories
            }

            val bestNovel = novelCategories.maxByOrNull { cat ->
                cat.topics.count { topic ->
                    val normalized = tokenizer.normalizeLemma(topic)
                    (brain.globalVector.topics[normalized] ?: 0.0) > 0.0
                }
            }

            if (bestNovel != null) {
                val bridgeTopic = bestNovel.topics.firstOrNull { topic ->
                    val normalized = tokenizer.normalizeLemma(topic)
                    (brain.globalVector.topics[normalized] ?: 0.0) > 0.0
                } ?: bestNovel.topics.random()

                queries.add(
                    DiscoveryQuery(
                        "$bridgeTopic introduction",
                        QueryStrategy.ADJACENT_EXPLORATION,
                        0.40,
                        "Novel category via bridge: ${bestNovel.name}"
                    )
                )
            }
        }

        return queries
    }

    // ══════════════════════════════════════════════
    // Strategy 5: CHANNEL DISCOVERY
    // Use knowledge of favorite channels to find
    // similar creators the user hasn't seen.
    // ══════════════════════════════════════════════

    private fun generateChannelDiscoveryQueries(
        topChannels: List<Map.Entry<String, Double>>,
        channelProfiles: Map<String, Map<String, Double>>
    ): List<DiscoveryQuery> {
        if (topChannels.isEmpty()) return emptyList()

        val queries = mutableListOf<DiscoveryQuery>()

        // ── Channel topic signature queries ──
        topChannels.take(3).forEach { (channelId, score) ->
            val profile = channelProfiles[channelId] ?: return@forEach
            if (profile.size < 2) return@forEach

            val topChannelTopics = profile.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

            if (topChannelTopics.size >= 2) {
                val signatureQuery = topChannelTopics.take(2).joinToString(" ")
                queries.add(
                    DiscoveryQuery(
                        signatureQuery,
                        QueryStrategy.CHANNEL_DISCOVERY,
                        0.60 + (score * 0.2),
                        "Channel signature match for channel: $channelId"
                    )
                )
            }
        }

        // ── "Channels like X" style queries ──
        topChannels
            .filter { it.value > 0.6 }
            .take(2)
            .forEach { (channelId, _) ->
                val profile = channelProfiles[channelId]
                if (profile != null && profile.isNotEmpty()) {
                    val niche = profile.entries
                        .maxByOrNull { it.value }?.key ?: return@forEach
                    queries.add(
                        DiscoveryQuery(
                            "best $niche channels",
                            QueryStrategy.CHANNEL_DISCOVERY,
                            0.55,
                            "Find similar channels in niche: $niche"
                        )
                    )
                }
            }

        return queries
    }

    // ══════════════════════════════════════════════
    // Strategy 6: CONTEXTUAL
    // Time-of-day and day-of-week aware queries.
    // ══════════════════════════════════════════════

    private fun generateContextualQueries(
        timeTopics: List<String>,
        bucket: TimeBucket,
        persona: FlowPersona
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()

        // ── Time-of-day mood templates ──
        val moodTemplates = when (bucket) {
            TimeBucket.WEEKDAY_MORNING,
            TimeBucket.WEEKEND_MORNING -> listOf(
                "morning routine", "daily motivation",
                "breakfast cooking", "morning news",
                "start your day", "morning workout"
            )
            TimeBucket.WEEKDAY_AFTERNOON -> listOf(
                "lunch break watching", "afternoon productivity",
                "quick tutorials", "midday podcast"
            )
            TimeBucket.WEEKEND_AFTERNOON -> listOf(
                "weekend project", "DIY weekend",
                "afternoon adventure", "weekend cooking"
            )
            TimeBucket.WEEKDAY_EVENING,
            TimeBucket.WEEKEND_EVENING -> listOf(
                "evening relaxation", "dinner recipe",
                "evening documentary", "wind down"
            )
            TimeBucket.WEEKDAY_NIGHT,
            TimeBucket.WEEKEND_NIGHT -> listOf(
                "late night content", "ambient sleep",
                "night time stories", "chill playlist",
                "lo-fi study", "night documentary"
            )
        }

        // ── Combine time mood with user's time-specific interest ──
        val timeTop = timeTopics.firstOrNull()
        if (timeTop != null) {
            val moodWord = when (bucket) {
                TimeBucket.WEEKDAY_MORNING,
                TimeBucket.WEEKEND_MORNING -> "morning"
                TimeBucket.WEEKDAY_AFTERNOON,
                TimeBucket.WEEKEND_AFTERNOON -> "afternoon"
                TimeBucket.WEEKDAY_EVENING,
                TimeBucket.WEEKEND_EVENING -> "evening"
                TimeBucket.WEEKDAY_NIGHT,
                TimeBucket.WEEKEND_NIGHT -> "night"
            }

            queries.add(
                DiscoveryQuery(
                    "$timeTop $moodWord",
                    QueryStrategy.CONTEXTUAL,
                    0.65,
                    "Time-context interest: $timeTop at $moodWord"
                )
            )
        }

        // ── One pure mood query (lower confidence) ──
        moodTemplates.shuffled().take(1).forEach { template ->
            queries.add(
                DiscoveryQuery(
                    template, QueryStrategy.CONTEXTUAL, 0.40,
                    "Time-of-day mood: $bucket"
                )
            )
        }

        return queries
    }

    // ══════════════════════════════════════════════
    // Strategy 7: FORMAT-DRIVEN
    // Match the user's preferred content FORMAT
    // ══════════════════════════════════════════════

    private fun generateFormatQueries(
        topTopics: List<String>,
        persona: FlowPersona,
        brain: UserBrain
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()
        val primary = topTopics.firstOrNull() ?: return emptyList()

        val v = brain.globalVector

        // ── Duration preference ──
        val durationFormat = when {
            v.duration > 0.75 -> listOf(
                "$primary full documentary",
                "$primary 1 hour",
                "$primary complete guide",
                "$primary long form essay"
            )
            v.duration < 0.30 -> listOf(
                "$primary shorts",
                "$primary in under 5 minutes",
                "$primary quick tips",
                "$primary one minute"
            )
            else -> listOf(
                "$primary 20 minute guide",
                "$primary medium length"
            )
        }

        durationFormat.shuffled().take(1).forEach { template ->
            queries.add(
                DiscoveryQuery(
                    template, QueryStrategy.FORMAT_DRIVEN, 0.60,
                    "Duration preference: ${
                        "%.2f".format(v.duration)
                    }"
                )
            )
        }

        // ── Pacing preference ──
        if (v.pacing > 0.65 && topTopics.size >= 2) {
            queries.add(
                DiscoveryQuery(
                    "${topTopics[0]} compilation ${topTopics.getOrNull(1) ?: "best of"}",
                    QueryStrategy.FORMAT_DRIVEN,
                    0.55,
                    "High pacing preference: compilations"
                )
            )
        }

        // ── Complexity preference ──
        if (v.complexity > 0.70) {
            queries.add(
                DiscoveryQuery(
                    "$primary technical deep dive",
                    QueryStrategy.FORMAT_DRIVEN,
                    0.60,
                    "High complexity preference"
                )
            )
        } else if (v.complexity < 0.30) {
            queries.add(
                DiscoveryQuery(
                    "$primary explained simply",
                    QueryStrategy.FORMAT_DRIVEN,
                    0.60,
                    "Low complexity preference"
                )
            )
        }

        return queries
    }

    // ══════════════════════════════════════════════
    // QUERY BALANCING & DEDUPLICATION
    // ══════════════════════════════════════════════

    private fun balanceQueryStrategies(
        queries: List<DiscoveryQuery>
    ): List<DiscoveryQuery> {
        // ── Semantic deduplication ──
        val deduped = mutableListOf<DiscoveryQuery>()
        val seenTokenSets = mutableListOf<Set<String>>()

        val sorted = queries.sortedByDescending { it.confidence }

        for (query in sorted) {
            val tokens = query.query.lowercase()
                .split(NeuroTokenizer.WHITESPACE_REGEX)
                .filter { it.length > 2 }
                .map { tokenizer.normalizeLemma(it) }
                .toSet()

            val isDuplicate = seenTokenSets.any { existing ->
                val intersection = tokens.intersect(existing).size
                val union = tokens.union(existing).size
                if (union == 0) false
                else (intersection.toDouble() / union) > 0.6
            }

            if (!isDuplicate) {
                deduped.add(query)
                seenTokenSets.add(tokens)
            }
        }

        // ── Strategy balancing ──
        val maxQueries = 12
        val byStrategy = deduped.groupBy { it.strategy }
        val balanced = mutableListOf<DiscoveryQuery>()

        // Phase 1: Take the best query from each strategy
        val strategyPriority = listOf(
            QueryStrategy.DEEP_DIVE,
            QueryStrategy.TRENDING,
            QueryStrategy.CROSS_TOPIC,
            QueryStrategy.ADJACENT_EXPLORATION,
            QueryStrategy.CONTEXTUAL,
            QueryStrategy.CHANNEL_DISCOVERY,
            QueryStrategy.FORMAT_DRIVEN
        )

        strategyPriority.forEach { strategy ->
            byStrategy[strategy]?.firstOrNull()?.let { best ->
                balanced.add(best)
            }
        }

        // Phase 2: Fill remaining slots by confidence
        val used = balanced.toSet()
        val remaining = deduped
            .filter { it !in used }
            .sortedByDescending { it.confidence }

        for (query in remaining) {
            if (balanced.size >= maxQueries) break

            val strategyCount = balanced.count { it.strategy == query.strategy }
            if (strategyCount < 3) {
                balanced.add(query)
            }
        }

        // Phase 3: Shuffle within confidence tiers
        val highConf = balanced.filter { it.confidence >= 0.7 }.shuffled()
        val medConf = balanced.filter {
            it.confidence in 0.4..0.69
        }.shuffled()
        val lowConf = balanced.filter { it.confidence < 0.4 }.shuffled()

        return highConf + medConf + lowConf
    }

    // ── Legacy API: exploration queries ──

    fun getExplorationQueries(brain: UserBrain): List<String> {
        val blocked = brain.blockedTopics

        val macroCategoryCleanRegex = Regex("[^a-zA-Z ]")
        val macroCategories = topicCategories.flatMap { category ->
            listOf(
                category.name
                    .replace(macroCategoryCleanRegex, "")
                    .trim()
            ) + category.topics.take(3)
        }.distinct()

        return macroCategories
            .filter { category ->
                val normalized = category.lowercase()
                val lemma = tokenizer.normalizeLemma(normalized)
                !blocked.any { blockedTerm ->
                    normalized.contains(blockedTerm) ||
                        lemma.contains(blockedTerm)
                }
            }
            .map { category ->
                val score = brain.globalVector
                    .topics[tokenizer.normalizeLemma(category)] ?: 0.0
                category to score
            }
            .filter { it.second < NeuroScoring.EXPLORATION_SCORE_THRESHOLD }
            .sortedBy { it.second }
            .take(2)
            .map { it.first }
    }

    fun getSnowballSeeds(
        recentlyWatched: List<Video>,
        count: Int = 3
    ): List<String> {
        return recentlyWatched.take(count).map { it.id }
    }
}
