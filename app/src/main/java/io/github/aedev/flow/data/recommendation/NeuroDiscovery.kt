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

import io.github.aedev.flow.data.model.Video
import java.util.Calendar

/**
 * Smart Discovery Query Engine V4.
 *
 * Core principle: every query is rooted in something the user has
 * demonstrated interest in. No generic mood templates, no robotic
 * grammar patterns. Queries should read like what a human types
 * into YouTube search — short, direct, natural.
 *
 * Key changes from V3:
 * - Removed template grammar system ("{S} crash course" etc.)
 * - Removed hardcoded mood/time-of-day content injection
 * - Time context uses the user's OWN viewing patterns, not generic moods
 * - Queries are natural topic combinations, not filled templates
 * - Confirmed-interest gating prevents spurious one-time watches from generating queries
 */
internal class NeuroDiscovery(
    private val topicCategories: List<TopicCategory>,
    private val tokenizer: NeuroTokenizer
) {

    // ═══════════════════════════════════════════════
    // TOPIC MATURITY SYSTEM
    // A topic needs sustained engagement to be considered
    // a real interest vs. a fleeting curiosity.
    // ═══════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════
    // TOPIC SELECTION — DIVERSITY ACROSS CATEGORIES
    // ═══════════════════════════════════════════════

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
                        cat.topics.any { t ->
                            tokenizer.normalizeLemma(t) == it.name
                        }
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
                    cat.topics.any {
                        tokenizer.normalizeLemma(it) == topic.name
                    }
                }?.name
            }.toSet()

        val crossCategory = matureTopics
            .filter { topic ->
                val cat = topicCategories.find { cat ->
                    cat.topics.any {
                        tokenizer.normalizeLemma(it) == topic.name
                    }
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

    // ═══════════════════════════════════════════════
    // NATURAL QUERY QUALIFIERS
    // Short words people actually append to YouTube searches.
    // Used sparingly, only when a clear preference exists.
    // ═══════════════════════════════════════════════

    private val FRESHNESS_WORDS = listOf("2025", "2026", "new", "latest")

    private val LONG_FORM_WORDS = listOf(
        "documentary", "deep dive", "essay", "full", "breakdown"
    )

    private val SHORT_FORM_WORDS = listOf(
        "highlights", "best moments", "compilation"
    )

    // ═══════════════════════════════════════════════
    // QUERY ENRICHMENT FOR AMBIGUOUS TOPICS
    // ═══════════════════════════════════════════════

    private val AMBIGUOUS_QUERY_WORDS = hashSetOf(
        "code", "design", "build", "run", "play", "model", "train",
        "stream", "fire", "rock", "metal", "spring", "cell", "plant",
        "pitch", "jam", "bar", "wave", "track", "scale", "craft",
        "mine", "host", "board", "drop", "lead", "light", "block",
        "bass", "clip", "fan", "gear", "kit", "log", "net", "pad",
        "port", "rig", "set", "tap", "tip", "web", "flow",
        "mix", "beat", "sound", "work", "world", "life", "point",
        "style", "power", "space", "match"
    )

    private fun needsQueryEnrichment(topic: String): Boolean {
        val base = NeuroScoring.stripDomainTag(topic)
        return base.length < 6 ||
            base in AMBIGUOUS_QUERY_WORDS ||
            base in tokenizer.POLYSEMOUS_WORDS
    }

    /**
     * Enriches an ambiguous topic into a specific YouTube query.
     *
     * Priority:
     * 1. Domain tag → use as natural qualifier ("code:programming" → "code programming")
     * 2. Strongest affinity partner ("code" + partner "python" → "code python")
     * 3. Strongest co-topic in vector ("code" + co-topic "web" → "code web")
     * 4. Category keyword ("code" → category "Technology" → "code technology")
     * 5. Fallback: bare topic (shouldn't happen often)
     */
    private fun buildNaturalQuery(
        topic: String,
        brain: UserBrain
    ): String {
        val base = NeuroScoring.stripDomainTag(topic)

        if (!needsQueryEnrichment(base)) return base

        // 1. Domain-tagged: the tag IS the context
        if (topic.contains(":")) {
            val domain = topic.substringAfter(":")
            val qualifier = DOMAIN_TO_QUERY_WORD[domain] ?: domain
            return "$base $qualifier"
        }

        // 2. Strongest affinity partner
        brain.topicAffinities.entries
            .filter { (key, value) ->
                val parts = key.split("|")
                parts.size == 2 &&
                    (parts[0] == base || parts[1] == base) &&
                    value > 0.10
            }
            .sortedByDescending { it.value }
            .firstOrNull()?.let { (key, _) ->
                val parts = key.split("|")
                val partner = if (parts[0] == base) parts[1] else parts[0]
                if (isSubstantialTopic(partner)) return "$base $partner"
            }

        // 3. Strongest co-topic in global vector
        brain.globalVector.topics.entries
            .filter { (k, v) ->
                val kBase = NeuroScoring.stripDomainTag(k)
                kBase != base && v > 0.05 && isSubstantialTopic(kBase)
            }
            .sortedByDescending { it.value }
            .firstOrNull()?.let { (k, _) ->
                val kBase = NeuroScoring.stripDomainTag(k)
                return "$base $kBase"
            }

        // 4. Category keyword
        topicCategories.find { cat ->
            cat.topics.any { tokenizer.normalizeLemma(it) == base }
        }?.let { cat ->
            val catWord = cat.topics
                .firstOrNull { tokenizer.normalizeLemma(it) != base && it.length > 3 }
            if (catWord != null) return "$base $catWord"
        }

        return base
    }

    private val DOMAIN_TO_QUERY_WORD = mapOf(
        "programming" to "programming",
        "music" to "music",
        "gaming" to "gaming",
        "tech" to "technology",
        "sport" to "sports",
        "fitness" to "fitness",
        "science" to "science",
        "nature" to "nature",
        "fishing" to "fishing",
        "climbing" to "climbing",
        "live" to "livestream",
        "ai" to "artificial intelligence",
        "fashion" to "fashion",
        "hobby" to "hobby",
        "season" to "season",
        "biology" to "biology",
        "energy" to "energy",
        "botany" to "plants",
        "industrial" to "industrial",
        "business" to "business",
        "pc" to "pc build",
        "construction" to "construction",
        "car" to "car build",
        "graphic" to "graphic design",
        "interior" to "interior design",
        "game" to "game design",
        "diy" to "diy crafts",
        "promo" to "deals",
        "entertainment" to "movie",
        "hair" to "hairstyle"
    )

    // ═══════════════════════════════════════════════
    // MAIN QUERY GENERATION
    // ═══════════════════════════════════════════════

    fun generateQueries(
        brain: UserBrain,
        personaProvider: (UserBrain) -> FlowPersona
    ): List<DiscoveryQuery> {
        val persona = personaProvider(brain)
        val blocked = brain.blockedTopics
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // Step 1: Analyze topic maturity
        val bucket = TimeBucket.current()
        val timeVector = brain.timeVectors[bucket] ?: ContentVector()
        val timeTopicSet = timeVector.topics.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .filter { isSubstantialTopic(it) }
            .toSet()

        val matureTopics = analyzeMatureTopics(brain, timeTopicSet)

        // Step 2: Select diverse topics
        val selection = selectDiverseTopics(matureTopics, brain)

        // Step 3: Generate queries — every strategy is interest-rooted
        val queries = mutableListOf<DiscoveryQuery>()

        addDirectQueries(queries, selection, persona, brain)
        addCombinationQueries(queries, selection, brain)
        addAffinityQueries(queries, brain)
        addTimeContextQueries(queries, brain, bucket, selection)
        addChannelQueries(queries, brain)
        addFreshQueries(queries, selection, currentYear, brain)
        addFormatQueries(queries, selection, brain, persona)
        addExplorationQueries(queries, brain)

        // Step 4: Filter, sanitize, balance
        val filtered = queries
            .filter { q ->
                !blocked.any { b -> q.query.lowercase().contains(b) }
            }
            .mapNotNull { q ->
                sanitizeQuery(q.query)?.let { q.copy(query = it) }
            }

        return balanceQueryStrategies(filtered, selection.uniqueTopicCount())
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 1: DIRECT INTEREST QUERIES
    // The most natural search — just the topic itself.
    // "minecraft", "python", "guitar", "cooking"
    // ═══════════════════════════════════════════════

    private fun addDirectQueries(
        queries: MutableList<DiscoveryQuery>,
        selection: TopicSelection,
        persona: FlowPersona,
        brain: UserBrain
    ) {
        // Primary interest — always included
        selection.primary.forEach { topic ->
            queries.add(
                DiscoveryQuery(
                    buildNaturalQuery(topic.name, brain),
                    QueryStrategy.DEEP_DIVE,
                    calculateConfidence(topic),
                    "Core interest: ${topic.name}"
                )
            )
        }

        // Secondary interests — count varies by persona
        val secondaryCount = when (persona) {
            FlowPersona.SPECIALIST -> 1
            FlowPersona.EXPLORER -> 4
            FlowPersona.SKIMMER -> 3
            else -> 2
        }

        selection.secondary.take(secondaryCount).forEach { topic ->
            queries.add(
                DiscoveryQuery(
                    buildNaturalQuery(topic.name, brain),
                    QueryStrategy.DEEP_DIVE,
                    calculateConfidence(topic) - 0.05,
                    "Secondary interest: ${topic.name}"
                )
            )
        }

        // Emerging interests — test with direct query
        selection.emerging.take(1).forEach { topic ->
            queries.add(
                DiscoveryQuery(
                    buildNaturalQuery(topic.name, brain),
                    QueryStrategy.ADJACENT_EXPLORATION,
                    0.40,
                    "Emerging interest test: ${topic.name}"
                )
            )
        }
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 2: INTEREST COMBINATION QUERIES
    // Two of the user's topics combined naturally.
    // "minecraft redstone", "python web", "cooking italian"
    // ═══════════════════════════════════════════════

    private fun addCombinationQueries(
        queries: MutableList<DiscoveryQuery>,
        selection: TopicSelection,
        brain: UserBrain
    ) {
        val primary = selection.primary.firstOrNull() ?: return
        val secondary = selection.secondary

        if (secondary.isEmpty()) return

        val primaryName = NeuroScoring.stripDomainTag(primary.name)

        // Primary × top 2 secondary
        secondary.take(2).forEach { sec ->
            val secName = NeuroScoring.stripDomainTag(sec.name)
            queries.add(
                DiscoveryQuery(
                    "$primaryName $secName",
                    QueryStrategy.CROSS_TOPIC,
                    0.60,
                    "Combination: ${primary.name} + ${sec.name}"
                )
            )
        }

        // Secondary × secondary (one pair)
        if (secondary.size >= 2) {
            queries.add(
                DiscoveryQuery(
                    "${NeuroScoring.stripDomainTag(secondary[0].name)} ${NeuroScoring.stripDomainTag(secondary[1].name)}",
                    QueryStrategy.CROSS_TOPIC,
                    0.50,
                    "Secondary pair: ${secondary[0].name} + ${secondary[1].name}"
                )
            )
        }

        // Cross-category combinations
        selection.crossCategory.take(1).forEach { cross ->
            queries.add(
                DiscoveryQuery(
                    "$primaryName ${NeuroScoring.stripDomainTag(cross.name)}",
                    QueryStrategy.CROSS_TOPIC,
                    0.45,
                    "Cross-category: ${primary.name} + ${cross.name}"
                )
            )
        }
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 3: AFFINITY-BACKED QUERIES
    // Topics the user actually watches together.
    // Inherently natural because they reflect real viewing.
    // ═══════════════════════════════════════════════

    private fun addAffinityQueries(
        queries: MutableList<DiscoveryQuery>,
        brain: UserBrain
    ) {
        brain.topicAffinities.entries
            .filter { it.value > 0.15 }
            .sortedByDescending { it.value }
            .take(3)
            .forEach { (key, score) ->
                val parts = key.split("|")
                if (parts.size != 2) return@forEach
                val (t1, t2) = parts
                if (!isSubstantialTopic(t1) ||
                    !isSubstantialTopic(t2)
                ) return@forEach

                queries.add(
                    DiscoveryQuery(
                        "$t1 $t2",
                        QueryStrategy.CROSS_TOPIC,
                        0.55 + (score * 0.25),
                        "Co-watched (${"%.2f".format(score)}): $t1 + $t2"
                    )
                )
            }
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 4: USER'S OWN TIME-CONTEXT INTERESTS
    // What THIS user watches at this time of day.
    // NOT generic moods — the user's actual patterns.
    //
    // A gamer at midnight gets "minecraft", not "lofi beats".
    // A coder in the morning gets "python", not "morning motivation".
    //
    // Confirmed-interest gating: time topics must also appear
    // in global interests to prevent one-time watches from
    // generating recurring queries.
    // ═══════════════════════════════════════════════

    private fun addTimeContextQueries(
        queries: MutableList<DiscoveryQuery>,
        brain: UserBrain,
        bucket: TimeBucket,
        selection: TopicSelection
    ) {
        val timeVector = brain.timeVectors[bucket] ?: return
        if (timeVector.topics.isEmpty()) return

        val timeTopics = timeVector.topics.entries
            .sortedByDescending { it.value }
            .take(5)
            .filter { isSubstantialTopic(it.key) }
            .map { it.key }

        if (timeTopics.isEmpty()) return

        // Only use time topics confirmed by global interest vector
        // This prevents spurious one-time watches from generating queries
        val globalTopics = brain.globalVector.topics
        val confirmed = timeTopics.filter { topic ->
            val globalScore = globalTopics[topic] ?: 0.0
            globalScore > 0.10
        }

        // Fallback to raw time topics if nothing is confirmed yet
        // (early brain with few interactions)
        val usableTopics = confirmed.ifEmpty {
            timeTopics.take(2)
        }

        val primaryName = selection.primary.firstOrNull()?.name

        // Add top time-context interest (if different from primary)
        usableTopics.firstOrNull()?.let { timeTop ->
            if (timeTop != primaryName) {
                queries.add(
                    DiscoveryQuery(
                        timeTop,
                        QueryStrategy.CONTEXTUAL,
                        0.60,
                        "Your ${formatBucketName(bucket)} interest: $timeTop"
                    )
                )
            }
        }

        // Combine two time-context interests
        if (usableTopics.size >= 2 &&
            usableTopics[0] != usableTopics[1]
        ) {
            queries.add(
                DiscoveryQuery(
                    "${usableTopics[0]} ${usableTopics[1]}",
                    QueryStrategy.CONTEXTUAL,
                    0.50,
                    "Time combination: ${usableTopics[0]} + ${usableTopics[1]}"
                )
            )
        }
    }

    private fun formatBucketName(bucket: TimeBucket): String = when (bucket) {
        TimeBucket.WEEKDAY_MORNING,
        TimeBucket.WEEKEND_MORNING -> "morning"

        TimeBucket.WEEKDAY_AFTERNOON,
        TimeBucket.WEEKEND_AFTERNOON -> "afternoon"

        TimeBucket.WEEKDAY_EVENING,
        TimeBucket.WEEKEND_EVENING -> "evening"

        TimeBucket.WEEKDAY_NIGHT,
        TimeBucket.WEEKEND_NIGHT -> "night"
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 5: CHANNEL TOPIC SIGNATURES
    // Derive queries from the topic profiles of channels
    // the user rates highly. Discovers similar creators.
    // ═══════════════════════════════════════════════

    private fun addChannelQueries(
        queries: MutableList<DiscoveryQuery>,
        brain: UserBrain
    ) {
        val topChannels = brain.channelScores.entries
            .filter { it.value > 0.5 }
            .sortedByDescending { it.value }
            .take(3)

        topChannels.forEach { (channelId, score) ->
            val profile = brain.channelTopicProfiles[channelId]
                ?: return@forEach
            if (profile.size < 2) return@forEach

            val topTopics = profile.entries
                .sortedByDescending { it.value }
                .take(2)
                .map { it.key }
                .filter { isSubstantialTopic(it) }

            if (topTopics.size >= 2) {
                queries.add(
                    DiscoveryQuery(
                        "${topTopics[0]} ${topTopics[1]}",
                        QueryStrategy.CHANNEL_DISCOVERY,
                        0.50 + (score * 0.15),
                        "Channel signature: $channelId"
                    )
                )
            }
        }

        // Top niche across all channels
        val topNiche = brain.channelTopicProfiles.values
            .flatMap { it.entries }
            .groupBy { it.key }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
            .filter { isSubstantialTopic(it.key) }
            .maxByOrNull { it.value }

        if (topNiche != null) {
            queries.add(
                DiscoveryQuery(
                    topNiche.key,
                    QueryStrategy.CHANNEL_DISCOVERY,
                    0.50,
                    "Top channel niche: ${topNiche.key}"
                )
            )
        }
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 6: FRESH CONTENT QUERIES
    // Established interest + recency word.
    // "minecraft 2025", "new python", "latest cooking"
    // ═══════════════════════════════════════════════

    private fun addFreshQueries(
        queries: MutableList<DiscoveryQuery>,
        selection: TopicSelection,
        currentYear: Int,
        brain: UserBrain
    ) {
        val established = selection.allTopics()
            .filter { it.maturityLevel >= TopicMaturity.ESTABLISHED }
            .take(2)

        established.forEachIndexed { index, topic ->
            val baseName = buildNaturalQuery(topic.name, brain)
            val qualifier = if (index == 0) {
                currentYear.toString()
            } else {
                FRESHNESS_WORDS.random()
            }

            queries.add(
                DiscoveryQuery(
                    "$baseName $qualifier",
                    QueryStrategy.TRENDING,
                    calculateConfidence(topic) - 0.05,
                    "Fresh: ${topic.name} $qualifier"
                )
            )
        }
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 7: FORMAT-MATCHED QUERIES
    // Only when user has a clear format preference.
    // Deep diver → "minecraft documentary"
    // Skimmer → "minecraft highlights"
    // No preference → skip entirely.
    // ═══════════════════════════════════════════════

    private fun addFormatQueries(
        queries: MutableList<DiscoveryQuery>,
        selection: TopicSelection,
        brain: UserBrain,
        persona: FlowPersona
    ) {
        val primary = selection.primary.firstOrNull() ?: return
        val v = brain.globalVector

        val formatWord = when {
            v.duration > 0.75 ||
                persona == FlowPersona.DEEP_DIVER ||
                persona == FlowPersona.SCHOLAR ->
                LONG_FORM_WORDS.random()

            v.duration < 0.30 ||
                persona == FlowPersona.SKIMMER ->
                SHORT_FORM_WORDS.random()

            else -> return // No clear preference — skip format queries
        }

        queries.add(
            DiscoveryQuery(
                "${primary.name} $formatWord",
                QueryStrategy.FORMAT_DRIVEN,
                0.55,
                "Format: ${primary.name} $formatWord"
            )
        )
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 8: EXPLORATION
    // Low-scored categories for broadening horizons.
    // Uses actual category topics, not invented queries.
    // ═══════════════════════════════════════════════

    private fun addExplorationQueries(
        queries: MutableList<DiscoveryQuery>,
        brain: UserBrain
    ) {
        val explorationBudget = when {
            brain.totalInteractions > 200 -> 0
            brain.totalInteractions > 80 -> 1
            else -> 2
        }

        if (explorationBudget == 0) return

        val blocked = brain.blockedTopics

        val underexplored = topicCategories
            .flatMap { cat -> cat.topics.take(3) }
            .distinct()
            .filter { topic ->
                val normalized = topic.lowercase()
                val lemma = tokenizer.normalizeLemma(normalized)
                val score = brain.globalVector.topics[lemma] ?: 0.0
                score < NeuroScoring.EXPLORATION_SCORE_THRESHOLD &&
                    !blocked.any { b ->
                        normalized.contains(b) || lemma.contains(b)
                    }
            }
            .shuffled()
            .take(explorationBudget)

        underexplored.forEach { topic ->
            queries.add(
                DiscoveryQuery(
                    topic,
                    QueryStrategy.ADJACENT_EXPLORATION,
                    0.35,
                    "Exploration: $topic"
                )
            )
        }
    }

    // ═══════════════════════════════════════════════
    // CONFIDENCE CALIBRATION
    // ═══════════════════════════════════════════════

    private fun calculateConfidence(topic: MatureTopic): Double {
        val maturityBase = when (topic.maturityLevel) {
            TopicMaturity.CORE -> 0.90
            TopicMaturity.ESTABLISHED -> 0.75
            TopicMaturity.DEVELOPING -> 0.55
            TopicMaturity.EMERGING -> 0.35
        }

        val supportBonus = (topic.categorySupport * 0.03)
            .coerceAtMost(0.10)
        val timeBonus = if (topic.hasTimeContext) 0.05 else 0.0

        return (maturityBase + supportBonus + timeBonus)
            .coerceIn(0.20, 0.95)
    }

    // ═══════════════════════════════════════════════
    // QUERY QUALITY FILTERS
    // ═══════════════════════════════════════════════

    private val YEAR_REGEX = Regex("^20[2-9]\\d$")

    private val QUERY_NOISE_WORDS = hashSetOf(
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
                !YEAR_REGEX.matches(lower)
        }
        // Allow single-word queries (direct topic searches are natural)
        if (cleaned.isEmpty()) return null
        val result = cleaned.joinToString(" ")
        if (result.length > 60) return result.take(60)
            .substringBeforeLast(" ")
        return result
    }

    private fun isSubstantialTopic(topic: String): Boolean {
        if (topic.length < 3) return false
        val lower = topic.lowercase()
        if (lower in QUERY_NOISE_WORDS) return false
        if (YEAR_REGEX.matches(lower)) return false
        if (lower.all { it.isDigit() }) return false
        // Strip domain tags for checking: "metal:music" → "metal"
        val base = if (lower.contains(":")) lower.substringBefore(":") else lower
        if (base.length < 3) return false
        return true
    }

    // ═══════════════════════════════════════════════
    // BALANCING & DEDUPLICATION
    // ═══════════════════════════════════════════════

    private val FILLER_WORDS = hashSetOf(
        "best", "new", "top", "how", "what", "why",
        "complete", "full", "advanced", "beginner",
        "learn", "understand", "understanding",
        "explained", "explains", "explanation",
        "morning", "evening", "night", "afternoon",
        "late", "early", "chill", "relaxing",
        "quick", "fast", "slow",
        "must", "watch", "see",
        "latest"
    )

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
            QueryStrategy.CROSS_TOPIC,
            QueryStrategy.TRENDING,
            QueryStrategy.CONTEXTUAL,
            QueryStrategy.CHANNEL_DISCOVERY,
            QueryStrategy.ADJACENT_EXPLORATION,
            QueryStrategy.FORMAT_DRIVEN
        )

        val byStrategy = deduped.groupBy { it.strategy }
        strategyPriority.forEach { strategy ->
            byStrategy[strategy]?.firstOrNull()?.let { best ->
                balanced.add(best)
                extractTopicRoot(best.query)?.let {
                    topicsCovered.add(it)
                }
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
                topicCountInOutput[root] =
                    (topicCountInOutput[root] ?: 0) + 1
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

            val strategyCount = balanced
                .count { it.strategy == query.strategy }
            if (strategyCount >= 3) continue

            balanced.add(query)
            if (topicRoot != null) {
                topicCountInOutput[topicRoot] =
                    (topicCountInOutput[topicRoot] ?: 0) + 1
            }
        }

        // Phase 4: Shuffle within confidence tiers for variety
        val highConf = balanced
            .filter { it.confidence >= 0.7 }.shuffled()
        val medConf = balanced
            .filter { it.confidence in 0.4..0.69 }.shuffled()
        val lowConf = balanced
            .filter { it.confidence < 0.4 }.shuffled()

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

    // ═══════════════════════════════════════════════
    // LEGACY API — kept for backward compatibility
    // ═══════════════════════════════════════════════

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
            .filter {
                it.second < NeuroScoring.EXPLORATION_SCORE_THRESHOLD
            }
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