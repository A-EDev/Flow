package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.data.recommendation.GraphSeedSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Watch-history seed candidates for related-graph retrieval, newest first. */
internal fun graphSeedInputsFromHistory(
    history: List<VideoHistoryEntry>,
    max: Int = 40,
): List<GraphSeedInput> =
    history
        .filter { !it.isShort }
        .sortedByDescending { it.timestamp }
        .take(max)
        .map {
            GraphSeedInput(
                id = it.videoId,
                title = it.title,
                channelId = it.channelId,
                source = GraphSeedSource.WATCH_HISTORY,
                engagementWeight = (it.progressPercentage / 100.0).coerceIn(0.0, 1.0),
                timestamp = it.timestamp,
                durationSec = it.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                percentWatched = it.progressPercentage.toDouble(),
                isShort = it.isShort,
            )
        }

/** Saved-interest seed pools: watch history (newest-first), liked videos, and saved playlists. */
internal data class SavedSeedSources(
    val history: List<GraphSeedInput>,
    val liked: List<GraphSeedInput>,
    val playlists: List<GraphSeedInput>,
)

internal data class GraphCandidate(
    val video: Video,
    val seedId: String,
    val seedScore: Double,
    val graphRank: Int,
    val seedCluster: String,
    val seedResultCount: Int,
    val hitCount: Int = 1,
)

private data class GraphCandidateAccumulator(
    var candidate: GraphCandidate,
    val seedIds: MutableSet<String>,
)

internal fun savedInterestSeedInputs(
    sources: SavedSeedSources,
    cooldown: Set<String>,
    maxPerSource: Int = 40,
): List<GraphSeedInput> =
    listOf(sources.history, sources.liked, sources.playlists)
        .flatMap { seeds -> seeds.filterNot { it.id in cooldown }.take(maxPerSource) }

internal fun mergeGraphCandidates(candidates: List<GraphCandidate>): List<GraphCandidate> {
    if (candidates.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, GraphCandidateAccumulator>()
    for (candidate in candidates) {
        val videoId = candidate.video.id
        val accumulator = merged[videoId]
        if (accumulator == null) {
            merged[videoId] =
                GraphCandidateAccumulator(
                    candidate = candidate,
                    seedIds = mutableSetOf(candidate.seedId),
                )
            continue
        }

        accumulator.seedIds.add(candidate.seedId)
        val current = accumulator.candidate
        val strongerSeed = candidate.seedScore > current.seedScore
        accumulator.candidate =
            current.copy(
                video = if (strongerSeed) candidate.video else current.video,
                seedId = if (strongerSeed) candidate.seedId else current.seedId,
                seedScore = maxOf(current.seedScore, candidate.seedScore),
                graphRank = minOf(current.graphRank, candidate.graphRank),
                seedCluster = if (strongerSeed) candidate.seedCluster else current.seedCluster,
                seedResultCount = maxOf(current.seedResultCount, candidate.seedResultCount),
                hitCount = accumulator.seedIds.size,
            )
    }
    return merged.values.map { it.candidate }
}

internal fun graphBoost(candidate: GraphCandidate): Double {
    val highSeedBoost = if (candidate.seedScore >= 1.0) 0.04 else 0.0
    val convergenceBoost = if (candidate.hitCount > 1) 0.03 else 0.0
    val topThirdCount = (candidate.seedResultCount + 2) / 3
    val graphRankBoost = if (candidate.graphRank < topThirdCount.coerceAtLeast(1)) 0.02 else 0.0
    return (highSeedBoost + convergenceBoost + graphRankBoost).coerceAtMost(0.08)
}

internal fun applyGraphBoost(
    ranked: List<Video>,
    metadata: Map<String, GraphCandidate>,
): List<Video> {
    if (ranked.size <= 1 || metadata.isEmpty()) return ranked
    val maxIndex = (ranked.size - 1).coerceAtLeast(1)
    return ranked
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<Video>> { indexed ->
                val base = 1.0 - (indexed.index.toDouble() / maxIndex)
                base + (metadata[indexed.value.id]?.let(::graphBoost) ?: 0.0)
            }.thenBy { it.index },
        ).map { it.value }
}
