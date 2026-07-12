package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.core.SabrBase64
import org.junit.Test
import java.util.Base64

class SabrBase64Test {
    @Test
    fun `decodes standard padded data`() {
        val bytes = byteArrayOf(0, 1, 2, 3, 127, -1)

        assertThat(SabrBase64.decode(Base64.getEncoder().encodeToString(bytes))).isEqualTo(bytes)
    }

    @Test
    fun `decodes url safe unpadded ustreamer data`() {
        val bytes = byteArrayOf(-5, -17, -1, 0, 42, 99)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        assertThat(SabrBase64.decode(encoded)).isEqualTo(bytes)
    }

    @Test
    fun `ignores transport whitespace`() {
        assertThat(SabrBase64.decode("AQID\nBA==")).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun `accepts youtube dot padding`() {
        assertThat(SabrBase64.decode("AQI.")).isEqualTo(byteArrayOf(1, 2))
    }

    @Test
    fun `rejects malformed data`() {
        assertThat(SabrBase64.decode("A")).isNull()
        assertThat(SabrBase64.decode("not base64!")).isNull()
    }
}
