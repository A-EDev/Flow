package io.github.aedev.flow.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Picks the route per connection from the proxy in force at that moment, so a client built before
 * the saved proxy loaded, or before a VPN came up, still follows it. Devices on the local network
 * stay direct, as casting and device sync need.
 */
internal object AppProxySelector : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host
        val proxy = AppProxyManager.currentProxy()
        return listOf(if (proxy == null || host == null || isLocalHost(host)) Proxy.NO_PROXY else proxy)
    }

    override fun connectFailed(
        uri: URI?,
        address: SocketAddress?,
        error: IOException?,
    ) = Unit
}

/** Answers an HTTP proxy's sign-in challenge with the credentials in force at that moment. */
internal object AppProxyAuthenticator : Authenticator {
    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        val header = AppProxyManager.currentHttpProxyAuthorizationHeader() ?: return null
        // The proxy already refused these credentials; asking again would only loop.
        if (response.request.header(PROXY_AUTHORIZATION) == header) return null
        return response.request
            .newBuilder()
            .header(PROXY_AUTHORIZATION, header)
            .build()
    }

    private const val PROXY_AUTHORIZATION = "Proxy-Authorization"
}

/**
 * Whether [host] names this device or one on the local network. Only literal addresses and local
 * names are recognised: resolving a hostname here would cost a DNS lookup per connection.
 */
internal fun isLocalHost(host: String): Boolean {
    val name = host.lowercase().removePrefix("[").removeSuffix("]")
    if (name == "localhost" || name.endsWith(".local")) return true
    if (':' in name) {
        return name == "::1" || name.startsWith("fe80:") || name.startsWith("fc") || name.startsWith("fd")
    }
    val octets = name.split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    val (first, second) = octets
    return first == 10 ||
        first == 127 ||
        (first == 169 && second == 254) ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168)
}
