package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.integration.SabrSegmentBuffer
import io.github.aedev.flow.player.sabr.integration.SegmentAppendResult
import io.github.aedev.flow.player.sabr.integration.positiveBandwidthEstimate
import io.github.aedev.flow.player.sabr.integration.shouldPauseFollowUpRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
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

    @Test
    fun partialReadReturnsDuringANetworkGap() {
        val buffer = SabrSegmentBuffer()
        val executor = Executors.newSingleThreadExecutor()
        try {
            buffer.appendSegment(byteArrayOf(1, 2))
            val target = ByteArray(4)
            val read = executor.submit<Int> { buffer.read(target, 0, target.size) }

            assertEquals(2, read.get(1, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(1, 2, 0, 0), target)
            buffer.appendSegment(byteArrayOf(3, 4))
            assertEquals(2, buffer.read(target, 2, 2))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), target)
        } finally {
            buffer.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun byteBudgetIncludesTheEntireCurrentSegmentUntilItIsConsumed() {
        val buffer = SabrSegmentBuffer(maxBufferedBytes = 6)
        val target = ByteArray(2)

        assertEquals(SegmentAppendResult.ACCEPTED, buffer.appendSegment(byteArrayOf(1, 2, 3, 4)))
        assertEquals(4L, buffer.bufferedBytes)
        assertEquals(2, buffer.read(target, 0, target.size))
        assertEquals(4L, buffer.bufferedBytes)
        assertEquals(SegmentAppendResult.ACCEPTED, buffer.appendSegment(byteArrayOf(5, 6)))
        assertEquals(SegmentAppendResult.CAPACITY_EXCEEDED, buffer.appendSegment(byteArrayOf(7)))
        assertEquals(6L, buffer.bufferedBytes)

        assertEquals(2, buffer.read(target, 0, target.size))
        assertEquals(2L, buffer.bufferedBytes)
        assertEquals(2, buffer.read(target, 0, target.size))
        assertEquals(0L, buffer.bufferedBytes)
        buffer.signalEndOfStream()
        assertEquals(SegmentAppendResult.CLOSED, buffer.appendSegment(byteArrayOf(8)))
        buffer.close()
    }

    @Test
    fun appendRejectsAtCapacityWithoutWaitingForAReader() {
        val buffer = SabrSegmentBuffer(maxBufferedBytes = 2)
        assertEquals(SegmentAppendResult.ACCEPTED, buffer.appendSegment(byteArrayOf(1, 2)))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val append = executor.submit<SegmentAppendResult> { buffer.appendSegment(byteArrayOf(3)) }
            assertEquals(SegmentAppendResult.CAPACITY_EXCEEDED, append.get(1, TimeUnit.SECONDS))
        } finally {
            buffer.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun closeAndResetWakeBlockedReads() {
        val buffer = SabrSegmentBuffer()
        val executor = Executors.newSingleThreadExecutor()
        try {
            buffer.reset()
            val readStarted = CountDownLatch(1)
            val resetTarget = ByteArray(1)
            val resetRead =
                executor.submit<Int> {
                    readStarted.countDown()
                    buffer.read(resetTarget, 0, 1)
                }
            assertEquals(true, readStarted.await(1, TimeUnit.SECONDS))
            buffer.reset()
            assertEquals(SegmentAppendResult.ACCEPTED, buffer.appendSegment(byteArrayOf(9)))
            val resetReadResult = resetRead.get(2, TimeUnit.SECONDS)
            if (resetReadResult == -1) {
                assertEquals(1, buffer.read(resetTarget, 0, 1))
            } else {
                assertEquals(1, resetReadResult)
            }
            assertArrayEquals(byteArrayOf(9), resetTarget)

            val closeStarted = CountDownLatch(1)
            val closeRead =
                executor.submit<Int> {
                    closeStarted.countDown()
                    buffer.read(ByteArray(1), 0, 1)
                }
            assertEquals(true, closeStarted.await(1, TimeUnit.SECONDS))
            buffer.close()
            assertEquals(-1, closeRead.get(2, TimeUnit.SECONDS))
            assertEquals(SegmentAppendResult.CLOSED, buffer.appendSegment(byteArrayOf(10)))
        } finally {
            buffer.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun interruptingAWaitingReadDoesNotPoisonLaterReads() {
        val buffer = SabrSegmentBuffer()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val readStarted = CountDownLatch(1)
            val readStopped = CountDownLatch(1)
            val blockedRead =
                executor.submit<Int> {
                    readStarted.countDown()
                    try {
                        buffer.read(ByteArray(1), 0, 1)
                    } finally {
                        readStopped.countDown()
                    }
                }
            assertEquals(true, readStarted.await(1, TimeUnit.SECONDS))
            assertEquals(true, blockedRead.cancel(true))
            assertEquals(true, readStopped.await(2, TimeUnit.SECONDS))

            assertEquals(SegmentAppendResult.ACCEPTED, buffer.appendSegment(byteArrayOf(8)))
            val target = ByteArray(1)
            assertEquals(1, buffer.read(target, 0, 1))
            assertArrayEquals(byteArrayOf(8), target)
        } finally {
            buffer.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun highWaterPacingUsesOnlyAudioBufferInAudioOnlyMode() {
        assertEquals(
            true,
            shouldPauseFollowUpRequest(
                audioOnly = true,
                audioAtHighWater = true,
                videoAtHighWater = false,
                bufferedAheadMs = 10_000,
                criticalLeadMs = 5_000,
                heartbeatDue = false,
            ),
        )
        assertEquals(
            false,
            shouldPauseFollowUpRequest(
                audioOnly = true,
                audioAtHighWater = false,
                videoAtHighWater = true,
                bufferedAheadMs = 10_000,
                criticalLeadMs = 5_000,
                heartbeatDue = false,
            ),
        )
        assertEquals(
            true,
            shouldPauseFollowUpRequest(
                audioOnly = false,
                audioAtHighWater = true,
                videoAtHighWater = true,
                bufferedAheadMs = 10_000,
                criticalLeadMs = 5_000,
                heartbeatDue = false,
            ),
        )
        assertEquals(
            false,
            shouldPauseFollowUpRequest(
                audioOnly = false,
                audioAtHighWater = false,
                videoAtHighWater = true,
                bufferedAheadMs = 4_999,
                criticalLeadMs = 5_000,
                heartbeatDue = false,
            ),
        )
        assertEquals(
            false,
            shouldPauseFollowUpRequest(
                audioOnly = false,
                audioAtHighWater = false,
                videoAtHighWater = true,
                bufferedAheadMs = 0,
                criticalLeadMs = 5_000,
                heartbeatDue = false,
            ),
        )
        assertEquals(
            false,
            shouldPauseFollowUpRequest(
                audioOnly = false,
                audioAtHighWater = false,
                videoAtHighWater = false,
                bufferedAheadMs = 10_000,
                criticalLeadMs = 5_000,
                heartbeatDue = false,
            ),
        )
        assertEquals(
            false,
            shouldPauseFollowUpRequest(
                audioOnly = false,
                audioAtHighWater = true,
                videoAtHighWater = true,
                bufferedAheadMs = 10_000,
                criticalLeadMs = 5_000,
                heartbeatDue = true,
            ),
        )
    }

    @Test
    fun oneHighTrackPausesOnlyWhenBothTrackLeadsAreHealthy() {
        for ((audioHigh, videoHigh) in listOf(true to false, false to true)) {
            assertEquals(
                true,
                shouldPauseFollowUpRequest(
                    false,
                    audioHigh,
                    videoHigh,
                    10_000,
                    5_000,
                    false,
                ),
            )
            assertEquals(
                false,
                shouldPauseFollowUpRequest(
                    false,
                    audioHigh,
                    videoHigh,
                    4_999,
                    5_000,
                    false,
                ),
            )
            assertEquals(
                false,
                shouldPauseFollowUpRequest(
                    false,
                    audioHigh,
                    videoHigh,
                    10_000,
                    5_000,
                    true,
                ),
            )
        }
    }

    @Test
    fun depletedLeadBypassesHighWaterOnBothTracks() {
        for (leadMs in listOf(0L, 4_999L)) {
            assertEquals(
                false,
                shouldPauseFollowUpRequest(
                    audioOnly = false,
                    audioAtHighWater = true,
                    videoAtHighWater = true,
                    bufferedAheadMs = leadMs,
                    criticalLeadMs = 5_000,
                    heartbeatDue = false,
                ),
            )
        }
    }

    @Test
    fun bandwidthEstimateAcceptsPositiveSamplesAndIgnoresInvalidOnes() {
        assertEquals(5_000_000L, positiveBandwidthEstimate(5_000_000L))
        assertEquals(null, positiveBandwidthEstimate(0L))
        assertEquals(null, positiveBandwidthEstimate(-1L))
        assertEquals(null, positiveBandwidthEstimate(null))
    }
}
