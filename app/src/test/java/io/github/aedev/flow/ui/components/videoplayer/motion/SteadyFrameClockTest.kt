package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.runtime.BroadcastFrameClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SteadyFrameClockTest {
    private val clock = BroadcastFrameClock()
    private val steady = SteadyFrameClock(clock, maxStepNanos = 24_000_000L)

    private suspend fun kotlinx.coroutines.test.TestScope.frameAt(nanos: Long): Long {
        val playTime = async { steady.withFrameNanos { it } }
        runCurrent()
        clock.sendFrame(nanos)
        runCurrent()
        return playTime.await()
    }

    @Test
    fun `play time starts at zero and follows ordinary frames`() =
        runTest {
            assertThat(frameAt(1_000_000_000L)).isEqualTo(0L)
            assertThat(frameAt(1_016_000_000L)).isEqualTo(16_000_000L)
            assertThat(frameAt(1_032_000_000L)).isEqualTo(32_000_000L)
        }

    @Test
    fun `a long frame advances play time by at most one step`() =
        runTest {
            frameAt(1_000_000_000L)
            frameAt(1_016_000_000L)

            assertThat(frameAt(1_216_000_000L)).isEqualTo(40_000_000L)
            assertThat(frameAt(1_232_000_000L)).isEqualTo(56_000_000L)
        }

    @Test
    fun `animations sharing the clock see one play time per frame`() =
        runTest {
            frameAt(1_000_000_000L)
            val first = async { steady.withFrameNanos { it } }
            val second = async { steady.withFrameNanos { it } }
            runCurrent()
            clock.sendFrame(1_100_000_000L)
            runCurrent()

            assertThat(first.await()).isEqualTo(24_000_000L)
            assertThat(second.await()).isEqualTo(24_000_000L)
        }
}
