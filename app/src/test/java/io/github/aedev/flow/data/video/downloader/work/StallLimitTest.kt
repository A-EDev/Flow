package io.github.aedev.flow.data.video.downloader.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StallLimitTest {
    private val limit = StallLimit(ticks = 3) {}

    @Test
    fun `a byte count that keeps moving never stalls`() {
        (1L..100L).forEach { assertFalse(limit.stillAt(it * 1_000)) }
    }

    @Test
    fun `a byte count stuck for the limit stalls`() {
        assertFalse(limit.stillAt(500))
        assertFalse(limit.stillAt(500))
        assertFalse(limit.stillAt(500))
        assertTrue(limit.stillAt(500))
    }

    @Test
    fun `one new byte starts the count again`() {
        repeat(3) { limit.stillAt(500) }
        assertFalse(limit.stillAt(501))
        assertFalse(limit.stillAt(501))
        assertFalse(limit.stillAt(501))
        assertTrue(limit.stillAt(501))
    }

    @Test
    fun `a transfer that never starts stalls too`() {
        assertFalse(limit.stillAt(0))
        repeat(2) { assertFalse(limit.stillAt(0)) }
        assertTrue(limit.stillAt(0))
    }
}
