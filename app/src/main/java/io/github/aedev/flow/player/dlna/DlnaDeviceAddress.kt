package io.github.aedev.flow.player.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

private const val TAG = "StreamProxy"

internal fun dlnaDeviceAddress(context: Context): String {
    try {
        val wifiManager =
            context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val ip = wifiInfo?.ipAddress ?: 0
        if (ip != 0) {
            return String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                (ip shr 8) and 0xff,
                (ip shr 16) and 0xff,
                (ip shr 24) and 0xff,
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to get WiFi IP", e)
    }

    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress ?: continue
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to enumerate interfaces", e)
    }

    return "127.0.0.1"
}
