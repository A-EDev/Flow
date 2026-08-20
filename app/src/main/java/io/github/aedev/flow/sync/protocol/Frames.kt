package io.github.aedev.flow.sync.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** FLOW-SYNC/1 frame type bytes */
object FrameType {
    const val HELLO: Byte = 0x01
    const val HELLO_ACK: Byte = 0x02
    const val CAPABILITIES: Byte = 0x03
    const val SELECTION: Byte = 0x04
    const val CONSENT: Byte = 0x05
    const val MANIFEST: Byte = 0x10
    const val CHUNK: Byte = 0x11
    const val CHUNK_ACK: Byte = 0x12
    const val COMPLETE: Byte = 0x13
    const val APPLY_RESULT: Byte = 0x20
    const val PING: Byte = 0x7E
    const val ERROR: Byte = 0x7F

    fun name(b: Byte): String =
        when (b) {
            HELLO -> "HELLO"
            HELLO_ACK -> "HELLO_ACK"
            CAPABILITIES -> "CAPABILITIES"
            SELECTION -> "SELECTION"
            CONSENT -> "CONSENT"
            MANIFEST -> "MANIFEST"
            CHUNK -> "CHUNK"
            CHUNK_ACK -> "CHUNK_ACK"
            COMPLETE -> "COMPLETE"
            APPLY_RESULT -> "APPLY_RESULT"
            PING -> "PING"
            ERROR -> "ERROR"
            else -> "UNKNOWN(0x%02x)".format(b)
        }
}

/** Canonical collection identifiers exchanged over the wire */
object SyncCollection {
    const val WATCH_HISTORY = "watch_history"
    const val PLAYLISTS = "playlists"
    const val LIKES = "likes"
    const val SETTINGS = "settings"
    const val FLOW_NEURO_BRAIN = "flow_neuro_brain"
    const val MUSIC_BRAIN = "music_brain"

    /** Subscription **groups** (the folders), not the channels themselves. */
    const val SUBSCRIPTIONS = "subscriptions"

    /** The channels the user actually follows. Separate from [SUBSCRIPTIONS] on the wire because a
     * group only references channel ids — it can neither create a subscription that is in no group
     * nor express an unsubscribe. */
    const val SUBSCRIBED_CHANNELS = "subscribed_channels"

    /** Collections Android can exchange in v1 (music_brain excluded — Android has no consumer). */
    val ANDROID_SYNCABLE =
        listOf(
            WATCH_HISTORY,
            PLAYLISTS,
            LIKES,
            SETTINGS,
            FLOW_NEURO_BRAIN,
            SUBSCRIBED_CHANNELS,
            SUBSCRIPTIONS,
        )
}

// --- Control-frame payloads (JSON; gzip+sealed by SyncCodec) ---

@Serializable
data class Hello(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val protocol: Int = 1,
)

@Serializable
data class HelloAck(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val sasConfirmRequired: Boolean = true,
)

@Serializable
data class Capability(
    val schema: Int,
    val produce: Boolean,
    val consume: Boolean,
)

@Serializable
data class Capabilities(
    val collections: Map<String, Capability>,
)

/** Which collections this side will SEND and which it will ACCEPT */
@Serializable
data class Selection(
    val send: List<String> = emptyList(),
    val accept: List<String> = emptyList(),
)

@Serializable
data class Consent(
    val accepted: Boolean,
)

@Serializable
data class ManifestEntry(
    val records: Int,
    val bytes: Long,
    val hash: String,
)

@Serializable
data class Manifest(
    val collections: Map<String, ManifestEntry>,
)

/** Header line that precedes the NDJSON body inside a CHUNK plaintext */
@Serializable
data class ChunkHeader(
    val collection: String,
    val seq: Int,
    val last: Boolean,
)

/**
 * CHUNK plaintext framing (addendum §7): the header line, a newline, then the `\n`-joined NDJSON
 * records.
 *
 * The header/body separator is **mandatory even when the body is empty** — an empty collection
 * must go on the wire as `header` + `\n`, not as a bare header. Emitting the bare form is what made
 * a phone with an empty likes/history/playlist fail the desktop's decoder with
 * `codec: chunk frame missing header newline`.
 *
 * [decode] stays deliberately lenient about a missing separator so a peer still speaking the old
 * bare-header form keeps working.
 */
object ChunkFraming {
    private const val SEPARATOR = '\n'

    fun encode(
        header: ChunkHeader,
        lines: List<String>,
    ): ByteArray {
        val headerJson = FrameJson.json.encodeToString(ChunkHeader.serializer(), header)
        val sb = StringBuilder(headerJson).append(SEPARATOR)
        lines.joinTo(sb, SEPARATOR.toString())
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(plaintext: ByteArray): Pair<ChunkHeader, List<String>> {
        val text = String(plaintext, Charsets.UTF_8)
        val nl = text.indexOf(SEPARATOR)
        val headerJson = if (nl < 0) text else text.substring(0, nl)
        val header = FrameJson.json.decodeFromString(ChunkHeader.serializer(), headerJson)
        val body =
            if (nl < 0) {
                emptyList()
            } else {
                text.substring(nl + 1).split(SEPARATOR).filter { it.isNotEmpty() }
            }
        return header to body
    }
}

@Serializable
data class ChunkAck(
    val collection: String,
    val seq: Int,
)

@Serializable
data class Complete(
    val collection: String,
    val recordsSent: Int,
    val hash: String,
)

@Serializable
data class ApplyStats(
    val added: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val tombstoned: Int = 0,
)

@Serializable
data class ApplyResult(
    val collections: Map<String, ApplyStats>,
)

@Serializable
data class Ping(
    val pong: Boolean = false,
)

@Serializable
data class ErrorFrame(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
)

/**
 * Shared JSON for control frames: compact output, lenient parsing
 */
object FrameJson {
    val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            isLenient = true
        }

    inline fun <reified T> encode(value: T): ByteArray = json.encodeToString(serializer<T>(), value).toByteArray(Charsets.UTF_8)

    inline fun <reified T> decode(bytes: ByteArray): T = json.decodeFromString(serializer<T>(), String(bytes, Charsets.UTF_8))
}
