package io.github.aedev.flow.sync.protocol

import io.github.aedev.flow.sync.apply.PeerInfo
import io.github.aedev.flow.sync.apply.ReceivedCollection
import io.github.aedev.flow.sync.crypto.DirectionalKeys
import io.github.aedev.flow.sync.transport.SyncConnection
import javax.crypto.AEADBadTagException

enum class SyncRole { SENDER, RECEIVER }

/** Summary shown to the receiver before it consents to merge. */
data class TransferSummary(val collections: List<String>)

/** Final outcome of a session: per-collection merge stats + the peer that was synced with. */
data class SyncResult(
    val peerName: String,
    val stats: Map<String, ApplyStats>,
)

interface ProtocolCallbacks {
    suspend fun onPeerHello(peer: PeerInfo) {}

    /** Both sides display the 6-digit SAS; return true to proceed (defends against MITM). */
    suspend fun confirmSas(sas: String): Boolean

    /** Receiver-only: "peer wants to send X — merge?" Return true to accept. */
    suspend fun confirmConsent(summary: TransferSummary): Boolean

    fun onProgress(collection: String, done: Int, total: Int) {}
}

class SyncProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Drives one FLOW-SYNC/1 session over a [SyncConnection]. Strict lockstep with an
 * independent monotonic `seq` per direction: handshake → capability/selection
 * negotiation → SAS → consent → streamed transfer → atomic apply. v1 is one-way per session.
 *
 * Transport-symmetric: [isHost] only decides which directional key seals/opens and who speaks
 * first in each exchange; either role can be the SENDER or RECEIVER.
 */
class SyncProtocol(
    private val conn: SyncConnection,
    private val isHost: Boolean,
    private val keys: DirectionalKeys,
    private val sessionId: ByteArray,
    private val sasDigits: String,
    private val localHello: Hello,
    private val localCaps: Capabilities,
    private val localSelection: Selection,
    private val callbacks: ProtocolCallbacks,
    private val buildPayload: suspend (collections: List<String>) -> Map<String, CollectionWire>,
    private val applyReceived: suspend (peer: PeerInfo, payload: Map<String, ReceivedCollection>) -> Map<String, ApplyStats>,
) {
    private var sendSeq = 0L
    private var expectedRecvSeq = 0L
    private val json = FrameJson.json

    suspend fun run(role: SyncRole): SyncResult {
        val peer = handshake()
        callbacks.onPeerHello(peer)
        val peerCaps = exchangeCapabilities()
        val peerSelection = exchangeSelection()

        if (!callbacks.confirmSas(sasDigits)) {
            sendError("sas_rejected", "SAS not confirmed")
            throw SyncProtocolException("SAS not confirmed — possible MITM")
        }

        return when (role) {
            SyncRole.SENDER -> runSender(peer, peerSelection, peerCaps)
            SyncRole.RECEIVER -> runReceiver(peer)
        }
    }

    // --- phases ---

    private suspend fun handshake(): PeerInfo {
        return if (isHost) {
            val hello = decode(expect(FrameType.HELLO).plaintext, Hello.serializer())
            if (hello.protocol != 1) throw SyncProtocolException("unsupported peer protocol ${hello.protocol}")
            sendFrame(FrameType.HELLO_ACK, HelloAck.serializer(), localHello.toAck())
            PeerInfo(hello.deviceId, hello.deviceName, hello.platform)
        } else {
            sendFrame(FrameType.HELLO, Hello.serializer(), localHello)
            val ack = decode(expect(FrameType.HELLO_ACK).plaintext, HelloAck.serializer())
            PeerInfo(ack.deviceId, ack.deviceName, ack.platform)
        }
    }

    private suspend fun exchangeCapabilities(): Capabilities = if (!isHost) {
        sendFrame(FrameType.CAPABILITIES, Capabilities.serializer(), localCaps)
        decode(expect(FrameType.CAPABILITIES).plaintext, Capabilities.serializer())
    } else {
        val peer = decode(expect(FrameType.CAPABILITIES).plaintext, Capabilities.serializer())
        sendFrame(FrameType.CAPABILITIES, Capabilities.serializer(), localCaps)
        peer
    }

    private suspend fun exchangeSelection(): Selection = if (!isHost) {
        sendFrame(FrameType.SELECTION, Selection.serializer(), localSelection)
        decode(expect(FrameType.SELECTION).plaintext, Selection.serializer())
    } else {
        val peer = decode(expect(FrameType.SELECTION).plaintext, Selection.serializer())
        sendFrame(FrameType.SELECTION, Selection.serializer(), localSelection)
        peer
    }

    /**
     * Sender side of the canonical sequence (must mirror the desktop authority exactly):
     * `MANIFEST` (one aggregate frame) → `CONSENT` both ways → per-collection
     * `CHUNK*`/`CHUNK_ACK`/`COMPLETE` stream → one aggregate `APPLY_RESULT`.
     */
    private suspend fun runSender(peer: PeerInfo, peerSelection: Selection, peerCaps: Capabilities): SyncResult {
        val toSend = localSelection.send.filter { it in peerSelection.accept && canConsume(peerCaps, it) }
        val payload = buildPayload(toSend)

        // 1. Single aggregate MANIFEST describing everything we are about to stream.
        sendFrame(
            FrameType.MANIFEST, Manifest.serializer(),
            Manifest(payload.mapValues { (_, w) -> ManifestEntry(w.recordCount, w.lines.sumOf { it.length.toLong() }, w.hash) }),
        )

        // 2. CONSENT (one-way, receiver→sender): the receiver is the SOLE merge gate. The sender
        // sends no consent of its own — the user authorizes the send by verifying the SAS here.
        val consent = decode(expect(FrameType.CONSENT).plaintext, Consent.serializer())
        if (!consent.accepted) throw SyncProtocolException("peer declined the merge")

        // 3. Stream each collection: CHUNK* → CHUNK_ACK → COMPLETE.
        for ((collection, wire) in payload) {
            val chunks = wire.lines.chunked(CHUNK_SIZE)
            if (chunks.isEmpty()) {
                sendChunk(collection, 0, true, emptyList())
                expect(FrameType.CHUNK_ACK)
            } else {
                chunks.forEachIndexed { i, lines ->
                    sendChunk(collection, i, i == chunks.lastIndex, lines)
                    expect(FrameType.CHUNK_ACK)
                    callbacks.onProgress(collection, i + 1, chunks.size)
                }
            }
            sendFrame(FrameType.COMPLETE, Complete.serializer(), Complete(collection, wire.recordCount, wire.hash))
        }

        // 4. One aggregate APPLY_RESULT from the receiver.
        val result = decode(expect(FrameType.APPLY_RESULT).plaintext, ApplyResult.serializer())
        return SyncResult(peer.deviceName, result.collections)
    }

    /**
     * Receiver side: read the single aggregate `MANIFEST` first, gate on the user, then `CONSENT`
     * both ways, then consume the per-collection stream, then emit one aggregate `APPLY_RESULT`.
     */
    private suspend fun runReceiver(peer: PeerInfo): SyncResult {
        // 1. Read the single aggregate MANIFEST before anything else.
        val manifest = decode(expect(FrameType.MANIFEST).plaintext, Manifest.serializer())
        val incoming = manifest.collections.keys.filter { it in localSelection.accept }

        // 2. Ask the user, then send our CONSENT (one-way — the receiver is the sole merge gate).
        if (!callbacks.confirmConsent(TransferSummary(incoming))) {
            sendFrame(FrameType.CONSENT, Consent.serializer(), Consent(false))
            throw SyncProtocolException("merge declined by user")
        }
        sendFrame(FrameType.CONSENT, Consent.serializer(), Consent(true))

        // 3. Receive each collection.
        val received = LinkedHashMap<String, ReceivedCollection>()

        for (collection in manifest.collections.keys) {
            val lines = ArrayList<String>()
            val expected = manifest.collections[collection]?.records ?: 0
            while (true) {
                val frame = expect(FrameType.CHUNK)
                val (header, body) = parseChunk(frame.plaintext)
                lines.addAll(body)
                sendFrame(FrameType.CHUNK_ACK, ChunkAck.serializer(), ChunkAck(header.collection, header.seq))
                callbacks.onProgress(collection, lines.size, expected)
                if (header.last) break
            }
            val complete = decode(expect(FrameType.COMPLETE).plaintext, Complete.serializer())
            val computed = SyncSerialization.sha256Hex(lines.joinToString("\n"))
            if (computed != complete.hash) {
                sendError("hash_mismatch", "Integrity check failed for $collection")
                throw SyncProtocolException("payload hash mismatch for $collection")
            }
            received[collection] = ReceivedCollection(lines, complete.hash)
        }

        val stats = applyReceived(peer, received)
        sendFrame(FrameType.APPLY_RESULT, ApplyResult.serializer(), ApplyResult(stats))
        return SyncResult(peer.deviceName, stats)
    }

    // --- framing helpers ---

    private fun canConsume(caps: Capabilities, collection: String): Boolean =
        caps.collections[collection]?.consume ?: false

    private suspend fun <T> sendFrame(type: Byte, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        sendRaw(type, json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8))
    }

    private suspend fun sendRaw(type: Byte, plaintext: ByteArray) {
        val msg = SyncCodec.seal(keys.sealKey(isHost), sessionId, type, sendSeq, plaintext)
        sendSeq++
        conn.send(msg)
    }

    private suspend fun sendChunk(collection: String, chunkSeq: Int, last: Boolean, lines: List<String>) {
        val header = json.encodeToString(ChunkHeader.serializer(), ChunkHeader(collection, chunkSeq, last))
        val sb = StringBuilder(header)
        for (line in lines) {
            sb.append('\n')
            sb.append(line)
        }
        sendRaw(FrameType.CHUNK, sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun parseChunk(plaintext: ByteArray): Pair<ChunkHeader, List<String>> {
        val text = String(plaintext, Charsets.UTF_8)
        val nl = text.indexOf('\n')
        val headerStr = if (nl < 0) text else text.substring(0, nl)
        val header = json.decodeFromString(ChunkHeader.serializer(), headerStr)
        val body = if (nl < 0) emptyList() else text.substring(nl + 1).split('\n').filter { it.isNotEmpty() }
        return header to body
    }

    private suspend fun expect(type: Byte): SyncCodec.Opened {
        val frame = recvFrame()
        if (frame.frameType == FrameType.ERROR) {
            val err = decode(frame.plaintext, ErrorFrame.serializer())
            throw SyncProtocolException("peer error [${err.code}]: ${err.message}")
        }
        if (frame.frameType != type) {
            throw SyncProtocolException("expected ${FrameType.name(type)}, got ${FrameType.name(frame.frameType)}")
        }
        return frame
    }

    private suspend fun recvFrame(): SyncCodec.Opened {
        val msg = conn.receive() ?: throw SyncProtocolException("connection closed by peer")
        val opened = try {
            SyncCodec.open(keys.openKey(isHost), sessionId, msg)
        } catch (e: AEADBadTagException) {
            throw SyncProtocolException("authentication failed (wrong key or tampered frame)", e)
        }
        if (opened.seq != expectedRecvSeq) {
            throw SyncProtocolException("out-of-order frame: got ${opened.seq}, expected $expectedRecvSeq")
        }
        expectedRecvSeq++
        return opened
    }

    private suspend fun sendError(code: String, message: String) {
        runCatching { sendFrame(FrameType.ERROR, ErrorFrame.serializer(), ErrorFrame(code, message)) }
    }

    private fun <T> decode(bytes: ByteArray, serializer: kotlinx.serialization.KSerializer<T>): T =
        json.decodeFromString(serializer, String(bytes, Charsets.UTF_8))

    private fun Hello.toAck() = HelloAck(deviceId, deviceName, platform, appVersion, sasConfirmRequired = true)

    companion object {
        private const val CHUNK_SIZE = 1500
    }
}
