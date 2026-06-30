package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync.protocol.FrameType
import io.github.aedev.flow.sync.protocol.SyncCodec
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class SyncCodecFuzzTest {

    private val master = SyncCrypto.randomMasterKey()
    private val sid = SyncCrypto.randomSessionId()
    private val keys = SyncCrypto.deriveKeys(master, sid)
    private val rng = Random(0xF10C5117L)

    /** A failure that is NOT one of the two sanctioned "fail closed" outcomes is a real bug. */
    private fun assertRejected(message: ByteArray) {
        try {
            SyncCodec.open(keys.openKey(isHost = false), sid, message)
            fail("parser accepted a malformed/forged frame of ${message.size} bytes")
        } catch (e: IllegalArgumentException) {
            // envelope malformed (too short / bad version) — fine
        } catch (e: javax.crypto.AEADBadTagException) {
            // authentication failed (tamper / garbage) — fine
        }
        // Any other Throwable propagates and fails the test (e.g. AIOOBE, NPE, OOM = a parser bug).
    }

    @Test
    fun random_garbage_of_every_length_is_rejected() {
        for (len in 0..200) {
            val buf = ByteArray(len).also { rng.nextBytes(it) }
            assertRejected(buf)
        }
    }

    @Test
    fun truncations_of_a_valid_frame_are_rejected() {
        val valid = SyncCodec.seal(keys.sealKey(isHost = true), sid, FrameType.MANIFEST, 7L, "hello".toByteArray())
        // Every strict prefix is incomplete and must be rejected (never partially decoded).
        for (cut in 0 until valid.size) {
            assertRejected(valid.copyOfRange(0, cut))
        }
    }

    @Test
    fun single_bit_flips_anywhere_break_auth() {
        val valid = SyncCodec.seal(keys.sealKey(isHost = true), sid, FrameType.CHUNK, 42L, """{"k":"v"}""".toByteArray())
        for (i in valid.indices) {
            for (bit in 0..7) {
                val m = valid.copyOf()
                m[i] = (m[i].toInt() xor (1 shl bit)).toByte()
                // Flipping the version byte (index 0) to a non-0x01 value is an envelope error;
                // anything else is an AAD/ciphertext/tag change → auth failure. Both are "rejected".
                assertRejected(m)
            }
        }
    }

    @Test
    fun appended_trailing_bytes_break_auth() {
        val valid = SyncCodec.seal(keys.sealKey(isHost = true), sid, FrameType.COMPLETE, 1L, "x".toByteArray())
        for (extra in 1..16) {
            val m = valid + ByteArray(extra).also { rng.nextBytes(it) }
            assertRejected(m)
        }
    }

    @Test
    fun a_valid_frame_still_opens_after_all_the_abuse() {
        // Sanity: the fuzzing harness didn't corrupt shared state — a clean frame round-trips.
        val valid = SyncCodec.seal(keys.sealKey(isHost = true), sid, FrameType.PING, 5L, "{}".toByteArray())
        val opened = SyncCodec.open(keys.openKey(isHost = false), sid, valid)
        assertTrue(opened.frameType == FrameType.PING && opened.seq == 5L)
    }
}
