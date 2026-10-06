package io.github.aedev.flow.player.sabr.integration

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SabrRequestWakeupTest {
    @Test
    fun `paused playback waits for the heartbeat instead of active polling`() =
        runTest {
            val wakeup = SabrRequestWakeup()
            wakeup.setPlaybackRequested(false)
            val waiting = async { wakeup.awaitNext(250, 8_000) }
            runCurrent()
            advanceTimeBy(7_999)
            runCurrent()
            assertThat(waiting.isCompleted).isFalse()
            advanceTimeBy(1)
            runCurrent()
            assertThat(waiting.isCompleted).isTrue()
        }

    @Test
    fun `resuming wakes a paused request without waiting for the heartbeat`() =
        runTest {
            val wakeup = SabrRequestWakeup()
            wakeup.setPlaybackRequested(false)
            val waiting = async { wakeup.awaitNext(250, 8_000) }
            runCurrent()
            advanceTimeBy(500)
            wakeup.setPlaybackRequested(true)
            runCurrent()
            assertThat(waiting.isCompleted).isTrue()
        }

    @Test
    fun `active playback retains its request checking interval`() =
        runTest {
            val waiting = async { SabrRequestWakeup().awaitNext(250, 8_000) }
            runCurrent()
            advanceTimeBy(249)
            runCurrent()
            assertThat(waiting.isCompleted).isFalse()
            advanceTimeBy(1)
            runCurrent()
            assertThat(waiting.isCompleted).isTrue()
        }
}
