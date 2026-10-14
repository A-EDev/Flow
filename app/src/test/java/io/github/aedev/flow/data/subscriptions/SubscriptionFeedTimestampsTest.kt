package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class SubscriptionFeedTimestampsTest {
    private val now = 1_791_331_200_000L
    private val hourMs = 3_600_000L
    private val dayMs = 24 * hourMs

    @Test
    fun `a plural age is read in its own unit, not as seconds`() {
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("3 days ago", now)).isEqualTo(now - 3 * dayMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("2 hours ago", now)).isEqualTo(now - 2 * hourMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("5 years ago", now)).isEqualTo(now - 5 * 365 * dayMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("10 seconds ago", now)).isEqualTo(now - 10_000L)
    }

    @Test
    fun `scheduled and live text keep their boosts`() {
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("Premieres in 2 days", now)).isEqualTo(now + dayMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("Streaming live", now)).isEqualTo(now + hourMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("unknown", now)).isNull()
    }

    @Test
    fun `a placeholder timestamp no longer jumps a days-old upload to the top`() {
        val video =
            Video(
                id = "v1",
                title = "t",
                channelName = "c",
                channelId = "UCc",
                thumbnailUrl = "",
                duration = 0,
                viewCount = 0,
                uploadDate = "3 days ago",
                timestamp = now,
            )

        assertThat(SubscriptionFeedTimestamps.effectiveUploadTimestamp(video, now)).isEqualTo(now - 3 * dayMs)
    }
}
