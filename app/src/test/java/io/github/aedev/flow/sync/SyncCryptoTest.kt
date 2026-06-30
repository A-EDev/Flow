package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync.crypto.SyncCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * Known-answer tests for FLOW-SYNC/1 crypto. These guard byte-exact interop with the Rust
 * desktop (RustCrypto). HKDF is validated against RFC 5869, base64url against RFC 4648, and
 * AES-GCM via round-trip + tamper + AAD-binding.
 */
class SyncCryptoTest {

    // --- base64url ---

    @Test
    fun base64url_rfc4648_vectors() {
        val cases = mapOf(
            "" to "",
            "f" to "Zg",
            "fo" to "Zm8",
            "foo" to "Zm9v",
            "foob" to "Zm9vYg",
            "fooba" to "Zm9vYmE",
            "foobar" to "Zm9vYmFy",
        )
        for ((plain, expected) in cases) {
            val encoded = SyncBytes.b64urlEncode(plain.toByteArray(Charsets.US_ASCII))
            assertEquals("encode('$plain')", expected, encoded)
            assertEquals("decode round-trip", plain, String(SyncBytes.b64urlDecode(expected), Charsets.US_ASCII))
        }
    }

    @Test
    fun base64url_uses_url_safe_alphabet() {
        // Bytes that would produce '+' and '/' in standard base64 must become '-' and '_'.
        val data = SyncBytes.fromHex("fbff")
        val encoded = SyncBytes.b64urlEncode(data)
        assertTrue("should be url-safe, got $encoded", encoded.none { it == '+' || it == '/' || it == '=' })
        assertArrayEquals(data, SyncBytes.b64urlDecode(encoded))
    }

    @Test
    fun hex_roundtrip() {
        val data = SyncCrypto.randomBytes(64)
        assertArrayEquals(data, SyncBytes.fromHex(SyncBytes.toHex(data)))
    }

    // --- HKDF-SHA256 (RFC 5869 Appendix A.1, Test Case 1) ---

    @Test
    fun hkdf_rfc5869_test_case_1() {
        val ikm = SyncBytes.fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = SyncBytes.fromHex("000102030405060708090a0b0c")
        val info = SyncBytes.fromHex("f0f1f2f3f4f5f6f7f8f9")
        val expectedPrk = "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"
        val expectedOkm = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"

        val prk = SyncCrypto.hkdfExtract(salt, ikm)
        assertEquals("PRK", expectedPrk, SyncBytes.toHex(prk))

        val okm = SyncCrypto.hkdfExpand(prk, info, 42)
        assertEquals("OKM", expectedOkm, SyncBytes.toHex(okm))
    }

    // --- directional key derivation ---

    @Test
    fun directional_keys_are_deterministic_and_distinct() {
        val master = SyncBytes.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val sid = SyncBytes.fromHex("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf")

        val k1 = SyncCrypto.deriveKeys(master, sid)
        val k2 = SyncCrypto.deriveKeys(master, sid)

        assertArrayEquals("h2c deterministic", k1.hostToClient, k2.hostToClient)
        assertArrayEquals("c2h deterministic", k1.clientToHost, k2.clientToHost)
        assertEquals(32, k1.hostToClient.size)
        assertEquals(32, k1.clientToHost.size)
        assertFalse("directions must differ", k1.hostToClient.contentEquals(k1.clientToHost))

        // sealKey(host) must equal openKey(client) — the host->client channel.
        assertArrayEquals(k1.sealKey(isHost = true), k1.openKey(isHost = false))
        assertArrayEquals(k1.sealKey(isHost = false), k1.openKey(isHost = true))
    }

    private fun assertFalse(msg: String, cond: Boolean) = assertTrue(msg, !cond)

    // --- SAS ---

    @Test
    fun sas_is_six_digits_and_deterministic() {
        val master = SyncBytes.fromHex("ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100")
        val sid = SyncBytes.fromHex("00112233445566778899aabbccddeeff")

        val sas1 = SyncCrypto.sas(master, sid)
        val sas2 = SyncCrypto.sas(master, sid)
        assertEquals(sas1, sas2)
        assertEquals("must be 6 chars", 6, sas1.length)
        assertTrue("must be all digits: $sas1", sas1.all { it.isDigit() })

        // Different session id => (almost surely) different SAS.
        val other = SyncCrypto.sas(master, SyncBytes.fromHex("ffffffffffffffffffffffffffffffff"))
        assertNotEquals(sas1, other)
    }

    // --- AES-256-GCM seal/open ---

    @Test
    fun gcm_seal_open_roundtrip() {
        val key = SyncCrypto.randomMasterKey()
        val nonce = SyncCrypto.randomNonce()
        val aad = SyncBytes.fromHex("01") + ByteArray(24) { it.toByte() } + SyncBytes.fromHex("0a")
        val plaintext = "hello flow-sync".toByteArray()

        val sealed = SyncCrypto.seal(key, nonce, plaintext, aad)
        assertEquals("ct should be plaintext+tag", plaintext.size + SyncCrypto.TAG_LEN, sealed.size)

        val opened = SyncCrypto.open(key, nonce, sealed, aad)
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun gcm_open_fails_on_tampered_ciphertext() {
        val key = SyncCrypto.randomMasterKey()
        val nonce = SyncCrypto.randomNonce()
        val aad = ByteArray(26)
        val sealed = SyncCrypto.seal(key, nonce, "secret".toByteArray(), aad)
        sealed[0] = (sealed[0].toInt() xor 0x01).toByte() // flip a bit

        try {
            SyncCrypto.open(key, nonce, sealed, aad)
            fail("expected AEADBadTagException")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }

    @Test
    fun gcm_open_fails_on_wrong_aad() {
        val key = SyncCrypto.randomMasterKey()
        val nonce = SyncCrypto.randomNonce()
        val sealed = SyncCrypto.seal(key, nonce, "secret".toByteArray(), ByteArray(26) { 1 })
        try {
            SyncCrypto.open(key, nonce, sealed, ByteArray(26) { 2 })
            fail("expected AEADBadTagException for mismatched AAD")
        } catch (e: AEADBadTagException) {
            // expected — AAD is authenticated (binds version/session/type/seq)
        }
    }
}
