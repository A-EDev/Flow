package io.github.aedev.flow.data.shorts

internal fun <T> diversifySubscriptions(
    items: List<T>,
    isSubscribed: (T) -> Boolean,
    maxSubscribedInWindow: Int = 1,
    windowSize: Int = 3,
): List<T> {
    if (items.size < 2 || maxSubscribedInWindow >= windowSize) return items

    val indexed = items.withIndex()
    val subscribed = ArrayDeque(indexed.filter { isSubscribed(it.value) })
    val discovery = ArrayDeque(indexed.filterNot { isSubscribed(it.value) })
    if (subscribed.isEmpty() || discovery.isEmpty()) return items

    val result = ArrayList<T>(items.size)
    val recentSubscriptionFlags = ArrayDeque<Boolean>()
    while (subscribed.isNotEmpty() || discovery.isNotEmpty()) {
        val subscriptionAllowed = recentSubscriptionFlags.count { it } < maxSubscribedInWindow
        val nextSubscribed = subscribed.firstOrNull()
        val nextDiscovery = discovery.firstOrNull()
        val takeSubscribed = when {
            nextSubscribed == null -> false
            nextDiscovery == null -> true
            !subscriptionAllowed -> false
            else -> nextSubscribed.index < nextDiscovery.index
        }
        val next = if (takeSubscribed) subscribed.removeFirst() else discovery.removeFirst()
        result += next.value
        recentSubscriptionFlags += takeSubscribed
        if (recentSubscriptionFlags.size >= windowSize) recentSubscriptionFlags.removeFirst()
    }
    return result
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
