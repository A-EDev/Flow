package com.flow.youtube.ui.screens.search

import com.flow.youtube.data.local.Duration
import com.flow.youtube.data.local.SearchFilter
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.model.SearchResult
import com.flow.youtube.data.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: YouTubeRepository = mockk()
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SearchViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createVideo(
        id: String,
        title: String = "Title $id",
        duration: Int = 100,
        viewCount: Long = 1000L
    ) = Video(
        id = id,
        title = title,
        channelName = "Channel",
        channelId = "channelId",
        thumbnailUrl = "thumbnail",
        duration = duration,
        viewCount = viewCount,
        uploadDate = "1 day ago",
        likeCount = 0
    )

    @Test
    fun `initial state is correct`() {
        val state = viewModel.uiState.value
        assertThat(state.videos).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.query).isEmpty()
    }

    @Test
    fun `search updates uiState with results`() = runTest {
        val query = "test query"
        val mockResult = SearchResult(
            videos = listOf(createVideo(id = "1", title = "Video 1", duration = 100)),
            channels = emptyList(),
            playlists = emptyList()
        )
        
        coEvery { repository.search(query, any(), any()) } returns mockResult
        
        viewModel.search(query)
        
        assertThat(viewModel.uiState.value.isLoading).isTrue()
        
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.videos).containsExactlyElementsIn(mockResult.videos)
        assertThat(state.query).isEqualTo(query)
    }

    @Test
    fun `applyFilters correctly filters videos by duration`() = runTest {
        val videos = listOf(
            createVideo(id = "under", duration = 100), // < 240s
            createVideo(id = "between", duration = 600), // 240-1200s
            createVideo(id = "over", duration = 1500) // > 1200s
        )
        
        val filters = SearchFilter(duration = Duration.UNDER_4_MINUTES)
        
        // Mock repository to return all videos, then filter in VM
        coEvery { repository.search(any(), any(), any()) } returns SearchResult(videos = videos)
        
        viewModel.search("test", filters)
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.videos.map { it.id }).containsExactly("under")
    }

    @Test
    fun `clearSearch resets uiState`() = runTest {
        coEvery { repository.search(any(), any(), any()) } returns SearchResult(videos = listOf(createVideo(id = "1")))
        
        viewModel.search("test")
        advanceUntilIdle()
        
        viewModel.clearSearch()
        
        val state = viewModel.uiState.value
        assertThat(state.videos).isEmpty()
        assertThat(state.query).isEmpty()
        assertThat(state.isLoading).isFalse()
    }
}
