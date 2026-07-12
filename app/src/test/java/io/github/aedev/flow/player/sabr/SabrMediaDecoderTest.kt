package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.core.SabrMediaDecoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

class SabrMediaDecoderTest {
    @Test
    fun uncompressedMediaIsReturnedUnchanged() {
        val media = byteArrayOf(1, 2, 3)

        assertArrayEquals(media, SabrMediaDecoder.decode(0, media))
    }

    @Test
    fun gzipMediaIsDecodedBeforeDelivery() {
        val media = "SABR segment payload".encodeToByteArray()
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(media) }
            output.toByteArray()
        }

        assertArrayEquals(media, SabrMediaDecoder.decode(1, compressed))
    }

    @Test
    fun unknownCompressionIsRejected() {
        assertThrows(IOException::class.java) {
            SabrMediaDecoder.decode(99, byteArrayOf(1))
        }
    }
}
