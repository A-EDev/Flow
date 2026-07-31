package io.github.aedev.flow.ui.screens.likedvideos

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
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
class LikedVideosViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: LikedVideosRepository = mockk(relaxed = true)

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
    fun `initial state loads liked videos from repository`() =
        runTest {
            val sampleLikes =
                listOf(
                    LikedVideoInfo(
                        videoId = "vid_1",
                        title = "Sample Video",
                        thumbnail = "https://example.com/thumb.jpg",
                        channelName = "Sample Channel",
                        likedAt = 1000L,
                        isMusic = false,
                    ),
                )
            coEvery { repository.getAllLikedVideos() } returns flowOf(sampleLikes)

            val viewModel = LikedVideosViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.likedVideos).isEqualTo(sampleLikes)
        }

    @Test
    fun `removeLike delegates to repository to remove video`() =
        runTest {
            coEvery { repository.getAllLikedVideos() } returns flowOf(emptyList())
            coEvery { repository.removeLikeState("vid_1") } returns Unit

            val viewModel = LikedVideosViewModel(repository)
            viewModel.removeLike("vid_1")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { repository.removeLikeState("vid_1") }
        }
}
