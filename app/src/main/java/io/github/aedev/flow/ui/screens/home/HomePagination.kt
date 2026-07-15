package io.github.aedev.flow.ui.screens.home

internal const val HOME_PREFETCH_AHEAD_VIDEO_COUNT = 24
internal const val HOME_PREFETCH_MAX_PAGES_PER_RUN = 3

internal data class HomePrefetchRequest(
    val generation: Int,
    val targetVideoCount: Int
)

internal class HomePrefetchQueue(
    private val prefetchAheadVideoCount: Int = HOME_PREFETCH_AHEAD_VIDEO_COUNT
) {
    private var generation = 0
    private var isVisible = false
    private var initialTargetVideoCount = 0
    private var targetVideoCount = 0

    @Synchronized
    fun onVisible(currentVideoCount: Int, feedReady: Boolean): HomePrefetchRequest? {
        isVisible = true
        return if (feedReady) onFeedReadyLocked(currentVideoCount) else null
    }

    @Synchronized
    fun onHidden() {
        isVisible = false
        generation++
    }

    @Synchronized
    fun onFeedReady(currentVideoCount: Int): HomePrefetchRequest? =
        onFeedReadyLocked(currentVideoCount)

    @Synchronized
    fun onViewportChanged(
        currentVideoCount: Int,
        lastVisibleVideoIndex: Int
    ): HomePrefetchRequest? {
        if (!isVisible || currentVideoCount <= 0) return null
        if (initialTargetVideoCount == 0) {
            initialTargetVideoCount = currentVideoCount + prefetchAheadVideoCount
        }
        val viewportTarget = (lastVisibleVideoIndex + 1 + prefetchAheadVideoCount)
            .coerceAtMost(currentVideoCount + prefetchAheadVideoCount)
        targetVideoCount = maxOf(targetVideoCount, initialTargetVideoCount, viewportTarget)
        return currentRequestLocked(currentVideoCount)
    }

    @Synchronized
    fun currentRequest(currentVideoCount: Int): HomePrefetchRequest? =
        currentRequestLocked(currentVideoCount)

    @Synchronized
    fun reset() {
        generation++
        initialTargetVideoCount = 0
        targetVideoCount = 0
    }

    @Synchronized
    fun isCurrent(requestGeneration: Int): Boolean =
        isVisible && generation == requestGeneration

    private fun onFeedReadyLocked(currentVideoCount: Int): HomePrefetchRequest? {
        if (!isVisible || currentVideoCount <= 0) return null
        if (initialTargetVideoCount == 0) {
            initialTargetVideoCount = currentVideoCount + prefetchAheadVideoCount
        }
        targetVideoCount = maxOf(targetVideoCount, initialTargetVideoCount)
        return currentRequestLocked(currentVideoCount)
    }

    private fun currentRequestLocked(currentVideoCount: Int): HomePrefetchRequest? =
        if (isVisible && currentVideoCount < targetVideoCount) {
            HomePrefetchRequest(generation, targetVideoCount)
        } else {
            null
        }
}
