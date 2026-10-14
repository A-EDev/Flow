package io.github.aedev.flow.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this app's traffic runs through a VPN. It reads the app's own default network, so a VPN
 * that excludes Flow by split tunnelling counts as off.
 */
@Singleton
class VpnStateMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Emits the current state, then every change. A network callback is held only while collected. */
        fun vpnActive(): Flow<Boolean> =
            callbackFlow {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                if (connectivity == null) {
                    trySend(false)
                    awaitClose()
                    return@callbackFlow
                }
                trySend(connectivity.getNetworkCapabilities(connectivity.activeNetwork).isVpn())
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            trySend(capabilities.isVpn())
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }
                val registered = runCatching { connectivity.registerDefaultNetworkCallback(callback) }.isSuccess
                awaitClose { if (registered) connectivity.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()

        private fun NetworkCapabilities?.isVpn(): Boolean = this?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
