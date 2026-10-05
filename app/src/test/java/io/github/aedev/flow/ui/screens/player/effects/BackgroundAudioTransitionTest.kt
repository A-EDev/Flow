package io.github.aedev.flow.ui.screens.player.effects

import android.app.Activity
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackgroundAudioTransitionTest {
    private val activity = mockk<Activity>(relaxed = true)
    private val viewModel = mockk<VideoPlayerViewModel>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(PictureInPictureHelper)
        every { PictureInPictureHelper.updatePipParams(any(), any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(PictureInPictureHelper)
    }

    @Test
    fun `background playback is armed before dismissing the pip task`() {
        BackgroundAudioTransition.enterBackgroundAudio(activity, viewModel)

        verifyOrder {
            viewModel.startBackgroundPlayback()
            PictureInPictureHelper.updatePipParams(activity, autoEnterEnabled = false)
            activity.moveTaskToBack(true)
        }
    }

    @Test
    fun `failed task dismissal leaves background playback armed`() {
        every { activity.moveTaskToBack(true) } throws IllegalStateException("Task unavailable")

        BackgroundAudioTransition.enterBackgroundAudio(activity, viewModel)

        verifyOrder {
            viewModel.startBackgroundPlayback()
            PictureInPictureHelper.updatePipParams(activity, autoEnterEnabled = false)
            activity.moveTaskToBack(true)
        }
    }
}
