package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FeedExclusions

internal const val HOME_TARGET_SIZE = 40

// Fresh subs pinned to the very top; the rest interleave via the SUBS lane.
private const val FRESH_SUBS_PIN_TOP = 2

// Seen fresh uploads follow this many ranked subs: on the first page, but not the first card again.
private const val SHOWN_FRESH_AFTER_SUBS = 3

private const val BEST_SUBS_LIMIT = 15
private const val BEST_DISCOVERY_LIMIT = 15
private const val BEST_MEMORY_LIMIT = 6
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
    val shownFresh: List<Video>,
    val bestSubs: List<Video>,
    val bestDiscovery: List<Video>,
    val bestMemory: List<Video>,
    val bestRelated: List<Video>,
    val relatedCandidates: List<GraphCandidate>,
    val relatedMetadata: Map<String, GraphCandidate>,
    val subsByRecency: List<Video>,
    val subsPoolSize: Int,
    val discoveryPoolSize: Int,
    val memoryPoolSize: Int,
) {
    val freshCandidates: Sequence<Video>
        get() =
            pinnedFresh.asSequence() + overflowFresh.asSequence() + bestSubs.asSequence() + shownFresh.asSequence() +
                bestRelated.asSequence() + bestDiscovery.asSequence() + bestMemory.asSequence()
}

/**
 * Turns the raw fetch results into the ranked lanes the blend draws from.
 *
 * [rank] is the engine call, passed in so the whole pipeline can be exercised without one.
 */
internal suspend fun buildHomeFeedLanes(
    rawSubs: List<Video>,
    rawDiscovery: List<Video>,
    rawMemory: List<Video>,
    rawRelated: List<GraphCandidate>,
    rssFeed: List<Video>,
    watched: Set<String>,
    exclusions: FeedExclusions,
    isRecentlyShown: (videoId: String) -> Boolean,
    taste: FeedTasteProfile,
    now: Long,
    freshSlotTarget: Int,
    subAvatarMap: Map<String, String>,
    rank: suspend (List<Video>) -> List<Video>,
): HomeFeedLanes {
    // The fresh-subs lane and the subs backlog bypass rank(), so hidden videos, channels and
    // topics are dropped here or they would resurface through them.
    val subsPool =
        rawSubs
            .distinctBy { it.id }
            .filterValid()
            .filterWatched(watched)
            .filterNot(exclusions::hidesFromRecommendations)
            .enrichAvatars(subAvatarMap)
    val discoveryPool =
        rawDiscovery
            .filterValid()
            .filterWatched(watched)
            .filterRecentHomeSuggestion(now)
            .filterNot(exclusions::hidesFromRecommendations)
    val memoryPool =
        rawMemory
            .filterValid()
            .filterWatched(watched)
            .filterRecentHomeSuggestion(now)
            .filterNot(exclusions::hidesFromRecommendations)

    val subsByRecency = subsPool.sortedByDescending { it.timestamp }

    // Fresh-subs lane is RSS-FIRST: the subscription feed store covers ALL subscribed channels
    // with real publish timestamps, so a fresh upload is visible even when its channel missed
    // this refresh's rotating 10-18 channel fetch window.
    val rssFresh =
        rssFeed
            .asSequence()
            .filter { !it.isShort && !it.isUpcoming && (it.duration > 0 || it.isLive) }
            .filter { (now - it.timestamp) in 0..FRESH_SUB_WINDOW_MS }
            .filterNot(exclusions::hidesFromRecommendations)
            .toList()
    val freshSubsLane =
        (rssFresh + subsByRecency.filter { isFreshSubscribedCandidate(it, now) })
            .filterWatched(watched)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
            // One fresh slot per channel — a channel that uploaded three times today must not
            // occupy three fresh slots.
            .distinctBy { it.channelId.ifBlank { it.id } }
    // An upload already shown recently stays on Home but goes to the back of the SUBS lane, so the
    // same card is not pinned to the top of every launch for three days. Splitting before the cap
    // lets the next unseen upload take the slot.
    val (shownFresh, unshownFresh) =
        freshSubsLane
            .partition { isRecentlyShown(it.id) }
            .let { (shown, unshown) -> shown.take(freshSlotTarget) to unshown.take(freshSlotTarget) }
    val freshIds = (shownFresh + unshownFresh).mapTo(HashSet()) { it.id }

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
            .filterNot { exclusions.hidesFromRecommendations(it.video) }
    val relatedPool = relatedCandidates.map { it.video }
    val relatedMetadata = relatedCandidates.associateBy { it.video.id }

    return HomeFeedLanes(
        // Only a couple of fresh subs are pinned to the very top; the rest ride the SUBS lane so
        // the first screen is a real source MIX instead of a wall of subscriptions.
        pinnedFresh = unshownFresh.take(FRESH_SUBS_PIN_TOP),
        overflowFresh = unshownFresh.drop(FRESH_SUBS_PIN_TOP),
        shownFresh = shownFresh,
        bestSubs = bestSubs,
        bestDiscovery = demoteByFit(rank(discoveryPool), taste).take(BEST_DISCOVERY_LIMIT),
        // One upload per remembered channel: the lane spreads across channels, never floods with one.
        bestMemory =
            demoteByFit(rank(memoryPool), taste)
                .distinctBy { it.channelId }
                .take(BEST_MEMORY_LIMIT),
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
        memoryPoolSize = memoryPool.size,
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
                    FeedSource.SUBS to
                        lanes.overflowFresh + lanes.bestSubs.take(SHOWN_FRESH_AFTER_SUBS) + lanes.shownFresh +
                        lanes.bestSubs.drop(SHOWN_FRESH_AFTER_SUBS),
                    FeedSource.RELATED to lanes.bestRelated,
                    FeedSource.DISCOVERY to lanes.bestDiscovery,
                    FeedSource.CHANNEL_MEMORY to lanes.bestMemory,
                ),
            quotas = quotas,
            targetSize = remaining,
            channelCounts = usedChannelCounts,
            usedVideoIds = usedVideoIds,
            singleChannels = lanes.bestMemory.mapTo(HashSet()) { it.channelId },
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
