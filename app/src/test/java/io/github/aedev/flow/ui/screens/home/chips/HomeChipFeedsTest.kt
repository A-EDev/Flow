package io.github.aedev.flow.ui.screens.home.chips

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.recommendation.FeedExclusions
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.ui.screens.home.HomeFeedSources
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeChipFeedsTest {
    private val repository: YouTubeRepository = mockk(relaxed = true)
    private val feedSources: HomeFeedSources = mockk(relaxed = true)
    private val subscriptionFeed: SubscriptionFeedRepository = mockk(relaxed = true)

    private fun feeds() =
        HomeChipFeeds(
            repository = repository,
            feedSources = feedSources,
            subscriptionFeed = subscriptionFeed,
            subscriptions = mockk(relaxed = true),
            channelMemory = mockk(relaxed = true),
            viewHistory = mockk(relaxed = true),
            likedVideos = mockk(relaxed = true),
            videoStats = mockk(relaxed = true),
            homeFeedCache = mockk(relaxed = true),
            videoDao = mockk(relaxed = true),
        )

    private fun context() =
        ChipFeedContext(
            filters = { HomeFeedCacheFilters(emptySet(), FeedExclusions.NONE) },
            exclusions = { FeedExclusions.NONE },
            watched = { emptySet() },
        )

    @Test
    fun `the All chip never fetches another chip's sources`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val feeds = feeds()

            feeds.attach(TestScope(dispatcher), SavedStateHandle(), context(), io = dispatcher, network = dispatcher)
            feeds.refreshChips()
            feeds.select(HomeChip.All.key)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.searchVideos(any(), any(), any()) }
            coVerify(exactly = 0) { feedSources.relatedVideos(any(), any()) }
            coVerify(exactly = 0) { subscriptionFeed.observeFeed() }
            assertThat(feeds.state.value.isAll).isTrue()
            assertThat(feeds.state.value.feed).isNull()
        }

    @Test
    fun `the selected chip comes back after the process is recreated`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val saved = SavedStateHandle(mapOf("home_selected_chip" to HomeChip.RecentlyUploaded.key))
            val feeds = feeds()

            feeds.attach(TestScope(dispatcher), saved, context(), io = dispatcher, network = dispatcher)

            assertThat(feeds.state.value.selected).isEqualTo(HomeChip.RecentlyUploaded.key)
        }
}
