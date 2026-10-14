package io.github.aedev.flow.data.shorts

internal fun <T> openingOnSeed(
    items: List<T>,
    seed: T,
    id: (T) -> String,
): List<T> {
    val seedId = id(seed)
    val opening = items.firstOrNull { id(it) == seedId } ?: seed
    return listOf(opening) + items.filterNot { id(it) == seedId }
}

internal fun <T> mergeDiscoveryCandidates(
    current: List<T>,
    discovery: List<T>,
    currentIndex: Int,
    id: (T) -> String,
): List<T> {
    if (current.isEmpty() || discovery.isEmpty()) return current
    val pinnedCount = (currentIndex + 1).coerceIn(0, current.size)
    val pinned = current.take(pinnedCount)
    val knownIds = current.asSequence().map(id).toMutableSet()
    val freshDiscovery = discovery.filter { knownIds.add(id(it)) }
    if (freshDiscovery.isEmpty()) return current

    val existingTail = ArrayDeque(current.drop(pinnedCount))
    val discoveryQueue = ArrayDeque(freshDiscovery)
    val mergedTail = ArrayList<T>(existingTail.size + discoveryQueue.size)
    while (existingTail.isNotEmpty() || discoveryQueue.isNotEmpty()) {
        if (discoveryQueue.isNotEmpty()) mergedTail += discoveryQueue.removeFirst()
        if (existingTail.isNotEmpty()) mergedTail += existingTail.removeFirst()
    }
    return pinned + mergedTail
}

/**
 * Keeps any one channel to [maxPerChannel] reels before every other channel has had its turn.
 * Surplus reels are not dropped: they form the next round, in their original order, so a channel
 * that posted five reels still shows all five — spread through the feed rather than in a run.
 * Reels without a channel id are left where they are.
 */
internal fun <T> spreadChannels(
    items: List<T>,
    channelId: (T) -> String,
    maxPerChannel: Int = MAX_REELS_PER_CHANNEL_PER_ROUND,
): List<T> {
    if (items.size < 2 || maxPerChannel < 1) return items
    val seen = HashMap<String, Int>()
    return items
        .withIndex()
        .map { (index, item) ->
            val channel = channelId(item)
            val round = if (channel.isBlank()) 0 else (seen.merge(channel, 1, Int::plus)!! - 1) / maxPerChannel
            Triple(round, index, item)
        }.sortedWith(compareBy({ it.first }, { it.second }))
        .map { it.third }
}

internal const val MAX_REELS_PER_CHANNEL_PER_ROUND = 2

/** [count] items from [offset] on, wrapping round the end, so a short list still yields a full slice. */
internal fun <T> List<T>.sliceFrom(
    offset: Int,
    count: Int,
): List<T> {
    if (isEmpty() || count <= 0) return emptyList()
    val start = offset.mod(size)
    return (indices.drop(start) + indices.take(start)).take(minOf(count, size)).map { this[it] }
}

/**
 * Moves a reel that has just learnt its channel away from a run of that channel, by swapping it
 * with the next later reel of another channel. Only reels after [currentIndex] move: what the user
 * has already seen stays where it was. Reels whose channel is still unknown are never touched.
 */
internal fun <T> deferChannelRuns(
    items: List<T>,
    changedIds: Set<String>,
    currentIndex: Int,
    id: (T) -> String,
    channelId: (T) -> String,
    window: Int = 2,
): List<T> {
    if (items.size < 3 || changedIds.isEmpty()) return items
    val out = items.toMutableList()
    val moved = mutableSetOf<String>()
    for (index in (currentIndex + 1) until out.size) {
        if (id(out[index]) !in changedIds || id(out[index]) in moved) continue
        val channel = channelId(out[index])
        if (channel.isBlank()) continue
        val before = (maxOf(0, index - window) until index)
        if (before.none { channelId(out[it]) == channel }) continue
        val swap =
            ((index + 1) until out.size).firstOrNull { later ->
                val other = channelId(out[later])
                other != channel && before.none { other.isNotBlank() && channelId(out[it]) == other }
            } ?: continue
        moved += id(out[index])
        val later = out[swap]
        out[swap] = out[index]
        out[index] = later
    }
    return out
}
