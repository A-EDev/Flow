package io.github.aedev.flow.player.sabr.core

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal object SabrMediaDecoder {
    private const val COMPRESSION_NONE = 0
    private const val COMPRESSION_GZIP = 1
    private const val COMPRESSION_BROTLI = 2

    fun decode(compressionType: Int, data: ByteArray): ByteArray = when (compressionType) {
        COMPRESSION_NONE -> data
        COMPRESSION_GZIP -> decompress(data, ::GZIPInputStream)
        COMPRESSION_BROTLI -> decompress(data, ::BrotliInputStream)
        else -> throw IOException("Unsupported SABR media compression: $compressionType")
    }

    private fun decompress(
        data: ByteArray,
        inputFactory: (ByteArrayInputStream) -> InputStream
    ): ByteArray = inputFactory(ByteArrayInputStream(data)).use { input ->
        ByteArrayOutputStream().use { output ->
            input.copyTo(output)
            output.toByteArray()
        }
    }
}
