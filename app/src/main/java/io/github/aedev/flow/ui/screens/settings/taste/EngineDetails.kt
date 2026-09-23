package io.github.aedev.flow.ui.screens.settings.taste

import androidx.compose.runtime.Immutable
import io.github.aedev.flow.data.recommendation.UserBrain

/** Counts from the video engine's memory, for anyone debugging what it has learned. */
@Immutable
data class EngineDetails(
    val interactions: Int,
    val topics: Int,
    val channels: Int,
    val history: Int,
    val feedMemory: Int,
    val suppressed: Int,
    val shortsSeen: Int,
    /** The words behind the discovery searches it ran most recently, newest first. */
    val recentQueries: List<String>,
) {
    companion object {
        private const val RECENT_QUERY_COUNT = 5

        fun of(brain: UserBrain) =
            EngineDetails(
                interactions = brain.totalInteractions,
                topics = brain.globalVector.topics.size,
                channels = brain.channelScores.size,
                history = brain.watchHistoryMap.size,
                feedMemory = brain.feedHistory.size,
                suppressed = brain.suppressedVideoIds.size + brain.suppressedChannels.size,
                shortsSeen = brain.seenShortsHistory.size,
                recentQueries =
                    brain.recentQueryTokens
                        .takeLast(RECENT_QUERY_COUNT)
                        .reversed()
                        .map { it.joinToString(" ") },
            )
    }
}
