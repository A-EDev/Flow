package io.github.aedev.flow.data.recommendation

/**
 * Picks the reels a related reel chain is opened from. A reel-only twin of [GraphSeedSelector]:
 * the same source and recency weighting, but reels carry no title worth clustering on, so the
 * spread is by channel — one seed per channel before any channel gets a second.
 */
internal object ShortsSeedSelector {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MIN_WATCHED_PERCENT = 60.0
    private const val FULL_WATCH_PERCENT = 90.0

    fun select(
        candidates: List<ShortsSeedInput>,
        maxSeeds: Int,
        now: Long = System.currentTimeMillis(),
        excludedChannelIds: Set<String> = emptySet(),
        cooldownIds: Set<String> = emptySet(),
        maxPerChannel: Int = 1,
    ): List<String> {
        if (candidates.isEmpty() || maxSeeds <= 0) return emptyList()
        val eligible = candidates.filter { it.isEligible(excludedChannelIds) }
        val rotated = eligible.filterNot { it.id in cooldownIds }
        val pool = if (rotated.size >= maxSeeds) rotated else eligible
        val ranked =
            pool
                .map { seed -> SeedRank(id = seed.id, clusterKey = seed.channelId.ifBlank { seed.id }, weight = seed.score(now)) }
                .filter { it.weight > 0.0 }
                .groupBy { it.id }
                .values
                .mapNotNull { ranks -> ranks.maxByOrNull { it.weight } }
        return NeuroScoring.pickDiverseSeeds(ranked, maxSeeds, maxPerChannel)
    }

    private fun ShortsSeedInput.isEligible(excludedChannelIds: Set<String>): Boolean =
        id.isNotBlank() &&
            (channelId.isBlank() || channelId !in excludedChannelIds) &&
            (source != ShortsSeedSource.WATCHED || percentWatched >= MIN_WATCHED_PERCENT)

    private fun ShortsSeedInput.score(now: Long): Double = sourceWeight() * recencyWeight(timestamp, now)

    private fun ShortsSeedInput.sourceWeight(): Double =
        when (source) {
            ShortsSeedSource.WANT_MORE -> 1.5
            ShortsSeedSource.LIKED -> 1.4
            ShortsSeedSource.WATCHED -> if (percentWatched >= FULL_WATCH_PERCENT) 1.2 else 0.8
            ShortsSeedSource.SAVED -> 1.0
            ShortsSeedSource.FEED -> 0.7
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
}
