package io.github.aedev.flow.sync.qr

import io.github.aedev.flow.sync.crypto.SyncBytes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Builds and parses the `FLOW-SYNC/1` QR payload.
 *
 * The QR carries the out-of-band session secret (`K_master`) plus the host's LAN address and a
 * short TTL. It contains **no IV/nonce** (nonces are per-frame and random) and is single-use.
 */
object QrCodec {

    const val PROTOCOL_VERSION = 1
    const val DEFAULT_TTL_SECONDS = 120L
    const val WS_PATH = "/flow-sync"

    /**
     * The sync role of the **device displaying the QR**. The scanner takes the complement:
     * scanning a [ROLE_SENDER] QR makes you the receiver, scanning a [ROLE_RECEIVER] QR makes
     * you the sender. This is what lets a camera-less peer (e.g. desktop) *receive* by showing a
     * QR that the sender scans. Absent/unknown → [ROLE_SENDER] (back-compat with v1 QRs).
     */
    const val ROLE_SENDER = "sender"
    const val ROLE_RECEIVER = "receiver"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class QrPayload(
        @SerialName("v") val version: Int = PROTOCOL_VERSION,
        @SerialName("sid") val sid: String,
        @SerialName("k") val key: String,
        @SerialName("ip") val ip: String,
        @SerialName("p") val port: Int,
        @SerialName("d") val deviceName: String,
        @SerialName("exp") val exp: Long,
        @SerialName("role") val role: String = ROLE_SENDER,
    )

    /** Decoded, validated QR contents. */
    class ParsedQr(
        val sessionId: ByteArray,
        val masterKey: ByteArray,
        val ip: String,
        val port: Int,
        val deviceName: String,
        val expEpochSeconds: Long,
        /** Role of the device that showed this QR ([ROLE_SENDER] or [ROLE_RECEIVER]). */
        val displayerRole: String,
    ) {
        val wsUrl: String get() = "ws://$ip:$port$WS_PATH"
        val displayerIsSender: Boolean get() = displayerRole == ROLE_SENDER
    }

    enum class QrError { MALFORMED, WRONG_VERSION, EXPIRED, BAD_SESSION, BAD_KEY, BAD_ADDRESS }

    sealed class Result {
        data class Ok(val qr: ParsedQr) : Result()
        data class Err(val error: QrError) : Result()
    }

    /** Serialize a QR payload to compact JSON. */
    fun build(
        sessionId: ByteArray,
        masterKey: ByteArray,
        ip: String,
        port: Int,
        deviceName: String,
        expEpochSeconds: Long,
        role: String = ROLE_SENDER,
    ): String {
        require(sessionId.size == 16) { "session id must be 16 bytes" }
        require(masterKey.size == 32) { "master key must be 32 bytes" }
        val payload = QrPayload(
            sid = SyncBytes.b64urlEncode(sessionId),
            key = SyncBytes.b64urlEncode(masterKey),
            ip = ip,
            port = port,
            deviceName = deviceName.take(64),
            exp = expEpochSeconds,
            role = if (role == ROLE_RECEIVER) ROLE_RECEIVER else ROLE_SENDER,
        )
        return json.encodeToString(QrPayload.serializer(), payload)
    }

    /** Parse + validate scanned/pasted QR text. [nowEpochSeconds] is injectable for tests. */
    fun parse(text: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Result {
        val payload = try {
            json.decodeFromString(QrPayload.serializer(), text.trim())
        } catch (e: Exception) {
            return Result.Err(QrError.MALFORMED)
        }
        if (payload.version != PROTOCOL_VERSION) return Result.Err(QrError.WRONG_VERSION)

        val sid = try {
            SyncBytes.b64urlDecode(payload.sid)
        } catch (e: Exception) {
            return Result.Err(QrError.BAD_SESSION)
        }
        if (sid.size != 16) return Result.Err(QrError.BAD_SESSION)

        val key = try {
            SyncBytes.b64urlDecode(payload.key)
        } catch (e: Exception) {
            return Result.Err(QrError.BAD_KEY)
        }
        if (key.size != 32) return Result.Err(QrError.BAD_KEY)

        if (payload.ip.isBlank() || payload.port !in 1..65535) return Result.Err(QrError.BAD_ADDRESS)
        if (payload.exp <= nowEpochSeconds) return Result.Err(QrError.EXPIRED)

        val role = if (payload.role == ROLE_RECEIVER) ROLE_RECEIVER else ROLE_SENDER
        return Result.Ok(
            ParsedQr(sid, key, payload.ip.trim(), payload.port, payload.deviceName, payload.exp, role)
        )
    }
}
