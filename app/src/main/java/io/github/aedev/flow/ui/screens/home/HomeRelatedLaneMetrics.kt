package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.data.recommendation.GraphSeedSource
import io.github.aedev.flow.data.recommendation.UserBrain
import kotlin.math.ln

internal data class RelatedLaneMetrics(
    val seedCandidatesAvailable: Int,
    val seedsSelected: Int,
    val seedSourceCounts: Map<GraphSeedSource, Int>,
    val nextEmptyResponses: Int,
    val relatedCandidatesFetched: Int,
    val relatedCandidatesMerged: Int,
    val relatedCandidatesSurvivingFilters: Int,
    val finalRelatedCount: Int,
    val finalFeedCount: Int,
    val relatedWatchThroughProxy: Double,
    val relatedSkipDislikeProxy: Double,
    val sourceEntropy: Double,
) {
    val nextEmptyRate: Double
        get() = if (seedsSelected == 0) 0.0 else nextEmptyResponses.toDouble() / seedsSelected

    val relatedDedupeRate: Double
        get() =
            if (relatedCandidatesFetched == 0) {
                0.0
            } else {
                1.0 - (relatedCandidatesMerged.toDouble() / relatedCandidatesFetched.toDouble())
            }

    val finalRelatedShare: Double
        get() = if (finalFeedCount == 0) 0.0 else finalRelatedCount.toDouble() / finalFeedCount.toDouble()

    fun toLogString(): String =
        "Related metrics: seedCandidates=$seedCandidatesAvailable, seeds=$seedsSelected, " +
            "seedSources=$seedSourceCounts, nextEmpty=${"%.2f".format(nextEmptyRate)}, " +
            "fetched=$relatedCandidatesFetched, merged=$relatedCandidatesMerged, " +
            "dedupe=${"%.2f".format(relatedDedupeRate)}, survived=$relatedCandidatesSurvivingFilters, " +
            "finalRelated=$finalRelatedCount/$finalFeedCount, share=${"%.2f".format(finalRelatedShare)}, " +
            "watchProxy=${"%.2f".format(relatedWatchThroughProxy)}, " +
            "negativeProxy=${"%.2f".format(relatedSkipDislikeProxy)}, entropy=${"%.2f".format(sourceEntropy)}"
}

internal fun sourceEntropy(sourceCounts: Map<FeedSource, Int>): Double {
    val total = sourceCounts.values.sum()
    if (total <= 0) return 0.0
    val activeSources = sourceCounts.values.count { it > 0 }
    if (activeSources <= 1) return 0.0
    val entropy =
        sourceCounts.values
            .filter { it > 0 }
            .sumOf { count ->
                val p = count.toDouble() / total.toDouble()
                -p * ln(p)
            }
    return entropy / ln(activeSources.toDouble())
}

internal fun buildRelatedLaneMetrics(
    seedInputs: List<GraphSeedInput>,
    seedIds: List<String>,
    fetchedPerSeed: Map<String, Int>,
    mergedRelatedCandidates: List<GraphCandidate>,
    filteredRelatedCandidates: List<GraphCandidate>,
    selectedSourceCounts: Map<FeedSource, Int>,
    finalFeedCount: Int,
    finalRelatedVideoIds: Set<String>,
    brain: UserBrain,
): RelatedLaneMetrics {
    val selectedSeedSet = seedIds.toSet()
    val seedSourceCounts =
        seedInputs
            .filter { it.id in selectedSeedSet }
            .groupingBy { it.source }
            .eachCount()
    val watchedRelated = finalRelatedVideoIds.count { (brain.watchHistoryMap[it] ?: 0f) >= 0.40f }
    val negativeRelated = finalRelatedVideoIds.count { it in brain.suppressedVideoIds }
    val denominator = finalRelatedVideoIds.size.takeIf { it > 0 } ?: 1

    return RelatedLaneMetrics(
        seedCandidatesAvailable = seedInputs.size,
        seedsSelected = seedIds.size,
        seedSourceCounts = seedSourceCounts,
        nextEmptyResponses = seedIds.count { (fetchedPerSeed[it] ?: 0) == 0 },
        relatedCandidatesFetched = fetchedPerSeed.values.sum(),
        relatedCandidatesMerged = mergedRelatedCandidates.size,
        relatedCandidatesSurvivingFilters = filteredRelatedCandidates.size,
        finalRelatedCount = selectedSourceCounts[FeedSource.RELATED] ?: 0,
        finalFeedCount = finalFeedCount,
        relatedWatchThroughProxy = watchedRelated.toDouble() / denominator.toDouble(),
        relatedSkipDislikeProxy = negativeRelated.toDouble() / denominator.toDouble(),
        sourceEntropy = sourceEntropy(selectedSourceCounts),
    )
}
