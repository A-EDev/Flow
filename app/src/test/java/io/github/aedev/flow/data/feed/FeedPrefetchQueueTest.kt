package io.github.aedev.flow.data.feed

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeedPrefetchQueueTest {
    private fun queue() =
        FeedPrefetchQueue(
            prefetchAheadItemCount = 24,
            triggerRemainingItems = 8,
        )

    @Test
    fun `a freshly loaded feed does not prefetch before it is scrolled`() {
        val queue = queue()

        assertThat(queue.onVisible(currentItemCount = 40, feedReady = true)).isNull()
    }

    @Test
    fun `scrolling well short of the tail does not prefetch`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 40, feedReady = true)

        val request =
            queue.onViewportChanged(
                currentItemCount = 40,
                lastVisibleItemIndex = 10,
            )

        assertThat(request).isNull()
    }

    @Test
    fun `prefetch starts once the viewport approaches the end of the loaded feed`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 40, feedReady = true)

        val request =
            queue.onViewportChanged(
                currentItemCount = 40,
                lastVisibleItemIndex = 35,
            )

        assertThat(request?.targetItemCount).isEqualTo(60)
    }

    @Test
    fun `a feed shorter than the trigger distance prefetches as soon as it is seen`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 6, feedReady = true)

        val request =
            queue.onViewportChanged(
                currentItemCount = 6,
                lastVisibleItemIndex = 3,
            )

        assertThat(request?.targetItemCount).isEqualTo(28)
    }

    @Test
    fun `requests are coalesced against the largest target`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 40, feedReady = true)
        queue.onViewportChanged(currentItemCount = 40, lastVisibleItemIndex = 35)

        // Scrolling back up must not shrink the target the deeper position already earned.
        queue.onViewportChanged(currentItemCount = 40, lastVisibleItemIndex = 32)

        assertThat(queue.currentRequest(currentItemCount = 40)?.targetItemCount).isEqualTo(60)
    }

    @Test
    fun `prefetch stops once the target is satisfied`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 40, feedReady = true)
        queue.onViewportChanged(currentItemCount = 40, lastVisibleItemIndex = 35)

        assertThat(queue.currentRequest(currentItemCount = 60)).isNull()
    }

    @Test
    fun `hiding invalidates queued work and showing resumes the remaining target`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 40, feedReady = true)
        val original =
            queue.onViewportChanged(
                currentItemCount = 40,
                lastVisibleItemIndex = 35,
            )!!

        queue.onHidden()

        assertThat(queue.isCurrent(original.generation)).isFalse()
        assertThat(queue.currentRequest(currentItemCount = 40)).isNull()

        val resumed = queue.onVisible(currentItemCount = 48, feedReady = true)
        assertThat(resumed?.targetItemCount).isEqualTo(60)
    }

    @Test
    fun `refresh drops the pending target for the replacement feed`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 40, feedReady = true)
        val original =
            queue.onViewportChanged(
                currentItemCount = 40,
                lastVisibleItemIndex = 35,
            )!!

        queue.reset()

        assertThat(queue.isCurrent(original.generation)).isFalse()
        assertThat(queue.currentRequest(currentItemCount = 30)).isNull()
    }

    /**
     * Issue #765: a page that appends nothing leaves the feed the same length, so the viewport
     * index cannot change. The outstanding request must survive so a retry can re-arm it.
     */
    @Test
    fun `a request parked at the last item survives a page that appended nothing`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 40, feedReady = true)
        queue.onViewportChanged(currentItemCount = 40, lastVisibleItemIndex = 39)

        // The load-more attempt came back empty, so the feed is still 40 long and the user has
        // not moved. Re-asking with the unchanged count must still report work to do.
        assertThat(queue.currentRequest(currentItemCount = 40)).isNotNull()
    }

    @Test
    fun `re-reporting the same bottom index still returns the outstanding request`() {
        val queue = queue()
        queue.onVisible(currentItemCount = 40, feedReady = true)
        val first = queue.onViewportChanged(currentItemCount = 40, lastVisibleItemIndex = 39)

        val second = queue.onViewportChanged(currentItemCount = 40, lastVisibleItemIndex = 39)

        assertThat(first).isNotNull()
        assertThat(second).isEqualTo(first)
    }
}
