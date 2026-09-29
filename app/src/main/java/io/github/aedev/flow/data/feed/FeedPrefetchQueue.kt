package io.github.aedev.flow.data.feed

data class FeedPrefetchRequest(
    val generation: Int,
    val targetItemCount: Int,
)

/**
 * Viewport-driven paging for an endless feed: it arms a target only once fewer than
 * [triggerRemainingItems] loaded items remain below the viewport, raises that target as the user
 * keeps going, and invalidates in-flight work through a generation that changes whenever the
 * surface is hidden. Home and the Shorts pager share it with their own numbers.
 */
class FeedPrefetchQueue(
    private val prefetchAheadItemCount: Int,
    private val triggerRemainingItems: Int,
) {
    private var generation = 0
    private var isVisible = false
    private var targetItemCount = 0

    /**
     * Returning to the surface resumes a target the user already scrolled into, but never creates
     * one: a feed that has just loaded has nothing to prefetch until it is actually consumed.
     */
    @Synchronized
    fun onVisible(
        currentItemCount: Int,
        feedReady: Boolean,
    ): FeedPrefetchRequest? {
        isVisible = true
        return if (feedReady) currentRequestLocked(currentItemCount) else null
    }

    @Synchronized
    fun onHidden() {
        isVisible = false
        generation++
    }

    @Synchronized
    fun onViewportChanged(
        currentItemCount: Int,
        lastVisibleItemIndex: Int,
    ): FeedPrefetchRequest? {
        if (!isVisible || currentItemCount <= 0) return null
        val remainingBelowViewport = currentItemCount - (lastVisibleItemIndex + 1)
        if (remainingBelowViewport > triggerRemainingItems) return null
        targetItemCount = maxOf(targetItemCount, lastVisibleItemIndex + 1 + prefetchAheadItemCount)
        return currentRequestLocked(currentItemCount)
    }

    @Synchronized
    fun currentRequest(currentItemCount: Int): FeedPrefetchRequest? = currentRequestLocked(currentItemCount)

    @Synchronized
    fun reset() {
        generation++
        targetItemCount = 0
    }

    @Synchronized
    fun isCurrent(requestGeneration: Int): Boolean = isVisible && generation == requestGeneration

    private fun currentRequestLocked(currentItemCount: Int): FeedPrefetchRequest? =
        if (isVisible && currentItemCount < targetItemCount) FeedPrefetchRequest(generation, targetItemCount) else null
}
