package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.ChannelShortsPage
import io.github.aedev.flow.innertube.pages.SearchShortItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RSS lists Shorts and ordinary uploads together and marks neither, so without this the
 * subscription feed's "Show Shorts" toggle has nothing to act on (#903).
 */
class ChannelReelIndexTest {
    private val channelId = "UCchannel"

    @After
    fun tearDown() = unmockkAll()

    private fun video(
        id: String,
        isShort: Boolean = false,
    ) = Video(
        id = id,
        title = id,
        channelName = "Chan",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0L,
        uploadDate = "",
        isShort = isShort,
    )

    private fun page(vararg ids: String) =
        ChannelShortsPage(
            shorts = ids.map { SearchShortItem(id = it, title = it, viewCount = 0L) },
            sorts = emptyList(),
            continuation = null,
        )

    @Test
    fun `reels named by the Shorts tab are marked`() =
        runTest {
            mockkObject(YouTube)
            coEvery { YouTube.channelShorts(channelId) } returns Result.success(page("reel1", "reel2"))

            val marked =
                ChannelReelIndex().markReels(
                    channelId,
                    listOf(video("reel1"), video("upload1"), video("reel2")),
                )

            assertEquals(listOf(true, false, true), marked.map { it.isShort })
        }

    // A failed lookup must leave the list alone: guessing "not a reel" is the bug being fixed.
    @Test
    fun `a failed lookup changes nothing`() =
        runTest {
            mockkObject(YouTube)
            coEvery { YouTube.channelShorts(channelId) } returns Result.failure(IllegalStateException("offline"))

            val input = listOf(video("reel1"), video("upload1", isShort = true))
            val marked = ChannelReelIndex().markReels(channelId, input)

            assertEquals(input, marked)
        }

    @Test
    fun `an answer is reused rather than re-fetched`() =
        runTest {
            mockkObject(YouTube)
            coEvery { YouTube.channelShorts(channelId) } returns Result.success(page("reel1"))

            val index = ChannelReelIndex()
            index.markReels(channelId, listOf(video("reel1")), nowMillis = 0L)
            index.markReels(channelId, listOf(video("upload1")), nowMillis = 60_000L)

            coVerify(exactly = 1) { YouTube.channelShorts(channelId) }
        }

    @Test
    fun `a stale answer is refetched`() =
        runTest {
            mockkObject(YouTube)
            coEvery { YouTube.channelShorts(channelId) } returns Result.success(page("reel1"))

            val index = ChannelReelIndex()
            index.markReels(channelId, listOf(video("reel1")), nowMillis = 0L)
            index.markReels(channelId, listOf(video("upload1")), nowMillis = 7 * 60 * 60 * 1000L)

            coVerify(exactly = 2) { YouTube.channelShorts(channelId) }
        }

    // The channel tabs already answer for the videos they supply; a reel the Shorts tab has since
    // paged past must not be un-marked.
    @Test
    fun `an already marked reel is never asked about`() =
        runTest {
            mockkObject(YouTube)

            val input = listOf(video("reel1", isShort = true))
            val marked = ChannelReelIndex().markReels(channelId, input)

            assertEquals(input, marked)
            coVerify(exactly = 0) { YouTube.channelShorts(any()) }
        }
}
