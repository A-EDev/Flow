package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelHeader
import io.github.aedev.flow.innertube.pages.channel.ChannelPage
import io.github.aedev.flow.innertube.pages.channel.ChannelTabContent
import io.github.aedev.flow.innertube.pages.channel.ChannelTabDescriptor
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/** #1094: an unreachable upload tab must fail the channel instead of reading as an empty one. */
class ChannelUploadsClientTest {
    private val hour = 3_600_000L
    private val now = System.currentTimeMillis()

    private fun video(
        id: String,
        ageHours: Long,
    ) = Video(
        id = id,
        title = id,
        channelName = "",
        channelId = "UCa",
        thumbnailUrl = "",
        duration = 60,
        viewCount = 0L,
        uploadDate = "",
        timestamp = now - ageHours * hour,
    )

    private fun landing(vararg kinds: ChannelTabKind) =
        ChannelPage(
            header = ChannelHeader(id = "UCa", title = "Channel", avatarUrl = "https://yt3.ggpht.com/a=s88"),
            tabs = kinds.map { ChannelTabDescriptor(kind = it, title = it.name, params = "p-${it.name}") },
            initialTab = null,
        )

    private fun page(
        kind: ChannelTabKind,
        videos: List<Video>,
        continuation: String? = null,
    ) = ChannelTabContent(kind = kind, items = videos.map { FeedItem.VideoItem(it) }, continuation = continuation)

    private val continuationCalls = mutableListOf<String>()

    private fun client(
        landingPage: Result<ChannelPage>,
        tabs: Map<ChannelTabKind, Result<ChannelTabContent>>,
        continuations: Map<String, Result<ChannelTabContent>> = emptyMap(),
    ) = ChannelUploadsClient(
        landing = { landingPage },
        tab = { _, _, _, kind -> tabs.getValue(kind) },
        continuation = { token, _, _ ->
            continuationCalls += token
            continuations.getValue(token)
        },
    )

    @Test
    fun `a failed first page fails the channel`() =
        runTest {
            val result =
                client(
                    landingPage = Result.success(landing(ChannelTabKind.Videos, ChannelTabKind.Shorts)),
                    tabs =
                        mapOf(
                            ChannelTabKind.Videos to Result.failure(IOException("browse 500")),
                            ChannelTabKind.Shorts to Result.success(page(ChannelTabKind.Shorts, emptyList())),
                        ),
                ).fetch("UCa", notBeforeMillis = now - 60 * 24 * hour)

            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `a failed later page keeps the newest uploads`() =
        runTest {
            val result =
                client(
                    landingPage = Result.success(landing(ChannelTabKind.Videos)),
                    tabs = mapOf(ChannelTabKind.Videos to Result.success(page(ChannelTabKind.Videos, listOf(video("v1", 1)), "next"))),
                    continuations = mapOf("next" to Result.failure(IOException("timeout"))),
                ).fetch("UCa", notBeforeMillis = now - 60 * 24 * hour)

            assertThat(result.getOrThrow().videos.map { it.id }).containsExactly("v1")
        }

    @Test
    fun `paging stops once the tab is past the window`() =
        runTest {
            client(
                landingPage = Result.success(landing(ChannelTabKind.Videos)),
                tabs = mapOf(ChannelTabKind.Videos to Result.success(page(ChannelTabKind.Videos, listOf(video("old", 24 * 90)), "next"))),
            ).fetch("UCa", notBeforeMillis = now - 60 * 24 * hour)

            assertThat(continuationCalls).isEmpty()
        }

    @Test
    fun `a channel with tabs but no upload tab is empty, not failed`() =
        runTest {
            val result =
                client(
                    landingPage = Result.success(landing(ChannelTabKind.Home, ChannelTabKind.Posts)),
                    tabs = emptyMap(),
                ).fetch("UCa", notBeforeMillis = now - 60 * 24 * hour)

            assertThat(result.getOrThrow().videos).isEmpty()
        }

    @Test
    fun `a landing with no tabs at all is a failure`() =
        runTest {
            val result =
                client(landingPage = Result.success(landing()), tabs = emptyMap())
                    .fetch("UCa", notBeforeMillis = now - 60 * 24 * hour)

            assertThat(result.isFailure).isTrue()
        }
}
