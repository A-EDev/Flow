package io.github.aedev.flow.ui.screens.home

internal const val HOME_PREFETCH_AHEAD_VIDEO_COUNT = 24

/**
 * Prefetch only starts once fewer than this many loaded videos remain below the viewport.
 *
 * The feed used to queue a full prefetch target the moment it first painted, so a cold start
 * spent radio, parsing and ranking work on pages the user had not scrolled toward — and often
 * never would. Roughly two screenfuls of runway is enough to keep scrolling seamless.
 */
internal const val HOME_PREFETCH_TRIGGER_REMAINING_VIDEOS = 8

/**
 * One page per run. The drain loop re-arms itself from the viewport, so a user who keeps
 * scrolling still gets continuous content — just fetched in step with them rather than in a
 * burst of up to three pages.
 */
internal const val HOME_PREFETCH_MAX_PAGES_PER_RUN = 1

internal data class HomePrefetchRequest(
    val generation: Int,
    val targetVideoCount: Int
)

internal class HomePrefetchQueue(
    private val prefetchAheadVideoCount: Int = HOME_PREFETCH_AHEAD_VIDEO_COUNT,
    private val triggerRemainingVideos: Int = HOME_PREFETCH_TRIGGER_REMAINING_VIDEOS
) {
    private var generation = 0
    private var isVisible = false
    private var targetVideoCount = 0

    /**
     * Returning to Home resumes a target the user already scrolled into, but never creates one:
     * a feed that has just loaded has nothing to prefetch until it is actually consumed.
     */
    @Synchronized
    fun onVisible(currentVideoCount: Int, feedReady: Boolean): HomePrefetchRequest? {
        isVisible = true
        return if (feedReady) currentRequestLocked(currentVideoCount) else null
    }

    @Synchronized
    fun onHidden() {
        isVisible = false
        generation++
    }

    @Synchronized
    fun onViewportChanged(
        currentVideoCount: Int,
        lastVisibleVideoIndex: Int
    ): HomePrefetchRequest? {
        if (!isVisible || currentVideoCount <= 0) return null
        val remainingBelowViewport = currentVideoCount - (lastVisibleVideoIndex + 1)
        if (remainingBelowViewport > triggerRemainingVideos) return null
        targetVideoCount = maxOf(
            targetVideoCount,
            lastVisibleVideoIndex + 1 + prefetchAheadVideoCount
        )
        return currentRequestLocked(currentVideoCount)
    }

    @Synchronized
    fun currentRequest(currentVideoCount: Int): HomePrefetchRequest? =
        currentRequestLocked(currentVideoCount)

    @Synchronized
    fun reset() {
        generation++
        targetVideoCount = 0
    }

    @Synchronized
    fun isCurrent(requestGeneration: Int): Boolean =
        isVisible && generation == requestGeneration

    private fun currentRequestLocked(currentVideoCount: Int): HomePrefetchRequest? =
        if (isVisible && currentVideoCount < targetVideoCount) {
            HomePrefetchRequest(generation, targetVideoCount)
        } else {
            null
        }
}
