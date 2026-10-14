package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.CanonicalSetting
import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync.protocol.SyncCodec
import io.github.aedev.flow.sync.protocol.SyncSerialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The desktop's crypto golden vectors (the JSON files in `src-tauri/tests/fixtures/flow-sync/`, mirrored into
 * `resources/sync/desktop/crypto/`). [SyncGoldenVectorsTest] asserts Android's own set; this asserts
 * the desktop's, so each side checks both fixture sets and neither can drift alone.
 */
class DesktopGoldenVectorsTest {
    private fun fixture(name: String): JsonObject =
        checkNotNull(javaClass.getResourceAsStream("/sync/desktop/crypto/$name.json")) { "missing fixture $name" }
            .use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }

    private fun JsonObject.hex(key: String) = SyncBytes.fromHex(getValue(key).jsonPrimitive.content)

    @Test
    fun hkdf_directional_keys_match() {
        val f = fixture("hkdf")
        val keys = SyncCrypto.deriveKeys(f.hex("master_hex"), f.hex("session_id_hex"))
        assertEquals(f.getValue("k_h2c_hex").jsonPrimitive.content, SyncBytes.toHex(keys.hostToClient))
        assertEquals(f.getValue("k_c2h_hex").jsonPrimitive.content, SyncBytes.toHex(keys.clientToHost))
    }

    @Test
    fun sas_matches() {
        val f = fixture("sas")
        assertEquals("920700", SyncCrypto.sas(f.hex("master_hex"), f.hex("session_id_hex")))
        assertEquals(f.getValue("sas").jsonPrimitive.content, SyncCrypto.sas(f.hex("master_hex"), f.hex("session_id_hex")))
    }

    @Test
    fun aes_gcm_wire_matches() {
        val f = fixture("seal_open")
        val nonce = f.hex("nonce_hex")
        val aad =
            SyncCodec.buildAad(
                f.hex("session_id_hex"),
                f
                    .getValue("frame_type")
                    .jsonPrimitive.int
                    .toByte(),
                f.getValue("seq").jsonPrimitive.long,
            )
        val sealed = SyncCrypto.seal(f.hex("key_hex"), nonce, f.hex("plaintext_hex"), aad)
        assertEquals(f.getValue("wire_hex").jsonPrimitive.content, SyncBytes.toHex(nonce + sealed))
    }

    @Test
    fun canonical_json_matches() {
        val f = fixture("canonical_json")
        val line = SyncSerialization.encodeSettings(listOf(CanonicalSetting("k", f.getValue("input")))).lines.single()
        assertEquals("""{"hlc":"","key":"k","value":${f.getValue("expected").jsonPrimitive.content}}""", line)
    }
}
