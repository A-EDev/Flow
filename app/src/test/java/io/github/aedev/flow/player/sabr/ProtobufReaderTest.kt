package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.proto.ProtobufReader
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtobufReaderTest {
    @Test
    fun `truncated scalar fields fail predictably`() {
        listOf(
            byteArrayOf(8, 0x80.toByte()),
            byteArrayOf(9, 1, 2),
            byteArrayOf(13, 1, 2),
            byteArrayOf(0x80.toByte()),
        ).forEach { bytes ->
            assertThrows(IllegalStateException::class.java) { ProtobufReader(bytes).readField() }
        }
    }

    @Test
    fun `a length exceeding the remaining payload is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            ProtobufReader(byteArrayOf(10, 100, 1)).readField()
        }
    }

    @Test
    fun `an unsigned length beyond the signed range is rejected`() {
        val oversized = byteArrayOf(10) + ByteArray(9) { 0xff.toByte() } + byteArrayOf(1)
        assertThrows(IllegalStateException::class.java) { ProtobufReader(oversized).readField() }
    }

    @Test
    fun `valid bytes and following fields retain their boundaries`() {
        val reader = ProtobufReader(byteArrayOf(10, 2, 1, 2, 16, 7))
        assertThat(reader.readField()!!.asBytes()).isEqualTo(byteArrayOf(1, 2))
        assertThat(reader.readField()!!.asLong()).isEqualTo(7)
        assertThat(reader.readField()).isNull()
    }
}
