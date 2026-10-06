package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.core.SabrRequestBuilder
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.proto.FormatBufferedRange
import io.github.aedev.flow.player.sabr.proto.FormatId
import io.github.aedev.flow.player.sabr.proto.SabrContextUpdate
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SabrSessionConcurrencyTest {
    @Test
    fun `buffer snapshots survive a seek without changing`() {
        val state = SabrSessionState()
        val range = FormatBufferedRange(formatId = FormatId(140, 1), durationMs = 1000)
        state.addBufferedRange(true, range)
        val snapshot = state.audioBufferedRanges

        state.seekTo(5000)

        assertThat(snapshot).containsExactly(range)
        assertThat(state.audioBufferedRanges).isEmpty()
    }

    @Test
    fun `requests and seeks tolerate concurrent collection updates`() {
        val state = SabrSessionState()
        val executor = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        try {
            val writes =
                executor.submit {
                    start.await()
                    repeat(1000) {
                        state.updateFromContextUpdate(SabrContextUpdate(type = it, value = byteArrayOf(1), sendByDefault = true))
                        state.addBufferedRange(true, FormatBufferedRange(formatId = FormatId(140, 1), startSequence = it, endSequence = it))
                    }
                }
            val seeks =
                executor.submit {
                    start.await()
                    repeat(1000) { state.seekTo(it.toLong()) }
                }
            val reads =
                executor.submit {
                    start.await()
                    repeat(1000) {
                        SabrRequestBuilder.buildFollowUpRequest(state)
                        state.audioBufferedRanges.forEach { range -> assertThat(range.durationMs).isAtLeast(0) }
                    }
                }
            start.countDown()
            listOf(writes, seeks, reads).forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
}
