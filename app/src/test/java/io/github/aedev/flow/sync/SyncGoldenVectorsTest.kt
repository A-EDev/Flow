package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.canonical.CanonicalPlaylistItem
import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync.protocol.SyncSerialization
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared FLOW-SYNC/1 golden vectors (addendum §11), mirrored in
 * `notes/sync/fixtures/golden_vectors.json`. Any drift in Android's HKDF, SAS, AES-GCM byte layout,
 * or canonical-JSON key ordering fails here; the desktop (Rust) asserts the **same** fixture, so the
 * union is the cross-platform anti-drift gate. Update only with a protocol version bump.
 */
class SyncGoldenVectorsTest {

    private val master = SyncBytes.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
    private val sid = SyncBytes.fromHex("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf")

    @Test
    fun directional_keys_match_golden() {
        val keys = SyncCrypto.deriveKeys(master, sid)
        assertEquals("221ee0e415029675477ff6f034cd21c7b097a1979be22a11b6e4e8ec9fe09f0c", SyncBytes.toHex(keys.hostToClient))
        assertEquals("39bdede0bb3be1d2c07da377caecf4bb52ce2b8dff02ad1a468de2fcec6331e8", SyncBytes.toHex(keys.clientToHost))
    }

    @Test
    fun sas_matches_golden() {
        assertEquals("996576", SyncCrypto.sas(master, sid))
    }

    @Test
    fun raw_gcm_seal_matches_golden() {
        val keys = SyncCrypto.deriveKeys(master, sid)
        val nonce = SyncBytes.fromHex("0102030405060708090a0b0c")
        val aad = SyncBytes.fromHex("01a0a1a2a3a4a5a6a7a8a9aaabacadaeaf100000000000000003")
        val ct = SyncCrypto.seal(keys.hostToClient, nonce, "flow-sync golden".toByteArray(Charsets.UTF_8), aad)
        assertEquals("7b3de5b1fbf326894fcc98a9836edaa15f1164b1fada823ba82f4e0195352944", SyncBytes.toHex(ct))
        // And it must open back to the plaintext (interop both directions).
        assertEquals("flow-sync golden", String(SyncCrypto.open(keys.hostToClient, nonce, ct, aad), Charsets.UTF_8))
    }

    @Test
    fun canonical_playlist_matches_golden() {
        val pl = CanonicalPlaylist(
            syncId = "p1", origin = "local", youtubeId = null, title = "Gym", description = "",
            isMusic = false, isUserCreated = true, isProtected = false,
            createdAtMs = 1781000000000L, updatedHlc = "100:0:aaa", deleted = false,
            items = listOf(
                CanonicalPlaylistItem(
                    videoId = "v1", position = 0, addedAtMs = 1, deleted = false, title = "A",
                    channelName = "c", channelId = "uc", thumbnailUrl = "", durationSeconds = 212,
                    isMusic = false, hlc = "100:0:aaa",
                ),
            ),
        )
        val wire = SyncSerialization.encodePlaylists(listOf(pl))
        val expected =
            """{"createdAtMs":1781000000000,"deleted":false,"description":"","isMusic":false,"isProtected":false,"isUserCreated":true,"items":[{"addedAtMs":1,"channelId":"uc","channelName":"c","deleted":false,"durationSeconds":212,"hlc":"100:0:aaa","isMusic":false,"position":0,"thumbnailUrl":"","title":"A","videoId":"v1"}],"origin":"local","syncId":"p1","title":"Gym","updatedHlc":"100:0:aaa","youtubeId":null}"""
        assertEquals("canonical JSON must be sorted-key compact", expected, wire.lines.first())
        assertEquals("a0534d548eb59e88a7125e803f5d743e55d626bbca45a8622c57d44086e1b528", wire.hash)
    }
}
