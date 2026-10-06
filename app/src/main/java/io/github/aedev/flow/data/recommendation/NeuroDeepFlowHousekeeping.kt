package io.github.aedev.flow.data.recommendation

/**
 * Keeps the engine's own bookkeeping from a Deep Flow session off disk. The session's feed memory,
 * seen Shorts and query rotation keep working in memory, so the feed does not start repeating, but
 * saves write the state from before Deep Flow and that state comes back once Deep Flow ends.
 * Call everything under the engine's brain lock.
 */
internal class NeuroDeepFlowHousekeeping {
    private var held: Fields? = null

    /** Call before a bookkeeping write; returns the brain the write should start from. */
    fun beforeWrite(
        brain: UserBrain,
        deepFlowActive: Boolean,
    ): UserBrain {
        if (deepFlowActive) {
            if (held == null) held = Fields.of(brain)
            return brain
        }
        val restore = held ?: return brain
        held = null
        return restore.applyTo(brain)
    }

    /** The brain as it may be saved, exported or backed up. */
    fun persistable(brain: UserBrain): UserBrain = held?.applyTo(brain) ?: brain

    /** The brain was replaced wholesale (reset or import): nothing held applies to it any more. */
    fun forget() {
        held = null
    }

    private data class Fields(
        val feedHistory: Map<String, FeedEntry>,
        val seenShortsHistory: Map<String, Long>,
        val recentQueryTokens: List<Set<String>>,
        val clusterRotation: Map<String, Long>,
        val recentRelatedSeeds: Map<String, Long>,
        val recentShortsSeeds: Map<String, Long>,
        val staleQueries: Map<String, Long>,
        val interestChips: InterestChipSet,
    ) {
        fun applyTo(brain: UserBrain): UserBrain =
            brain.copy(
                feedHistory = feedHistory,
                seenShortsHistory = seenShortsHistory,
                recentQueryTokens = recentQueryTokens,
                clusterRotation = clusterRotation,
                recentRelatedSeeds = recentRelatedSeeds,
                recentShortsSeeds = recentShortsSeeds,
                staleQueries = staleQueries,
                interestChips = interestChips,
            )

        companion object {
            fun of(brain: UserBrain) =
                Fields(
                    feedHistory = brain.feedHistory,
                    seenShortsHistory = brain.seenShortsHistory,
                    recentQueryTokens = brain.recentQueryTokens,
                    clusterRotation = brain.clusterRotation,
                    recentRelatedSeeds = brain.recentRelatedSeeds,
                    recentShortsSeeds = brain.recentShortsSeeds,
                    staleQueries = brain.staleQueries,
                    interestChips = brain.interestChips,
                )
        }
    }
}
