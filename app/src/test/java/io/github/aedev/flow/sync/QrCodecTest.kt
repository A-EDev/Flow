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

    /** A well-formed code without a lease, as older builds emit it. */
    private fun codeJson(
        ip: String = "192.168.1.4",
        exp: Long = 9_999_999_999L,
        extra: String = "",
    ) = """{"v":1,"sid":"$VALID_SID","k":"$VALID_KEY","ip":"$ip","p":5000,"d":"x","exp":$exp$extra}"""

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
        assertEquals(listOf("ws://192.168.1.42:49210/flow-sync"), qr.wsUrls)
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
    fun every_host_code_is_session_bound_and_ignores_the_scanners_clock() {
        val text = QrCodec.build(sid, key, "10.0.0.5", 8080, "Pixel", 1_000L)

        assertTrue(text.contains("\"lease\":\"host\""))
        assertTrue(QrCodec.parse(text, nowEpochSeconds = 1_000_000L) is QrCodec.Result.Ok)
    }

    @Test
    fun leaseless_code_expires_after_a_clock_skew_grace() {
        val text = codeJson(exp = 1_000L)

        assertTrue(QrCodec.parse(text, nowEpochSeconds = 1_599L) is QrCodec.Result.Ok)
        assertEquals(QrCodec.QrError.EXPIRED, (QrCodec.parse(text, nowEpochSeconds = 1_600L) as QrCodec.Result.Err).error)
    }

    @Test
    fun only_local_network_addresses_are_accepted() {
        fun parse(ip: String) = QrCodec.parse(codeJson(ip = ip), nowEpochSeconds = 1L)
        for (ok in listOf("192.168.1.4", "10.0.0.2", "172.20.1.1", "169.254.3.4", "100.101.1.1", "127.0.0.1", "fd12::1")) {
            assertTrue("$ok should be accepted", parse(ok) is QrCodec.Result.Ok)
        }
        for (bad in listOf("8.8.8.8", "example.com", "192.168.1.4/evil", "attacker@192.168.1.4", "2001:db8::1", "999.1.1.1")) {
            assertEquals("$bad should be refused", QrCodec.QrError.NOT_LOCAL, (parse(bad) as QrCodec.Result.Err).error)
        }
    }

    @Test
    fun fallback_addresses_are_dialled_after_the_primary() {
        val text = codeJson(extra = ""","ips":["10.0.0.7","fd12::1","192.168.1.4"]""")
        val qr = (QrCodec.parse(text, nowEpochSeconds = 1L) as QrCodec.Result.Ok).qr

        assertEquals(
            listOf("ws://192.168.1.4:5000/flow-sync", "ws://10.0.0.7:5000/flow-sync", "ws://[fd12::1]:5000/flow-sync"),
            qr.wsUrls,
        )
    }

    @Test
    fun a_public_fallback_address_poisons_the_code() {
        val text = codeJson(extra = ""","ips":["8.8.8.8"]""")

        assertEquals(QrCodec.QrError.NOT_LOCAL, (QrCodec.parse(text, nowEpochSeconds = 1L) as QrCodec.Result.Err).error)
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
        val qr = (QrCodec.parse(codeJson(), nowEpochSeconds = 1L) as QrCodec.Result.Ok).qr
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

    private companion object {
        const val VALID_SID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val VALID_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
