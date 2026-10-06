package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.core.SabrEvent
import io.github.aedev.flow.player.sabr.core.SabrSegment
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.integration.SabrOrchestrator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SabrOrchestratorCapacityTest {
    @Test
    fun `capacity failure reports once stops fetching and drains accepted bytes`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val events = MutableSharedFlow<SabrEvent>()
            val controller = mockk<SabrStreamController>(relaxed = true)
            every { controller.events } returns events
            coEvery { controller.startSession() } coAnswers { awaitCancellation() }
            val orchestrator = SabrOrchestrator(controller, audioBufferLimitBytes = 2, videoBufferLimitBytes = 2)
            val errors = mutableListOf<Pair<Int, Boolean>>()
            orchestrator.onError = { code, _, recoverable, _ -> errors.add(code to recoverable) }
            try {
                orchestrator.start()
                events.emit(segment(byteArrayOf(1, 2)))
                events.emit(segment(byteArrayOf(3)))
                events.emit(segment(byteArrayOf(4)))
                assertThat(errors).containsExactly(SabrOrchestrator.BUFFER_CAPACITY_ERROR to false)
                assertThat(orchestrator.isRunning).isFalse()
                verify(exactly = 1) { controller.abort() }
                val accepted = ByteArray(2)
                assertThat(orchestrator.audioBuffer.read(accepted, 0, 2)).isEqualTo(2)
                assertThat(accepted).isEqualTo(byteArrayOf(1, 2))
                assertThat(orchestrator.audioBuffer.read(accepted, 0, 2)).isEqualTo(-1)
            } finally {
                orchestrator.release()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `an audio overflow reports the audio itag, not the video itag`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val events = MutableSharedFlow<SabrEvent>()
            val session = mockk<SabrSessionState>(relaxed = true)
            every { session.selectedAudioItag } returns 140
            every { session.selectedVideoItag } returns 137
            val controller = mockk<SabrStreamController>(relaxed = true)
            every { controller.events } returns events
            every { controller.sessionState } returns session
            coEvery { controller.startSession() } coAnswers { awaitCancellation() }
            val orchestrator = SabrOrchestrator(controller, audioBufferLimitBytes = 2, videoBufferLimitBytes = 4096)
            val reported = mutableListOf<Int?>()
            orchestrator.onError = { _, _, _, itag -> reported.add(itag) }
            try {
                orchestrator.start()
                events.emit(segment(byteArrayOf(1, 2)))
                events.emit(segment(byteArrayOf(3)))
                assertThat(reported).containsExactly(140)
            } finally {
                orchestrator.release()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `a video overflow reports the video itag`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val events = MutableSharedFlow<SabrEvent>()
            val session = mockk<SabrSessionState>(relaxed = true)
            every { session.selectedAudioItag } returns 140
            every { session.selectedVideoItag } returns 137
            val controller = mockk<SabrStreamController>(relaxed = true)
            every { controller.events } returns events
            every { controller.sessionState } returns session
            coEvery { controller.startSession() } coAnswers { awaitCancellation() }
            val orchestrator = SabrOrchestrator(controller, audioBufferLimitBytes = 4096, videoBufferLimitBytes = 2)
            val reported = mutableListOf<Int?>()
            orchestrator.onError = { _, _, _, itag -> reported.add(itag) }
            try {
                orchestrator.start()
                events.emit(videoSegment(byteArrayOf(1, 2)))
                events.emit(videoSegment(byteArrayOf(3)))
                assertThat(reported).containsExactly(137)
            } finally {
                orchestrator.release()
                Dispatchers.resetMain()
            }
        }

    private fun segment(bytes: ByteArray) =
        SabrEvent.SegmentReady(
            SabrSegment(1, 140, "video", true, 0, 1_000, 1, bytes),
        )

    private fun videoSegment(bytes: ByteArray) =
        SabrEvent.SegmentReady(
            SabrSegment(1, 137, "video", false, 0, 1_000, 1, bytes),
        )
}
