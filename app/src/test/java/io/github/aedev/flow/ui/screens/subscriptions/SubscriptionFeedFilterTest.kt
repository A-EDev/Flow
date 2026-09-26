package io.github.aedev.flow.ui.screens.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FeedExclusions
import io.github.aedev.flow.utils.formatYouTubeRelativeTime
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

/** What the Subscriptions screen shows from the cached feed (#1031, owner decision D5). */
class SubscriptionFeedFilterTest {
    private val now = 1_800_000_000_000L

    // The relative-date formatter is ICU, which the local unit-test android.jar only stubs.
    @Before
    fun stubRelativeDates() {
        mockkStatic(FORMAT_UTILS)
        every { formatYouTubeRelativeTime(any(), any(), any()) } returns "recently"
    }

    @After
    fun restoreRelativeDates() = unmockkStatic(FORMAT_UTILS)

    private fun video(
        id: String,
        channelId: String = "UCa",
        ageHours: Long = 1L,
        isShort: Boolean = false,
        isLive: Boolean = false,
    ) = Video(
        id = id,
        title = id,
        channelName = channelId,
        channelId = channelId,
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0L,
        uploadDate = "",
        timestamp = now - ageHours * 3_600_000L,
        isShort = isShort,
        isLive = isLive,
    )

    private fun sections(
        videos: List<Video>,
        filters: SubscriptionFeedFilters = SubscriptionFeedFilters(),
    ) = subscriptionFeedSections(videos, filters, now)

    @Test
    fun `a video marked not interested is hidden`() {
        val result =
            sections(
                listOf(video("keep"), video("hidden")),
                SubscriptionFeedFilters(exclusions = FeedExclusions(suppressedVideoIds = setOf("hidden"))),
            )

        assertThat(result.recentVideos.map { it.id }).containsExactly("keep")
    }

    @Test
    fun `a blocked channel is hidden, videos and reels alike`() {
        val result =
            sections(
                listOf(video("keep", "UCa"), video("blocked", "UCb"), video("blocked-reel", "UCb", isShort = true)),
                SubscriptionFeedFilters(exclusions = FeedExclusions(blockedChannelIds = setOf("UCb"))),
            )

        assertThat(result.recentVideos.map { it.id }).containsExactly("keep")
        assertThat(result.shorts).isEmpty()
    }

    // A single not-interested mark suppresses its channel in recommendations for a while; a
    // subscription is an explicit ask, so the channel's other uploads stay.
    @Test
    fun `an engine-suppressed channel still shows its uploads`() {
        val result =
            sections(
                listOf(video("v1", "UCb")),
                SubscriptionFeedFilters(exclusions = FeedExclusions(suppressedChannelIds = setOf("UCb"))),
            )

        assertThat(result.recentVideos.map { it.id }).containsExactly("v1")
    }

    @Test
    fun `the shelf keeps one reel per channel, the newest`() {
        val result =
            sections(
                listOf(
                    video("old-reel", ageHours = 5, isShort = true),
                    video("new-reel", ageHours = 1, isShort = true),
                    video("other-reel", "UCb", ageHours = 3, isShort = true),
                ),
            )

        assertThat(result.shorts.map { it.id }).containsExactly("new-reel", "other-reel").inOrder()
    }

    @Test
    fun `type toggles, watched, group and muted-shorts filters still apply`() {
        val result =
            sections(
                listOf(
                    video("live", isLive = true),
                    video("watched"),
                    video("other-group", "UCc"),
                    video("muted-reel", "UCa", isShort = true),
                    video("keep", ageHours = 2),
                ),
                SubscriptionFeedFilters(
                    showLive = false,
                    watchedVideoIds = setOf("watched"),
                    allowedChannelIds = setOf("UCa"),
                    excludedShortsChannelIds = setOf("UCa"),
                ),
            )

        assertThat(result.recentVideos.map { it.id }).containsExactly("keep")
        assertThat(result.shorts).isEmpty()
    }

    private companion object {
        const val FORMAT_UTILS = "io.github.aedev.flow.utils.FormatUtilsKt"
    }
}
