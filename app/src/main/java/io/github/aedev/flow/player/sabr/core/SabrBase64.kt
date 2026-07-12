package io.github.aedev.flow.player.sabr.core

import okio.ByteString.Companion.decodeBase64

object SabrBase64 {
    fun decode(value: String): ByteArray? {
        val normalized = value
            .filterNot(Char::isWhitespace)
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
        if (normalized.isEmpty() || normalized.length % 4 == 1) return null

        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return padded.decodeBase64()?.toByteArray()
    }
}
