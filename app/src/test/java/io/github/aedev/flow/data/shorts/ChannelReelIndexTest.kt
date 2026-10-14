package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.channel.ChannelShortsPage
import io.github.aedev.flow.innertube.pages.reel.ReelLockup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
            shorts = ids.map { ReelLockup(id = it, title = it, viewCount = 0L) },
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

            assertEquals(listOf(true, false, true), marked?.map { it.isShort })
        }

    // A failed lookup must not read as "no reels": the caller would store that guess as a verdict.
    @Test
    fun `a failed lookup is reported, not guessed`() =
        runTest {
            mockkObject(YouTube)
            coEvery { YouTube.channelShorts(channelId) } returns Result.failure(IllegalStateException("offline"))

            val marked = ChannelReelIndex().markReels(channelId, listOf(video("reel1"), video("upload1", isShort = true)))

            assertEquals(null, marked)
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

    @Test
    fun `at most two Shorts tab lookups run at once, so the sweep leaves room for everything else`() =
        runTest {
            mockkObject(YouTube)
            var running = 0
            var peak = 0
            coEvery { YouTube.channelShorts(any()) } coAnswers {
                running++
                peak = maxOf(peak, running)
                delay(1_000)
                running--
                Result.success(page("reel"))
            }
            val index = ChannelReelIndex()

            coroutineScope { (1..6).map { n -> async { index.reelIds("UC$n") } }.awaitAll() }

            assertEquals(2, peak)
        }

    @Test
    fun `a lookup that hangs gives up instead of holding its slot`() =
        runTest {
            mockkObject(YouTube)
            coEvery { YouTube.channelShorts(channelId) } coAnswers {
                delay(60_000)
                Result.success(page("reel"))
            }

            assertEquals(null, ChannelReelIndex().reelIds(channelId))
        }
}
