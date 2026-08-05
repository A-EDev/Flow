package io.github.aedev.flow.data.local

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class BufferDurationsTest {
    @Test
    fun `heap cap below a stored resume threshold still yields durations the load control accepts`() {
        // Issue #876: a Pixel-class heap caps the min buffer at 8s while the stored resume threshold is 10s,
        // which is reachable from the Custom rebuffer slider.
        val result = sanitizeOnLowHeapDevice(minMs = 30_000, maxMs = 50_000, playbackMs = 2_500, rebufferMs = 10_000)

        assertEquals(LOW_HEAP_MIN_CAP_MS, result.minMs)
        assertEquals(LOW_HEAP_MIN_CAP_MS, result.rebufferMs)
        assertEquals(LOW_HEAP_MAX_CAP_MS, result.maxMs)
        assertEquals(2_500, result.playbackMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the load control still rejects the combination that crashed on startup`() {
        // Keeps the checks above from passing vacuously: this is what the shipped build handed Media3.
        DefaultLoadControl.Builder().setBufferDurationsMs(LOW_HEAP_MIN_CAP_MS, LOW_HEAP_MAX_CAP_MS, 2_500, 10_000)
    }

    @Test
    fun `custom slider extremes cannot produce a rejected combination`() {
        // The Custom sliders write each duration independently: min bottoms out at 1s while playback and
        // rebuffer reach 5s and 10s.
        val result = sanitizeOnLowHeapDevice(minMs = 1_000, maxMs = 30_000, playbackMs = 5_000, rebufferMs = 10_000)

        assertAcceptedByLoadControl(result)
    }

    @Test
    fun `durations that already satisfy the contract are left alone`() {
        val result =
            BufferDurations.sanitize(
                minMs = 15_000,
                maxMs = 40_000,
                playbackMs = 2_500,
                rebufferMs = 5_000,
                maxSafeMinMs = 20_000,
                maxSafeMaxMs = 45_000,
            )

        assertEquals(
            BufferDurations(minMs = 15_000, maxMs = 40_000, playbackMs = 2_500, rebufferMs = 5_000),
            result,
        )
    }

    @Test
    fun `a stored max buffer below the min buffer is given loading headroom`() {
        val result = sanitizeOnLowHeapDevice(minMs = 8_000, maxMs = 3_000, playbackMs = 1_000, rebufferMs = 2_000)

        assertEquals(LOW_HEAP_MIN_CAP_MS + BufferDurations.MAX_HEADROOM_MS, result.maxMs)
        assertAcceptedByLoadControl(result)
    }

    @Test
    fun `corrupt durations fall back to the floors instead of throwing`() {
        val result = sanitizeOnLowHeapDevice(minMs = -1, maxMs = 0, playbackMs = -5_000, rebufferMs = Int.MIN_VALUE)

        assertEquals(BufferDurations.MIN_FLOOR_MS, result.minMs)
        assertEquals(BufferDurations.PLAYBACK_FLOOR_MS, result.playbackMs)
        assertEquals(BufferDurations.REBUFFER_FLOOR_MS, result.rebufferMs)
        assertAcceptedByLoadControl(result)
    }

    @Test
    fun `caps smaller than the floors do not invert any range`() {
        val result =
            BufferDurations.sanitize(
                minMs = 30_000,
                maxMs = 50_000,
                playbackMs = 2_500,
                rebufferMs = 5_000,
                maxSafeMinMs = 0,
                maxSafeMaxMs = 0,
            )

        assertEquals(BufferDurations.MIN_FLOOR_MS, result.minMs)
        assertAcceptedByLoadControl(result)
    }

    @Test
    fun `every combination of realistic stored values survives every heap cap`() {
        val stored = listOf(-1, 0, 1_000, 2_500, 10_000, 30_000, 180_000, Int.MAX_VALUE)
        val caps = listOf(0, BufferDurations.MIN_FLOOR_MS, LOW_HEAP_MIN_CAP_MS, 20_000)

        for (min in stored) {
            for (rebuffer in stored) {
                for (playback in stored) {
                    for (cap in caps) {
                        assertAcceptedByLoadControl(
                            BufferDurations.sanitize(
                                minMs = min,
                                maxMs = stored.first(),
                                playbackMs = playback,
                                rebufferMs = rebuffer,
                                maxSafeMinMs = cap,
                                maxSafeMaxMs = cap,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun sanitizeOnLowHeapDevice(
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
    ): BufferDurations =
        BufferDurations.sanitize(
            minMs = minMs,
            maxMs = maxMs,
            playbackMs = playbackMs,
            rebufferMs = rebufferMs,
            maxSafeMinMs = LOW_HEAP_MIN_CAP_MS,
            maxSafeMaxMs = LOW_HEAP_MAX_CAP_MS,
        )

    /** Pins the assertions Media3 itself makes, so the invariant cannot drift from the one that crashes. */
    private fun assertAcceptedByLoadControl(durations: BufferDurations) {
        DefaultLoadControl.Builder().setBufferDurationsMs(
            durations.minMs,
            durations.maxMs,
            durations.playbackMs,
            durations.rebufferMs,
        )
        assertTrue("durations must stay positive: $durations", durations.playbackMs > 0)
    }

    private companion object {
        /** Mirrors PlayerConfig's low-heap budgets, which PlayerFactory passes in on Pixel-class devices. */
        const val LOW_HEAP_MIN_CAP_MS = 8_000
        const val LOW_HEAP_MAX_CAP_MS = 18_000
    }
}
