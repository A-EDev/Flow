package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException

class HomeSearchPagesTest {
    private val calls = mutableListOf<Pair<String, String?>>()
    private var now = 0L
    private var answer: (String, String?) -> Result<Pair<List<Video>, String?>> = { query, continuation ->
        Result.success(listOf(video("$query-${continuation ?: "1"}")) to "${continuation ?: "page"}+")
    }

    private fun video(id: String) = Video(id, "Title $id", "Channel", "ch-$id", "", 600, 1_000, uploadDate = "1 day ago")

    private val pages =
        HomeSearchPages(
            search = { query, continuation ->
                calls += query to continuation
                answer(query, continuation)
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
    fun `a cached page names the search a video came from`() =
        runBlocking<Unit> {
            pages.firstPage("pixel")

            assertThat(pages.queryOf("pixel-1")).isEqualTo("pixel")
            assertThat(pages.queryOf("unknown")).isNull()
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
    fun `a refresh asks the same search again`() =
        runBlocking<Unit> {
            pages.firstPage("pixel")
            pages.reset()
            pages.firstPage("pixel")

            assertThat(calls).containsExactly("pixel" to null, "pixel" to null)
        }

    @Test
    fun `a failed search throws and is asked again next time`() =
        runBlocking<Unit> {
            answer = { _, _ -> Result.failure(IOException("offline")) }
            val failure = runCatching { pages.firstPage("pixel") }.exceptionOrNull()
            answer = { query, _ -> Result.success(listOf(video(query)) to null) }
            val retried = pages.firstPage("pixel")

            assertThat(failure).isInstanceOf(IOException::class.java)
            assertThat(retried.map { it.id }).containsExactly("pixel")
            assertThat(calls).hasSize(2)
        }

    @Test
    fun `an empty search is not kept`() =
        runBlocking<Unit> {
            answer = { _, _ -> Result.success(emptyList<Video>() to null) }
            pages.firstPage("pixel")
            pages.firstPage("pixel")

            assertThat(calls).hasSize(2)
        }

    @Test
    fun `a next page whose request failed is kept for another try`() =
        runBlocking<Unit> {
            pages.firstPage("pixel")
            answer = { _, _ -> Result.failure(IOException("offline")) }
            runCatching { pages.nextPageOfAny() }
            answer = { query, continuation -> Result.success(listOf(video("$query-$continuation")) to null) }

            assertThat(pages.nextPageOfAny()?.second?.map { it.id }).containsExactly("pixel-page+")
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
