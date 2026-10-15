package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HomeSearchPagesTest {
    private val calls = mutableListOf<Pair<String, String?>>()
    private var now = 0L

    private fun video(id: String) = Video(id, "Title $id", "Channel", "ch-$id", "", 600, 1_000, uploadDate = "1 day ago")

    private val pages =
        HomeSearchPages(
            search = { query, continuation ->
                calls += query to continuation
                listOf(video("$query-${continuation ?: "1"}")) to "${continuation ?: "page"}+"
            },
            clock = { now },
        )

    private fun searchCards(
        query: String,
        count: Int,
    ) = (0 until count).associate { "$query$it" to HomeCardOrigin.search(query) }

    @Test
    fun `a search asked again within half an hour is not fetched again`() =
        runBlocking<Unit> {
            pages.firstPage("pixel")
            now += 29L * 60L * 1000L
            pages.firstPage("pixel")
            now += 2L * 60L * 1000L
            pages.firstPage("pixel")

            assertThat(calls).containsExactly("pixel" to null, "pixel" to null)
        }

    @Test
    fun `load-more continues the search that put the most cards on Home`() =
        runBlocking<Unit> {
            pages.firstPage("pixel")
            pages.firstPage("linux")

            val next = pages.nextPageOfProductive(searchCards("pixel", 3) + searchCards("linux", 4))

            assertThat(next?.first).isEqualTo("linux")
            assertThat(calls.last()).isEqualTo("linux" to "page+")
        }

    @Test
    fun `a search with fewer than three cards on Home is not continued`() =
        runBlocking<Unit> {
            pages.firstPage("pixel")

            assertThat(pages.nextPageOfProductive(searchCards("pixel", 2))).isNull()
        }

    @Test
    fun `a refresh forgets the next pages`() =
        runBlocking<Unit> {
            pages.firstPage("pixel")
            pages.reset()

            assertThat(pages.nextPageOfProductive(searchCards("pixel", 5))).isNull()
        }

    @Test
    fun `a short page takes the next page of the search asked longest ago`() =
        runBlocking<Unit> {
            pages.firstPage("pixel")
            pages.firstPage("linux")

            assertThat(pages.nextPageOfAny()?.first).isEqualTo("pixel")
            assertThat(pages.nextPageOfAny()?.first).isEqualTo("linux")
        }
}
