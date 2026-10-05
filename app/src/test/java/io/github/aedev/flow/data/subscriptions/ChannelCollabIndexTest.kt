package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.innertube.pages.channel.ChannelHeader
import io.github.aedev.flow.innertube.pages.channel.ChannelPage
import io.github.aedev.flow.innertube.pages.channel.ChannelTabContent
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.channel.toChannelHeader
import io.github.aedev.flow.innertube.pages.channel.toChannelTabContent
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/** #840: a collaboration only shows on its collaborators' Home tab. */
class ChannelCollabIndexTest {
    private val ltt = "UCXuqSBlHAE6Xw-yeJA0Tunw"

    private fun lttLanding(): ChannelPage {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("channel/landing.json"))
        val json = Json.parseToJsonElement(stream.bufferedReader().use { it.readText() })
        val header = json.toChannelHeader(ltt)
        return ChannelPage(
            header = header,
            tabs = emptyList(),
            initialTab = json.toChannelTabContent(ChannelTabKind.Home, FeedItemOwner(id = ltt, name = header.title)),
        )
    }

    @Test
    fun `reads the collaborations a channel took part in from its real home tab`() {
        val collabs = lttLanding().collaborationsOf(ltt, followed = setOf(ltt))

        assertThat(collabs.map { it.id }).contains("OxuFWyoOt8k")
        collabs.forEach { video ->
            assertThat(video.channelId).isNotEqualTo(ltt)
            assertThat(video.collaborators.map { it.channelId }).contains(ltt)
        }
    }

    @Test
    fun `a collaboration uploaded by a followed channel is left to that channel's own feed`() {
        val uploaders = lttLanding().collaborationsOf(ltt, followed = setOf(ltt)).map { it.channelId }.toSet()

        assertThat(lttLanding().collaborationsOf(ltt, followed = uploaders + ltt)).isEmpty()
    }

    @Test
    fun `channels are looked at never-seen first, then longest waiting, a few at a time`() {
        val due = dueChannels(listOf("a", "b", "c", "d"), mapOf("a" to 50L, "b" to 10L, "d" to 500L), now = 100L, max = 2)

        assertThat(due).containsExactly("c", "b").inOrder()
    }

    private fun video(
        id: String,
        uploader: String,
        vararg with: String,
    ) = Video(
        id = id,
        title = id,
        channelName = uploader,
        channelId = uploader,
        thumbnailUrl = "",
        duration = 60,
        viewCount = 0,
        uploadDate = "",
        collaborators = (listOf(uploader) + with).map { VideoCollaborator(name = it, channelId = it) },
    )

    private fun page(vararg videos: Video) =
        ChannelPage(
            header = ChannelHeader(id = "B", title = "B"),
            tabs = emptyList(),
            initialTab =
                ChannelTabContent(
                    kind = ChannelTabKind.Home,
                    sections = listOf(FeedShelf(id = "s", items = videos.map { FeedItem.VideoItem(it) })),
                ),
        )

    @Test
    fun `a channel is looked at once a day, and once a week when it has no collaborations`() =
        runTest {
            var now = 0L
            val pages = mapOf("B" to page(video("v1", "A", "B")), "C" to page(video("v2", "C")))
            val index = ChannelCollabIndex(landing = { Result.success(pages.getValue(it)) }, clock = { now })

            val first = index.scan(listOf("B", "C"))
            assertThat(first.mapValues { (_, videos) -> videos.map { it.id } }).containsExactly("B", listOf("v1"), "C", emptyList<String>())
            assertThat(index.scan(listOf("B", "C"))).isEmpty()

            now = 25L * 3_600_000L
            assertThat(index.scan(listOf("B", "C")).keys).containsExactly("B")

            now = 8L * 24L * 3_600_000L
            assertThat(index.scan(listOf("B", "C")).keys).containsExactly("B", "C")
        }

    @Test
    fun `a channel that cannot be read is tried again next time`() =
        runTest {
            var fail = true
            val index =
                ChannelCollabIndex(
                    landing = { if (fail) Result.failure(IllegalStateException("offline")) else Result.success(page()) },
                    clock = { 0L },
                )

            assertThat(index.scan(listOf("B"))).isEmpty()
            fail = false
            assertThat(index.scan(listOf("B"))).containsExactly("B", emptyList<Video>())
        }
}
