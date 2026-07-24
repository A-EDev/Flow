package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync.qr.QrCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodecTest {

    private val sid = SyncCrypto.randomSessionId()
    private val key = SyncCrypto.randomMasterKey()

    @Test
    fun build_parse_roundtrip() {
        val text = QrCodec.build(sid, key, "192.168.1.42", 49210, "Flow Desktop (Windows)", 2_000_000_000L)
        val result = QrCodec.parse(text, nowEpochSeconds = 1_000_000_000L)
        assertTrue(result is QrCodec.Result.Ok)
        val qr = (result as QrCodec.Result.Ok).qr
        assertArrayEquals(sid, qr.sessionId)
        assertArrayEquals(key, qr.masterKey)
        assertEquals("192.168.1.42", qr.ip)
        assertEquals(49210, qr.port)
        assertEquals("Flow Desktop (Windows)", qr.deviceName)
        assertEquals("ws://192.168.1.42:49210/flow-sync", qr.wsUrl)
    }

    @Test
    fun compact_json_has_short_keys() {
        val text = QrCodec.build(sid, key, "10.0.0.5", 8080, "Pixel", 2_000_000_000L)
        assertTrue(text.contains("\"v\":1"))
        assertTrue(text.contains("\"sid\":"))
        assertTrue(text.contains("\"k\":"))
        assertTrue(text.contains("\"exp\":"))
        assertTrue("should be compact (no spaces after colons)", !text.contains("\": "))
    }

    @Test
    fun expired_qr_is_rejected() {
        val text = QrCodec.build(sid, key, "10.0.0.5", 8080, "Pixel", 1_000L)
        val result = QrCodec.parse(text, nowEpochSeconds = 2_000L)
        assertEquals(QrCodec.QrError.EXPIRED, (result as QrCodec.Result.Err).error)
    }

    @Test
    fun tv_session_bound_qr_does_not_depend_on_scanner_wall_clock() {
        val text = QrCodec.build(
            sid,
            key,
            "10.0.0.5",
            8080,
            "Flow TV",
            1_000L,
            sessionBound = true,
        )

        val qr = (QrCodec.parse(text, nowEpochSeconds = 2_000L) as QrCodec.Result.Ok).qr

        assertTrue(qr.isSessionBound)
    }

    @Test
    fun regular_mobile_qr_does_not_include_session_bound_lease() {
        val text = QrCodec.build(sid, key, "10.0.0.5", 8080, "Pixel", 2_000L)

        assertTrue(!text.contains("\"lease\""))
    }

    @Test
    fun wrong_version_is_rejected() {
        val result = QrCodec.parse("""{"v":2,"sid":"AAAA","k":"AAAA","ip":"1.2.3.4","p":1,"d":"x","exp":9999999999}""")
        assertEquals(QrCodec.QrError.WRONG_VERSION, (result as QrCodec.Result.Err).error)
    }

    @Test
    fun bad_key_length_is_rejected() {
        // sid valid length but key too short
        val text = """{"v":1,"sid":"AAAAAAAAAAAAAAAAAAAAAA","k":"AAAA","ip":"1.2.3.4","p":1,"d":"x","exp":9999999999}"""
        val result = QrCodec.parse(text, nowEpochSeconds = 1L)
        assertEquals(QrCodec.QrError.BAD_KEY, (result as QrCodec.Result.Err).error)
    }

    @Test
    fun malformed_json_is_rejected() {
        val result = QrCodec.parse("not json at all")
        assertEquals(QrCodec.QrError.MALFORMED, (result as QrCodec.Result.Err).error)
    }

    @Test
    fun role_defaults_to_sender_for_back_compat() {
        // A v1 QR with no "role" field must parse as the displayer being the SENDER.
        val text = """{"v":1,"sid":"AAAAAAAAAAAAAAAAAAAAAA","k":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","ip":"1.2.3.4","p":1,"d":"x","exp":9999999999}"""
        val qr = (QrCodec.parse(text, nowEpochSeconds = 1L) as QrCodec.Result.Ok).qr
        assertEquals(QrCodec.ROLE_SENDER, qr.displayerRole)
        assertTrue(qr.displayerIsSender)
    }

    @Test
    fun receiver_role_roundtrips() {
        val text = QrCodec.build(sid, key, "10.0.0.5", 8080, "Flow Desktop", 2_000_000_000L, QrCodec.ROLE_RECEIVER)
        assertTrue(text.contains("\"role\":\"receiver\""))
        val qr = (QrCodec.parse(text, nowEpochSeconds = 1_000_000_000L) as QrCodec.Result.Ok).qr
        assertEquals(QrCodec.ROLE_RECEIVER, qr.displayerRole)
        assertTrue(!qr.displayerIsSender)
    }
}
