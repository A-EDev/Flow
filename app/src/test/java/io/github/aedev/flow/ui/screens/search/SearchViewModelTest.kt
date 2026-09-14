package io.github.aedev.flow.ui.screens.search

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.SortType
import io.github.aedev.flow.data.search.SearchSuggestionsRepository
import io.github.aedev.flow.data.shorts.ShortsContentFilter
import io.github.aedev.flow.data.shorts.queue.ShortsQueueHandoff
import io.github.aedev.flow.innertube.pages.search.SearchSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val suggestions: SearchSuggestionsRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private fun viewModel() = SearchViewModel(context, suggestions, ShortsContentFilter(flowOf(true)), ShortsQueueHandoff())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial ui state has an empty query and no narrowing`() {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.query).isEmpty()
        assertThat(viewModel.uiState.value.filters).isEqualTo(SearchFilter.DEFAULT)
        assertThat(viewModel.uiState.value.filters.isDefault).isTrue()
    }

    @Test
    fun `search with valid query updates uiState`() {
        val viewModel = viewModel()
        viewModel.search("Kotlin Compose")

        assertThat(viewModel.uiState.value.query).isEqualTo("Kotlin Compose")
    }

    @Test
    fun `search with empty query resets uiState`() {
        val viewModel = viewModel()
        viewModel.search("Kotlin")
        viewModel.search("")

        assertThat(viewModel.uiState.value.query).isEmpty()
        assertThat(viewModel.uiState.value.filters).isEqualTo(SearchFilter.DEFAULT)
    }

    @Test
    fun `updateFilters updates filters in uiState when query is active`() {
        val viewModel = viewModel()
        viewModel.search("Music")

        val filter = SearchFilter(contentType = ContentType.VIDEOS)
        viewModel.updateFilters(filter)

        assertThat(viewModel.uiState.value.filters).isEqualTo(filter)
    }

    @Test
    fun `clearSearch resets search query and filters`() {
        val viewModel = viewModel()
        viewModel.search("Android", SearchFilter(contentType = ContentType.PLAYLISTS))

        viewModel.clearSearch()

        assertThat(viewModel.uiState.value.query).isEmpty()
        assertThat(viewModel.uiState.value.filters).isEqualTo(SearchFilter.DEFAULT)
    }

    @Test
    fun `a filter change keeps the query it was applied to`() {
        val viewModel = viewModel()
        viewModel.search("bodybuilding")

        viewModel.updateFilters(SearchFilter(sortType = SortType.VIEW_COUNT))

        assertThat(viewModel.uiState.value.query).isEqualTo("bodybuilding")
        assertThat(viewModel.uiState.value.filters.sortType).isEqualTo(SortType.VIEW_COUNT)
    }

    @Test
    fun `counts the active narrowing choices for the filter button`() {
        val filter =
            SearchFilter(
                contentType = ContentType.VIDEOS,
                sortType = SortType.VIEW_COUNT,
                features = setOf(io.github.aedev.flow.data.local.SearchFeature.FOUR_K),
            )

        assertThat(filter.activeCount).isEqualTo(3)
        assertThat(filter.isDefault).isFalse()
    }

    @Test
    fun `suggestions come from the one suggestions repository`() =
        runTest {
            val expected = listOf(SearchSuggestion("kotlin tutorial"), SearchSuggestion("kotlin android"))
            coEvery { suggestions.suggestions("kotlin") } returns expected

            val result = viewModel().getSearchSuggestions("kotlin")

            assertThat(result).isEqualTo(expected)
            coVerify(exactly = 1) { suggestions.suggestions("kotlin") }
        }

    @Test
    fun `a suggestions failure leaves the field usable`() =
        runTest {
            coEvery { suggestions.suggestions(any()) } throws IllegalStateException("offline")

            assertThat(viewModel().getSearchSuggestions("kotlin")).isEmpty()
        }
}
