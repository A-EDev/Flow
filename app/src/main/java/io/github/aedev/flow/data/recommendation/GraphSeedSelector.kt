package io.github.aedev.flow.data.recommendation

internal object GraphSeedSelector {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MIN_LONG_WATCH_SECONDS = 180

    fun select(
        candidates: List<GraphSeedInput>,
        maxSeeds: Int,
        now: Long = System.currentTimeMillis(),
        excludedChannelIds: Set<String> = emptySet(),
        maxPerCluster: Int = 2
    ): List<String> {
        if (candidates.isEmpty() || maxSeeds <= 0) return emptyList()
        val tokenizer = NeuroTokenizer()
        val ranked = candidates
            .asSequence()
            .filter { it.isEligible(excludedChannelIds) }
            .map { seed ->
                ScoredSeed(
                    id = seed.id,
                    clusterKey = clusterKey(seed.title, tokenizer),
                    weight = seed.score(now)
                )
            }
            .filter { it.weight > 0.0 }
            .groupBy { it.id }
            .values
            .mapNotNull { seeds -> seeds.maxByOrNull { it.weight } }
            .map { SeedRank(it.id, it.clusterKey, it.weight) }
            .toList()

        return NeuroScoring.pickDiverseSeeds(ranked, maxSeeds, maxPerCluster)
    }

    private fun GraphSeedInput.isEligible(excludedChannelIds: Set<String>): Boolean {
        if (id.isBlank() || isShort) return false
        if (channelId.isNotBlank() && channelId in excludedChannelIds) return false
        if (source != GraphSeedSource.WATCH_HISTORY) return true

        val watchedSeconds = durationSec * (percentWatched / 100.0)
        return percentWatched >= 70.0 ||
            (percentWatched >= 35.0 && watchedSeconds >= MIN_LONG_WATCH_SECONDS)
    }

    private fun GraphSeedInput.score(now: Long): Double =
        engagementWeight.coerceAtLeast(0.0) * sourceWeight() * recencyWeight(timestamp, now)

    private fun GraphSeedInput.sourceWeight(): Double = when (source) {
        GraphSeedSource.LIKED -> 1.4
        GraphSeedSource.PLAYLIST -> 1.0
        GraphSeedSource.WATCH_HISTORY -> if (percentWatched >= 70.0) 1.2 else 0.8
    }

    private fun recencyWeight(timestamp: Long, now: Long): Double {
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

    private fun clusterKey(title: String, tokenizer: NeuroTokenizer): String {
        val tokens = tokenizer.tokenize(title)
        for (token in tokens) {
            val lemma = tokenizer.normalizeLemma(token)
            val category = NeuroTopicCatalog.TOPIC_CATEGORIES.firstOrNull { c ->
                c.topics.any { tokenizer.normalizeLemma(it) == lemma }
            }
            if (category != null) return "cat:${category.name}"
        }
        return tokens.firstOrNull()?.let { tokenizer.normalizeLemma(it) } ?: "misc"
    }

    private data class ScoredSeed(
        val id: String,
        val clusterKey: String,
        val weight: Double
    )
}
