package io.github.aedev.flow.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubscriptionNotificationPolicyTest {
    private val reels = setOf("short1", "short2")

    @Test
    fun `a regular video is announced even when Shorts alerts are off`() {
        assertThat(
            SubscriptionNotificationPolicy.shouldAnnounce(
                videoId = "video1",
                reelVideoIds = reels,
                announceReelsInFeed = true,
                notifyShorts = false,
            ),
        ).isTrue()
    }

    @Test
    fun `a Short is announced when the feed shows Shorts and Shorts alerts are on`() {
        assertThat(
            SubscriptionNotificationPolicy.shouldAnnounce(
                videoId = "short1",
                reelVideoIds = reels,
                announceReelsInFeed = true,
                notifyShorts = true,
            ),
        ).isTrue()
    }

    @Test
    fun `a Short is silent when Shorts alerts are off`() {
        assertThat(
            SubscriptionNotificationPolicy.shouldAnnounce(
                videoId = "short1",
                reelVideoIds = reels,
                announceReelsInFeed = true,
                notifyShorts = false,
            ),
        ).isFalse()
    }

    @Test
    fun `a Short is silent when Shorts are hidden from the subscription feed`() {
        assertThat(
            SubscriptionNotificationPolicy.shouldAnnounce(
                videoId = "short1",
                reelVideoIds = reels,
                announceReelsInFeed = false,
                notifyShorts = true,
            ),
        ).isFalse()
    }
}
