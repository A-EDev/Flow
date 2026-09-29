package io.github.aedev.flow.data.shorts.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortsFeedQuotasTest {
    @Test
    fun `a mature account with subscriptions leans on related reels and caps subscriptions`() {
        val quotas = shortsFeedQuotas(16, ShortsFeedProfile(subscribedChannelIds = setOf("UCa"), isColdStart = false))

        assertEquals(5, quotas[ShortsFeedLane.RELATED])
        assertEquals(3, quotas[ShortsFeedLane.DISCOVERY])
        assertEquals(2, quotas[ShortsFeedLane.SUBSCRIPTIONS])
        assertEquals(6, quotas[ShortsFeedLane.EXPLORE])
        assertEquals(16, quotas.values.sum())
    }

    @Test
    fun `no subscriptions means no subscription slots`() {
        val quotas = shortsFeedQuotas(16, ShortsFeedProfile(subscribedChannelIds = emptySet(), isColdStart = false))

        assertEquals(0, quotas[ShortsFeedLane.SUBSCRIPTIONS])
        assertEquals(16, quotas.values.sum())
    }

    @Test
    fun `a cold start leans on the explore chain`() {
        val quotas = shortsFeedQuotas(16, ShortsFeedProfile(subscribedChannelIds = emptySet(), isColdStart = true))

        assertEquals(2, quotas[ShortsFeedLane.RELATED])
        assertEquals(4, quotas[ShortsFeedLane.DISCOVERY])
        assertEquals(10, quotas[ShortsFeedLane.EXPLORE])
        assertEquals(16, quotas.values.sum())
    }

    @Test
    fun `no slots means no quotas`() {
        assertEquals(0, shortsFeedQuotas(0, ShortsFeedProfile(setOf("UCa"), false)).values.sum())
    }
}
