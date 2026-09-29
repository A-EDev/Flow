package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.flow.map

internal enum class FeedSource {
    SUBS,
    RELATED,
    DISCOVERY,
    VIRAL,
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
    val viral = (slots - subs - related - discovery).coerceAtLeast(0)

    return mapOf(
        FeedSource.SUBS to subs,
        FeedSource.RELATED to related,
        FeedSource.DISCOVERY to discovery,
        FeedSource.VIRAL to viral,
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

internal fun blendFeedSources(
    lanes: Map<FeedSource, List<Video>>,
    quotas: Map<FeedSource, Int>,
    targetSize: Int,
    channelCounts: MutableMap<String, Int> = mutableMapOf(),
    usedVideoIds: MutableSet<String> = mutableSetOf(),
): FeedMixResult {
    val target = targetSize.coerceAtLeast(0)
    if (target == 0) return FeedMixResult(emptyList(), emptyMap())

    val queues =
        FeedSource.entries.associateWith { source ->
            java.util.ArrayDeque(lanes[source].orEmpty().map { FeedCandidate(it, source) })
        }
    val quotaOrder = listOf(FeedSource.SUBS, FeedSource.RELATED, FeedSource.DISCOVERY, FeedSource.VIRAL)
    val scarcityOrder = listOf(FeedSource.RELATED, FeedSource.DISCOVERY, FeedSource.SUBS, FeedSource.VIRAL)
    val addedBySource = mutableMapOf<FeedSource, Int>()
    val out = mutableListOf<FeedCandidate>()

    while (out.size < target && queues.any { it.value.isNotEmpty() }) {
        var addedThisRound = false
        for (source in quotaOrder) {
            if (out.size >= target) break
            val added = addedBySource[source] ?: 0
            val quota = quotas[source] ?: 0
            if (added < quota && addUniqueCandidate(queues[source]?.pollFirst(), out, channelCounts, usedVideoIds)) {
                addedBySource[source] = added + 1
                addedThisRound = true
            }
        }

        if (!addedThisRound) {
            val forced =
                scarcityOrder.any { source ->
                    if (out.size >= target) {
                        true
                    } else {
                        addUniqueCandidate(queues[source]?.pollFirst(), out, channelCounts, usedVideoIds).also { added ->
                            if (added) addedBySource[source] = (addedBySource[source] ?: 0) + 1
                        }
                    }
                }
            if (!forced) break
        }
    }

    return FeedMixResult(
        items = out,
        sourceCounts = FeedSource.entries.associateWith { source -> addedBySource[source] ?: 0 },
    )
}
