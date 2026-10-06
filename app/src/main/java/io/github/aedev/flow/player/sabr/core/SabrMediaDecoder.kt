package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.ump.UmpFrameDecoder
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal object SabrMediaDecoder {
    const val COMPRESSION_NONE = 0
    private const val COMPRESSION_GZIP = 1
    private const val COMPRESSION_BROTLI = 2
    const val DEFAULT_MAX_DECODED_BYTES = 32 * 1024 * 1024
    private const val DECODE_BUFFER_SIZE = 8192

    fun decode(
        compressionType: Int,
        data: ByteArray,
        maxDecodedBytes: Int = DEFAULT_MAX_DECODED_BYTES,
    ): ByteArray {
        require(maxDecodedBytes > 0) { "maxDecodedBytes must be positive" }
        return when (compressionType) {
            COMPRESSION_NONE -> {
                if (data.size > maxDecodedBytes) {
                    throw UmpFrameDecoder.ResourceLimitExceeded(
                        "Uncompressed SABR media exceeds $maxDecodedBytes bytes",
                    )
                }
                data
            }

            COMPRESSION_GZIP -> {
                decompress(data, maxDecodedBytes, ::GZIPInputStream)
            }

            COMPRESSION_BROTLI -> {
                decompress(data, maxDecodedBytes, ::BrotliInputStream)
            }

            else -> {
                throw IOException("Unsupported SABR media compression: $compressionType")
            }
        }
    }

    private fun decompress(
        data: ByteArray,
        maxDecodedBytes: Int,
        inputFactory: (ByteArrayInputStream) -> InputStream,
    ): ByteArray =
        inputFactory(ByteArrayInputStream(data)).use { input ->
            ByteArrayOutputStream(minOf(maxDecodedBytes, data.size.coerceAtLeast(32))).use { output ->
                val buffer = ByteArray(DECODE_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read > maxDecodedBytes - output.size()) {
                        throw UmpFrameDecoder.ResourceLimitExceeded(
                            "Decoded SABR media exceeds $maxDecodedBytes bytes",
                        )
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }
}
