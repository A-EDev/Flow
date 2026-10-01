package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.flow.map

internal enum class FeedSource {
    SUBS,
    RELATED,
    DISCOVERY,

    /** New uploads from channels the viewer keeps watching without subscribing; see ChannelMemory. */
    CHANNEL_MEMORY,
}

internal data class FeedCandidate(
    val video: Video,
    val source: FeedSource,
)

internal data class FeedMixResult(
    val items: List<FeedCandidate>,
    val sourceCounts: Map<FeedSource, Int>,
) {
    val videos: List<Video> get() = items.map { it.video }
}

internal fun homeFeedQuotas(
    remaining: Int,
    subCount: Int,
    totalInteractions: Int,
): Map<FeedSource, Int> {
    val slots = remaining.coerceAtLeast(0)
    if (slots == 0) {
        return FeedSource.entries.associateWith { 0 }
    }

    val subs =
        when {
            subCount <= 0 -> 0
            totalInteractions > 50 -> (slots * 0.40).toInt()
            else -> (slots * 0.35).toInt()
        }.coerceAtLeast(0)
    val related =
        when {
            subCount <= 0 -> (slots * 0.35).toInt()
            totalInteractions > 50 -> (slots * 0.25).toInt()
            else -> (slots * 0.30).toInt()
        }.coerceAtLeast(0)
    val discovery =
        when {
            subCount <= 0 -> (slots * 0.45).toInt()
            else -> (slots * 0.25).toInt()
        }.coerceAtLeast(0)
    // The remainder, about an eighth of the page: the share the retired trending lane used to get.
    val channelMemory = (slots - subs - related - discovery).coerceAtLeast(0)

    return mapOf(
        FeedSource.SUBS to subs,
        FeedSource.RELATED to related,
        FeedSource.DISCOVERY to discovery,
        FeedSource.CHANNEL_MEMORY to channelMemory,
    )
}

internal fun addUniqueVideo(
    video: Video?,
    targetList: MutableList<Video>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    maxPerChannel: Int = 2,
): Boolean {
    if (video == null) return false

    val hasChannel = video.channelId.isNotBlank()
    val count = channelCounts[video.channelId] ?: 0
    if (hasChannel && count >= maxPerChannel) return false
    if (!usedVideoIds.add(video.id)) return false
    targetList.add(video)
    if (hasChannel) channelCounts[video.channelId] = count + 1
    return true
}

internal fun addUniquePageVideos(
    candidates: Iterable<Video>,
    targetList: MutableList<Video>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    targetSize: Int,
    maxPerChannel: Int = 2,
): Int {
    var added = 0
    for (candidate in candidates) {
        if (targetList.size >= targetSize) break
        if (addUniqueVideo(candidate, targetList, channelCounts, usedVideoIds, maxPerChannel)) {
            added++
        }
    }
    return added
}

private fun addUniqueCandidate(
    candidate: FeedCandidate?,
    targetList: MutableList<FeedCandidate>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    maxPerChannel: Int = 2,
): Boolean {
    if (candidate == null) return false
    val temp = mutableListOf<Video>()
    if (!addUniqueVideo(candidate.video, temp, channelCounts, usedVideoIds, maxPerChannel)) return false
    targetList.add(candidate)
    return true
}

/**
 * Round-robin over the lanes up to each quota, then lanes with leftovers fill what the others could
 * not. [CHANNEL_MEMORY] never takes more than its quota, and a channel in [singleChannels] (one the
 * memory lane serves) appears at most once.
 */
internal fun blendFeedSources(
    lanes: Map<FeedSource, List<Video>>,
    quotas: Map<FeedSource, Int>,
    targetSize: Int,
    channelCounts: MutableMap<String, Int> = mutableMapOf(),
    usedVideoIds: MutableSet<String> = mutableSetOf(),
    singleChannels: Set<String> = emptySet(),
): FeedMixResult {
    val target = targetSize.coerceAtLeast(0)
    if (target == 0) return FeedMixResult(emptyList(), emptyMap())

    val queues =
        FeedSource.entries.associateWith { source ->
            java.util.ArrayDeque(lanes[source].orEmpty().map { FeedCandidate(it, source) })
        }
    val quotaOrder = listOf(FeedSource.SUBS, FeedSource.RELATED, FeedSource.DISCOVERY, FeedSource.CHANNEL_MEMORY)
    val scarcityOrder = listOf(FeedSource.RELATED, FeedSource.DISCOVERY, FeedSource.SUBS)
    val addedBySource = mutableMapOf<FeedSource, Int>()
    val out = mutableListOf<FeedCandidate>()

    fun take(candidate: FeedCandidate?): Boolean {
        val cap = if (candidate != null && candidate.video.channelId in singleChannels) 1 else 2
        return addUniqueCandidate(candidate, out, channelCounts, usedVideoIds, cap).also { added ->
            if (added) addedBySource[candidate!!.source] = (addedBySource[candidate.source] ?: 0) + 1
        }
    }

    while (out.size < target && queues.any { it.value.isNotEmpty() }) {
        var addedThisRound = false
        for (source in quotaOrder) {
            if (out.size >= target) break
            if ((addedBySource[source] ?: 0) < (quotas[source] ?: 0) && take(queues[source]?.pollFirst())) {
                addedThisRound = true
            }
        }

        if (!addedThisRound) {
            // A rejected head (duplicate or capped channel) must not end the refill while the lane
            // still holds usable videos behind it.
            val forced =
                scarcityOrder.any { source ->
                    val queue = queues.getValue(source)
                    var added = false
                    while (!added && queue.isNotEmpty()) added = take(queue.pollFirst())
                    added
                }
            if (!forced) break
        }
    }

    return FeedMixResult(
        items = out,
        sourceCounts = FeedSource.entries.associateWith { source -> addedBySource[source] ?: 0 },
    )
}
