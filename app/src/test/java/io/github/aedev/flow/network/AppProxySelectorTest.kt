package io.github.aedev.flow.network

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

/**
 * Long-lived clients are built before the saved proxy loads, so they ask the selector per
 * connection instead of capturing a proxy once.
 */
class AppProxySelectorTest {
    @After
    fun reset() = AppProxyManager.update(AppProxyConfig())

    private fun enable() = AppProxyManager.update(AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128))

    private fun select(url: String) = AppProxySelector.select(URI(url)).single()

    @Test
    fun `remote hosts go direct while no proxy is set`() {
        assertThat(select("https://i.ytimg.com/vi/x/hq.jpg")).isEqualTo(Proxy.NO_PROXY)
    }

    @Test
    fun `remote hosts follow a proxy set after the client was built`() {
        enable()

        val proxy = select("https://i.ytimg.com/vi/x/hq.jpg")

        assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP)
        assertThat((proxy.address() as InetSocketAddress).hostString).isEqualTo("proxy.example.test")
    }

    @Test
    fun `turning the proxy off sends remote hosts direct again`() {
        enable()
        AppProxyManager.update(AppProxyConfig())

        assertThat(select("https://www.youtube.com/")).isEqualTo(Proxy.NO_PROXY)
    }

    @Test
    fun `devices on the local network stay direct`() {
        enable()

        assertThat(select("ws://192.168.1.20:8765/sync")).isEqualTo(Proxy.NO_PROXY)
        assertThat(select("http://[fe80::1]:8080/")).isEqualTo(Proxy.NO_PROXY)
        assertThat(select("http://tv.local/")).isEqualTo(Proxy.NO_PROXY)
    }

    @Test
    fun `local hosts are recognised from literals and local names only`() {
        val local =
            listOf(
                "localhost",
                "127.0.0.1",
                "10.0.2.2",
                "172.16.0.9",
                "172.31.255.1",
                "192.168.0.4",
                "169.254.1.1",
                "::1",
                "fd12::4",
                "printer.local",
            )
        val remote = listOf("www.youtube.com", "172.32.0.1", "8.8.8.8", "192.169.0.1", "fdroid.org", "300.1.1.1", "2001:db8::1")

        local.forEach { assertWithMessage(it).that(isLocalHost(it)).isTrue() }
        remote.forEach { assertWithMessage(it).that(isLocalHost(it)).isFalse() }
    }

    @Test
    fun `a vpn sends traffic direct and rekeys clients when the bypass is on`() {
        val config = AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128, bypassOnVpn = true)
        AppProxyManager.update(config, vpnActive = false)
        val withProxy = AppProxyManager.currentSignature()

        AppProxyManager.update(config, vpnActive = true)

        assertThat(select("https://www.youtube.com/")).isEqualTo(Proxy.NO_PROXY)
        assertThat(AppProxyManager.currentSignature()).isNotEqualTo(withProxy)

        AppProxyManager.update(config, vpnActive = false)

        assertThat(select("https://www.youtube.com/").type()).isEqualTo(Proxy.Type.HTTP)
        assertThat(AppProxyManager.currentSignature()).isEqualTo(withProxy)
    }

    @Test
    fun `a vpn leaves the proxy on when the bypass is off`() {
        AppProxyManager.update(AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128), vpnActive = true)

        assertThat(select("https://www.youtube.com/").type()).isEqualTo(Proxy.Type.HTTP)
    }
}
