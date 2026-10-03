package io.github.aedev.flow.data.innertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.ChannelReelIndex
import io.github.aedev.flow.data.subscriptions.ChannelRssClient
import io.github.aedev.flow.data.subscriptions.ChannelRssEntry
import io.github.aedev.flow.data.subscriptions.ChannelRssFeed
import io.github.aedev.flow.data.subscriptions.ChannelUploads
import io.github.aedev.flow.data.subscriptions.ChannelUploadsClient
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.utils.formatYouTubeRelativeTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** #1094: which channels the subscription sweep reports, keeps and falls back for. */
class RssSubscriptionServiceTest {
    private val rssClient: ChannelRssClient = mockk()
    private val reelIndex: ChannelReelIndex = mockk()
    private val uploads: ChannelUploadsClient = mockk()
    private val service = RssSubscriptionService(rssClient, reelIndex, uploads)
    private val hour = 3_600_000L

    private var reelIds: Set<String> = emptySet()

    // The relative-date formatter is ICU, which the local unit-test android.jar only stubs.
    @Before
    fun stubRelativeDates() {
        mockkStatic(FORMAT_UTILS)
        every { formatYouTubeRelativeTime(any(), any(), any()) } returns "recently"
    }

    @After
    fun restoreRelativeDates() = unmockkStatic(FORMAT_UTILS)

    init {
        coEvery { reelIndex.markReels(any(), any(), any()) } coAnswers {
            secondArg<List<Video>>().map { if (it.id in reelIds) it.copy(isShort = true) else it }
        }
    }

    private fun entry(
        id: String,
        title: String = "Title $id",
        description: String? = null,
        ageHours: Long = 2L,
    ) = ChannelRssEntry(
        videoId = id,
        title = title,
        thumbnailUrl = null,
        publishedAtMillis = System.currentTimeMillis() - ageHours * hour,
        description = description,
    )

    private fun rss(
        channelId: String,
        vararg entries: ChannelRssEntry,
    ) {
        coEvery { rssClient.fetch(channelId) } returns Result.success(ChannelRssFeed("Channel", entries.toList()))
    }

    private fun upload(
        id: String,
        channelId: String,
        ageHours: Long = 3L,
    ) = Video(
        id = id,
        title = "Upload $id",
        channelName = "",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 600,
        viewCount = 10L,
        uploadDate = "",
        timestamp = System.currentTimeMillis() - ageHours * hour,
    )

    private suspend fun sweep(vararg channelIds: String) = service.fetchSubscriptionVideos(channelIds.toList()).last()

    @Test
    fun `the stock membership call to action in a description does not hide the upload`() =
        runTest {
            rss(
                "UCa",
                entry("v1", description = "Join this channel to get access to perks. Thanks to our channel members!"),
            )

            val chunk = sweep("UCa")

            assertThat(chunk.videos.map { it.id }).containsExactly("v1")
        }

    @Test
    fun `a members-only title is still dropped`() =
        runTest {
            rss("UCa", entry("v1", title = "Members-only livestream replay"), entry("v2"))

            assertThat(sweep("UCa").videos.map { it.id }).containsExactly("v2")
        }

    @Test
    fun `a channel whose RSS lists only reels falls back to its tabs`() =
        runTest {
            reelIds = setOf("r1", "r2")
            // The feed keeps a channel's newest reel only, so r2 is clearly older: two equal ages read
            // the clock twice and could otherwise put r2 a millisecond ahead.
            rss("UCa", entry("r1"), entry("r2", ageHours = 4))
            coEvery { uploads.fetch("UCa", any(), any()) } returns
                Result.success(ChannelUploads(owner = FeedItemOwner("UCa", "Channel"), videos = listOf(upload("long", "UCa"))))

            val chunk = sweep("UCa")

            coVerify(exactly = 1) { uploads.fetch("UCa", any(), any()) }
            assertThat(chunk.videos.map { it.id }).containsAtLeast("long", "r1")
            assertThat(chunk.failedChannelIds).isEmpty()
        }

    @Test
    fun `an old reel off the Shorts tab is not passed off as a new upload (1175)`() =
        runTest {
            reelIds = setOf("r1")
            rss("UCa", entry("r1", ageHours = 5))
            coEvery { uploads.fetch("UCa", any(), any()) } returns
                Result.success(
                    ChannelUploads(
                        owner = FeedItemOwner("UCa", "Channel"),
                        shorts =
                            listOf(
                                upload("r1", "UCa").copy(isShort = true, timestamp = 0L),
                                upload("old-reel", "UCa").copy(isShort = true, timestamp = 0L),
                            ),
                    ),
                )

            val chunk = sweep("UCa")

            assertThat(chunk.videos.map { it.id }).doesNotContain("old-reel")
            val dated = chunk.videos.single { it.id == "r1" }
            assertThat(System.currentTimeMillis() - dated.timestamp).isAtLeast(4 * hour)
        }

    @Test
    fun `a channel whose entries are all classified already is not browsed again`() =
        runTest {
            rss("UCa", entry("reel"), entry("video"))

            val chunk =
                service
                    .fetchSubscriptionVideos(listOf("UCa"), storedReelVerdicts = mapOf("reel" to true, "video" to false))
                    .last()

            coVerify(exactly = 0) { reelIndex.markReels(any(), any(), any()) }
            assertThat(chunk.videos.single { it.id == "reel" }.isShort).isTrue()
            assertThat(chunk.videos.single { it.id == "video" }.isShort).isFalse()
        }

    @Test
    fun `a new entry still gets its channel browsed`() =
        runTest {
            reelIds = setOf("new-reel")
            rss("UCa", entry("video"), entry("new-reel"))

            val chunk = service.fetchSubscriptionVideos(listOf("UCa"), storedReelVerdicts = mapOf("video" to false)).last()

            coVerify(exactly = 1) { reelIndex.markReels("UCa", any(), any()) }
            assertThat(chunk.videos.single { it.id == "new-reel" }.isShort).isTrue()
        }

    @Test
    fun `a channel with a long-form upload in RSS needs no fallback`() =
        runTest {
            reelIds = setOf("r1")
            rss("UCa", entry("r1"), entry("v1"))

            sweep("UCa")

            coVerify(exactly = 0) { uploads.fetch(any(), any(), any()) }
        }

    @Test
    fun `a failed tab pass after RSS answered keeps the channel without reporting it (1186)`() =
        runTest {
            reelIds = setOf("r1")
            rss("UCa", entry("r1"))
            coEvery { uploads.fetch("UCa", any(), any()) } returns Result.failure(IOException("browse 500"))

            val chunk = sweep("UCa")

            assertThat(chunk.failedChannelIds).isEmpty()
            assertThat(chunk.incompleteChannelIds).containsExactly("UCa")
            assertThat(chunk.videos.map { it.id }).containsExactly("r1")
        }

    @Test
    fun `a slice the Shorts tab could not classify is shown but left unfinished`() =
        runTest {
            rss("UCa", entry("v1"))
            coEvery { reelIndex.markReels("UCa", any(), any()) } returns null

            val chunk = sweep("UCa")

            assertThat(chunk.videos.map { it.id }).containsExactly("v1")
            assertThat(chunk.failedChannelIds).isEmpty()
            assertThat(chunk.incompleteChannelIds).containsExactly("UCa")
        }

    @Test
    fun `a channel RSS and the tabs both miss is reported`() =
        runTest {
            coEvery { rssClient.fetch("UCa") } returns Result.failure(IOException("HTTP 429"))
            coEvery { uploads.fetch("UCa", any(), any()) } returns Result.failure(IOException("browse 500"))
            rss("UCb", entry("v1"))

            val chunk = sweep("UCa", "UCb")

            assertThat(chunk.failedChannelIds).containsExactly("UCa")
            assertThat(chunk.videos.map { it.id }).containsExactly("v1")
        }

    @Test
    fun `the tabs rescue a channel RSS could not reach`() =
        runTest {
            coEvery { rssClient.fetch("UCa") } returns Result.failure(IOException("HTTP 404"))
            coEvery { uploads.fetch("UCa", any(), any()) } returns
                Result.success(
                    ChannelUploads(
                        owner = FeedItemOwner("UCa", "Channel"),
                        videos = listOf(upload("v1", "UCa")),
                        live = listOf(upload("past-stream", "UCa")),
                    ),
                )

            val chunk = sweep("UCa")

            assertThat(chunk.failedChannelIds).isEmpty()
            assertThat(chunk.videos.map { it.id }).containsExactly("v1", "past-stream")
            assertThat(chunk.videos.single { it.id == "past-stream" }.isLive).isTrue()
            assertThat(chunk.videos.single { it.id == "v1" }.channelName).isEqualTo("Channel")
        }

    @Test
    fun `a members-only upload from the tabs is dropped by its badge`() =
        runTest {
            coEvery { rssClient.fetch("UCa") } returns Result.failure(IOException("HTTP 404"))
            coEvery { uploads.fetch("UCa", any(), any()) } returns
                Result.success(
                    ChannelUploads(
                        owner = FeedItemOwner("UCa", "Channel"),
                        videos = listOf(upload("public", "UCa"), upload("members", "UCa").copy(membersOnlyText = "Members only")),
                    ),
                )

            assertThat(sweep("UCa").videos.map { it.id }).containsExactly("public")
        }

    private companion object {
        const val FORMAT_UTILS = "io.github.aedev.flow.utils.FormatUtilsKt"
    }
}
