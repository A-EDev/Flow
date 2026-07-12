package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.integration.SabrSegmentBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SabrSegmentBufferTest {
    @Test
    fun nonEmptyReadWaitsForMediaInsteadOfReturningZero() {
        val buffer = SabrSegmentBuffer()
        val target = ByteArray(3)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val read = executor.submit<Int> { buffer.read(target, 0, target.size) }

            Thread.sleep(100)
            buffer.appendSegment(byteArrayOf(4, 5, 6))

            assertEquals(3, read.get(2, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(4, 5, 6), target)
        } finally {
            buffer.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun endOfStreamUnblocksWaitingRead() {
        val buffer = SabrSegmentBuffer()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val read = executor.submit<Int> { buffer.read(ByteArray(1), 0, 1) }

            buffer.signalEndOfStream()

            assertEquals(-1, read.get(2, TimeUnit.SECONDS))
        } finally {
            buffer.close()
            executor.shutdownNow()
        }
    }
}
