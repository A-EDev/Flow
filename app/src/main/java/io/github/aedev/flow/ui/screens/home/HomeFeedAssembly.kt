package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

internal const val HOME_TARGET_SIZE = 40

// Fresh subs pinned to the very top; the rest interleave via the SUBS lane.
private const val FRESH_SUBS_PIN_TOP = 2

private const val BEST_SUBS_LIMIT = 15
private const val BEST_DISCOVERY_LIMIT = 15
private const val BEST_VIRAL_LIMIT = 6
private const val BEST_RELATED_LIMIT = 12

internal fun List<Video>.enrichAvatars(subAvatarMap: Map<String, String>): List<Video> =
    if (subAvatarMap.isEmpty()) {
        this
    } else {
        map { v ->
            if (v.channelThumbnailUrl.isEmpty() && subAvatarMap.containsKey(v.channelId)) {
                v.copy(
                    channelThumbnailUrl = subAvatarMap.getValue(v.channelId),
                    channelThumbnailUrls =
                        v.channelThumbnailUrls.ifEmpty {
                            listOf(subAvatarMap.getValue(v.channelId))
                        },
                )
            } else {
                v
            }
        }
    }

internal data class HomeFeedLanes(
    val pinnedFresh: List<Video>,
    val overflowFresh: List<Video>,
    val bestSubs: List<Video>,
    val bestDiscovery: List<Video>,
    val bestViral: List<Video>,
    val bestRelated: List<Video>,
    val relatedCandidates: List<GraphCandidate>,
    val relatedMetadata: Map<String, GraphCandidate>,
    val subsByRecency: List<Video>,
    val subsPoolSize: Int,
    val discoveryPoolSize: Int,
    val viralPoolSize: Int,
) {
    val freshCandidates: Sequence<Video>
        get() =
            pinnedFresh.asSequence() + overflowFresh.asSequence() + bestSubs.asSequence() +
                bestRelated.asSequence() + bestDiscovery.asSequence() + bestViral.asSequence()
}

/**
 * Turns the four raw fetch results into the ranked lanes the blend draws from.
 *
 * [rank] is the engine call, passed in so the whole pipeline can be exercised without one.
 */
internal suspend fun buildHomeFeedLanes(
    rawSubs: List<Video>,
    rawDiscovery: List<Video>,
    rawViral: List<Video>,
    rawRelated: List<GraphCandidate>,
    rssFeed: List<Video>,
    watched: Set<String>,
    excludedChannels: Set<String>,
    taste: FeedTasteProfile,
    now: Long,
    freshSlotTarget: Int,
    subAvatarMap: Map<String, String>,
    rank: suspend (List<Video>) -> List<Video>,
): HomeFeedLanes {
    fun Video.isAllowedChannel(): Boolean = channelId.isBlank() || channelId !in excludedChannels

    // The fresh-subs lane bypasses rank(): exclude blocked/suppressed channels here so they
    // cannot resurface through it.
    val subsPool =
        rawSubs
            .filterValid()
            .filterWatched(watched)
            .filter { it.isAllowedChannel() }
            .enrichAvatars(subAvatarMap)
    val discoveryPool =
        rawDiscovery
            .filterValid()
            .filterWatched(watched)
            .filterRecentHomeSuggestion(now)
    val viralPool =
        rawViral
            .filterValid()
            .filterWatched(watched)
            .filterRecentHomeSuggestion(now)

    val subsByRecency = subsPool.sortedByDescending { it.timestamp }

    // Fresh-subs lane is RSS-FIRST: the subscription feed store covers ALL subscribed channels
    // with real publish timestamps, so a fresh upload is visible even when its channel missed
    // this refresh's rotating 10-18 channel fetch window.
    val rssFresh =
        rssFeed
            .asSequence()
            .filter { !it.isShort && !it.isUpcoming && (it.duration > 0 || it.isLive) }
            .filter { (now - it.timestamp) in 0..FRESH_SUB_WINDOW_MS }
            .filter { it.isAllowedChannel() }
            .toList()
    val freshSubsLane =
        (rssFresh + subsByRecency.filter { isFreshSubscribedCandidate(it, now) })
            .filterWatched(watched)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
            // One fresh slot per channel — a channel that uploaded three times today must not
            // occupy three fresh slots.
            .distinctBy { it.channelId.ifBlank { it.id } }
            .take(freshSlotTarget)
    val freshIds = freshSubsLane.map { it.id }.toHashSet()

    val rankedSubs = rank(subsPool)
    val bestSubs =
        rankedSubs
            .filter { !freshIds.contains(it.id) }
            .take(BEST_SUBS_LIMIT)

    val relatedCandidates =
        rawRelated
            .filterValidGraph()
            .filterWatchedGraph(watched)
            .filterRecentHomeSuggestionGraph(now)
    val relatedPool = relatedCandidates.map { it.video }
    val relatedMetadata = relatedCandidates.associateBy { it.video.id }

    return HomeFeedLanes(
        // Only a couple of fresh subs are pinned to the very top; the rest ride the SUBS lane so
        // the first screen is a real source MIX instead of a wall of subscriptions.
        pinnedFresh = freshSubsLane.take(FRESH_SUBS_PIN_TOP),
        overflowFresh = freshSubsLane.drop(FRESH_SUBS_PIN_TOP),
        bestSubs = bestSubs,
        bestDiscovery = demoteByFit(rank(discoveryPool), taste).take(BEST_DISCOVERY_LIMIT),
        bestViral = demoteByFit(rank(viralPool), taste).take(BEST_VIRAL_LIMIT),
        bestRelated =
            demoteByFit(
                applyGraphBoost(rank(relatedPool), relatedMetadata),
                taste,
            ).take(BEST_RELATED_LIMIT),
        relatedCandidates = relatedCandidates,
        relatedMetadata = relatedMetadata,
        subsByRecency = subsByRecency,
        subsPoolSize = subsPool.size,
        discoveryPoolSize = discoveryPool.size,
        viralPoolSize = viralPool.size,
    )
}

internal data class HomeFeedMix(
    val videos: List<Video>,
    val sourceMix: FeedMixResult,
    val selectedSourceCounts: Map<FeedSource, Int>,
    val quotas: Map<FeedSource, Int>,
    val freshAdded: Int,
    val subsBacklog: List<Video>,
)

/**
 * Fills the feed from the lanes: pinned fresh first, then the quota blend.
 *
 * [onScreenIds] is excluded so a refresh produces a visibly different feed, but only while the
 * lanes are deep enough to still fill half the target without them.
 */
internal fun assembleHomeFeed(
    lanes: HomeFeedLanes,
    onScreenIds: Set<String>,
    subCount: Int,
    totalInteractions: Int,
    targetSize: Int = HOME_TARGET_SIZE,
): HomeFeedMix {
    val finalMix = mutableListOf<Video>()
    val usedChannelCounts = mutableMapOf<String, Int>()
    val usedVideoIds = mutableSetOf<String>()
    var freshAdded = 0

    if (onScreenIds.isNotEmpty()) {
        val freshCandidateCount = lanes.freshCandidates.distinctBy { it.id }.count { it.id !in onScreenIds }
        if (freshCandidateCount >= targetSize / 2) {
            usedVideoIds += onScreenIds
        }
    }

    lanes.pinnedFresh.forEach { video ->
        if (addUniqueVideo(video, finalMix, usedChannelCounts, usedVideoIds)) freshAdded++
    }

    val remaining = (targetSize - finalMix.size).coerceAtLeast(0)
    val quotas = homeFeedQuotas(remaining, subCount, totalInteractions)
    val sourceMix =
        blendFeedSources(
            lanes =
                mapOf(
                    FeedSource.SUBS to (lanes.overflowFresh + lanes.bestSubs),
                    FeedSource.RELATED to lanes.bestRelated,
                    FeedSource.DISCOVERY to lanes.bestDiscovery,
                    FeedSource.VIRAL to lanes.bestViral,
                ),
            quotas = quotas,
            targetSize = remaining,
            channelCounts = usedChannelCounts,
            usedVideoIds = usedVideoIds,
        )
    finalMix += sourceMix.videos

    return HomeFeedMix(
        videos = finalMix,
        sourceMix = sourceMix,
        selectedSourceCounts =
            sourceMix.sourceCounts.toMutableMap().also { counts ->
                counts[FeedSource.SUBS] = (counts[FeedSource.SUBS] ?: 0) + freshAdded
            },
        quotas = quotas,
        freshAdded = freshAdded,
        subsBacklog = lanes.subsByRecency.filterNot { usedVideoIds.contains(it.id) },
    )
}
