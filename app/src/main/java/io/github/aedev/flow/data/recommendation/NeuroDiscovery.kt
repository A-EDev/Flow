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
 * Smart Discovery Query Engine V3.
 *
 * Key improvements over V2:
 * - Topic maturity scoring: distinguishes fleeting watches from real interests
 * - Query grammar system: generates natural-language queries using
 *   subject + intent + modifier patterns instead of raw concatenation
 * - Topic rotation: ensures queries spread across interests, not just top-1
 * - Recency bias correction: recent-but-shallow topics are deprioritized
 * - Diversity budget: guarantees minimum topic variety in output
 * - Confidence calibration: query confidence reflects actual signal strength
 */
internal class NeuroDiscovery(
    private val topicCategories: List<TopicCategory>,
    private val tokenizer: NeuroTokenizer
) {

    // =======================================================
    // TOPIC MATURITY SYSTEM
    // A topic needs sustained engagement to be considered
    // a real interest vs. a fleeting curiosity.
    // =======================================================

    private data class MatureTopic(
        val name: String,
        val score: Double,
        val maturityLevel: TopicMaturity,
        val categorySupport: Int,
        val hasTimeContext: Boolean
    )

    private enum class TopicMaturity {
        EMERGING,
        DEVELOPING,
        ESTABLISHED,
        CORE
    }

    private fun analyzeMatureTopics(
        brain: UserBrain,
        timeTopics: Set<String>
    ): List<MatureTopic> {
        val allTopics = brain.globalVector.topics
        if (allTopics.isEmpty()) return emptyList()

        return allTopics.entries
            .filter { isSubstantialTopic(it.key) }
            .map { (name, score) ->
                val maturity = when {
                    score >= 0.70 -> TopicMaturity.CORE
                    score >= 0.40 -> TopicMaturity.ESTABLISHED
                    score >= 0.15 -> TopicMaturity.DEVELOPING
                    else -> TopicMaturity.EMERGING
                }

                val categorySupport = topicCategories.count { cat ->
                    val catTopics = cat.topics.map { tokenizer.normalizeLemma(it) }
                    catTopics.contains(name) &&
                        catTopics.count { it in allTopics } >= 2
                }

                MatureTopic(
                    name = name,
                    score = score,
                    maturityLevel = maturity,
                    categorySupport = categorySupport,
                    hasTimeContext = name in timeTopics
                )
            }
            .sortedWith(
                compareByDescending<MatureTopic> { it.maturityLevel.ordinal }
                    .thenByDescending { it.score }
                    .thenByDescending { it.categorySupport }
            )
    }

    // QUERY GRAMMAR SYSTEM

    private enum class QueryIntent {
        LEARN, DISCOVER, TRENDING, DEEPEN, ENTERTAIN, FORMAT, COMMUNITY, COMPARE
    }

    private val QUERY_PATTERNS: Map<QueryIntent, List<String>> = mapOf(
        QueryIntent.LEARN to listOf(
            "{S} explained",
            "{S} for beginners",
            "how {S} works",
            "understanding {S}",
            "what is {S}",
            "{S} crash course",
            "learn {S} from scratch",
            "{S} fundamentals",
            "{S} basics everyone should know",
            "complete guide to {S}"
        ),
        QueryIntent.DISCOVER to listOf(
            "best {S} channels",
            "best {S} content",
            "{S} you need to watch",
            "underrated {S} creators",
            "{S} hidden gems",
            "must watch {S}",
            "{S} recommendations"
        ),
        QueryIntent.TRENDING to listOf(
            "{S} {Y}",
            "best {S} {Y}",
            "{S} latest news",
            "new {S} {Y}",
            "{S} what changed",
            "{S} this month",
            "{S} trending"
        ),
        QueryIntent.DEEPEN to listOf(
            "{S} advanced techniques",
            "{S} deep dive",
            "{S} masterclass",
            "{S} in depth analysis",
            "{S} expert breakdown",
            "{S} pro level",
            "advanced {S} strategies"
        ),
        QueryIntent.ENTERTAIN to listOf(
            "{S} highlights",
            "best {S} moments",
            "{S} compilation",
            "funniest {S}",
            "{S} best of",
            "amazing {S}"
        ),
        QueryIntent.FORMAT to listOf(
            "{S} documentary",
            "{S} podcast",
            "{S} video essay",
            "{S} interview",
            "{S} lecture",
            "{S} full breakdown",
            "{S} long form"
        ),
        QueryIntent.COMMUNITY to listOf(
            "{S} community",
            "{S} creators to follow",
            "{S} discussion",
            "why people love {S}",
            "{S} scene"
        ),
        QueryIntent.COMPARE to listOf(
            "{S} vs {M}",
            "{S} or {M} which is better",
            "{S} compared to {M}",
            "difference between {S} and {M}"
        )
    )

    private fun fillPattern(
        pattern: String,
        subject: String,
        modifier: String? = null,
        year: Int? = null
    ): String {
        var result = pattern
            .replace("{S}", subject)
            .replace("{Y}", (year ?: Calendar.getInstance()
                .get(Calendar.YEAR)).toString())

        if (modifier != null) {
            result = result.replace("{M}", modifier)
        } else {
            if (result.contains("{M}")) return ""
        }

        return result.trim()
    }

    private fun selectIntentsForPersona(
        persona: FlowPersona,
        maturity: TopicMaturity
    ): List<QueryIntent> {
        val personaIntents = when (persona) {
            FlowPersona.SCHOLAR -> listOf(
                QueryIntent.LEARN, QueryIntent.DEEPEN,
                QueryIntent.FORMAT, QueryIntent.COMPARE
            )
            FlowPersona.DEEP_DIVER -> listOf(
                QueryIntent.DEEPEN, QueryIntent.FORMAT,
                QueryIntent.LEARN, QueryIntent.COMMUNITY
            )
            FlowPersona.SKIMMER -> listOf(
                QueryIntent.ENTERTAIN, QueryIntent.DISCOVER,
                QueryIntent.TRENDING
            )
            FlowPersona.AUDIOPHILE -> listOf(
                QueryIntent.DISCOVER, QueryIntent.TRENDING,
                QueryIntent.COMMUNITY, QueryIntent.FORMAT
            )
            FlowPersona.BINGER -> listOf(
                QueryIntent.ENTERTAIN, QueryIntent.DISCOVER,
                QueryIntent.FORMAT, QueryIntent.TRENDING
            )
            FlowPersona.LIVEWIRE -> listOf(
                QueryIntent.TRENDING, QueryIntent.COMMUNITY,
                QueryIntent.DISCOVER
            )
            FlowPersona.SPECIALIST -> listOf(
                QueryIntent.DEEPEN, QueryIntent.COMPARE,
                QueryIntent.COMMUNITY, QueryIntent.TRENDING
            )
            FlowPersona.EXPLORER -> listOf(
                QueryIntent.DISCOVER, QueryIntent.LEARN,
                QueryIntent.TRENDING, QueryIntent.ENTERTAIN
            )
            else -> listOf(
                QueryIntent.DISCOVER, QueryIntent.LEARN,
                QueryIntent.TRENDING
            )
        }

        return when (maturity) {
            TopicMaturity.EMERGING -> listOf(
                QueryIntent.LEARN, QueryIntent.DISCOVER
            )
            TopicMaturity.DEVELOPING -> listOf(
                QueryIntent.LEARN, QueryIntent.DISCOVER,
                QueryIntent.TRENDING
            )
            TopicMaturity.ESTABLISHED -> personaIntents
            TopicMaturity.CORE -> (personaIntents + listOf(
                QueryIntent.DEEPEN, QueryIntent.COMMUNITY
            ))
        }.distinct()
    }

    // ========================
    // TOPIC ROTATION SYSTEM
    // ========================

    private data class TopicSelection(
        val primary: List<MatureTopic>,
        val secondary: List<MatureTopic>,
        val emerging: List<MatureTopic>,
        val crossCategory: List<MatureTopic>
    ) {
        fun allTopics(): List<MatureTopic> =
            (primary + secondary + emerging + crossCategory).distinctBy { it.name }

        fun uniqueTopicCount(): Int = allTopics().map { it.name }.distinct().size
    }

    private fun selectDiverseTopics(
        matureTopics: List<MatureTopic>,
        brain: UserBrain
    ): TopicSelection {
        if (matureTopics.isEmpty()) return TopicSelection(
            emptyList(), emptyList(), emptyList(), emptyList()
        )

        val primary = matureTopics.firstOrNull {
            it.maturityLevel >= TopicMaturity.ESTABLISHED
        } ?: matureTopics.first()

        val primaryCategory = topicCategories.find { cat ->
            cat.topics.any { tokenizer.normalizeLemma(it) == primary.name }
        }

        val secondary = matureTopics
            .filter { it.name != primary.name }
            .sortedWith(
                compareByDescending<MatureTopic> {
                    val cat = topicCategories.find { cat ->
                        cat.topics.any { t -> tokenizer.normalizeLemma(t) == it.name }
                    }
                    if (cat != null && cat != primaryCategory) 1 else 0
                }.thenByDescending { it.maturityLevel.ordinal }
                    .thenByDescending { it.score }
            )
            .take(4)

        val emerging = matureTopics
            .filter {
                it.maturityLevel == TopicMaturity.DEVELOPING &&
                    it.name != primary.name &&
                    it.name !in secondary.map { s -> s.name }
            }
            .take(2)

        val representedCategories = (listOf(primary) + secondary)
            .mapNotNull { topic ->
                topicCategories.find { cat ->
                    cat.topics.any { tokenizer.normalizeLemma(it) == topic.name }
                }?.name
            }.toSet()

        val crossCategory = matureTopics
            .filter { topic ->
                val cat = topicCategories.find { cat ->
                    cat.topics.any { tokenizer.normalizeLemma(it) == topic.name }
                }
                cat != null && cat.name !in representedCategories &&
                    topic.maturityLevel >= TopicMaturity.DEVELOPING
            }
            .take(2)

        return TopicSelection(
            primary = listOf(primary),
            secondary = secondary,
            emerging = emerging,
            crossCategory = crossCategory
        )
    }

    // =================================
    // QUERY NOISE AND QUALITY FILTERS
    // =================================

    private val QUERY_NOISE_WORDS = hashSetOf(
        "2020", "2021", "2022", "2023", "2024",
        "2025", "2026", "2027", "2028", "2029", "2030",
        "prompt", "prompts", "prompting",
        "use", "used", "using",
        "guide", "tutorial", "tips", "tricks",
        "thing", "things", "stuff", "way", "ways",
        "type", "types", "kind", "level",
        "sensei", "guru", "master", "pro", "official",
        "studio", "studios", "media", "network"
    )

    private fun sanitizeQuery(raw: String): String? {
        val words = raw.trim().split(NeuroTokenizer.WHITESPACE_REGEX)
        val deduped = LinkedHashSet(words)
        val cleaned = deduped.filter { word ->
            val lower = word.lowercase()
            lower.isNotEmpty() &&
                lower !in QUERY_NOISE_WORDS &&
                !lower.matches(Regex("\\d{4}"))
        }
        if (cleaned.size < 2) return null
        val result = cleaned.joinToString(" ")
        if (result.length > 80) return result.take(80).substringBeforeLast(" ")
        return result
    }

    private fun isSubstantialTopic(topic: String): Boolean {
        if (topic.length < 3) return false
        val lower = topic.lowercase()
        if (lower in QUERY_NOISE_WORDS) return false
        if (lower.matches(Regex("\\d{4}"))) return false
        if (lower.all { it.isDigit() }) return false
        return true
    }

    // ==========================
    // MAIN QUERY GENERATION
    // ==========================

    fun generateQueries(
        brain: UserBrain,
        personaProvider: (UserBrain) -> FlowPersona
    ): List<DiscoveryQuery> {
        val blocked = brain.blockedTopics
        val persona = personaProvider(brain)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        //  Step 1: Analyze topic maturity 
        val bucket = TimeBucket.current()
        val timeVector = brain.timeVectors[bucket] ?: ContentVector()
        val timeTopicSet = timeVector.topics.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .filter { isSubstantialTopic(it) }
            .toSet()

        val matureTopics = analyzeMatureTopics(brain, timeTopicSet)

        //  Step 2: Select diverse topics 
        val selection = selectDiverseTopics(matureTopics, brain)

        //  Step 3: Generate queries per topic with appropriate intents 
        val queries = mutableListOf<DiscoveryQuery>()
        val topicQueryCount = mutableMapOf<String, Int>()
        val maxQueriesPerTopic = 2

        // Primary topic: 1-2 queries with persona-matched intents
        selection.primary.forEach { topic ->
            val intents = selectIntentsForPersona(persona, topic.maturityLevel)
            val queryCount = if (topic.maturityLevel == TopicMaturity.CORE) 2 else 1

            intents.shuffled().take(queryCount).forEach { intent ->
                val patterns = QUERY_PATTERNS[intent] ?: return@forEach
                val modifier = selection.secondary.firstOrNull()?.name

                val pattern = patterns.shuffled().first()
                val filled = fillPattern(pattern, topic.name, modifier, currentYear)
                if (filled.isNotEmpty()) {
                    queries.add(
                        DiscoveryQuery(
                            filled,
                            QueryStrategy.DEEP_DIVE,
                            calculateConfidence(topic, intent),
                            "Primary(${topic.maturityLevel}): " +
                                "${topic.name} via ${intent.name}"
                        )
                    )
                    topicQueryCount[topic.name] =
                        (topicQueryCount[topic.name] ?: 0) + 1
                }
            }
        }

        // Secondary topics: 1 query each, different intents
        val usedIntents = mutableSetOf<QueryIntent>()
        selection.secondary.forEach { topic ->
            if ((topicQueryCount[topic.name] ?: 0) >= maxQueriesPerTopic) return@forEach

            val intents = selectIntentsForPersona(persona, topic.maturityLevel)
                .filter { it !in usedIntents }

            val intent = intents.firstOrNull() ?: return@forEach
            usedIntents.add(intent)

            val patterns = QUERY_PATTERNS[intent] ?: return@forEach
            val pattern = patterns.shuffled().first()

            val modifier = if (intent == QueryIntent.COMPARE) {
                selection.primary.firstOrNull()?.name
            } else null

            val filled = fillPattern(pattern, topic.name, modifier, currentYear)
            if (filled.isNotEmpty()) {
                queries.add(
                    DiscoveryQuery(
                        filled,
                        QueryStrategy.CROSS_TOPIC,
                        calculateConfidence(topic, intent),
                        "Secondary(${topic.maturityLevel}): " +
                            "${topic.name} via ${intent.name}"
                    )
                )
                topicQueryCount[topic.name] =
                    (topicQueryCount[topic.name] ?: 0) + 1
            }
        }

        // Emerging topics: LEARN intent only to test the interest
        selection.emerging.forEach { topic ->
            val patterns = QUERY_PATTERNS[QueryIntent.LEARN] ?: return@forEach
            val pattern = patterns.shuffled().first()
            val filled = fillPattern(pattern, topic.name, year = currentYear)
            if (filled.isNotEmpty()) {
                queries.add(
                    DiscoveryQuery(
                        filled,
                        QueryStrategy.ADJACENT_EXPLORATION,
                        0.45,
                        "Emerging interest test: ${topic.name} " +
                            "(score: ${"%.2f".format(topic.score)})"
                    )
                )
            }
        }

        // Cross-category topics: DISCOVER intent to broaden horizons
        selection.crossCategory.forEach { topic ->
            val patterns = QUERY_PATTERNS[QueryIntent.DISCOVER] ?: return@forEach
            val pattern = patterns.shuffled().first()
            val filled = fillPattern(pattern, topic.name, year = currentYear)
            if (filled.isNotEmpty()) {
                queries.add(
                    DiscoveryQuery(
                        filled,
                        QueryStrategy.ADJACENT_EXPLORATION,
                        0.50,
                        "Cross-category diversity: ${topic.name}"
                    )
                )
            }
        }

        //  Step 4: Affinity-backed cross-topic queries
        queries.addAll(generateAffinityQueries(brain, currentYear))

        //  Step 5: Channel discovery
        queries.addAll(
            generateChannelQueries(brain, topicQueryCount, maxQueriesPerTopic)
        )

        //  Step 6: Contextual time-of-day
        queries.addAll(generateContextualQueries(timeTopicSet.toList(), bucket))

        //  Step 7: Trending for established topics
        val establishedTopics = matureTopics
            .filter { it.maturityLevel >= TopicMaturity.ESTABLISHED }
            .take(3)
        queries.addAll(generateSmartTrendingQueries(establishedTopics, currentYear))

        //  Step 8: Format-driven for core topics
        val coreTopics = matureTopics
            .filter { it.maturityLevel == TopicMaturity.CORE }
        if (coreTopics.isNotEmpty()) {
            queries.addAll(generateFormatQueries(coreTopics, persona, brain))
        }

        //  Step 9: Filter, sanitize, balance
        val filtered = queries
            .filter { q ->
                !blocked.any { b -> q.query.lowercase().contains(b) }
            }
            .mapNotNull { q ->
                sanitizeQuery(q.query)?.let { q.copy(query = it) }
            }

        return balanceQueryStrategies(filtered, selection.uniqueTopicCount())
    }

    // =======================================
    // CONFIDENCE CALIBRATION
    // =======================================

    private fun calculateConfidence(
        topic: MatureTopic,
        intent: QueryIntent
    ): Double {
        val maturityBase = when (topic.maturityLevel) {
            TopicMaturity.CORE -> 0.90
            TopicMaturity.ESTABLISHED -> 0.75
            TopicMaturity.DEVELOPING -> 0.55
            TopicMaturity.EMERGING -> 0.35
        }

        val intentModifier = when (intent) {
            QueryIntent.LEARN -> 0.0
            QueryIntent.DISCOVER -> 0.0
            QueryIntent.TRENDING -> -0.05
            QueryIntent.DEEPEN -> 0.05
            QueryIntent.ENTERTAIN -> -0.05
            QueryIntent.FORMAT -> 0.0
            QueryIntent.COMMUNITY -> -0.10
            QueryIntent.COMPARE -> 0.05
        }

        val supportBonus = (topic.categorySupport * 0.03).coerceAtMost(0.10)
        val timeBonus = if (topic.hasTimeContext) 0.05 else 0.0

        return (maturityBase + intentModifier + supportBonus + timeBonus)
            .coerceIn(0.20, 0.95)
    }

    // =======================================
    // AFFINITY-BACKED CROSS-TOPIC QUERIES
    // =======================================

    private fun generateAffinityQueries(
        brain: UserBrain,
        currentYear: Int
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()

        val topAffinities = brain.topicAffinities.entries
            .filter { it.value > 0.15 }
            .sortedByDescending { it.value }
            .take(3)

        topAffinities.forEach { (key, score) ->
            val parts = key.split("|")
            if (parts.size != 2) return@forEach
            val (t1, t2) = parts
            if (!isSubstantialTopic(t1) || !isSubstantialTopic(t2)) return@forEach

            val patterns = if (score > 0.4) {
                QUERY_PATTERNS[QueryIntent.COMPARE] ?: emptyList()
            } else {
                listOf(
                    "{S} and {M}",
                    "{S} meets {M}",
                    "{S} with {M}",
                    "{S} {M} combination"
                )
            }

            val pattern = patterns.shuffled().firstOrNull() ?: return@forEach
            val filled = fillPattern(pattern, t1, t2, currentYear)
            if (filled.isNotEmpty()) {
                queries.add(
                    DiscoveryQuery(
                        filled,
                        QueryStrategy.CROSS_TOPIC,
                        0.60 + (score * 0.25),
                        "Affinity pair (${"%.2f".format(score)}): $t1 + $t2"
                    )
                )
            }
        }

        return queries
    }

    // ==================================
    // CHANNEL DISCOVERY QUERIES
    // ==================================

    private fun generateChannelQueries(
        brain: UserBrain,
        topicQueryCount: Map<String, Int>,
        maxPerTopic: Int
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()

        val topChannels = brain.channelScores.entries
            .filter { it.value > 0.5 }
            .sortedByDescending { it.value }
            .take(5)

        topChannels.forEach { (channelId, score) ->
            val profile = brain.channelTopicProfiles[channelId] ?: return@forEach
            if (profile.size < 2) return@forEach

            val topChannelTopics = profile.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }
                .filter { isSubstantialTopic(it) }

            if (topChannelTopics.size < 2) return@forEach

            val allSaturated = topChannelTopics.all {
                (topicQueryCount[it] ?: 0) >= maxPerTopic
            }
            if (allSaturated) return@forEach

            val signatureQuery = topChannelTopics.take(2).joinToString(" ")
            queries.add(
                DiscoveryQuery(
                    signatureQuery,
                    QueryStrategy.CHANNEL_DISCOVERY,
                    0.55 + (score * 0.15),
                    "Channel topic signature: $channelId"
                )
            )
        }

        val topNiche = brain.channelTopicProfiles.values
            .flatMap { it.entries }
            .groupBy { it.key }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
            .filter { isSubstantialTopic(it.key) }
            .maxByOrNull { it.value }

        if (topNiche != null) {
            queries.add(
                DiscoveryQuery(
                    "best ${topNiche.key} channels",
                    QueryStrategy.CHANNEL_DISCOVERY,
                    0.55,
                    "Top channel niche: ${topNiche.key}"
                )
            )
        }

        return queries
    }

    // =======================================
    // CONTEXTUAL QUERIES
    // =======================================

    private fun generateContextualQueries(
        timeTopics: List<String>,
        bucket: TimeBucket
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()

        val moodTemplates = when (bucket) {
            TimeBucket.WEEKDAY_MORNING,
            TimeBucket.WEEKEND_MORNING -> listOf(
                "morning routine", "daily motivation",
                "start your day", "morning podcast"
            )
            TimeBucket.WEEKDAY_AFTERNOON -> listOf(
                "afternoon productivity", "quick watch",
                "lunch break entertainment"
            )
            TimeBucket.WEEKEND_AFTERNOON -> listOf(
                "weekend project ideas", "DIY weekend",
                "weekend adventure", "satisfying crafts"
            )
            TimeBucket.WEEKDAY_EVENING,
            TimeBucket.WEEKEND_EVENING -> listOf(
                "evening relaxation", "wind down videos",
                "evening documentary", "chill evening content"
            )
            TimeBucket.WEEKDAY_NIGHT,
            TimeBucket.WEEKEND_NIGHT -> listOf(
                "late night documentary", "ambient sleep sounds",
                "chill music playlist", "lo-fi beats",
                "night time stories"
            )
        }

        val timeTop = timeTopics.firstOrNull()
        if (timeTop != null) {
            val moodWord = when (bucket) {
                TimeBucket.WEEKDAY_MORNING,
                TimeBucket.WEEKEND_MORNING -> "morning"
                TimeBucket.WEEKDAY_AFTERNOON,
                TimeBucket.WEEKEND_AFTERNOON -> "relaxing"
                TimeBucket.WEEKDAY_EVENING,
                TimeBucket.WEEKEND_EVENING -> "evening"
                TimeBucket.WEEKDAY_NIGHT,
                TimeBucket.WEEKEND_NIGHT -> "late night"
            }

            queries.add(
                DiscoveryQuery(
                    "$moodWord $timeTop",
                    QueryStrategy.CONTEXTUAL,
                    0.60,
                    "Time-context: $timeTop at $moodWord"
                )
            )
        }

        moodTemplates.shuffled().take(1).forEach { template ->
            queries.add(
                DiscoveryQuery(
                    template, QueryStrategy.CONTEXTUAL, 0.40,
                    "Mood: $bucket"
                )
            )
        }

        return queries
    }

    // SMART TRENDING

    private fun generateSmartTrendingQueries(
        establishedTopics: List<MatureTopic>,
        currentYear: Int
    ): List<DiscoveryQuery> {
        if (establishedTopics.isEmpty()) return emptyList()

        val queries = mutableListOf<DiscoveryQuery>()

        val primary = establishedTopics.first()
        val trendingPatterns = QUERY_PATTERNS[QueryIntent.TRENDING] ?: emptyList()
        val pattern = trendingPatterns.shuffled().firstOrNull() ?: return emptyList()
        val filled = fillPattern(pattern, primary.name, year = currentYear)

        if (filled.isNotEmpty()) {
            queries.add(
                DiscoveryQuery(
                    filled,
                    QueryStrategy.TRENDING,
                    calculateConfidence(primary, QueryIntent.TRENDING),
                    "Trending for established: ${primary.name}"
                )
            )
        }

        if (establishedTopics.size >= 2) {
            val secondary = establishedTopics[1]
            queries.add(
                DiscoveryQuery(
                    "new ${secondary.name} $currentYear",
                    QueryStrategy.TRENDING,
                    calculateConfidence(secondary, QueryIntent.TRENDING) - 0.05,
                    "Trending secondary: ${secondary.name}"
                )
            )
        }

        return queries
    }

    // ==========================================================
    // FORMAT-DRIVEN QUERIES
    // ==========================================================

    private fun generateFormatQueries(
        coreTopics: List<MatureTopic>,
        persona: FlowPersona,
        brain: UserBrain
    ): List<DiscoveryQuery> {
        val queries = mutableListOf<DiscoveryQuery>()
        val primary = coreTopics.firstOrNull() ?: return emptyList()
        val v = brain.globalVector

        val formatPatterns = when {
            v.duration > 0.75 -> listOf(
                "${primary.name} full documentary",
                "${primary.name} complete breakdown",
                "${primary.name} long form essay"
            )
            v.duration < 0.30 -> listOf(
                "${primary.name} in 5 minutes",
                "${primary.name} quick highlights",
                "${primary.name} shorts compilation"
            )
            else -> listOf(
                "${primary.name} video essay",
                "${primary.name} explained well"
            )
        }

        formatPatterns.shuffled().take(1).forEach { template ->
            queries.add(
                DiscoveryQuery(
                    template,
                    QueryStrategy.FORMAT_DRIVEN,
                    0.60,
                    "Format match: duration=${"%.2f".format(v.duration)}"
                )
            )
        }

        if (v.complexity > 0.70) {
            queries.add(
                DiscoveryQuery(
                    "${primary.name} technical analysis",
                    QueryStrategy.FORMAT_DRIVEN,
                    0.60,
                    "High complexity preference"
                )
            )
        }

        return queries
    }

    // ── Balancing with Diversity Budget ──

    private fun balanceQueryStrategies(
        queries: List<DiscoveryQuery>,
        availableTopicCount: Int
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
                if (existing.isEmpty() || tokens.isEmpty()) return@any false
                val intersection = tokens.intersect(existing).size
                val union = tokens.union(existing).size
                (intersection.toDouble() / union) > 0.5
            }

            if (!isDuplicate) {
                deduped.add(query)
                seenTokenSets.add(tokens)
            }
        }

        // ── Diversity budget ──
        val minDistinctTopics = when {
            availableTopicCount >= 6 -> 4
            availableTopicCount >= 3 -> 3
            else -> availableTopicCount.coerceAtLeast(1)
        }

        val maxQueries = 12
        val balanced = mutableListOf<DiscoveryQuery>()
        val topicsCovered = mutableSetOf<String>()

        // Phase 1: Ensure strategy diversity (1 per strategy)
        val strategyPriority = listOf(
            QueryStrategy.DEEP_DIVE,
            QueryStrategy.TRENDING,
            QueryStrategy.CROSS_TOPIC,
            QueryStrategy.ADJACENT_EXPLORATION,
            QueryStrategy.CONTEXTUAL,
            QueryStrategy.CHANNEL_DISCOVERY,
            QueryStrategy.FORMAT_DRIVEN
        )

        val byStrategy = deduped.groupBy { it.strategy }
        strategyPriority.forEach { strategy ->
            byStrategy[strategy]?.firstOrNull()?.let { best ->
                balanced.add(best)
                extractTopicRoot(best.query)?.let { topicsCovered.add(it) }
            }
        }

        // Phase 2: Fill topic diversity gaps
        if (topicsCovered.size < minDistinctTopics) {
            val remaining = deduped.filter { it !in balanced }
            for (query in remaining) {
                val topicRoot = extractTopicRoot(query.query)
                if (topicRoot != null && topicRoot !in topicsCovered) {
                    balanced.add(query)
                    topicsCovered.add(topicRoot)
                    if (topicsCovered.size >= minDistinctTopics) break
                }
            }
        }

        // Phase 3: Fill by confidence (with per-topic cap)
        val topicCountInOutput = mutableMapOf<String, Int>()
        balanced.forEach { q ->
            extractTopicRoot(q.query)?.let { root ->
                topicCountInOutput[root] = (topicCountInOutput[root] ?: 0) + 1
            }
        }

        val used = balanced.toSet()
        val rest = deduped
            .filter { it !in used }
            .sortedByDescending { it.confidence }

        for (query in rest) {
            if (balanced.size >= maxQueries) break

            val topicRoot = extractTopicRoot(query.query)
            val topicCount = if (topicRoot != null) {
                topicCountInOutput[topicRoot] ?: 0
            } else 0

            if (topicCount >= 3) continue

            val strategyCount = balanced.count { it.strategy == query.strategy }
            if (strategyCount >= 3) continue

            balanced.add(query)
            if (topicRoot != null) {
                topicCountInOutput[topicRoot] =
                    (topicCountInOutput[topicRoot] ?: 0) + 1
            }
        }

        // Phase 4: Shuffle within confidence tiers
        val highConf = balanced.filter { it.confidence >= 0.7 }.shuffled()
        val medConf = balanced.filter {
            it.confidence in 0.4..0.69
        }.shuffled()
        val lowConf = balanced.filter { it.confidence < 0.4 }.shuffled()

        return highConf + medConf + lowConf
    }

    private fun extractTopicRoot(query: String): String? {
        val words = query.lowercase()
            .split(NeuroTokenizer.WHITESPACE_REGEX)
            .filter { it.length > 2 }
            .map { tokenizer.normalizeLemma(it) }
            .filter { it !in FILLER_WORDS }

        return words.firstOrNull()
    }

    private val FILLER_WORDS = hashSetOf(
        "best", "new", "top", "how", "what", "why",
        "complete", "full", "advanced", "beginner",
        "learn", "understand", "understanding",
        "explained", "explains", "explanation",
        "morning", "evening", "night", "afternoon",
        "late", "early", "chill", "relaxing",
        "quick", "fast", "slow",
        "must", "watch", "see"
    )

    // ── Legacy API ──

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
