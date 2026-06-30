package io.github.aedev.flow.sync.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * FLOW-SYNC/1 cryptography. Implements the byte-exact scheme so that frames sealed by the Rust desktop
 * (RustCrypto `aes-gcm` / `hkdf` / `hmac`) open here and vice-versa.
 *
 * Primitives (all standard JCE, hardware-accelerated where available):
 * - HKDF-SHA256 (RFC 5869) over `javax.crypto.Mac("HmacSHA256")` → directional keys.
 * - AES-256-GCM (`AES/GCM/NoPadding`, 128-bit tag) with a 12-byte random nonce per frame.
 * - HMAC-SHA256 → 6-digit SAS.
 */
object SyncCrypto {

    const val SESSION_ID_LEN = 16
    const val MASTER_KEY_LEN = 32
    const val DERIVED_KEY_LEN = 32
    const val NONCE_LEN = 12
    const val TAG_LEN = 16
    const val GCM_TAG_BITS = 128

    /** HKDF info labels — exact ASCII bytes */
    private val INFO_H2C = "flow-sync/1 host->client".toByteArray(Charsets.US_ASCII)
    private val INFO_C2H = "flow-sync/1 client->host".toByteArray(Charsets.US_ASCII)
    private val LABEL_SAS = "flow-sync/1 sas".toByteArray(Charsets.US_ASCII)

    private val secureRandom = SecureRandom()

    // --- randomness ---

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { secureRandom.nextBytes(it) }
    fun randomSessionId(): ByteArray = randomBytes(SESSION_ID_LEN)
    fun randomMasterKey(): ByteArray = randomBytes(MASTER_KEY_LEN)
    fun randomNonce(): ByteArray = randomBytes(NONCE_LEN)

    // --- HKDF-SHA256 (RFC 5869) ---

    /** HKDF-Extract: `PRK = HMAC-SHA256(salt, ikm)`. */
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    /** HKDF-Expand: T(i) = HMAC(PRK, T(i-1) ∥ info ∥ i); OKM = first [length] bytes. */
    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length > 0 && length <= 255 * 32) { "HKDF length out of range" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, toCopy)
            pos += toCopy
            counter++
        }
        return out
    }

    /** Full HKDF (extract then expand) with `salt`. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = DERIVED_KEY_LEN): ByteArray =
        hkdfExpand(hkdfExtract(salt, ikm), info, length)

    /**
     * Derive the two directional keys from the QR `K_master` + `session_id`.
     * The host seals with [hostToClient]; the client seals with [clientToHost].
     */
    fun deriveKeys(masterKey: ByteArray, sessionId: ByteArray): DirectionalKeys {
        require(masterKey.size == MASTER_KEY_LEN) { "master key must be 32 bytes" }
        require(sessionId.size == SESSION_ID_LEN) { "session id must be 16 bytes" }
        val prk = hkdfExtract(sessionId, masterKey)
        val h2c = hkdfExpand(prk, INFO_H2C, DERIVED_KEY_LEN)
        val c2h = hkdfExpand(prk, INFO_C2H, DERIVED_KEY_LEN)
        prk.fill(0)
        return DirectionalKeys(h2c, c2h)
    }

    // --- SAS ---

    /**
     * 6-digit Short Authentication String:
     * `num = 31-bit BE of HMAC-SHA256(K_master, "flow-sync/1 sas" ∥ sid)[0..4]; num % 1e6`.
     */
    fun sas(masterKey: ByteArray, sessionId: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        mac.update(LABEL_SAS)
        mac.update(sessionId)
        val d = mac.doFinal()
        val num = ((d[0].toInt() and 0x7F) shl 24) or
            ((d[1].toInt() and 0xFF) shl 16) or
            ((d[2].toInt() and 0xFF) shl 8) or
            (d[3].toInt() and 0xFF)
        return (num % 1_000_000).toString().padStart(6, '0')
    }

    // --- AES-256-GCM ---

    /** Seal: returns `ciphertext ∥ tag(16)` */
    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    /**
     * Open: input is `ciphertext ∥ tag(16)`. Throws [javax.crypto.AEADBadTagException] (a
     * [javax.crypto.BadPaddingException] subtype) if the tag/AAD do not verify — the caller MUST
     * treat that as "drop the connection".
     */
    fun open(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextAndTag)
    }
}

/**
 * The two HKDF-derived directional keys. [hostToClient] is used by the host to seal and by the
 * client to open; [clientToHost] is the reverse. Wipe with [zeroize] at session end.
 */
class DirectionalKeys(
    val hostToClient: ByteArray,
    val clientToHost: ByteArray,
) {
    /** Key this role seals (encrypts) outgoing frames with. */
    fun sealKey(isHost: Boolean): ByteArray = if (isHost) hostToClient else clientToHost

    /** Key this role opens (decrypts) inbound frames with. */
    fun openKey(isHost: Boolean): ByteArray = if (isHost) clientToHost else hostToClient

    fun zeroize() = SyncBytes.zeroize(hostToClient, clientToHost)
}
