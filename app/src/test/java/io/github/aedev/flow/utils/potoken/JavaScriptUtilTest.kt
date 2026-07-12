package io.github.aedev.flow.utils.potoken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JavaScriptUtilTest {
    @Test
    fun integrityResponseRequiresUsableTokenAndLifetime() {
        val (token, lifetime) = parseIntegrityTokenData("[\"AQI=\",43200]")

        assertEquals("new Uint8Array([1,2])", token)
        assertEquals(43200L, lifetime)
    }

    @Test
    fun degradedIntegrityResponseIsRejected() {
        assertThrows(PoTokenException::class.java) {
            parseIntegrityTokenData("[\"\",0]")
        }
    }
}
