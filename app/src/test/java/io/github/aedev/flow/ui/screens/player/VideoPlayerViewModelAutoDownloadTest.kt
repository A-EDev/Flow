package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.video.AutoDownloadResolution
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModelHarness.Companion.video
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The player tells the auto-download trigger who opened what, which load is theirs, and how it resolved. */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelAutoDownloadTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: VideoPlayerViewModelHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        harness = VideoPlayerViewModelHarness(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        harness.close()
    }

    private fun TestScope.newViewModel(): VideoPlayerViewModel {
        val viewModel = harness.createViewModel()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `an opened video arms the trigger before its load starts and reports how it resolved`() =
        runTest {
            val viewModel = newViewModel()
            val video = video("vid_a")

            viewModel.playVideo(video)
            advanceUntilIdle()

            verifyOrder {
                harness.autoDownload.onOpened(video, true)
                harness.autoDownload.onLoadStarted("vid_a", any())
                harness.autoDownload.onResolved("vid_a", any(), AutoDownloadResolution.FAILED, any())
            }
            verify(exactly = 1) { harness.autoDownload.onResolved(any(), any(), any(), any()) }
        }

    @Test
    fun `a video that follows another is passed on as not opened`() =
        runTest {
            val viewModel = newViewModel()
            val next = video("vid_b")

            viewModel.playVideo(next, userOpened = false)
            advanceUntilIdle()

            verify(exactly = 1) { harness.autoDownload.onOpened(next, false) }
        }

    @Test
    fun `a device file never reaches the trigger`() =
        runTest {
            val viewModel = newViewModel()

            viewModel.playLocalVideo(video("local_a"), "content://media/external/video/media/1")
            advanceUntilIdle()

            verify(exactly = 0) { harness.autoDownload.onOpened(any(), any()) }
            verify(exactly = 0) { harness.autoDownload.onLoadStarted(any(), any()) }
        }
}
