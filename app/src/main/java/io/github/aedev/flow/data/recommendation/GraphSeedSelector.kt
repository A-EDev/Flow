package io.github.aedev.flow.data.recommendation

internal object GraphSeedSelector {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MIN_LONG_WATCH_SECONDS = 180

    private val tokenizer by lazy { NeuroTokenizer() }

    // Every seed of one selection reads the same topic map; index it once, not once per title word.
    @Volatile
    private var baseIndex: Pair<Map<String, Double>, Map<String, Double>>? = null

    private fun byBase(topicScores: Map<String, Double>): Map<String, Double> {
        baseIndex?.let { (source, index) -> if (source === topicScores) return index }
        val index = HashMap<String, Double>(topicScores.size)
        topicScores.forEach { (key, score) -> index.putIfAbsent(NeuroScoring.stripDomainTag(key), score) }
        baseIndex = topicScores to index
        return index
    }

    fun select(
        candidates: List<GraphSeedInput>,
        maxSeeds: Int,
        now: Long = System.currentTimeMillis(),
        excludedChannelIds: Set<String> = emptySet(),
        maxPerCluster: Int = 2,
        topicScores: Map<String, Double> = emptyMap(),
        communityOf: ((String) -> String)? = null,
    ): List<String> {
        if (candidates.isEmpty() || maxSeeds <= 0) return emptyList()
        val ranked =
            candidates
                .asSequence()
                .filter { it.isEligible(excludedChannelIds) }
                .map { seed ->
                    val rawKey = clusterKey(seed.title, tokenizer, topicScores)
                    ScoredSeed(
                        id = seed.id,
                        // Mapping topic keys to interest COMMUNITIES makes the
                        // spread-first pick allocate one seed per major interest.
                        clusterKey = communityOf?.invoke(rawKey) ?: rawKey,
                        weight = seed.score(now),
                    )
                }.filter { it.weight > 0.0 }
                .groupBy { it.id }
                .values
                .mapNotNull { seeds -> seeds.maxByOrNull { it.weight } }
                .map { SeedRank(it.id, it.clusterKey, it.weight) }
                .toList()

        return NeuroScoring.pickDiverseSeeds(ranked, maxSeeds, maxPerCluster)
    }

    /**
     * [select] over [candidates], skipping [cooledIds] (seeds used in the last few hours) unless too
     * few others qualify, with the last of [maxSeeds] given to a lasting interest none of the recent
     * picks covers ([selectLongTermSeed]).
     */
    fun selectWithLongTerm(
        candidates: List<GraphSeedInput>,
        maxSeeds: Int,
        longTermCandidates: List<GraphSeedInput>,
        communityMass: Map<String, Double>,
        communityOf: (String) -> String,
        now: Long = System.currentTimeMillis(),
        cooledIds: Set<String> = emptySet(),
        excludedChannelIds: Set<String> = emptySet(),
        topicScores: Map<String, Double> = emptyMap(),
    ): List<String> {
        fun pick(from: List<GraphSeedInput>) =
            select(from, maxSeeds, now, excludedChannelIds, topicScores = topicScores, communityOf = communityOf)

        // Cooled seeds come back only when no other seed qualifies: half-watched history never does,
        // so a size check on the raw list let a refresh inside the cooldown pick no seed at all.
        val fresh = pick(candidates.filterNot { it.id in cooledIds })
        val recent = fresh.ifEmpty { pick(candidates) }
        if (longTermCandidates.isEmpty() || maxSeeds < 2) return recent
        val kept = recent.take(maxSeeds - 1)
        val covered = candidates.filter { it.id in kept }.mapTo(HashSet()) { communityOf(clusterKey(it.title, tokenizer, topicScores)) }
        val longTerm =
            selectLongTermSeed(
                candidates = longTermCandidates,
                coveredCommunities = covered,
                communityMass = communityMass,
                communityOf = communityOf,
                excludedIds = cooledIds + kept,
                excludedChannelIds = excludedChannelIds,
                topicScores = topicScores,
            ) ?: return recent
        return kept + longTerm
    }

    /**
     * One seed for a lasting interest the recent seeds miss: the heaviest interest community not in
     * [coveredCommunities], drawn from likes, playlists and older watches. Age does not count against
     * it here, since being old is why these seeds are asked for.
     */
    fun selectLongTermSeed(
        candidates: List<GraphSeedInput>,
        coveredCommunities: Set<String>,
        communityMass: Map<String, Double>,
        communityOf: (String) -> String,
        excludedIds: Set<String> = emptySet(),
        excludedChannelIds: Set<String> = emptySet(),
        topicScores: Map<String, Double> = emptyMap(),
    ): String? {
        if (candidates.isEmpty() || communityMass.isEmpty()) return null
        return candidates
            .asSequence()
            .filter { it.id !in excludedIds && it.isEligible(excludedChannelIds) }
            .mapNotNull { seed ->
                val community = communityOf(clusterKey(seed.title, tokenizer, topicScores))
                val mass = communityMass[community] ?: return@mapNotNull null
                if (community in coveredCommunities) return@mapNotNull null
                Triple(seed.id, mass, seed.engagementWeight * seed.sourceWeight())
            }.maxWithOrNull(compareBy<Triple<String, Double, Double>> { it.second }.thenBy { it.third })
            ?.first
    }

    fun scoreSeed(
        seed: GraphSeedInput,
        now: Long = System.currentTimeMillis(),
    ): Double = seed.score(now)

    fun clusterKey(seed: GraphSeedInput): String = clusterKey(seed.title, tokenizer)

    private fun GraphSeedInput.isEligible(excludedChannelIds: Set<String>): Boolean {
        if (id.isBlank() || isShort) return false
        if (channelId.isNotBlank() && channelId in excludedChannelIds) return false
        if (source != GraphSeedSource.WATCH_HISTORY) return true

        return isRealWatch(durationSec.toLong(), percentWatched)
    }

    /** A watch that proves interest: most of the video, or a good part of a long one. */
    fun isRealWatch(
        durationSec: Long,
        percentWatched: Double,
    ): Boolean {
        val watchedSeconds = durationSec * (percentWatched / 100.0)
        return percentWatched >= 70.0 ||
            (percentWatched >= 35.0 && watchedSeconds >= MIN_LONG_WATCH_SECONDS)
    }

    private fun GraphSeedInput.score(now: Long): Double =
        engagementWeight.coerceAtLeast(0.0) * sourceWeight() * recencyWeight(timestamp, now)

    private fun GraphSeedInput.sourceWeight(): Double =
        when (source) {
            GraphSeedSource.LIKED -> 1.4

            GraphSeedSource.PLAYLIST -> 1.0

            GraphSeedSource.WATCH_HISTORY -> if (percentWatched >= 70.0) 1.2 else 0.8

            // Engine picks the viewer never touched: always below any real watch, like or save.
            GraphSeedSource.FEED -> 0.15
        }

    private fun recencyWeight(
        timestamp: Long,
        now: Long,
    ): Double {
        if (timestamp <= 0L) return 0.85
        val ageDays = ((now - timestamp).coerceAtLeast(0L) / DAY_MS).toInt()
        return when {
            ageDays <= 1 -> 1.0
            ageDays <= 7 -> 0.9
            ageDays <= 30 -> 0.75
            ageDays <= 90 -> 0.55
            else -> 0.4
        }
    }

    private fun clusterKey(
        title: String,
        tokenizer: NeuroTokenizer,
        topicScores: Map<String, Double> = emptyMap(),
    ): String {
        val tokens = tokenizer.tokenize(title)

        // Prefer the token the user's interest vector knows best — seeds then
        // cluster by INTEREST, not by whichever word happened to come first.
        if (topicScores.isNotEmpty()) {
            val best =
                tokens
                    .map { tokenizer.normalizeLemma(it) }
                    .mapNotNull { lemma ->
                        val score =
                            topicScores[lemma] ?: byBase(topicScores)[lemma]
                        score?.let { lemma to it }
                    }.maxByOrNull { it.second }
            if (best != null && best.second >= 0.05) return best.first
        }

        for (token in tokens) {
            val lemma = tokenizer.normalizeLemma(token)
            val category =
                NeuroTopicCatalog.TOPIC_CATEGORIES.firstOrNull { c ->
                    c.topics.any { tokenizer.normalizeLemma(it) == lemma }
                }
            if (category != null) return "cat:${category.name}"
        }
        return tokens.firstOrNull()?.let { tokenizer.normalizeLemma(it) } ?: "misc"
    }

    private data class ScoredSeed(
        val id: String,
        val clusterKey: String,
        val weight: Double,
    )
}
