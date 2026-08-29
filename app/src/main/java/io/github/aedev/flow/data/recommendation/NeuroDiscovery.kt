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
    private val tokenizer: NeuroTokenizer,
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
        val hasTimeContext: Boolean,
        val hasDiscoveryEvidence: Boolean,
        /** Interest-cluster representative this topic belongs to (set during selection). */
        val clusterKey: String? = null,
    )

    private enum class TopicMaturity {
        EMERGING,
        DEVELOPING,
        ESTABLISHED,
        CORE,
    }

    private fun analyzeMatureTopics(
        brain: UserBrain,
        timeTopics: Set<String>,
    ): List<MatureTopic> {
        val allTopics = brain.globalVector.topics
        if (allTopics.isEmpty()) return emptyList()

        return allTopics.entries
            .filter { isSubstantialTopic(it.key) }
            .map { (name, score) ->
                val maturity =
                    when {
                        score >= 0.70 -> TopicMaturity.CORE
                        score >= 0.40 -> TopicMaturity.ESTABLISHED
                        score >= 0.20 -> TopicMaturity.DEVELOPING
                        else -> TopicMaturity.EMERGING
                    }

                val categorySupport =
                    topicCategories.count { cat ->
                        val catTopics = cat.topics.map { tokenizer.normalizeLemma(it) }
                        catTopics.contains(name) &&
                            catTopics.count { it in allTopics } >= 2
                    }

                MatureTopic(
                    name = name,
                    score = score,
                    maturityLevel = maturity,
                    categorySupport = categorySupport,
                    hasTimeContext = name in timeTopics,
                    hasDiscoveryEvidence = hasDiscoveryEvidence(name, brain),
                )
            }.sortedWith(
                compareByDescending<MatureTopic> { it.maturityLevel.ordinal }
                    .thenByDescending { it.score }
                    .thenByDescending { it.categorySupport },
            )
    }

    // ═══════════════════════════════════════════════
    // TOPIC SELECTION — CLUSTER ROTATION
    // Interest communities come from NeuroClusters (label propagation
    // over the user's own co-watch/channel/catalog graph) and are served
    // in staleness-weighted order, so EVERY saved interest cycles into
    // the feed across sessions instead of the top topics monopolizing.
    // ═══════════════════════════════════════════════

    /**
     * One served cluster's contribution to this feed. At depth 0 the query is the
     * cluster's anchor; at depth d > 0 it becomes "anchor member_d" — walking the
     * cluster's members like a tree so each load-more digs a branch deeper.
     */
    private data class ClusterChoice(
        val topic: MatureTopic,
        val comboWith: String?,
    )

    private data class TopicSelection(
        val choices: List<ClusterChoice>,
        val emerging: List<MatureTopic>,
        val crossCategory: List<MatureTopic>,
    ) {
        val primary: List<MatureTopic> get() = choices.take(1).map { it.topic }
        val secondary: List<MatureTopic> get() = choices.drop(1).map { it.topic }

        fun allTopics(): List<MatureTopic> = (choices.map { it.topic } + emerging + crossCategory).distinctBy { it.name }

        fun uniqueTopicCount(): Int = allTopics().map { it.name }.distinct().size
    }

    private fun emptySelection() = TopicSelection(emptyList(), emptyList(), emptyList())

    private fun selectDiverseTopics(
        matureTopics: List<MatureTopic>,
        brain: UserBrain,
        now: Long,
        depth: Int,
        maxClusters: Int,
    ): TopicSelection {
        if (matureTopics.isEmpty()) return emptySelection()

        val clusters =
            NeuroClusters.buildClusters(
                topicScores = matureTopics.associate { it.name to it.score },
                affinities = brain.topicAffinities,
                channelTopicProfiles = brain.channelTopicProfiles,
                categories = topicCategories,
                normalizeLemma = tokenizer::normalizeLemma,
                tagAffinities = brain.tagAffinities,
            )
        if (clusters.isEmpty()) return emptySelection()

        val scheduled = NeuroClusters.schedule(clusters, brain.clusterRotation, now)

        val byBase = HashMap<String, MatureTopic>()
        matureTopics.forEach { topic ->
            byBase.putIfAbsent(NeuroScoring.stripDomainTag(topic.name), topic)
        }

        // The user's MAJOR interests (top clusters by mass) are in EVERY feed —
        // never rotated away by staleness. Rotation governs only the tail slots,
        // cycling micro-interests and fresh seeds through the remaining budget.
        val budget = maxClusters.coerceAtLeast(1)
        val majorKeys =
            clusters
                .sortedByDescending { it.mass }
                .take(NeuroScoring.MAJOR_CLUSTER_SLOTS.coerceAtMost(budget))
                .filter { it.mass > 0.0 }
                .map { it.representative }
                .toSet()
        val served =
            (
                scheduled.filter { it.representative in majorKeys } +
                    scheduled.filter { it.representative !in majorKeys && it.mass > 0.0 }
            ).take(budget)
        val choices =
            served.mapNotNull { cluster ->
                val members = cluster.topics.mapNotNull { byBase[it] }
                if (members.isEmpty()) return@mapNotNull null
                val anchor = members.first().copy(clusterKey = cluster.representative)
                val comboWith =
                    if (depth > 0 && members.size > 1) {
                        val branch = members[1 + ((depth - 1) % (members.size - 1))]
                        NeuroScoring.stripDomainTag(branch.name).takeIf { it != NeuroScoring.stripDomainTag(anchor.name) }
                    } else {
                        null
                    }
                ClusterChoice(anchor, comboWith)
            }
        if (choices.isEmpty()) return emptySelection()

        val representedNames = choices.map { it.topic.name }.toSet()
        val leftoverReps =
            scheduled
                .drop(served.size)
                .mapNotNull { cluster ->
                    cluster.topics
                        .firstNotNullOfOrNull { byBase[it] }
                        ?.copy(clusterKey = cluster.representative)
                }.filter { it.name !in representedNames }

        val emerging = leftoverReps.take(2)
        val representedCategories =
            (choices.map { it.topic } + emerging)
                .mapNotNull { categoryNameOf(it.name) }
                .toSet()
        val crossCategory =
            leftoverReps
                .filter { topic ->
                    topic !in emerging &&
                        categoryNameOf(topic.name)?.let { it !in representedCategories } == true
                }.take(2)

        return TopicSelection(
            choices = choices,
            emerging = emerging,
            crossCategory = crossCategory,
        )
    }

    private fun categoryNameOf(topicName: String): String? {
        val base = NeuroScoring.stripDomainTag(topicName)
        return topicCategories
            .find { cat ->
                cat.topics.any { tokenizer.normalizeLemma(it) == base }
            }?.name
    }

    private fun isDiscoveryEligible(
        topic: MatureTopic,
        isMatureBrain: Boolean = false,
    ): Boolean {
        if (topic.hasDiscoveryEvidence) return true
        if (topic.maturityLevel >= TopicMaturity.DEVELOPING) return true
        if (isMatureBrain && topic.score >= 0.10) return true
        return false
    }

    private fun hasDiscoveryEvidence(
        topic: String,
        brain: UserBrain,
    ): Boolean {
        val base = NeuroScoring.stripDomainTag(topic)
        val preferred =
            brain.preferredTopics.any {
                tokenizer.normalizeLemma(it).equals(base, ignoreCase = true)
            }
        if (preferred) return true

        val evidence =
            brain.topicEvidence[base] ?: brain.topicEvidence[topic]
                ?: return false

        return evidence.explicitSignals > 0 ||
            evidence.watchSignals >= 2 ||
            evidence.videoIds.size >= 2 ||
            evidence.positiveScore >= 1.2 ||
            // Modest but repeated engagement: enough to earn scheduled rotation
            // slots for weak-tail interests (probation still damps their scores).
            (evidence.positiveSignals >= 2 && evidence.positiveScore >= 0.5)
    }

    // ═══════════════════════════════════════════════
    // NATURAL QUERY QUALIFIERS
    // Short words people actually append to YouTube searches.
    // Used sparingly, only when a clear preference exists.
    // ═══════════════════════════════════════════════

    private val freshnessWords = listOf("2025", "2026", "new", "latest")

    private val longFormWords =
        listOf(
            "documentary",
            "deep dive",
            "essay",
            "full",
            "breakdown",
        )

    private val shortFormWords =
        listOf(
            "highlights",
            "best moments",
            "compilation",
        )

    // ═══════════════════════════════════════════════
    // QUERY ENRICHMENT FOR AMBIGUOUS TOPICS
    // ═══════════════════════════════════════════════

    private val ambiguousQueryWords =
        hashSetOf(
            "code",
            "design",
            "build",
            "run",
            "play",
            "model",
            "train",
            "stream",
            "fire",
            "rock",
            "metal",
            "spring",
            "cell",
            "plant",
            "pitch",
            "jam",
            "bar",
            "wave",
            "track",
            "scale",
            "craft",
            "mine",
            "host",
            "board",
            "drop",
            "lead",
            "light",
            "block",
            "bass",
            "clip",
            "fan",
            "gear",
            "kit",
            "log",
            "net",
            "pad",
            "port",
            "rig",
            "set",
            "tap",
            "tip",
            "web",
            "flow",
            "mix",
            "beat",
            "sound",
            "work",
            "world",
            "life",
            "point",
            "style",
            "power",
            "space",
            "match",
        )

    private fun needsQueryEnrichment(topic: String): Boolean {
        val base = NeuroScoring.stripDomainTag(topic)
        // Only genuinely AMBIGUOUS words need a qualifier. The old length<6 rule
        // polluted short-but-specific anchors: "mma" became "mma android".
        return base in ambiguousQueryWords || base in tokenizer.polysemousWords
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
        brain: UserBrain,
    ): String {
        val base = NeuroScoring.stripDomainTag(topic)

        if (!needsQueryEnrichment(base)) return base

        // 1. Domain-tagged: the tag IS the context
        if (topic.contains(":")) {
            val domain = topic.substringAfter(":")
            val qualifier = domainToQueryWord[domain] ?: domain
            return "$base $qualifier"
        }

        // 2. Strongest affinity partner
        brain.topicAffinities.entries
            .filter { (key, value) ->
                val parts = key.split("|")
                parts.size == 2 &&
                    (parts[0] == base || parts[1] == base) &&
                    value > 0.10
            }.sortedByDescending { it.value }
            .firstOrNull()
            ?.let { (key, _) ->
                val parts = key.split("|")
                val partner = if (parts[0] == base) parts[1] else parts[0]
                if (isSubstantialTopic(partner)) return "$base $partner"
            }

        // 3. Strongest co-topic in global vector
        brain.globalVector.topics.entries
            .filter { (k, v) ->
                val kBase = NeuroScoring.stripDomainTag(k)
                kBase != base && v > 0.05 && isSubstantialTopic(kBase)
            }.sortedByDescending { it.value }
            .firstOrNull()
            ?.let { (k, _) ->
                val kBase = NeuroScoring.stripDomainTag(k)
                return "$base $kBase"
            }

        // 4. Category keyword
        topicCategories
            .find { cat ->
                cat.topics.any { tokenizer.normalizeLemma(it) == base }
            }?.let { cat ->
                val catWord =
                    cat.topics
                        .firstOrNull { tokenizer.normalizeLemma(it) != base && it.length > 3 }
                if (catWord != null) return "$base $catWord"
            }

        return base
    }

    private val domainToQueryWord =
        mapOf(
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
            "hair" to "hairstyle",
        )

    // ═══════════════════════════════════════════════
    // MAIN QUERY GENERATION
    // ═══════════════════════════════════════════════

    fun generateQueries(
        brain: UserBrain,
        now: Long = System.currentTimeMillis(),
        depth: Int = 0,
        personaProvider: (UserBrain) -> FlowPersona,
    ): List<DiscoveryQuery> {
        val persona = personaProvider(brain)
        val blocked = brain.blockedTopics
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val isMatureBrain = brain.totalInteractions > 50

        // Step 1: Analyze topic maturity
        val bucket = TimeBucket.current()
        val timeVector = brain.timeVectors[bucket] ?: ContentVector()
        val timeTopicSet =
            timeVector.topics.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }
                .filter { isSubstantialTopic(it) }
                .toSet()

        val matureTopics = analyzeMatureTopics(brain, timeTopicSet)

        // Step 2: Select topics — EVERY served cluster contributes to EVERY feed,
        // and depth walks each cluster's members like a tree on load-more.
        val maxClusters =
            when (persona) {
                FlowPersona.SPECIALIST -> 3
                else -> NeuroScoring.MAX_CLUSTERS_PER_REFRESH
            }
        val selection = selectDiverseTopics(matureTopics, brain, now, depth, maxClusters)

        // Step 3: Generate queries — every strategy is interest-rooted
        val queries = mutableListOf<DiscoveryQuery>()

        addDirectQueries(queries, selection, brain, isMatureBrain, depth)
        addCombinationQueries(queries, selection, isMatureBrain)
        addAffinityQueries(queries, brain)
        addTimeContextQueries(queries, brain, bucket, selection)
        addChannelQueries(queries, brain)
        addFreshQueries(queries, selection, currentYear, brain, isMatureBrain)
        addFormatQueries(queries, selection, brain, persona, isMatureBrain)
        addExplorationQueries(queries, brain)
        if (isMatureBrain) addPreferredTopicAnchors(queries, brain)

        // Step 4: Filter, sanitize, balance
        val filtered =
            queries
                .filter { q ->
                    !blocked.any { b -> q.query.lowercase().contains(b) }
                }.mapNotNull { q ->
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
        brain: UserBrain,
        isMatureBrain: Boolean,
        depth: Int,
    ) {
        // EVERY served cluster contributes one direct query per feed — that is the
        // whole point of a multi-interest feed. Depth > 0 digs the cluster's branch
        // ("android" → "android machine learning") instead of repeating the anchor.
        selection.choices.forEachIndexed { index, choice ->
            val topic = choice.topic
            if (!isDiscoveryEligible(topic, isMatureBrain)) return@forEachIndexed

            val anchorBase = NeuroScoring.stripDomainTag(topic.name)
            val query =
                if (choice.comboWith != null) {
                    "$anchorBase ${choice.comboWith}"
                } else {
                    buildNaturalQuery(topic.name, brain)
                }
            val label = if (index == 0) "Core interest" else "Cluster interest"
            val branch = choice.comboWith?.let { " → $it" } ?: ""
            queries.add(
                DiscoveryQuery(
                    query,
                    QueryStrategy.DEEP_DIVE,
                    calculateConfidence(topic) - (if (index == 0) 0.0 else 0.05) - depth * 0.02,
                    "$label: ${topic.name}$branch",
                    clusterKey = topic.clusterKey,
                ),
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
        isMatureBrain: Boolean,
    ) {
        val primary = selection.primary.firstOrNull { isDiscoveryEligible(it, isMatureBrain) } ?: return
        val secondary = selection.secondary.filter { isDiscoveryEligible(it, isMatureBrain) }

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
                    "Combination: ${primary.name} + ${sec.name}",
                    clusterKey = sec.clusterKey,
                ),
            )
        }

        // Secondary × secondary (one pair)
        if (secondary.size >= 2) {
            queries.add(
                DiscoveryQuery(
                    "${NeuroScoring.stripDomainTag(secondary[0].name)} ${NeuroScoring.stripDomainTag(secondary[1].name)}",
                    QueryStrategy.CROSS_TOPIC,
                    0.50,
                    "Secondary pair: ${secondary[0].name} + ${secondary[1].name}",
                    clusterKey = secondary[0].clusterKey,
                ),
            )
        }

        // Cross-category combinations
        selection.crossCategory.take(1).forEach { cross ->
            queries.add(
                DiscoveryQuery(
                    "$primaryName ${NeuroScoring.stripDomainTag(cross.name)}",
                    QueryStrategy.CROSS_TOPIC,
                    0.45,
                    "Cross-category: ${primary.name} + ${cross.name}",
                    clusterKey = cross.clusterKey,
                ),
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
        brain: UserBrain,
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
                ) {
                    return@forEach
                }

                queries.add(
                    DiscoveryQuery(
                        "$t1 $t2",
                        QueryStrategy.CROSS_TOPIC,
                        0.55 + (score * 0.25),
                        "Co-watched (${"%.2f".format(score)}): $t1 + $t2",
                    ),
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
        selection: TopicSelection,
    ) {
        val timeVector = brain.timeVectors[bucket] ?: return
        if (timeVector.topics.isEmpty()) return

        val timeTopics =
            timeVector.topics.entries
                .sortedByDescending { it.value }
                .take(5)
                .filter { isSubstantialTopic(it.key) }
                .map { it.key }

        if (timeTopics.isEmpty()) return

        // Only use time topics confirmed by global interest vector
        // This prevents spurious one-time watches from generating queries
        val globalTopics = brain.globalVector.topics
        val confirmed =
            timeTopics.filter { topic ->
                val globalScore = globalTopics[topic] ?: 0.0
                globalScore > 0.10 && hasDiscoveryEvidence(topic, brain)
            }

        val usableTopics = confirmed
        if (usableTopics.isEmpty()) return

        val primaryName = selection.primary.firstOrNull()?.name

        // Add top time-context interest (if different from primary)
        usableTopics.firstOrNull()?.let { timeTop ->
            if (timeTop != primaryName) {
                queries.add(
                    DiscoveryQuery(
                        timeTop,
                        QueryStrategy.CONTEXTUAL,
                        0.60,
                        "Your ${formatBucketName(bucket)} interest: $timeTop",
                    ),
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
                    "Time combination: ${usableTopics[0]} + ${usableTopics[1]}",
                ),
            )
        }
    }

    private fun formatBucketName(bucket: TimeBucket): String =
        when (bucket) {
            TimeBucket.WEEKDAY_MORNING,
            TimeBucket.WEEKEND_MORNING,
            -> "morning"

            TimeBucket.WEEKDAY_AFTERNOON,
            TimeBucket.WEEKEND_AFTERNOON,
            -> "afternoon"

            TimeBucket.WEEKDAY_EVENING,
            TimeBucket.WEEKEND_EVENING,
            -> "evening"

            TimeBucket.WEEKDAY_NIGHT,
            TimeBucket.WEEKEND_NIGHT,
            -> "night"
        }

    // ═══════════════════════════════════════════════
    // STRATEGY 5: CHANNEL TOPIC SIGNATURES
    // Derive queries from the topic profiles of channels
    // the user rates highly. Discovers similar creators.
    // ═══════════════════════════════════════════════

    private fun addChannelQueries(
        queries: MutableList<DiscoveryQuery>,
        brain: UserBrain,
    ) {
        val topChannels =
            brain.channelScores.entries
                .filter { it.value > 0.5 }
                .sortedByDescending { it.value }
                .take(3)

        topChannels.forEach { (channelId, score) ->
            val profile =
                brain.channelTopicProfiles[channelId]
                    ?: return@forEach
            if (profile.size < 2) return@forEach

            val topTopics =
                profile.entries
                    .sortedByDescending { it.value }
                    .take(2)
                    .map { it.key }
                    .filter { isSubstantialTopic(it) && hasDiscoveryEvidence(it, brain) }

            if (topTopics.size >= 2) {
                queries.add(
                    DiscoveryQuery(
                        "${topTopics[0]} ${topTopics[1]}",
                        QueryStrategy.CHANNEL_DISCOVERY,
                        0.50 + (score * 0.15),
                        "Channel signature: $channelId",
                    ),
                )
            }
        }

        // Top niche across all channels
        val topNiche =
            brain.channelTopicProfiles.values
                .flatMap { it.entries }
                .groupBy { it.key }
                .mapValues { (_, entries) -> entries.sumOf { it.value } }
                .filter { isSubstantialTopic(it.key) && hasDiscoveryEvidence(it.key, brain) }
                .maxByOrNull { it.value }

        if (topNiche != null) {
            queries.add(
                DiscoveryQuery(
                    topNiche.key,
                    QueryStrategy.CHANNEL_DISCOVERY,
                    0.50,
                    "Top channel niche: ${topNiche.key}",
                ),
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
        brain: UserBrain,
        isMatureBrain: Boolean,
    ) {
        val established =
            selection
                .allTopics()
                .filter { it.maturityLevel >= TopicMaturity.ESTABLISHED && isDiscoveryEligible(it, isMatureBrain) }
                .take(2)

        established.forEachIndexed { index, topic ->
            val baseName = buildNaturalQuery(topic.name, brain)
            val qualifier =
                if (index == 0) {
                    currentYear.toString()
                } else {
                    freshnessWords.random()
                }

            queries.add(
                DiscoveryQuery(
                    "$baseName $qualifier",
                    QueryStrategy.TRENDING,
                    calculateConfidence(topic) - 0.05,
                    "Fresh: ${topic.name} $qualifier",
                    clusterKey = topic.clusterKey,
                ),
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
        persona: FlowPersona,
        isMatureBrain: Boolean,
    ) {
        val primary = selection.primary.firstOrNull { isDiscoveryEligible(it, isMatureBrain) } ?: return
        val v = brain.globalVector

        val formatWord =
            when {
                v.duration > 0.75 ||
                    persona == FlowPersona.DEEP_DIVER ||
                    persona == FlowPersona.SCHOLAR -> {
                    longFormWords.random()
                }

                v.duration < 0.30 ||
                    persona == FlowPersona.SKIMMER -> {
                    shortFormWords.random()
                }

                else -> {
                    return
                } // No clear preference — skip format queries
            }

        queries.add(
            DiscoveryQuery(
                "${primary.name} $formatWord",
                QueryStrategy.FORMAT_DRIVEN,
                0.55,
                "Format: ${primary.name} $formatWord",
                clusterKey = primary.clusterKey,
            ),
        )
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 8: ADJACENT EXPLORATION
    // Strictly user-grounded: candidates are topics the user's OWN graph
    // points at but the vector barely knows — affinity partners of real
    // interests, and topics taught by channels the user rates well.
    // No catalog topics are ever injected.
    // ═══════════════════════════════════════════════

    private fun addExplorationQueries(
        queries: MutableList<DiscoveryQuery>,
        brain: UserBrain,
    ) {
        // Always reserve at least one exploration slot — no decay-to-zero bubble.
        val explorationBudget = if (brain.totalInteractions > 80) 1 else 2
        val blocked = brain.blockedTopics

        fun globalScore(base: String): Double {
            brain.globalVector.topics[base]?.let { return it }
            return brain.globalVector.topics.entries
                .firstOrNull { NeuroScoring.stripDomainTag(it.key) == base }
                ?.value ?: 0.0
        }

        val adjacentWeights = mutableMapOf<String, Double>()

        // (a) Affinity partners of established interests that the vector barely knows:
        // the user has already watched these topics TOGETHER with a real interest.
        brain.topicAffinities.forEach { (key, affinity) ->
            val parts = key.split("|")
            if (parts.size != 2) return@forEach
            for ((anchor, partner) in listOf(parts[0] to parts[1], parts[1] to parts[0])) {
                if (!isSubstantialTopic(partner)) continue
                if (globalScore(anchor) >= 0.15 &&
                    globalScore(partner) < NeuroScoring.EXPLORATION_SCORE_THRESHOLD
                ) {
                    adjacentWeights.merge(partner, affinity * globalScore(anchor), Double::plus)
                }
            }
        }

        // (b) Topics that well-rated channels teach but the user hasn't explored.
        brain.channelTopicProfiles.forEach { (channelId, profile) ->
            val channelQuality = brain.channelScores[channelId] ?: return@forEach
            if (channelQuality < 0.55) return@forEach
            profile.entries
                .sortedByDescending { it.value }
                .take(4)
                .forEach { (topic, weight) ->
                    val base = NeuroScoring.stripDomainTag(topic)
                    if (isSubstantialTopic(base) &&
                        globalScore(base) < NeuroScoring.EXPLORATION_SCORE_THRESHOLD
                    ) {
                        adjacentWeights.merge(base, weight * channelQuality, Double::plus)
                    }
                }
        }

        val picks =
            adjacentWeights.entries
                .filter { (topic, _) ->
                    !blocked.any { b -> topic.contains(b) || tokenizer.normalizeLemma(topic).contains(b) }
                }.sortedByDescending { it.value }
                .take(6)
                .shuffled()
                .take(explorationBudget)

        picks.forEach { (topic, _) ->
            queries.add(
                DiscoveryQuery(
                    buildNaturalQuery(topic, brain),
                    QueryStrategy.ADJACENT_EXPLORATION,
                    0.35,
                    "Adjacent: $topic",
                ),
            )
        }
    }

    // ═══════════════════════════════════════════════
    // STRATEGY 9: PREFERRED TOPIC ANCHORS
    // ═══════════════════════════════════════════════

    private fun addPreferredTopicAnchors(
        queries: MutableList<DiscoveryQuery>,
        brain: UserBrain,
    ) {
        if (brain.preferredTopics.isEmpty()) return

        val existingTokens =
            queries
                .flatMap { q ->
                    q.query
                        .lowercase()
                        .split(NeuroTokenizer.WHITESPACE_REGEX)
                        .filter { it.length > 2 }
                        .map { tokenizer.normalizeLemma(it) }
                }.toSet()

        val blocked = brain.blockedTopics
        val missing =
            brain.preferredTopics
                .map { it.trim() }
                .filter { pref ->
                    val lemma = tokenizer.normalizeLemma(pref)
                    lemma.length >= 3 &&
                        lemma !in existingTokens &&
                        !blocked.any { b -> lemma.contains(b) }
                }.shuffled()
                .take(3)

        missing.forEach { topic ->
            queries.add(
                DiscoveryQuery(
                    topic,
                    QueryStrategy.DEEP_DIVE,
                    0.45,
                    "Preferred anchor: $topic",
                ),
            )
        }
    }

    // ═══════════════════════════════════════════════
    // CONFIDENCE CALIBRATION
    // ═══════════════════════════════════════════════

    private fun calculateConfidence(topic: MatureTopic): Double {
        val maturityBase =
            when (topic.maturityLevel) {
                TopicMaturity.CORE -> 0.90
                TopicMaturity.ESTABLISHED -> 0.75
                TopicMaturity.DEVELOPING -> 0.55
                TopicMaturity.EMERGING -> 0.35
            }

        val supportBonus =
            (topic.categorySupport * 0.03)
                .coerceAtMost(0.10)
        val timeBonus = if (topic.hasTimeContext) 0.05 else 0.0

        return (maturityBase + supportBonus + timeBonus)
            .coerceIn(0.20, 0.95)
    }

    // ═══════════════════════════════════════════════
    // QUERY QUALITY FILTERS
    // ═══════════════════════════════════════════════

    private val yearRegex = Regex("^20[2-9]\\d$")

    private val queryNoiseWords =
        hashSetOf(
            "prompt",
            "prompts",
            "prompting",
            "use",
            "used",
            "using",
            "guide",
            "tutorial",
            "tips",
            "tricks",
            "thing",
            "things",
            "stuff",
            "way",
            "ways",
            "type",
            "types",
            "kind",
            "level",
            "sensei",
            "guru",
            "master",
            "pro",
            "official",
            "studio",
            "studios",
            "media",
            "network",
        )

    private fun sanitizeQuery(raw: String): String? {
        val words = raw.trim().split(NeuroTokenizer.WHITESPACE_REGEX)
        val deduped = LinkedHashSet(words)
        val cleaned =
            deduped.filter { word ->
                val lower = word.lowercase()
                lower.isNotEmpty() &&
                    lower !in queryNoiseWords &&
                    !yearRegex.matches(lower)
            }
        // Allow single-word queries (direct topic searches are natural)
        if (cleaned.isEmpty()) return null
        val result = cleaned.joinToString(" ")
        if (result.length > 60) {
            return result
                .take(60)
                .substringBeforeLast(" ")
        }
        return result
    }

    private fun isSubstantialTopic(topic: String): Boolean {
        if (topic.length < 3) return false
        val lower = topic.lowercase()
        if (lower in queryNoiseWords) return false
        if (yearRegex.matches(lower)) return false
        if (lower.all { it.isDigit() }) return false
        // Strip domain tags for checking: "metal:music" → "metal"
        val base = if (lower.contains(":")) lower.substringBefore(":") else lower
        if (base.length < 3) return false
        return true
    }

    // ═══════════════════════════════════════════════
    // BALANCING & DEDUPLICATION
    // ═══════════════════════════════════════════════

    private val fillerWords =
        hashSetOf(
            "best",
            "new",
            "top",
            "how",
            "what",
            "why",
            "complete",
            "full",
            "advanced",
            "beginner",
            "learn",
            "understand",
            "understanding",
            "explained",
            "explains",
            "explanation",
            "morning",
            "evening",
            "night",
            "afternoon",
            "late",
            "early",
            "chill",
            "relaxing",
            "quick",
            "fast",
            "slow",
            "must",
            "watch",
            "see",
            "latest",
        )

    private fun balanceQueryStrategies(
        queries: List<DiscoveryQuery>,
        availableTopicCount: Int,
    ): List<DiscoveryQuery> {
        // ── Semantic deduplication ──
        val deduped = mutableListOf<DiscoveryQuery>()
        val seenTokenSets = mutableListOf<Set<String>>()

        // Cluster ANCHORS (direct deep-dives) enter dedup first: they are each
        // community's flag query. Otherwise a cross-combo like "android mma" can
        // out-confidence a developing cluster's own "mma" and absorb its tokens.
        val sorted =
            queries.sortedWith(
                compareByDescending<DiscoveryQuery> { it.strategy == QueryStrategy.DEEP_DIVE && it.clusterKey != null }
                    .thenByDescending { it.confidence },
            )

        for (query in sorted) {
            val tokens =
                query.query
                    .lowercase()
                    .split(NeuroTokenizer.WHITESPACE_REGEX)
                    .filter { it.length > 2 }
                    .map { tokenizer.normalizeLemma(it) }
                    .toSet()

            val isDuplicate =
                seenTokenSets.any { existing ->
                    if (existing.isEmpty() || tokens.isEmpty()) return@any false
                    val intersection = tokens.intersect(existing).size
                    val union = tokens.union(existing).size
                    (intersection.toDouble() / union) > 0.3
                }

            // A cluster's sole representative always survives dedup — token overlap
            // with another cluster's query must not erase a community from the feed.
            val keepForClusterCoverage =
                isDuplicate &&
                    query.clusterKey != null &&
                    deduped.none { it.clusterKey == query.clusterKey }

            if (!isDuplicate || keepForClusterCoverage) {
                deduped.add(query)
                seenTokenSets.add(tokens)
            }
        }

        // ── Diversity budget ──
        val minDistinctTopics =
            when {
                availableTopicCount >= 6 -> 4
                availableTopicCount >= 3 -> 3
                else -> availableTopicCount.coerceAtLeast(1)
            }

        val maxQueries = 12
        val balanced = mutableListOf<DiscoveryQuery>()
        val topicsCovered = mutableSetOf<String>()

        // Phase 0: cluster coverage is the FIRST guarantee — every served cluster's
        // best query makes the cut before any strategy or confidence balancing.
        // This is what puts mma + android + anime in the SAME feed.
        deduped
            .filter { it.clusterKey != null }
            .groupBy { it.clusterKey }
            .forEach { (_, clusterQueries) ->
                val best = clusterQueries.maxByOrNull { it.confidence } ?: return@forEach
                if (best !in balanced) {
                    balanced.add(best)
                    extractTopicRoot(best.query)?.let { topicsCovered.add(it) }
                }
            }

        // Phase 1: Ensure strategy diversity (1 per strategy)
        val strategyPriority =
            listOf(
                QueryStrategy.DEEP_DIVE,
                QueryStrategy.CROSS_TOPIC,
                QueryStrategy.TRENDING,
                QueryStrategy.CONTEXTUAL,
                QueryStrategy.CHANNEL_DISCOVERY,
                QueryStrategy.ADJACENT_EXPLORATION,
                QueryStrategy.FORMAT_DRIVEN,
            )

        val byStrategy = deduped.groupBy { it.strategy }
        strategyPriority.forEach { strategy ->
            byStrategy[strategy]?.firstOrNull { it !in balanced }?.let { best ->
                if (balanced.none { it.strategy == strategy }) {
                    balanced.add(best)
                    extractTopicRoot(best.query)?.let {
                        topicsCovered.add(it)
                    }
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
        val rest =
            deduped
                .filter { it !in used }
                .sortedByDescending { it.confidence }

        for (query in rest) {
            if (balanced.size >= maxQueries) break

            val topicRoot = extractTopicRoot(query.query)
            val topicCount =
                if (topicRoot != null) {
                    topicCountInOutput[topicRoot] ?: 0
                } else {
                    0
                }

            if (topicCount >= 2) continue

            val strategyCount =
                balanced
                    .count { it.strategy == query.strategy }
            if (strategyCount >= 3) continue

            balanced.add(query)
            if (topicRoot != null) {
                topicCountInOutput[topicRoot] =
                    (topicCountInOutput[topicRoot] ?: 0) + 1
            }
        }

        // Phase 4: Shuffle within confidence tiers for variety
        val highConf =
            balanced
                .filter { it.confidence >= 0.7 }
                .shuffled()
        val medConf =
            balanced
                .filter { it.confidence in 0.4..0.69 }
                .shuffled()
        val lowConf =
            balanced
                .filter { it.confidence < 0.4 }
                .shuffled()

        return highConf + medConf + lowConf
    }

    private fun extractTopicRoot(query: String): String? {
        val words =
            query
                .lowercase()
                .split(NeuroTokenizer.WHITESPACE_REGEX)
                .filter { it.length > 2 }
                .map { tokenizer.normalizeLemma(it) }
                .filter { it !in fillerWords }

        if (words.isEmpty()) return null
        return words.sorted().joinToString("|")
    }
}
