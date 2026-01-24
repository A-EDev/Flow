package com.flow.youtube.ui.screens.home

import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.recommendation.RecommendationRepository
import com.flow.youtube.data.recommendation.RecommendationWorker
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.data.shorts.ShortsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val repository: YouTubeRepository = mockk()
    private val recommendationRepository: RecommendationRepository = mockk()
    private val subscriptionRepository: SubscriptionRepository = mockk()
    private val shortsRepository: ShortsRepository = mockk()
    private val playerPreferences: PlayerPreferences = mockk()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockkObject(FlowNeuroEngine)
        mockkObject(RecommendationWorker)
        
        // Mock default behaviors
        coEvery { FlowNeuroEngine.generateDiscoveryQueries() } returns listOf("test")
        coEvery { FlowNeuroEngine.rank(any(), any(), any()) } answers { it.invocation.args[0] as List<Video> }
        every { RecommendationWorker.schedulePeriodicRefresh(any()) } just Runs
        
        coEvery { shortsRepository.getHomeFeedShorts() } returns emptyList()
        coEvery { subscriptionRepository.getAllSubscriptionIds() } returns emptySet()
        every { playerPreferences.trendingRegion } returns flowOf("US")
        
        // Mock repository trending call to return empty by default
        coEvery { repository.getTrendingVideos(any(), any()) } returns Pair(emptyList(), null)
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
        isShort: Boolean = false,
        isLive: Boolean = false
    ) = Video(
        id = id,
        title = title,
        channelName = "Channel",
        channelId = "channelId",
        thumbnailUrl = "thumbnail",
        duration = duration,
        viewCount = 1000L,
        uploadDate = "1 day ago",
        isShort = isShort,
        isLive = isLive,
        likeCount = 0
    )

    @Test
    fun `initial state is loading`() = runTest {
        // We avoid init in Before to test initial state if possible, 
        // but HomeViewModel inits in init block. Let's mock a delay.
        coEvery { repository.getTrendingVideos(any(), any()) } coAnswers {
            testDispatcher.scheduler.advanceTimeBy(1000)
            Pair(emptyList(), null)
        }
        
        viewModel = HomeViewModel(repository, recommendationRepository, subscriptionRepository, shortsRepository, playerPreferences)
        
        assertThat(viewModel.uiState.value.isLoading).isTrue()
    }

    @Test
    fun `loadFlowFeed updates uiState with videos`() = runTest {
        val mockVideos = listOf(
            createVideo(id = "1", title = "Test 1", duration = 100),
            createVideo(id = "2", title = "Test 2", duration = 200)
        )
        
        // Mock fallback trending since FlowNeuroEngine might not be easily mockable here without static mocking
        coEvery { repository.getTrendingVideos(any(), any()) } returns Pair(mockVideos, null)
        
        viewModel = HomeViewModel(repository, recommendationRepository, subscriptionRepository, shortsRepository, playerPreferences)
        
        // Advance time to allow internal coroutines to finish
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.videos).containsExactlyElementsIn(mockVideos)
        assertThat(state.error).isNull()
    }

    @Test
    fun `updateVideosAndShorts filters out short videos correctly`() = runTest {
        viewModel = HomeViewModel(repository, recommendationRepository, subscriptionRepository, shortsRepository, playerPreferences)
        
        val mixedVideos = listOf(
            createVideo(id = "reg", title = "Regular", duration = 300),
            createVideo(id = "short1", title = "Short 1", duration = 30), // Short by duration
            createVideo(id = "short2", title = "Short 2", duration = 60, isShort = true), // Explicit short
            createVideo(id = "live", title = "Live", duration = 0, isLive = true) // Live should stay
        )
        
        // Access private updateVideosAndShorts via reflection
        val method = HomeViewModel::class.java.getDeclaredMethod("updateVideosAndShorts", List::class.java, Boolean::class.java)
        method.isAccessible = true
        method.invoke(viewModel, mixedVideos, false)
        
        val state = viewModel.uiState.value
        assertThat(state.videos.map { it.id }).containsExactly("reg", "live")
        assertThat(state.shorts.map { it.id }).containsExactly("short1", "short2")
    }
}
