package io.github.aedev.flow.network

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

class AppProxyManagerTest {
    @Test
    fun `applying a config sets its proxy on the builder`() {
        val config = AppProxyConfig(enabled = true, type = AppProxyType.HTTP, host = "10.0.0.1", port = 8080)

        val client = AppProxyManager.applyTo(OkHttpClient.Builder(), config).build()

        assertThat(client.proxy).isEqualTo(config.toProxy())
    }

    @Test
    fun `an unusable config leaves the builder without a proxy`() {
        val config = AppProxyConfig(enabled = false, host = "", port = 0)

        val client = AppProxyManager.applyTo(OkHttpClient.Builder(), config).build()

        assertThat(client.proxy).isNull()
    }
}
