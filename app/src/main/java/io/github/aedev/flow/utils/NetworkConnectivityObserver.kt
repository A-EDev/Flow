package io.github.aedev.flow.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes network connectivity changes and provides reactive state.
 * Used by Media3MusicService to pause/resume retries based on network availability.
 */
class NetworkConnectivityObserver(
    private val context: Context
) {
    companion object {
        private const val TAG = "NetworkConnectivity"
    }

    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Check current network connectivity status.
     */
    fun checkCurrentConnectivity(): Boolean = NetworkState.isOnline(context)

    /**
     * Check if the network is metered (e.g., mobile data).
     */
    fun isNetworkMetered(): Boolean {
        return connectivityManager.isActiveNetworkMetered
    }

    private fun networkRequest(): NetworkRequest =
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

    /**
     * One callback shape for both entry points below, so the polled and the pushed answer agree.
     *
     * [onAvailable] deliberately re-reads capabilities rather than emitting `true`: a network can be
     * available and still not reach the internet (captive portal), which is the case a media app
     * most needs to get right.
     */
    private fun connectivityCallback(emit: (Boolean) -> Unit) =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emit(checkCurrentConnectivity())
            }

            override fun onLost(network: Network) {
                emit(checkCurrentConnectivity())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                emit(networkCapabilities.hasInternet())
            }

            override fun onUnavailable() {
                emit(false)
            }
        }

    /**
     * Start observing network changes.
     */
    fun startObserving() {
        if (networkCallback != null) return

        val callback = connectivityCallback { connected ->
            Log.d(TAG, "Connectivity: $connected")
            _isConnected.value = connected
        }
        networkCallback = callback

        try {
            connectivityManager.registerNetworkCallback(networkRequest(), callback)
            Log.d(TAG, "Started observing network")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
            networkCallback = null
        }
    }

    /**
     * Stop observing network changes.
     */
    fun stopObserving() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "Stopped observing network")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister network callback", e)
            }
        }
        networkCallback = null
    }

    /**
     * Get network connectivity as a Flow for reactive observation.
     */
    fun observeConnectivity(): Flow<Boolean> = callbackFlow {
        val callback = connectivityCallback { trySend(it) }

        trySend(checkCurrentConnectivity())
        connectivityManager.registerNetworkCallback(networkRequest(), callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
