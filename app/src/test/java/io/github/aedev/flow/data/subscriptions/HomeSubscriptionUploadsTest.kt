package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelTabContent
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/** Home's live subscription lane (#1171 logs): a slow channel must not empty the lane. */
class HomeSubscriptionUploadsTest {
    private fun video(
        id: String,
        channelId: String,
        timestamp: Long = 1_000L,
        membersOnly: Boolean = false,
    ) = Video(
        id = id,
        title = id,
        channelName = channelId,
        channelId = channelId,
        thumbnailUrl = "",
        duration = 600,
        viewCount = 0L,
        uploadDate = "",
        timestamp = timestamp,
        membersOnlyText = if (membersOnly) "Members only" else null,
    )

    private val preferences =
        mockk<PlayerPreferences>(relaxed = true) {
            every { homeSubsRotationCursor } returns flowOf(0)
            coEvery { setHomeSubsRotationCursor(any()) } returns Unit
        }

    private fun uploads(tab: suspend (String, ChannelTabKind) -> Result<ChannelTabContent>) =
        HomeSubscriptionUploads(
            ChannelUploadsClient(
                landing = { error("Home never asks for the landing page") },
                tab = { browseId, _, _, kind -> tab(browseId, kind) },
                continuation = { _, _, _ -> error("Home reads first pages only") },
            ),
            preferences,
        )

    private fun page(
        kind: ChannelTabKind,
        vararg videos: Video,
    ) = Result.success(ChannelTabContent(kind = kind, items = videos.map { FeedItem.VideoItem(it) }, continuation = null))

    @Test
    fun `channels that answer before the deadline are kept when another does not`() =
        runTest {
            val fetcher =
                uploads { channelId, kind ->
                    if (channelId == "UCslow") delay(60_000)
                    page(kind, video("$channelId-1", channelId))
                }

            val videos =
                fetcher.fetch(
                    subscriptions = listOf(FeedItemOwner(id = "UCfast"), FeedItemOwner(id = "UCslow")),
                    deadlineMillis = 5_000,
                )

            assertThat(videos.map { it.id }).containsExactly("UCfast-1")
        }

    @Test
    fun `a failed channel and members-only uploads drop out, the rest come newest first`() =
        runTest {
            val fetcher =
                uploads { channelId, kind ->
                    when (channelId) {
                        "UCbroken" -> {
                            Result.failure(IOException("offline"))
                        }

                        else -> {
                            page(
                                kind,
                                video("old", channelId, timestamp = 1L),
                                video("new", channelId, timestamp = 9L),
                                video("paid", channelId, membersOnly = true),
                            )
                        }
                    }
                }

            val videos =
                fetcher.fetch(
                    subscriptions = listOf(FeedItemOwner(id = "UCa"), FeedItemOwner(id = "UCbroken")),
                )

            assertThat(videos.map { it.id }).containsExactly("new", "old").inOrder()
        }

    @Test
    fun `only the Videos tab is read, since reels come dated from RSS`() =
        runTest {
            val asked = mutableListOf<ChannelTabKind>()
            val fetcher =
                uploads { channelId, kind ->
                    asked += kind
                    page(kind, video("$channelId-$kind", channelId))
                }

            fetcher.fetch(listOf(FeedItemOwner(id = "UCa")))

            assertThat(asked).containsExactly(ChannelTabKind.Videos)
        }

    @Test
    fun `the window rotates through a long subscription list`() {
        val channels = (0 until 30).map { "c$it" }

        assertThat(homeSubsWindowSize(8)).isEqualTo(8)
        assertThat(homeSubsWindowSize(30)).isEqualTo(14)
        assertThat(homeSubsWindowSize(259)).isEqualTo(18)
        assertThat(rotatingWindow(channels, start = 25, count = 7)).containsExactly("c25", "c26", "c27", "c28", "c29", "c0", "c1").inOrder()
        assertThat(nextCursor(cursor = 25, windowSize = 7, channelCount = 30)).isEqualTo(2)
    }

    @Test
    fun `channels missing a length are asked first and not again within half an hour`() =
        runTest {
            val asked = mutableListOf<String>()
            val fetcher =
                uploads { channelId, kind ->
                    asked += channelId
                    page(kind, video("$channelId-1", channelId))
                }
            val subs = (0 until 30).map { FeedItemOwner(id = "UC%02d".format(it)) }

            fetcher.fetch(subs, priorityChannelIds = setOf("UC29"), now = 0L)
            assertThat(asked.first()).isEqualTo("UC29")

            asked.clear()
            fetcher.fetch(subs, priorityChannelIds = setOf("UC29"), now = 60_000L)
            assertThat(asked).doesNotContain("UC29")
        }

    @Test
    fun `channels missing lengths take at most half the window so the rotation keeps moving`() =
        runTest {
            val asked = mutableListOf<String>()
            val fetcher =
                uploads { channelId, kind ->
                    asked += channelId
                    page(kind, video("$channelId-1", channelId))
                }
            val subs = (0 until 30).map { FeedItemOwner(id = "UC%02d".format(it)) }

            fetcher.fetch(subs, priorityChannelIds = subs.mapTo(HashSet()) { it.id }.minus("UC00"), now = 0L)

            assertThat(asked).hasSize(homeSubsWindowSize(30))
            assertThat(asked).contains("UC00")
        }
}
