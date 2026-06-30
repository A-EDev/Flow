package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync.crypto.SyncCrypto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyZeroizeTest {

    @Test
    fun directional_keys_zeroize_clears_both_arrays() {
        val keys = SyncCrypto.deriveKeys(SyncCrypto.randomMasterKey(), SyncCrypto.randomSessionId())
        // Hold the same array references the keys use internally.
        val h2c = keys.hostToClient
        val c2h = keys.clientToHost
        assertFalse("precondition: keys are not already all-zero", h2c.all { it == 0.toByte() })

        keys.zeroize()

        assertTrue("hostToClient must be wiped", h2c.all { it == 0.toByte() })
        assertTrue("clientToHost must be wiped", c2h.all { it == 0.toByte() })
    }

    @Test
    fun syncbytes_zeroize_wipes_every_array_given() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(9, 8, 7, 6)
        SyncBytes.zeroize(a, b)
        assertTrue(a.all { it == 0.toByte() })
        assertTrue(b.all { it == 0.toByte() })
    }
}
