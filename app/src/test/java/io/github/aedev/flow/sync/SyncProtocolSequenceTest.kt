package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.apply.PeerInfo
import io.github.aedev.flow.sync.apply.ReceivedCollection
import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync.protocol.ApplyStats
import io.github.aedev.flow.sync.protocol.Capabilities
import io.github.aedev.flow.sync.protocol.Capability
import io.github.aedev.flow.sync.protocol.CollectionWire
import io.github.aedev.flow.sync.protocol.Hello
import io.github.aedev.flow.sync.protocol.ProtocolCallbacks
import io.github.aedev.flow.sync.protocol.Selection
import io.github.aedev.flow.sync.protocol.SyncCollection
import io.github.aedev.flow.sync.protocol.SyncProtocol
import io.github.aedev.flow.sync.protocol.SyncResult
import io.github.aedev.flow.sync.protocol.SyncRole
import io.github.aedev.flow.sync.protocol.SyncSerialization
import io.github.aedev.flow.sync.protocol.TransferSummary
import io.github.aedev.flow.sync.transport.SyncConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end protocol loopback that locks in the canonical FLOW-SYNC/1 sequence agreed with the
 * desktop authority: `MANIFEST` → `CONSENT` both ways → per-collection `CHUNK*`/`CHUNK_ACK`/
 * `COMPLETE` → one aggregate `APPLY_RESULT`. Two [SyncProtocol] instances run concurrently over a
 * pair of in-memory channels.
 *
 * Crucially it runs BOTH transport configurations — host=SENDER and host=RECEIVER — because role and
 * transport are independent (the desktop-receives-by-showing-a-QR case). A sequencing/seq drift in
 * either direction (the "expected CHUNK, got CONSENT" bug) makes a side throw and fails the test.
 */
class SyncProtocolSequenceTest {

    /** A pair of UNLIMITED channels wired as a full-duplex [SyncConnection] pair. */
    private class Loopback {
        private val a2b = Channel<ByteArray>(Channel.UNLIMITED)
        private val b2a = Channel<ByteArray>(Channel.UNLIMITED)
        private fun end(out: Channel<ByteArray>, inn: Channel<ByteArray>) = object : SyncConnection {
            override suspend fun send(bytes: ByteArray) { out.send(bytes) }
            override suspend fun receive(): ByteArray? = inn.receiveCatching().getOrNull()
            override fun close() { out.close() }
        }
        val host: SyncConnection get() = end(a2b, b2a)
        val client: SyncConnection get() = end(b2a, a2b)
    }

    private fun caps() = Capabilities(
        mapOf(SyncCollection.PLAYLISTS to Capability(1, produce = true, consume = true)),
    )

    private val autoApprove = object : ProtocolCallbacks {
        override suspend fun confirmSas(sas: String) = true
        override suspend fun confirmConsent(summary: TransferSummary) = true
    }

    private fun hello(tag: String) = Hello("dev-$tag", "Flow ($tag)", "android", "1.0")

    private fun playlistsPayload(): Map<String, CollectionWire> {
        // Mirror the desktop's canonical playlist shape, INCLUDING items + a null youtubeId, so the
        // item/stub-video path and null-coercion are exercised end-to-end (not just empty playlists).
        val lines = listOf(
            """{"syncId":"p1","origin":"local","youtubeId":null,"title":"Gym","description":"","isMusic":false,"isUserCreated":true,"isProtected":false,"createdAtMs":1781000000000,"updatedHlc":"100:0:aaa","deleted":false,"items":[{"videoId":"v1","position":0,"addedAtMs":1,"deleted":false,"title":"A","channelName":"c","channelId":"uc","thumbnailUrl":"","durationSeconds":212,"isMusic":false}]}""",
            """{"syncId":"p2","origin":"youtube","youtubeId":"PL123","title":"Chill","description":"","isMusic":false,"isUserCreated":false,"isProtected":false,"createdAtMs":1781000000001,"updatedHlc":"100:0:aaa","deleted":false,"items":[]}""",
        )
        return mapOf(
            SyncCollection.PLAYLISTS to
                CollectionWire(lines, lines.size, SyncSerialization.sha256Hex(lines.joinToString("\n"))),
        )
    }

    private fun selection(role: SyncRole) = if (role == SyncRole.SENDER) {
        Selection(send = listOf(SyncCollection.PLAYLISTS), accept = emptyList())
    } else {
        Selection(send = emptyList(), accept = listOf(SyncCollection.PLAYLISTS))
    }

    private fun runSession(
        hostRole: SyncRole,
        clientRole: SyncRole,
        payload: Map<String, CollectionWire> = playlistsPayload(),
        expectedRecords: Int = 2,
    ) = runBlocking {
        val master = SyncCrypto.randomMasterKey()
        val sid = SyncCrypto.randomSessionId()
        val keys = SyncCrypto.deriveKeys(master, sid)
        val sas = SyncCrypto.sas(master, sid)
        val link = Loopback()
        val received = AtomicReference<Map<String, ReceivedCollection>?>(null)

        fun proto(conn: SyncConnection, isHost: Boolean, role: SyncRole) = SyncProtocol(
            conn = conn,
            isHost = isHost,
            keys = keys,
            sessionId = sid,
            sasDigits = sas,
            localHello = hello(if (isHost) "host" else "client"),
            localCaps = caps(),
            localSelection = selection(role),
            callbacks = autoApprove,
            buildPayload = { _ -> payload },
            applyReceived = { _: PeerInfo, p: Map<String, ReceivedCollection> ->
                received.set(p)
                p.mapValues { ApplyStats(added = it.value.lines.size) }
            },
        )

        val hostJob = async { proto(link.host, isHost = true, hostRole).run(hostRole) }
        val clientJob = async { proto(link.client, isHost = false, clientRole).run(clientRole) }
        val hostResult: SyncResult = hostJob.await()
        val clientResult: SyncResult = clientJob.await()

        val got = received.get()
        assertNotNull("receiver applied a payload", got)
        assertEquals("all records arrived, hash-verified", expectedRecords, got!![SyncCollection.PLAYLISTS]!!.lines.size)
        // The sender's aggregate APPLY_RESULT reflects the receiver's stats.
        val senderResult = if (hostRole == SyncRole.SENDER) hostResult else clientResult
        assertEquals(expectedRecords, senderResult.stats[SyncCollection.PLAYLISTS]!!.added)
    }

    @Test
    fun sender_hosts_receiver_scans() = runSession(hostRole = SyncRole.SENDER, clientRole = SyncRole.RECEIVER)

    @Test
    fun receiver_hosts_sender_scans() = runSession(hostRole = SyncRole.RECEIVER, clientRole = SyncRole.SENDER)

    @Test
    fun large_collection_streams_across_many_chunks() {
        val lines = (0 until 20_000).map { """{"syncId":"p$it","title":"t$it"}""" }
        val payload = mapOf(
            SyncCollection.PLAYLISTS to
                CollectionWire(lines, lines.size, SyncSerialization.sha256Hex(lines.joinToString("\n"))),
        )
        runSession(SyncRole.SENDER, SyncRole.RECEIVER, payload = payload, expectedRecords = 20_000)
    }
}
