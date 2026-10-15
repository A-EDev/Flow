package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class HomeCardOriginTest {
    private fun video(
        id: String,
        channelId: String = "ch-$id",
    ) = Video(id, "Title $id", "Channel", channelId, "", 600, 1_000, uploadDate = "1 day ago")

    @Test
    fun `each lane names its own origin`() {
        val videos = listOf(video("a"), video("b"), video("c"), video("d"))
        val sources =
            mapOf("a" to FeedSource.RELATED, "b" to FeedSource.DISCOVERY, "c" to FeedSource.CHANNEL_MEMORY, "d" to FeedSource.SUBS)

        val origins = pageOrigins(videos, sources::get, mapOf("a" to "Pixel 10 review"), mapOf("b" to "tensor g5"), emptySet())

        assertThat(origins)
            .containsExactly(
                "a",
                HomeCardOrigin.related("Pixel 10 review"),
                "b",
                HomeCardOrigin.search("tensor g5"),
                "c",
                HomeCardOrigin.channelMemory,
                "d",
                HomeCardOrigin.subscription,
            )
    }

    @Test
    fun `a card no lane claims falls back to its seed, its search or a followed channel`() {
        val videos = listOf(video("a"), video("b"), video("c", channelId = "followed"), video("d"))

        val origins = pageOrigins(videos, { null }, mapOf("a" to "Seed"), mapOf("b" to "query"), setOf("followed"))

        assertThat(origins.keys).containsExactly("a", "b", "c")
        assertThat(origins["c"]).isEqualTo(HomeCardOrigin.subscription)
    }

    @Test
    fun `a search card whose query is no longer known still says it came from a search`() {
        val origins = pageOrigins(listOf(video("r")), { FeedSource.DISCOVERY }, emptyMap(), emptyMap(), emptySet())

        assertThat(origins).containsExactly("r", HomeCardOrigin.search(null))
    }

    @Test
    fun `a video several searches returned keeps the first`() {
        val shared = video("x")

        assertThat(queriesById(listOf("first" to listOf(shared), "second" to listOf(shared)))).containsExactly("x", "first")
    }
}
