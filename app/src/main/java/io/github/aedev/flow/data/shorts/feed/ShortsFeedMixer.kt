package io.github.aedev.flow.data.shorts.feed

internal data class ShortsMixResult(
    val items: List<ShortsLaneItem>,
    val laneCounts: Map<ShortsFeedLane, Int>,
)

/**
 * Round-robin blend of the lanes under their quotas, relaxing quotas in [SCARCITY_ORDER] when a
 * round adds nothing. At most [maxPerChannel] reels per known channel per page; reels whose channel
 * is not known yet are exempt and spaced later, when their `/player` details arrive.
 */
internal fun blendShortsLanes(
    lanes: Map<ShortsFeedLane, List<ShortsLaneItem>>,
    quotas: Map<ShortsFeedLane, Int>,
    targetSize: Int,
    maxPerChannel: Int = 1,
): ShortsMixResult {
    val target = targetSize.coerceAtLeast(0)
    if (target == 0) return ShortsMixResult(emptyList(), emptyMap())

    val queues = ShortsFeedLane.entries.associateWith { lane -> ArrayDeque(lanes[lane].orEmpty()) }
    val channelCounts = mutableMapOf<String, Int>()
    val usedIds = mutableSetOf<String>()
    val added = mutableMapOf<ShortsFeedLane, Int>()
    val out = mutableListOf<ShortsLaneItem>()

    fun admit(item: ShortsLaneItem?): Boolean {
        if (item == null) return false
        val channel = item.short.channelId
        val count = channelCounts[channel] ?: 0
        if (channel.isNotBlank() && count >= maxPerChannel) return false
        if (!usedIds.add(item.short.id)) return false
        out += item
        if (channel.isNotBlank()) channelCounts[channel] = count + 1
        return true
    }

    fun take(lane: ShortsFeedLane): Boolean {
        val queue = queues.getValue(lane)
        while (queue.isNotEmpty()) {
            if (admit(queue.removeFirst())) {
                added[lane] = (added[lane] ?: 0) + 1
                return true
            }
        }
        return false
    }

    while (out.size < target && queues.values.any { it.isNotEmpty() }) {
        var addedThisRound = false
        for (lane in QUOTA_ORDER) {
            if (out.size >= target) break
            if ((added[lane] ?: 0) < (quotas[lane] ?: 0) && take(lane)) addedThisRound = true
        }
        if (!addedThisRound) {
            val forced = SCARCITY_ORDER.any { lane -> out.size >= target || take(lane) }
            if (!forced) break
        }
    }

    return ShortsMixResult(items = out, laneCounts = ShortsFeedLane.entries.associateWith { added[it] ?: 0 })
}

/**
 * Keeps same-channel reels at least [gap] slots apart where the channel is known; order is
 * otherwise preserved. [seedRecent] carries the previous page's tail so appends are spaced too.
 */
internal fun spaceShortsByChannel(
    items: List<ShortsLaneItem>,
    gap: Int = 2,
    seedRecent: List<String> = emptyList(),
): List<ShortsLaneItem> {
    if (items.size < 2) return items
    val remaining = items.toMutableList()
    val out = ArrayList<ShortsLaneItem>(items.size)
    val recent = ArrayDeque<String>()
    seedRecent.takeLast(gap).forEach { recent.addLast(it) }
    while (remaining.isNotEmpty()) {
        val index = remaining.indexOfFirst { it.short.channelId.isBlank() || it.short.channelId !in recent }.let { if (it < 0) 0 else it }
        val pick = remaining.removeAt(index)
        out += pick
        if (pick.short.channelId.isNotBlank()) {
            recent.addLast(pick.short.channelId)
            while (recent.size > gap) recent.removeFirst()
        }
    }
    return out
}

private val QUOTA_ORDER = listOf(ShortsFeedLane.RELATED, ShortsFeedLane.EXPLORE, ShortsFeedLane.DISCOVERY, ShortsFeedLane.SUBSCRIPTIONS)
private val SCARCITY_ORDER = QUOTA_ORDER
