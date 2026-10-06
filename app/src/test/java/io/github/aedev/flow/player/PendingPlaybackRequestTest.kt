package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PendingPlaybackRequestTest {
    @Test
    fun `only the latest queued seek is executed`() =
        runTest {
            val requests = PendingPlaybackRequest(this)
            val positions = mutableListOf<Long>()
            requests.replace { positions.add(10_000L) }
            requests.replace { positions.add(20_000L) }
            requests.replace { positions.add(30_000L) }
            runCurrent()
            assertThat(positions).containsExactly(30_000L)
        }

    @Test
    fun `closing or changing the video cancels a pending seek`() =
        runTest {
            val requests = PendingPlaybackRequest(this)
            var rebuilt = false
            requests.replace { rebuilt = true }
            requests.cancel()
            runCurrent()
            assertThat(rebuilt).isFalse()
        }

    @Test
    fun `replacement cancels a suspended old request`() =
        runTest {
            val requests = PendingPlaybackRequest(this)
            var oldCancelled = false
            requests.replace {
                try {
                    awaitCancellation()
                } finally {
                    oldCancelled = true
                }
            }
            runCurrent()
            requests.replace {}
            runCurrent()
            assertThat(oldCancelled).isTrue()
        }
}
