package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.data.local.ChannelSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRefreshPlannerTest {
    private val now = 1_700_000_000_000L
    private val ttl = SubscriptionRefreshPlanner.CHANNEL_FEED_TTL_MS

    private fun subscription(
        id: String,
        lastFeedFetchAt: Long = now,
        lastCheckTime: Long = 0L,
    ) = ChannelSubscription(
        channelId = id,
        channelName = "Channel $id",
        channelThumbnail = "",
        lastCheckTime = lastCheckTime,
        lastFeedFetchAt = lastFeedFetchAt,
    )

    @Test
    fun `no subscriptions means nothing to do`() {
        val plan = SubscriptionRefreshPlanner.plan(emptyList(), now)

        assertTrue(plan.isEmpty)
        assertFalse(plan.isFullRefresh)
    }

    @Test
    fun `fresh channels are skipped`() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("a"), subscription("b")),
                now,
            )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `only the aged out channel is fetched`() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(
                    subscription("fresh", lastFeedFetchAt = now - ttl / 2),
                    subscription("stale", lastFeedFetchAt = now - ttl - 1),
                ),
                now,
            )

        assertEquals(listOf("stale"), plan.channelIds)
        assertFalse(plan.isFullRefresh)
    }

    @Test
    fun `a never fetched channel is always included`() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("known"), subscription("brand-new", lastFeedFetchAt = 0L)),
                now,
            )

        assertEquals(listOf("brand-new"), plan.channelIds)
    }

    @Test
    fun `a pending upload signal beats the ttl`() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(
                    subscription("quiet", lastFeedFetchAt = now - 1000L),
                    subscription("posted", lastFeedFetchAt = now - 1000L, lastCheckTime = now - 500L),
                ),
                now,
            )

        assertEquals(listOf("posted"), plan.channelIds)
    }

    @Test
    fun `an upload signal older than the last fetch is already covered`() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("covered", lastFeedFetchAt = now - 1000L, lastCheckTime = now - 5000L)),
                now,
            )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a fetch timestamp in the future is treated as stale`() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("clock-skew", lastFeedFetchAt = now + 60_000L)),
                now,
            )

        assertEquals(listOf("clock-skew"), plan.channelIds)
    }

    @Test
    fun `force fetches every channel and reports a full refresh`() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("a"), subscription("b")),
                now,
                force = true,
            )

        assertEquals(listOf("a", "b"), plan.channelIds)
        assertTrue(plan.isFullRefresh)
    }

    @Test
    fun `every channel being stale is reported as a full refresh`() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(
                    subscription("a", lastFeedFetchAt = 0L),
                    subscription("b", lastFeedFetchAt = 0L),
                ),
                now,
            )

        assertEquals(listOf("a", "b"), plan.channelIds)
        assertTrue(plan.isFullRefresh)
    }
}
