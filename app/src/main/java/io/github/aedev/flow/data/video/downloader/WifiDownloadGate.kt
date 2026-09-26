package io.github.aedev.flow.data.video.downloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/** Wi-Fi watch for Wi-Fi only downloads. One network callback serves every download of the service. */
internal class WifiDownloadGate(
    context: Context,
    private val onWifiAvailable: () -> Unit,
    private val onWifiLost: () -> Unit,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun isOnWifi(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    @Synchronized
    fun watch() {
        if (callback != null) return
        val wifiCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                        onWifiAvailable()
                    }
                }

                override fun onLost(network: Network) = onWifiLost()
            }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { connectivity.registerNetworkCallback(request, wifiCallback) }
            .onSuccess { callback = wifiCallback }
            .onFailure { Log.w(TAG, "Could not watch Wi-Fi", it) }
    }

    @Synchronized
    fun release() {
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
    }

    private companion object {
        const val TAG = "WifiDownloadGate"
    }
}
