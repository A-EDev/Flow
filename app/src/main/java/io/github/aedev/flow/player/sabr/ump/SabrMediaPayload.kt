package io.github.aedev.flow.player.sabr.ump

/**
 * MEDIA and MEDIA_END payloads address their MEDIA_HEADER with one unsigned byte.
 * This byte is not encoded with the variable-width integer used by the outer UMP envelope.
 */
object SabrMediaPayload {
    const val HEADER_ID_BYTES = 1

    fun headerId(payload: ByteArray): Int? =
        payload.firstOrNull()?.toInt()?.and(0xFF)

    fun dataOffset(payload: ByteArray): Int =
        if (payload.isEmpty()) 0 else HEADER_ID_BYTES
}
