package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionWatchedVideos
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue behind the Subscriptions Shorts shelf.
 *
 * #823: the shelf shows one reel per channel, so a queue built from it runs out after a handful of
 * swipes and drops into recommendations. The queue behind it has to be the full subscription list in
 * date order instead, and hold nothing that is not from a subscribed channel.
 */
class SubscriptionShortsLoaderTest {
    private fun reel(
        id: String,
        channelId: String = "UCa",
        timestamp: Long,
        isShort: Boolean = true,
    ) = Video(
        id = id,
        title = id,
        channelName = "Chan",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0L,
        uploadDate = "",
        timestamp = timestamp,
        isShort = isShort,
    )

    private fun loader(
        feed: List<Video>,
        excluded: Set<String> = emptySet(),
        watched: Set<String> = emptySet(),
        anchorVideoId: String? = null,
    ): SubscriptionShortsLoader {
        val repository: SubscriptionFeedRepository = mockk(relaxed = true)
        every { repository.observeFeed() } returns flowOf(feed)
        val preferences: PlayerPreferences = mockk(relaxed = true)
        every { preferences.subscriptionShortsExcludedChannels } returns flowOf(excluded)
        val watchedVideos: SubscriptionWatchedVideos = mockk(relaxed = true)
        every { watchedVideos.ids } returns flowOf(watched)
        return SubscriptionShortsLoader(repository, preferences, watchedVideos, anchorVideoId)
    }

    @Test
    fun `only reels, newest first`() =
        runTest {
            val page =
                loader(
                    listOf(
                        reel("older", timestamp = 100L),
                        reel("longform", timestamp = 300L, isShort = false),
                        reel("newest", timestamp = 400L),
                        reel("middle", timestamp = 200L),
                    ),
                ).initial()

            assertEquals(listOf("newest", "middle", "older"), page.items.map { it.id })
        }

    // One page, then done: the cache is already the whole list. Reporting itself exhausted is what
    // lets the controller hand over to the algorithmic feed instead of dead-ending.
    @Test
    fun `it never pages`() =
        runTest {
            val page = loader(listOf(reel("a", timestamp = 1L))).initial()

            assertTrue(page.exhausted)
            assertEquals(null, page.cursor)
        }

    @Test
    fun `channels muted for Shorts stay out`() =
        runTest {
            val page =
                loader(
                    feed = listOf(reel("keep", channelId = "UCa", timestamp = 2L), reel("drop", channelId = "UCb", timestamp = 1L)),
                    excluded = setOf("UCb"),
                ).initial()

            assertEquals(listOf("keep"), page.items.map { it.id })
        }

    @Test
    fun `watched reels stay out`() =
        runTest {
            val page =
                loader(
                    feed = listOf(reel("seen", timestamp = 2L), reel("unseen", timestamp = 1L)),
                    watched = setOf("seen"),
                ).initial()

            assertEquals(listOf("unseen"), page.items.map { it.id })
        }

    // Tapping a reel has to open on that reel. Filtering it out would silently start the queue on
    // whatever happened to be next.
    @Test
    fun `the tapped reel survives the watched filter`() =
        runTest {
            val page =
                loader(
                    feed = listOf(reel("seen", timestamp = 2L), reel("unseen", timestamp = 1L)),
                    watched = setOf("seen"),
                    anchorVideoId = "seen",
                ).initial()

            assertEquals(listOf("seen", "unseen"), page.items.map { it.id })
        }
}
