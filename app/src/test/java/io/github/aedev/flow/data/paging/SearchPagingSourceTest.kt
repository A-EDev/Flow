package io.github.aedev.flow.data.paging

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.Duration
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.SortType
import io.github.aedev.flow.data.local.UploadDate
import io.github.aedev.flow.innertube.pages.SearchFixture
import io.github.aedev.flow.innertube.pages.search.SearchHeader
import io.github.aedev.flow.innertube.pages.search.toSearchResultsPage
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchPagingSourceTest {
    private class RecordingLoader(
        private val pages: List<String>,
    ) : SearchPageLoader {
        val calls = mutableListOf<Triple<String, String?, String?>>()
        var failNext = false

        override suspend fun invoke(
            query: String,
            params: String?,
            continuation: String?,
        ) = run {
            calls += Triple(query, params, continuation)
            if (failNext) {
                failNext = false
                throw IllegalStateException("boom")
            }
            SearchFixture(pages[(calls.size - 1).coerceAtMost(pages.lastIndex)]).toSearchResultsPage()
        }
    }

    private fun source(
        loader: SearchPageLoader,
        filter: SearchFilter = SearchFilter.DEFAULT,
        shortsEnabled: Boolean = true,
        onHeader: (SearchHeader) -> Unit = {},
    ) = SearchPagingSource("sam sulek", filter, shortsEnabled, onHeader, loader)

    private suspend fun SearchPagingSource.loadPage(key: String? = null) =
        load(PagingSource.LoadParams.Refresh(key, 20, false)) as PagingSource.LoadResult.Page

    @Test
    fun `issues exactly one request per page`() =
        runTest {
            val loader = RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK))
            source(loader).loadPage()

            assertThat(loader.calls).hasSize(1)
        }

    @Test
    fun `the first page carries the query and the params, a later page carries only the token`() =
        runTest {
            val loader = RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK, SearchFixture.ALL_CONTINUATION))
            val paging = source(loader, SearchFilter(contentType = ContentType.VIDEOS))

            val first = paging.loadPage()
            paging.loadPage(first.nextKey)

            assertThat(loader.calls[0].second).isEqualTo("EgIQAQ%3D%3D")
            assertThat(loader.calls[0].third).isNull()
            assertThat(loader.calls[1].third).isEqualTo(first.nextKey)
        }

    @Test
    fun `hands the next page token back as the paging key`() =
        runTest {
            val page = source(RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK))).loadPage()

            assertThat(page.prevKey).isNull()
            assertThat(page.nextKey).isNotNull()
        }

    @Test
    fun `reports the header from the first page only`() =
        runTest {
            val headers = mutableListOf<SearchHeader>()
            val loader = RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK, SearchFixture.ALL_CONTINUATION))
            val paging = source(loader, onHeader = { headers += it })

            val first = paging.loadPage()
            paging.loadPage(first.nextKey)

            assertThat(headers).hasSize(1)
            assertThat(headers.single().filterGroups).hasSize(5)
        }

    @Test
    fun `keeps every strip distinct across the page`() =
        runTest {
            val page = source(RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK))).loadPage()
            val shelves = page.data.filterIsInstance<SearchResultItem.ShelfResult>()

            assertThat(shelves.map { it.id }).containsNoDuplicates()
            assertThat(shelves.count { it.kind == SearchShelfKind.SHORTS }).isEqualTo(2)
            assertThat(shelves.count { it.kind == SearchShelfKind.VIDEOS }).isEqualTo(1)
        }

    @Test
    fun `de-duplicates a result that comes back on a later page`() =
        runTest {
            val loader = RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK, SearchFixture.ALL_SAM_SULEK))
            val paging = source(loader)

            val first = paging.loadPage()
            val second = paging.loadPage(first.nextKey)

            assertThat(first.data).isNotEmpty()
            assertThat(second.data).isEmpty()
        }

    @Test
    fun `hides shorts and shorts strips when the user turned shorts off`() =
        runTest {
            val page = source(RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK)), shortsEnabled = false).loadPage()

            assertThat(
                page.data.filterIsInstance<SearchResultItem.ShelfResult>().none {
                    it.kind == SearchShelfKind.SHORTS
                },
            ).isTrue()
        }

    @Test
    fun `passes the shorts type as a params token instead of a second request`() =
        runTest {
            val loader = RecordingLoader(listOf(SearchFixture.TYPE_SHORTS))
            source(loader, SearchFilter(contentType = ContentType.SHORTS)).loadPage()

            assertThat(loader.calls).hasSize(1)
            assertThat(loader.calls.single().second).isEqualTo("EgIQCQ%3D%3D")
        }

    @Test
    fun `builds one token for a combination of filters`() =
        runTest {
            val loader = RecordingLoader(listOf(SearchFixture.COMBINED_FILTERS))
            val filter =
                SearchFilter(
                    contentType = ContentType.VIDEOS,
                    duration = Duration.OVER_20_MINUTES,
                    uploadDate = UploadDate.THIS_YEAR,
                    sortType = SortType.VIEW_COUNT,
                )
            source(loader, filter).loadPage()

            assertThat(loader.calls.single().second).isEqualTo("CAMSBggFEAEYAg%3D%3D")
        }

    @Test
    fun `returns every item the response carried, filtering nothing on the client`() =
        runTest {
            val response = SearchFixture(SearchFixture.DURATION_UNDER_3).toSearchResultsPage()
            val page =
                source(
                    RecordingLoader(listOf(SearchFixture.DURATION_UNDER_3)),
                    SearchFilter(duration = Duration.UNDER_3_MINUTES),
                ).loadPage()

            assertThat(page.data).hasSize(response.toResultItems(shortsEnabled = true).size)
        }

    @Test
    fun `surfaces a loader failure as an error result`() =
        runTest {
            val loader = RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK)).apply { failNext = true }

            val result = source(loader).load(PagingSource.LoadParams.Refresh(null, 20, false))

            assertThat(result).isInstanceOf(PagingSource.LoadResult.Error::class.java)
        }

    @Test
    fun `never resumes from a stale key`() =
        runTest {
            assertThat(source(RecordingLoader(listOf(SearchFixture.ALL_SAM_SULEK))).getRefreshKey(mockState())).isNull()
        }

    private fun mockState() = androidx.paging.PagingState<String, SearchResultItem>(emptyList(), null, PAGING_CONFIG, 0)

    private companion object {
        val PAGING_CONFIG = androidx.paging.PagingConfig(pageSize = 20)
    }
}
