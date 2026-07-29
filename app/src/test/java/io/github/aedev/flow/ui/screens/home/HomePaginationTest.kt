package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomePaginationTest {

    private fun queue() = HomePrefetchQueue(
        prefetchAheadVideoCount = 24,
        triggerRemainingVideos = 8
    )

    @Test
    fun `a freshly loaded feed does not prefetch before it is scrolled`() {
        val queue = queue()

        assertThat(queue.onVisible(currentVideoCount = 40, feedReady = true)).isNull()
    }

    @Test
    fun `scrolling well short of the tail does not prefetch`() {
        val queue = queue()
        queue.onVisible(currentVideoCount = 40, feedReady = true)

        val request = queue.onViewportChanged(
            currentVideoCount = 40,
            lastVisibleVideoIndex = 10
        )

        assertThat(request).isNull()
    }

    @Test
    fun `prefetch starts once the viewport approaches the end of the loaded feed`() {
        val queue = queue()
        queue.onVisible(currentVideoCount = 40, feedReady = true)

        val request = queue.onViewportChanged(
            currentVideoCount = 40,
            lastVisibleVideoIndex = 35
        )

        assertThat(request?.targetVideoCount).isEqualTo(60)
    }

    @Test
    fun `a feed shorter than the trigger distance prefetches as soon as it is seen`() {
        val queue = queue()
        queue.onVisible(currentVideoCount = 6, feedReady = true)

        val request = queue.onViewportChanged(
            currentVideoCount = 6,
            lastVisibleVideoIndex = 3
        )

        assertThat(request?.targetVideoCount).isEqualTo(28)
    }

    @Test
    fun `requests are coalesced against the largest target`() {
        val queue = queue()
        queue.onVisible(currentVideoCount = 40, feedReady = true)
        queue.onViewportChanged(currentVideoCount = 40, lastVisibleVideoIndex = 35)

        // Scrolling back up must not shrink the target the deeper position already earned.
        queue.onViewportChanged(currentVideoCount = 40, lastVisibleVideoIndex = 32)

        assertThat(queue.currentRequest(currentVideoCount = 40)?.targetVideoCount).isEqualTo(60)
    }

    @Test
    fun `prefetch stops once the target is satisfied`() {
        val queue = queue()
        queue.onVisible(currentVideoCount = 40, feedReady = true)
        queue.onViewportChanged(currentVideoCount = 40, lastVisibleVideoIndex = 35)

        assertThat(queue.currentRequest(currentVideoCount = 60)).isNull()
    }

    @Test
    fun `hiding invalidates queued work and showing resumes the remaining target`() {
        val queue = queue()
        queue.onVisible(currentVideoCount = 40, feedReady = true)
        val original = queue.onViewportChanged(
            currentVideoCount = 40,
            lastVisibleVideoIndex = 35
        )!!

        queue.onHidden()

        assertThat(queue.isCurrent(original.generation)).isFalse()
        assertThat(queue.currentRequest(currentVideoCount = 40)).isNull()

        val resumed = queue.onVisible(currentVideoCount = 48, feedReady = true)
        assertThat(resumed?.targetVideoCount).isEqualTo(60)
    }

    @Test
    fun `refresh drops the pending target for the replacement feed`() {
        val queue = queue()
        queue.onVisible(currentVideoCount = 40, feedReady = true)
        val original = queue.onViewportChanged(
            currentVideoCount = 40,
            lastVisibleVideoIndex = 35
        )!!

        queue.reset()

        assertThat(queue.isCurrent(original.generation)).isFalse()
        assertThat(queue.currentRequest(currentVideoCount = 30)).isNull()
    }
}
