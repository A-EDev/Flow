package io.github.aedev.flow.sync.transport

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Resolves this device's LAN IPv4 for the QR code. Filters
 * loopback (`127.`), link-local (`169.254.`), and Docker (`172.17.`) addresses, and prefers the
 * common private ranges so a hotspot/home-Wi-Fi address is chosen over a VPN/virtual one.
 */
object LanAddress {

    fun resolve(): String? {
        val candidates = ArrayList<String>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull() ?: return null
        for (nif in interfaces) {
            val up = runCatching { nif.isUp }.getOrDefault(false)
            val loopback = runCatching { nif.isLoopback }.getOrDefault(true)
            if (!up || loopback) continue
            for (addr in nif.inetAddresses) {
                if (addr !is Inet4Address) continue
                if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                val ip = addr.hostAddress ?: continue
                if (ip.startsWith("127.") || ip.startsWith("169.254.") || ip.startsWith("172.17.")) continue
                candidates.add(ip)
            }
        }
        return candidates.firstOrNull { it.startsWith("192.168.") }
            ?: candidates.firstOrNull { it.startsWith("10.") }
            ?: candidates.firstOrNull { it.startsWith("172.") }
            ?: candidates.firstOrNull()
    }
}
