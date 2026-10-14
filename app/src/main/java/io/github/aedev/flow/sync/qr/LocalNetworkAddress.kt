package io.github.aedev.flow.sync.qr

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Which host addresses a pairing code may point at. A code carries the session key, so a pasted
 * code naming an internet host would stream the library to whoever made it, and the matching
 * verification code proves nothing when the attacker minted the key. Only IP literals on a local
 * network pass: private, link-local, CGNAT (Tailscale and other overlays), loopback and IPv6
 * unique-local. Mirrors the desktop's `qr::is_local_address`.
 */
object LocalNetworkAddress {
    private val IPV4_LITERAL = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val IPV6_LITERAL = Regex("""^[0-9A-Fa-f:.]+$""")

    fun isLocal(host: String): Boolean {
        val address = literal(host) ?: return false
        return when (address) {
            is Inet4Address -> {
                val octets = address.address
                val first = octets[0].toInt() and 0xFF
                val second = octets[1].toInt() and 0xFF
                address.isSiteLocalAddress ||
                    address.isLinkLocalAddress ||
                    address.isLoopbackAddress ||
                    (first == 100 && second in 64..127)
            }

            // Unique-local only: a link-local fe80:: address needs a zone id a code can't carry.
            is Inet6Address -> {
                address.isLoopbackAddress || (address.address[0].toInt() and 0xFE) == 0xFC
            }

            else -> {
                false
            }
        }
    }

    /** [host] as it goes into a URL authority: IPv6 literals need brackets. */
    fun urlHost(host: String): String = if (':' in host) "[$host]" else host

    // Matching the literal syntax first keeps getByName from ever doing a DNS lookup.
    private fun literal(host: String): InetAddress? {
        val isV4 = IPV4_LITERAL.matches(host) && host.split('.').all { it.toInt() <= 255 }
        val isV6 = ':' in host && IPV6_LITERAL.matches(host)
        if (!isV4 && !isV6) return null
        return runCatching { InetAddress.getByName(host) }.getOrNull()
    }
}
