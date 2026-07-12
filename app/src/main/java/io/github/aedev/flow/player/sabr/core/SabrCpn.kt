package io.github.aedev.flow.player.sabr.core

import java.security.SecureRandom

object SabrCpn {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val random = SecureRandom()

    fun generate(): String = buildString {
        repeat(16) { append(CHARS[random.nextInt(CHARS.length)]) }
    }
}
