package io.github.aedev.flow.network

import android.util.Base64
import okhttp3.OkHttpClient
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy

enum class AppProxyType(val storageValue: String) {
    HTTP("http"),
    SOCKS5("socks5");

    companion object {
        fun fromStorageValue(value: String?): AppProxyType = when (value?.lowercase()) {
            SOCKS5.storageValue -> SOCKS5
            else -> HTTP
        }
    }
}

data class AppProxyConfig(
    val enabled: Boolean = false,
    val type: AppProxyType = AppProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = ""
) {
    fun normalized(): AppProxyConfig = copy(
        host = host.trim(),
        username = username.trim()
    )

    fun hasUsableEndpoint(): Boolean = enabled && host.isNotBlank() && port in 1..65535

    fun toProxy(): Proxy? {
        if (!hasUsableEndpoint()) return null
        val proxyType = when (type) {
            AppProxyType.HTTP -> Proxy.Type.HTTP
            AppProxyType.SOCKS5 -> Proxy.Type.SOCKS
        }
        return Proxy(proxyType, InetSocketAddress(host, port))
    }

    fun hasCredentials(): Boolean = username.isNotBlank()

    fun httpProxyAuthorizationHeader(): String? {
        if (!hasUsableEndpoint() || type != AppProxyType.HTTP || !hasCredentials()) return null
        val credentials = "$username:$password"
        val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    fun signature(): String = listOf(
        enabled,
        type.storageValue,
        host,
        port,
        username,
        password
    ).joinToString(separator = "|")
}

object AppProxyManager {
    @Volatile
    private var config: AppProxyConfig = AppProxyConfig()

    private val lock = Any()

    fun update(newConfig: AppProxyConfig) {
        synchronized(lock) {
            config = newConfig.normalized()
            installJvmAuthenticatorLocked()
        }
    }

    fun currentConfig(): AppProxyConfig = config

    fun currentProxy(): Proxy? = config.toProxy()

    fun currentHttpProxyAuthorizationHeader(): String? = config.httpProxyAuthorizationHeader()

    fun currentSignature(): String = config.signature()

    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val activeConfig = config
        val proxy = activeConfig.toProxy() ?: return builder
        builder.proxy(proxy)
        if (activeConfig.type == AppProxyType.HTTP) {
            val authHeader = activeConfig.httpProxyAuthorizationHeader()
            if (authHeader != null) {
                builder.proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", authHeader)
                        .build()
                }
            }
        }
        return builder
    }

    private fun installJvmAuthenticatorLocked() {
        val activeConfig = config
        val shouldInstallProxyAuthenticator =
            activeConfig.hasUsableEndpoint() &&
                activeConfig.type == AppProxyType.SOCKS5 &&
                activeConfig.hasCredentials()

        if (!shouldInstallProxyAuthenticator) {
            Authenticator.setDefault(null)
            return
        }

        val proxyHost = activeConfig.host
        val proxyPort = activeConfig.port
        val username = activeConfig.username
        val passwordChars = activeConfig.password.toCharArray()

        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val isProxyRequest = requestorType == RequestorType.PROXY
                val hostMatches = requestingHost.equals(proxyHost, ignoreCase = true)
                val portMatches = requestingPort == proxyPort || requestingPort <= 0
                return if (isProxyRequest && hostMatches && portMatches) {
                    PasswordAuthentication(username, passwordChars)
                } else {
                    null
                }
            }
        })
    }
}