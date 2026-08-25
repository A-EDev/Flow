package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.ChannelShortsFeedPage
import io.github.aedev.flow.data.shorts.ChannelShortsOwner
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionWatchedVideos
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tier that keeps the subscriptions queue going once the RSS-built feed runs out.
 *
 * The feed only reaches back as far as each channel's last few uploads, so without this a
 * subscriptions queue ends after the recent reels — sooner every session, since watched reels are
 * filtered out — and drops straight into recommendations.
 */
class SubscriptionDeepShortsLoaderTest {
    private fun reel(
        id: String,
        channelId: String,
        timestamp: Long = 0L,
        isShort: Boolean = true,
    ) = Video(
        id = id,
        title = id,
        channelName = channelId,
        channelId = channelId,
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0L,
        uploadDate = "",
        timestamp = timestamp,
        isShort = isShort,
    )

    private fun subscription(channelId: String) =
        ChannelSubscription(
            channelId = channelId,
            channelName = channelId,
            channelThumbnail = "",
        )

    private fun tabPage(
        channelId: String,
        ids: List<String>,
        continuation: String? = null,
    ) = ChannelShortsFeedPage(
        videos = ids.map { reel(it, channelId) },
        sorts = emptyList(),
        continuation = continuation,
        owner = ChannelShortsOwner(id = channelId, name = channelId, avatarUrl = ""),
    )

    /**
     * The loader advances its whole working set concurrently on `Dispatchers.IO`, so both fetch
     * hooks are entered from several threads at once. Unguarded collections here lose a write or
     * throw out of `ArrayList.add`, which surfaces as an unrelated-looking flake in any test that
     * walks more than one channel.
     */
    private class RecordingTabs(
        private val pages: Map<String, List<ChannelShortsFeedPage>>,
    ) {
        private val mutex = Mutex()
        private val requestedChannels = mutableListOf<String>()
        private val cursors = mutableMapOf<String, Int>()

        /** Snapshot for assertions, read from the test body once the loader has finished. */
        val requested: List<String>
            get() = requestedChannels.toList()

        suspend fun first(channelId: String): ChannelShortsFeedPage? =
            mutex.withLock {
                requestedChannels += channelId
                cursors[channelId] = 0
                pages[channelId]?.firstOrNull()
            }

        suspend fun next(
            continuation: String,
            owner: ChannelShortsOwner,
        ): ChannelShortsFeedPage? =
            mutex.withLock {
                val index = (cursors[owner.id] ?: 0) + 1
                cursors[owner.id] = index
                pages[owner.id]?.getOrNull(index)
            }
    }

    private fun loader(
        tabs: RecordingTabs,
        feed: List<Video> = emptyList(),
        subscriptions: List<String> = emptyList(),
        excluded: Set<String> = emptySet(),
        watched: Set<String> = emptySet(),
    ): SubscriptionDeepShortsLoader {
        val feedRepository: SubscriptionFeedRepository = mockk(relaxed = true)
        every { feedRepository.observeFeed() } returns flowOf(feed)
        val subscriptionRepository: SubscriptionRepository = mockk(relaxed = true)
        every { subscriptionRepository.getAllSubscriptions() } returns flowOf(subscriptions.map(::subscription))
        val preferences: PlayerPreferences = mockk(relaxed = true)
        every { preferences.subscriptionShortsExcludedChannels } returns flowOf(excluded)
        val watchedVideos: SubscriptionWatchedVideos = mockk(relaxed = true)
        every { watchedVideos.ids } returns flowOf(watched)
        return SubscriptionDeepShortsLoader(
            subscriptionFeedRepository = feedRepository,
            subscriptionRepository = subscriptionRepository,
            playerPreferences = preferences,
            watchedVideos = watchedVideos,
            fetchFirstPage = tabs::first,
            fetchNextPage = tabs::next,
        )
    }

    // ── Channel order ──

    @Test
    fun `channels that posted a reel recently are walked first, newest first`() {
        val order =
            subscriptionReelChannelOrder(
                feed =
                    listOf(
                        reel("old", channelId = "UCold", timestamp = 100L),
                        reel("new", channelId = "UCnew", timestamp = 300L),
                        reel("mid", channelId = "UCmid", timestamp = 200L),
                    ),
                subscribedChannelIds = listOf("UCold", "UCnew", "UCmid"),
            )

        assertEquals(listOf("UCnew", "UCmid", "UCold"), order)
    }

    @Test
    fun `a channel with no reel in the feed is still reachable, after the reel posters`() {
        val order =
            subscriptionReelChannelOrder(
                feed = listOf(reel("r", channelId = "UCreels", timestamp = 10L)),
                subscribedChannelIds = listOf("UCquiet", "UCreels"),
            )

        assertEquals(listOf("UCreels", "UCquiet"), order)
    }

    @Test
    fun `long-form uploads do not make a channel a reel poster`() {
        val order =
            subscriptionReelChannelOrder(
                feed = listOf(reel("v", channelId = "UCvideos", timestamp = 900L, isShort = false)),
                subscribedChannelIds = listOf("UCa", "UCvideos"),
            )

        assertEquals(listOf("UCa", "UCvideos"), order)
    }

    @Test
    fun `the walk is bounded however many channels are subscribed`() {
        val order =
            subscriptionReelChannelOrder(
                feed = emptyList(),
                subscribedChannelIds = (1..500).map { "UC$it" },
            )

        assertEquals(MAX_DEEP_SUBSCRIPTION_CHANNELS, order.size)
    }

    // ── Paging ──

    @Test
    fun `a page alternates between channels instead of draining one`() =
        runTest {
            val tabs =
                RecordingTabs(
                    mapOf(
                        "UCa" to listOf(tabPage("UCa", listOf("a1", "a2"))),
                        "UCb" to listOf(tabPage("UCb", listOf("b1", "b2"))),
                    ),
                )
            val page = loader(tabs, subscriptions = listOf("UCa", "UCb")).initial()

            assertEquals(listOf("a1", "b1", "a2", "b2"), page.items.map { it.id })
            assertTrue("nothing is left to fetch", page.exhausted)
        }

    @Test
    fun `a channel is paged deeper before the queue gives up`() =
        runTest {
            val tabs =
                RecordingTabs(
                    mapOf(
                        "UCa" to
                            listOf(
                                tabPage("UCa", listOf("a1"), continuation = "more"),
                                tabPage("UCa", listOf("a2")),
                            ),
                    ),
                )
            val loader = loader(tabs, subscriptions = listOf("UCa"))

            val first = loader.initial()
            assertEquals(listOf("a1"), first.items.map { it.id })
            assertFalse(first.exhausted)

            val second = loader.more(first.cursor)
            assertEquals(listOf("a2"), second.items.map { it.id })
            assertTrue(second.exhausted)
        }

    @Test
    fun `reels the user already watched are left out`() =
        runTest {
            val tabs = RecordingTabs(mapOf("UCa" to listOf(tabPage("UCa", listOf("seen", "fresh")))))

            val page = loader(tabs, subscriptions = listOf("UCa"), watched = setOf("seen")).initial()

            assertEquals(listOf("fresh"), page.items.map { it.id })
        }

    // A channel's Shorts tab opens on the same recent reels the subscription feed already queued. Left
    // in, a whole page would arrive only to be discarded, and the queue would go back for pages it did
    // not need — requests and battery spent to show nothing.
    @Test
    fun `reels the feed already offered are not offered again`() =
        runTest {
            val tabs = RecordingTabs(mapOf("UCa" to listOf(tabPage("UCa", listOf("recent", "older")))))

            val page =
                loader(
                    tabs,
                    feed = listOf(reel("recent", channelId = "UCa", timestamp = 50L)),
                    subscriptions = listOf("UCa"),
                ).initial()

            assertEquals(listOf("older"), page.items.map { it.id })
        }

    @Test
    fun `an excluded channel is never even asked`() =
        runTest {
            val tabs =
                RecordingTabs(
                    mapOf(
                        "UCa" to listOf(tabPage("UCa", listOf("a1"))),
                        "UCmuted" to listOf(tabPage("UCmuted", listOf("m1"))),
                    ),
                )

            val page =
                loader(
                    tabs,
                    subscriptions = listOf("UCa", "UCmuted"),
                    excluded = setOf("UCmuted"),
                ).initial()

            assertEquals(listOf("a1"), page.items.map { it.id })
            assertEquals(listOf("UCa"), tabs.requested)
        }

    // A channel with no Shorts tab, or a browse that fails, answers with nothing. Ending there would
    // strand every channel behind it — which is the whole reason this tier exists.
    @Test
    fun `a channel that answers with nothing does not end the queue`() =
        runTest {
            val tabs = RecordingTabs(mapOf("UCb" to listOf(tabPage("UCb", listOf("b1")))))

            val page = loader(tabs, subscriptions = listOf("UCdead", "UCb")).initial()

            assertEquals(listOf("b1"), page.items.map { it.id })
            assertTrue(tabs.requested.containsAll(listOf("UCdead", "UCb")))
        }

    @Test
    fun `no subscriptions means an immediately exhausted tier`() =
        runTest {
            val page = loader(RecordingTabs(emptyMap())).initial()

            assertTrue(page.items.isEmpty())
            assertTrue(page.exhausted)
            assertEquals(null, page.cursor)
        }
}
