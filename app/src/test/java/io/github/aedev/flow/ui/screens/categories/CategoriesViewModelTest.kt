package io.github.aedev.flow.ui.screens.categories

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.innertube.pages.explore.ExploreDestination
import io.github.aedev.flow.innertube.pages.explore.ExploreSectionKind
import io.mockk.every
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
class CategoriesViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val preferences: PlayerPreferences =
        mockk(relaxed = true) {
            every { categoriesIsListView } returns flowOf(false)
            every { trendingRegion } returns flowOf("US")
        }

    private fun viewModel() = CategoriesViewModel(preferences, context)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    /**
     * The first tab is already the default in state, so routing the first load through `select`
     * short-circuited on its own guard and the screen stayed empty until a tab was switched.
     */
    @Test
    fun `the first tab starts loading without waiting for a tab switch`() =
        runTest(testDispatcher) {
            val state = viewModel().uiState.value

            assertThat(state.selected).isEqualTo(CATEGORY_TABS.first().destination)
            assertThat(state.isLoading).isTrue()
        }

    @Test
    fun `re-tapping the selected tab does not reload it`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            viewModel.select(ExploreDestination.GAMING)
            val afterSwitch = viewModel.uiState.value

            viewModel.select(ExploreDestination.GAMING)

            assertThat(viewModel.uiState.value).isEqualTo(afterSwitch)
        }

    @Test
    fun `switching tab adopts that destination's section kind and clears the last one's content`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()

            viewModel.select(ExploreDestination.MUSIC)

            val state = viewModel.uiState.value
            assertThat(state.selected).isEqualTo(ExploreDestination.MUSIC)
            assertThat(state.sectionKind).isEqualTo(ExploreSectionKind.CHART)
            assertThat(state.shelves).isEmpty()
            assertThat(state.subTabs).isEmpty()
        }

    @Test
    fun `a shelf with no see-all cannot be opened`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()

            viewModel.openShelf(shelf(moreParams = null))

            assertThat(viewModel.uiState.value.openShelfTitle).isNull()
        }

    @Test
    fun `opening a shelf switches to the paged grid and titles it`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()

            viewModel.openShelf(shelf(moreParams = "EgdsaXZldGFikgEDCKEK"))

            val state = viewModel.uiState.value
            assertThat(state.openShelfTitle).isEqualTo("Live Now")
            assertThat(state.sectionKind).isEqualTo(ExploreSectionKind.GRID)
        }

    @Test
    fun `backing out of a shelf returns to the destination's shelves`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            viewModel.openShelf(shelf(moreParams = "EgdsaXZldGFikgEDCKEK"))

            viewModel.closeShelf()

            val state = viewModel.uiState.value
            assertThat(state.openShelfTitle).isNull()
            assertThat(state.sectionKind).isEqualTo(ExploreSectionKind.SHELVES)
        }

    @Test
    fun `toggling the view mode flips it and persists it`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            val before = viewModel.uiState.value.isListView

            viewModel.toggleViewMode()

            assertThat(viewModel.uiState.value.isListView).isEqualTo(!before)
        }

    private fun shelf(moreParams: String?) =
        io.github.aedev.flow.innertube.pages.renderer
            .FeedShelf(id = "shelf:0:Live Now", title = "Live Now", moreParams = moreParams)
}
