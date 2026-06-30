package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.identity.Hlc
import io.github.aedev.flow.sync.identity.HlcClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlcTest {

    @Test
    fun encode_decode_roundtrip() {
        val hlc = Hlc(1781512000123L, 7, "a1b2c3d4")
        assertEquals("1781512000123:7:a1b2c3d4", hlc.encode())
        assertEquals(hlc, Hlc.decode(hlc.encode()))
    }

    @Test
    fun decode_malformed_is_zero() {
        assertEquals(Hlc.ZERO, Hlc.decode(null))
        assertEquals(Hlc.ZERO, Hlc.decode(""))
        assertEquals(Hlc.ZERO, Hlc.decode("garbage"))
        assertEquals(Hlc.ZERO, Hlc.decode("123")) // missing fields
    }

    @Test
    fun total_order_physical_then_counter_then_node() {
        assertTrue(Hlc(1, 0, "z") < Hlc(2, 0, "a"))      // physical dominates
        assertTrue(Hlc(5, 1, "z") < Hlc(5, 2, "a"))      // then counter
        assertTrue(Hlc(5, 2, "a") < Hlc(5, 2, "b"))      // then node tiebreak
        assertEquals(0, Hlc(5, 2, "a").compareTo(Hlc(5, 2, "a")))
    }

    @Test
    fun clock_now_is_monotonic_even_with_frozen_physical_time() {
        var fakeTime = 1000L
        val clock = HlcClock(node = "nodeabcd", physicalNow = { fakeTime })
        val a = clock.now()
        val b = clock.now()
        val c = clock.now()
        assertTrue(a < b)
        assertTrue(b < c)
        assertEquals(1000L, a.physicalMs)
        assertEquals(0, a.counter)
        assertEquals(1, b.counter)
        assertEquals(2, c.counter)

        fakeTime = 2000L
        val d = clock.now()
        assertEquals(2000L, d.physicalMs)
        assertEquals(0, d.counter)
        assertTrue(c < d)
    }

    @Test
    fun clock_update_advances_past_remote() {
        var fakeTime = 500L
        val clock = HlcClock(node = "localnod", physicalNow = { fakeTime })
        val remote = Hlc(10_000L, 4, "remoteno")
        val merged = clock.update(remote)
        assertTrue("must be > remote", merged > remote)
        assertEquals(10_000L, merged.physicalMs)
        assertEquals(5, merged.counter)

        val next = clock.now()
        assertTrue(next > merged)
    }
}
