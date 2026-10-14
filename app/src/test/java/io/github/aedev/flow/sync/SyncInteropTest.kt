package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.apply.PeerInfo
import io.github.aedev.flow.sync.apply.ReceivedCollection
import io.github.aedev.flow.sync.crypto.DirectionalKeys
import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync.protocol.ApplyStats
import io.github.aedev.flow.sync.protocol.Capabilities
import io.github.aedev.flow.sync.protocol.Capability
import io.github.aedev.flow.sync.protocol.ChunkFraming
import io.github.aedev.flow.sync.protocol.ChunkHeader
import io.github.aedev.flow.sync.protocol.CollectionWire
import io.github.aedev.flow.sync.protocol.FrameType
import io.github.aedev.flow.sync.protocol.Hello
import io.github.aedev.flow.sync.protocol.ProtocolCallbacks
import io.github.aedev.flow.sync.protocol.Selection
import io.github.aedev.flow.sync.protocol.SyncCodec
import io.github.aedev.flow.sync.protocol.SyncCollection
import io.github.aedev.flow.sync.protocol.SyncPeerException
import io.github.aedev.flow.sync.protocol.SyncProtocol
import io.github.aedev.flow.sync.protocol.SyncProtocolException
import io.github.aedev.flow.sync.protocol.SyncRole
import io.github.aedev.flow.sync.protocol.SyncSerialization
import io.github.aedev.flow.sync.protocol.TransferSummary
import io.github.aedev.flow.sync.transport.SyncConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The Android driver against peers that do not behave like another Android build: a scripted
 * desktop that streams collections in its own order, and a desktop whose capabilities lack
 * Android-only collections. The sequence test pairs Android with itself, which is how the chunk
 * misrouting and the `notes` failure went unnoticed.
 */
class SyncInteropTest {
    private val master = SyncCrypto.randomMasterKey()
    private val sid = SyncCrypto.randomSessionId()
    private val keys = SyncCrypto.deriveKeys(master, sid)
    private val sas = SyncCrypto.sas(master, sid)

    private val autoApprove =
        object : ProtocolCallbacks {
            override suspend fun confirmSas(sas: String) = true

            override suspend fun confirmConsent(summary: TransferSummary) = true
        }

    private fun caps(vararg collections: String) = Capabilities(collections.associateWith { Capability(1, produce = true, consume = true) })

    private fun wire(vararg lines: String) =
        CollectionWire(lines.toList(), lines.size, SyncSerialization.sha256Hex(lines.joinToString("\n")))

    private fun android(
        conn: SyncConnection,
        isHost: Boolean,
        localCaps: Capabilities,
        selection: Selection,
        payload: Map<String, CollectionWire> = emptyMap(),
        apply: suspend (Map<String, ReceivedCollection>) -> Map<String, ApplyStats> = { p ->
            p.mapValues { ApplyStats(added = it.value.lines.size) }
        },
    ) = SyncProtocol(
        conn = conn,
        isHost = isHost,
        keys = keys,
        sessionId = sid,
        sasDigits = sas,
        localHello = Hello("phone", "Pixel", "android", "2.3.0"),
        localCaps = localCaps,
        localSelection = selection,
        callbacks = autoApprove,
        buildPayload = { cols -> payload.filterKeys { it in cols } },
        applyReceived = { _: PeerInfo, p: Map<String, ReceivedCollection> -> apply(p) },
    )

    /** One end of a session driven frame by frame, the way the desktop drives it. */
    private class ScriptedPeer(
        private val conn: SyncConnection,
        private val keys: DirectionalKeys,
        private val sid: ByteArray,
        private val isHost: Boolean,
    ) {
        private var sendSeq = 0L

        suspend fun send(
            type: Byte,
            plaintext: ByteArray,
        ) {
            conn.send(SyncCodec.seal(keys.sealKey(isHost), sid, type, sendSeq++, plaintext))
        }

        suspend fun sendJson(
            type: Byte,
            json: String,
        ) = send(type, json.toByteArray())

        suspend fun expect(type: Byte): JsonObject {
            val opened = SyncCodec.open(keys.openKey(isHost), sid, conn.receive()!!)
            assertEquals("frame order", FrameType.name(type), FrameType.name(opened.frameType))
            return Json.parseToJsonElement(String(opened.plaintext)).jsonObject
        }
    }

    @Test
    fun receiver_routes_a_desktop_stream_by_collection_name() =
        runBlocking {
            val link = SyncLoopback()
            val likes = wire("""{"id":"a","kind":"video","state":"liked"}""")
            val playlists = wire("""{"syncId":"p1","title":"Gym"}""", """{"syncId":"p2","title":"Chill"}""")
            val desktop = ScriptedPeer(link.host, keys, sid, isHost = true)

            val script =
                async {
                    desktop.expect(FrameType.HELLO)
                    desktop.sendJson(
                        FrameType.HELLO_ACK,
                        """{"deviceId":"pc","deviceName":"PC","platform":"desktop","appVersion":"0.1.0","sasConfirmRequired":true}""",
                    )
                    desktop.expect(FrameType.CAPABILITIES)
                    desktop.sendJson(
                        FrameType.CAPABILITIES,
                        """{"collections":{"likes":{"schema":1,"produce":true,"consume":true},"playlists":{"schema":1,"produce":true,"consume":true}}}""",
                    )
                    desktop.sendJson(FrameType.SELECTION, """{"send":["likes","playlists"],"accept":[]}""")
                    desktop.expect(FrameType.SELECTION)
                    desktop.sendJson(
                        FrameType.MANIFEST,
                        """{"collections":{"likes":{"records":1,"bytes":1,"hash":"${likes.hash}"},"playlists":{"records":2,"bytes":1,"hash":"${playlists.hash}"}}}""",
                    )
                    desktop.expect(FrameType.CONSENT)
                    // Playlists first, although the manifest lists likes first.
                    for ((name, w) in listOf(SyncCollection.PLAYLISTS to playlists, SyncCollection.LIKES to likes)) {
                        desktop.send(FrameType.CHUNK, ChunkFraming.encode(ChunkHeader(name, 0, true), w.lines))
                        assertEquals(name, desktop.expect(FrameType.CHUNK_ACK)["collection"]!!.jsonPrimitive.content)
                        desktop.sendJson(FrameType.COMPLETE, """{"collection":"$name","recordsSent":${w.recordCount},"hash":"${w.hash}"}""")
                    }
                    desktop.expect(FrameType.APPLY_RESULT)
                }

            var applied: Map<String, ReceivedCollection> = emptyMap()
            android(
                link.client,
                isHost = false,
                localCaps = caps(SyncCollection.LIKES, SyncCollection.PLAYLISTS),
                selection = Selection(accept = listOf(SyncCollection.LIKES, SyncCollection.PLAYLISTS)),
                apply = { p ->
                    applied = p
                    p.mapValues { ApplyStats(added = it.value.lines.size) }
                },
            ).run(SyncRole.RECEIVER)
            val result = script.await()

            assertEquals(likes.lines, applied[SyncCollection.LIKES]!!.lines)
            assertEquals(playlists.lines, applied[SyncCollection.PLAYLISTS]!!.lines)
            assertEquals(
                "2",
                result["collections"]!!
                    .jsonObject["playlists"]!!
                    .jsonObject["added"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun selection_only_names_collections_the_peer_advertised() =
        runBlocking {
            val link = SyncLoopback()
            val desktopCaps = caps(SyncCollection.PLAYLISTS)
            val receiver =
                async {
                    android(
                        link.host,
                        isHost = true,
                        localCaps = desktopCaps,
                        selection = Selection(accept = listOf(SyncCollection.PLAYLISTS)),
                    ).run(SyncRole.RECEIVER)
                }
            android(
                link.client,
                isHost = false,
                localCaps = caps(SyncCollection.PLAYLISTS, SyncCollection.NOTES),
                selection = Selection(send = listOf(SyncCollection.PLAYLISTS, SyncCollection.NOTES)),
                payload = mapOf(SyncCollection.PLAYLISTS to wire("""{"syncId":"p1"}""")),
            ).run(SyncRole.SENDER)
            receiver.await()

            val selection =
                link.sentByClient
                    .map { SyncCodec.open(keys.openKey(isHost = true), sid, it) }
                    .first { it.frameType == FrameType.SELECTION }
            val send = Json.parseToJsonElement(String(selection.plaintext)).jsonObject["send"]!!.jsonArray
            assertEquals(listOf(SyncCollection.PLAYLISTS), send.map { it.jsonPrimitive.content })
        }

    private fun brainCaps(
        keySpec: Int,
        keyLanguage: String,
    ) = Capabilities(
        mapOf(
            SyncCollection.PLAYLISTS to Capability(1, produce = true, consume = true),
            SyncCollection.FLOW_NEURO_BRAIN to Capability(13, produce = true, consume = true, keySpec = keySpec, keyLanguage = keyLanguage),
        ),
    )

    private fun sentSelection(peer: Capabilities): List<String> =
        runBlocking {
            val link = SyncLoopback()
            val receiver =
                async {
                    android(
                        link.host,
                        isHost = true,
                        localCaps = peer,
                        selection = Selection(accept = listOf(SyncCollection.PLAYLISTS, SyncCollection.FLOW_NEURO_BRAIN)),
                    ).run(SyncRole.RECEIVER)
                }
            android(
                link.client,
                isHost = false,
                localCaps = brainCaps(keySpec = 2, keyLanguage = "en"),
                selection = Selection(send = listOf(SyncCollection.PLAYLISTS, SyncCollection.FLOW_NEURO_BRAIN)),
                payload =
                    mapOf(
                        SyncCollection.PLAYLISTS to wire("""{"syncId":"p1"}"""),
                        SyncCollection.FLOW_NEURO_BRAIN to wire("""{"deviceId":"a"}"""),
                    ),
            ).run(SyncRole.SENDER)
            receiver.await()
            val selection =
                link.sentByClient
                    .map { SyncCodec.open(keys.openKey(isHost = true), sid, it) }
                    .first { it.frameType == FrameType.SELECTION }
            Json
                .parseToJsonElement(String(selection.plaintext))
                .jsonObject["send"]!!
                .jsonArray
                .map { it.jsonPrimitive.content }
        }

    @Test
    fun the_brain_goes_only_to_a_peer_that_writes_topic_keys_the_same_way() {
        assertEquals(listOf(SyncCollection.PLAYLISTS), sentSelection(caps(SyncCollection.PLAYLISTS, SyncCollection.FLOW_NEURO_BRAIN)))
        assertEquals(listOf(SyncCollection.PLAYLISTS), sentSelection(brainCaps(keySpec = 2, keyLanguage = "es")))
        assertEquals(
            listOf(SyncCollection.PLAYLISTS, SyncCollection.FLOW_NEURO_BRAIN),
            sentSelection(brainCaps(keySpec = 2, keyLanguage = "en")),
        )
    }

    @Test
    fun an_apply_failure_reaches_the_sender_as_an_error() =
        runBlocking {
            val link = SyncLoopback()
            val receiver =
                async {
                    runCatching {
                        android(
                            link.host,
                            isHost = true,
                            localCaps = caps(SyncCollection.PLAYLISTS),
                            selection = Selection(accept = listOf(SyncCollection.PLAYLISTS)),
                            apply = { throw IllegalStateException("disk full") },
                        ).run(SyncRole.RECEIVER)
                    }
                }
            val sent =
                runCatching {
                    android(
                        link.client,
                        isHost = false,
                        localCaps = caps(SyncCollection.PLAYLISTS),
                        selection = Selection(send = listOf(SyncCollection.PLAYLISTS)),
                        payload = mapOf(SyncCollection.PLAYLISTS to wire("""{"syncId":"p1"}""")),
                    ).run(SyncRole.SENDER)
                }

            val local = receiver.await().exceptionOrNull()
            assertTrue(local is SyncProtocolException && local.code == SyncProtocol.CODE_APPLY_FAILED)
            val peer = sent.exceptionOrNull()
            assertTrue("sender must learn why: $peer", peer is SyncPeerException && peer.code == SyncProtocol.CODE_APPLY_FAILED)
        }

    @Test
    fun a_chunk_the_manifest_never_announced_is_refused() =
        runBlocking {
            val link = SyncLoopback()
            val desktop = ScriptedPeer(link.host, keys, sid, isHost = true)
            val playlists = wire("""{"syncId":"p1"}""")
            val script =
                async {
                    desktop.expect(FrameType.HELLO)
                    desktop.sendJson(
                        FrameType.HELLO_ACK,
                        """{"deviceId":"pc","deviceName":"PC","platform":"desktop","appVersion":"0.1.0"}""",
                    )
                    desktop.expect(FrameType.CAPABILITIES)
                    desktop.sendJson(FrameType.CAPABILITIES, """{"collections":{"playlists":{"schema":1,"produce":true,"consume":true}}}""")
                    desktop.sendJson(FrameType.SELECTION, """{"send":["playlists"],"accept":[]}""")
                    desktop.expect(FrameType.SELECTION)
                    desktop.sendJson(
                        FrameType.MANIFEST,
                        """{"collections":{"playlists":{"records":1,"bytes":1,"hash":"${playlists.hash}"}}}""",
                    )
                    desktop.expect(FrameType.CONSENT)
                    desktop.send(FrameType.CHUNK, ChunkFraming.encode(ChunkHeader(SyncCollection.LIKES, 0, true), emptyList()))
                    desktop.expect(FrameType.ERROR)
                }
            try {
                android(
                    link.client,
                    isHost = false,
                    localCaps = caps(SyncCollection.PLAYLISTS, SyncCollection.LIKES),
                    selection = Selection(accept = listOf(SyncCollection.PLAYLISTS, SyncCollection.LIKES)),
                ).run(SyncRole.RECEIVER)
                fail("a chunk outside the manifest must end the session")
            } catch (e: SyncProtocolException) {
                assertEquals(SyncProtocol.CODE_PROTOCOL, e.code)
            }
            val error = script.await()
            assertEquals(SyncProtocol.CODE_PROTOCOL, error["code"]!!.jsonPrimitive.content)
            assertFalse(error["message"]!!.jsonPrimitive.content.isBlank())
        }
}
