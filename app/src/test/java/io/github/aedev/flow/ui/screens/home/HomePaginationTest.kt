package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomePaginationTest {

    @Test
    fun `entering a ready feed queues several pages before scrolling`() {
        val queue = HomePrefetchQueue(prefetchAheadVideoCount = 24)

        val request = queue.onVisible(currentVideoCount = 40, feedReady = true)

        assertThat(request?.targetVideoCount).isEqualTo(64)
    }

    @Test
    fun `viewport consumption extends the target while keeping requests bounded`() {
        val queue = HomePrefetchQueue(prefetchAheadVideoCount = 24)
        queue.onVisible(currentVideoCount = 40, feedReady = true)

        val request = queue.onViewportChanged(
            currentVideoCount = 64,
            lastVisibleVideoIndex = 60
        )

        assertThat(request?.targetVideoCount).isEqualTo(85)
        assertThat(request!!.targetVideoCount).isAtMost(64 + 24)
    }

    @Test
    fun `requests are coalesced against the largest target`() {
        val queue = HomePrefetchQueue(prefetchAheadVideoCount = 24)
        queue.onVisible(currentVideoCount = 40, feedReady = true)

        queue.onViewportChanged(currentVideoCount = 48, lastVisibleVideoIndex = 10)
        val request = queue.currentRequest(currentVideoCount = 48)

        assertThat(request?.targetVideoCount).isEqualTo(64)
    }

    @Test
    fun `feed readiness does not start work while home is hidden`() {
        val queue = HomePrefetchQueue(prefetchAheadVideoCount = 24)

        assertThat(queue.onFeedReady(currentVideoCount = 40)).isNull()
    }

    @Test
    fun `hiding invalidates queued work and showing resumes the remaining target`() {
        val queue = HomePrefetchQueue(prefetchAheadVideoCount = 24)
        val original = queue.onVisible(currentVideoCount = 40, feedReady = true)!!

        queue.onHidden()

        assertThat(queue.isCurrent(original.generation)).isFalse()
        assertThat(queue.currentRequest(currentVideoCount = 40)).isNull()
        val resumed = queue.onVisible(currentVideoCount = 48, feedReady = true)
        assertThat(resumed?.targetVideoCount).isEqualTo(64)
    }

    @Test
    fun `refresh resets the optimistic floor for the replacement feed`() {
        val queue = HomePrefetchQueue(prefetchAheadVideoCount = 24)
        val original = queue.onVisible(currentVideoCount = 40, feedReady = true)!!

        queue.reset()
        val replacement = queue.onFeedReady(currentVideoCount = 30)

        assertThat(queue.isCurrent(original.generation)).isFalse()
        assertThat(replacement?.targetVideoCount).isEqualTo(54)
    }
}
