package com.flow.youtube.player.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DLNA/UPnP casting engine — zero Google Play Services dependencies.
 *
 * ## Protocol overview
 *  1. **SSDP discovery** — sends an M-SEARCH UDP multicast to 239.255.255.250:1900 and
 *     listens for UPnP AVTransport services.
 *  2. **Device description** — fetches the device description XML over HTTP and locates
 *     the control URL for the `urn:schemas-upnp-org:service:AVTransport:1` service.
 *  3. **SOAP control** — sends SetAVTransportURI, Play, Pause, and Stop actions to the
 *     control URL; no external library required.
 *
 * All network calls are made on [Dispatchers.IO]; state is exposed as [StateFlow]s so
 * Compose can collect them directly.
 */
object DlnaCastManager {

    private const val TAG = "DlnaCastManager"
    private const val SSDP_ADDR = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val DISCOVERY_TIMEOUT_MS = 5_000L
    private val SOAP_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _currentDevice = MutableStateFlow<DlnaDevice?>(null)
    val currentDevice: StateFlow<DlnaDevice?> = _currentDevice.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    val isCasting: Boolean get() = _currentDevice.value != null

    private var discoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // ── Discovery ──────────────────────────────────────────────────────────────

    /**
     * Sends an SSDP M-SEARCH and collects AVTransport responses for
     * [DISCOVERY_TIMEOUT_MS] ms, then resolves each device description to
     * populate the control URL.
     */
    fun startDiscovery(context: Context) {
        discoveryJob?.cancel()
        _devices.value = emptyList()
        discoveryJob = scope.launch {
            _isDiscovering.value = true
            acquireMulticastLock(context)
            try {
                discoverDevices()
            } finally {
                releaseMulticastLock()
                _isDiscovering.value = false
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        releaseMulticastLock()
        _isDiscovering.value = false
    }

    private suspend fun discoverDevices() {
        val found = LinkedHashMap<String, DlnaDevice>()
        val mSearchQuery = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 3\r\n")
            append("ST: urn:schemas-upnp-org:service:AVTransport:1\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)

        try {
            withTimeout(DISCOVERY_TIMEOUT_MS + 1_000L) {
                MulticastSocket(0).use { socket ->
                    socket.soTimeout = DISCOVERY_TIMEOUT_MS.toInt()
                    val group = InetAddress.getByName(SSDP_ADDR)
                    socket.joinGroup(group)

                    socket.send(
                        DatagramPacket(mSearchQuery, mSearchQuery.size, group, SSDP_PORT)
                    )

                    val buf = ByteArray(4096)
                    val packet = DatagramPacket(buf, buf.size)
                    val stop = System.currentTimeMillis() + DISCOVERY_TIMEOUT_MS
                    while (System.currentTimeMillis() < stop) {
                        try {
                            socket.receive(packet)
                            val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                            parseSSDP(response)?.let { raw ->
                                if (!found.containsKey(raw.usn)) {
                                    found[raw.usn] = raw
                                    scope.launch {
                                        val resolved = resolveDevice(raw)
                                        if (resolved != null) {
                                            val current = _devices.value.toMutableList()
                                            val idx = current.indexOfFirst { it.usn == resolved.usn }
                                            if (idx >= 0) current[idx] = resolved else current.add(resolved)
                                            _devices.value = current
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) { /* timeout on receive is normal */ }
                    }
                    socket.leaveGroup(group)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP discovery error: ${e.message}")
        }
    }

    /** Parse an SSDP HTTP response into a raw [DlnaDevice] (no avTransportUrl yet). */
    private fun parseSSDP(response: String): DlnaDevice? {
        var location: String? = null
        var usn: String? = null
        for (line in response.lines()) {
            val lower = line.lowercase()
            when {
                lower.startsWith("location:") -> location = line.substringAfter(":").trim()
                lower.startsWith("usn:") -> usn = line.substringAfter(":").trim()
            }
        }
        if (location.isNullOrBlank()) return null
        return DlnaDevice(
            friendlyName = location,
            location = location,
            usn = usn ?: location
        )
    }

    /**
     * Fetches the UPnP device description XML at [device.location] and fills in
     * [DlnaDevice.friendlyName] and [DlnaDevice.avTransportUrl].  Returns null on failure.
     */
    private fun resolveDevice(device: DlnaDevice): DlnaDevice? {
        return try {
            val baseUrl = URL(device.location).let { "${it.protocol}://${it.host}:${it.port}" }
            val xml = http.newCall(Request.Builder().url(device.location).build())
                .execute().use { it.body?.string() ?: "" }

            var friendlyName = device.friendlyName
            var avTransportControlPath: String? = null
            var inAVTransport = false
            var inServiceType = false
            var inControlUrl = false

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.lowercase()
                        when (tag) {
                            "friendlyname" -> {
                                event = parser.next()
                                if (event == XmlPullParser.TEXT) friendlyName = parser.text.trim()
                            }
                            "servicetype" -> inServiceType = true
                            "controlurl" -> if (inAVTransport) inControlUrl = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        when {
                            inServiceType -> {
                                if (parser.text.contains("AVTransport")) inAVTransport = true
                                inServiceType = false
                            }
                            inControlUrl -> {
                                avTransportControlPath = parser.text.trim()
                                inControlUrl = false
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.lowercase() == "service") inAVTransport = false
                    }
                }
                event = parser.next()
            }

            if (avTransportControlPath == null) {
                Log.w(TAG, "No AVTransport service found at ${device.location}")
                return null
            }
            val controlUrl = if (avTransportControlPath.startsWith("http")) {
                avTransportControlPath
            } else {
                "$baseUrl${if (avTransportControlPath.startsWith("/")) "" else "/"}$avTransportControlPath"
            }
            device.copy(friendlyName = friendlyName, avTransportUrl = controlUrl)
        } catch (e: Exception) {
            Log.e(TAG, "resolveDevice failed for ${device.location}: ${e.message}")
            null
        }
    }

    // ── Control ────────────────────────────────────────────────────────────────

    /**
     * Sends the video URL to the DLNA device and starts playback.
     * Call this after the user has selected a device from [devices].
     */
    fun castTo(device: DlnaDevice, videoUrl: String, title: String) {
        scope.launch {
            try {
                setAVTransportUri(device, videoUrl, title)
                delay(500) 
                play(device)
                _currentDevice.value = device
            } catch (e: Exception) {
                Log.e(TAG, "castTo failed: ${e.message}")
            }
        }
    }

    fun stop() {
        val device = _currentDevice.value ?: return
        scope.launch {
            try {
                sendSoap(
                    device.avTransportUrl,
                    "urn:schemas-upnp-org:service:AVTransport:1#Stop",
                    soapAction("Stop", "urn:schemas-upnp-org:service:AVTransport:1",
                        "<InstanceID>0</InstanceID>")
                )
            } catch (e: Exception) {
                Log.e(TAG, "stop failed: ${e.message}")
            } finally {
                _currentDevice.value = null
            }
        }
    }

    fun pause() {
        val device = _currentDevice.value ?: return
        scope.launch {
            try {
                sendSoap(
                    device.avTransportUrl,
                    "urn:schemas-upnp-org:service:AVTransport:1#Pause",
                    soapAction("Pause", "urn:schemas-upnp-org:service:AVTransport:1",
                        "<InstanceID>0</InstanceID>")
                )
            } catch (e: Exception) {
                Log.e(TAG, "pause failed: ${e.message}")
            }
        }
    }

    private fun setAVTransportUri(device: DlnaDevice, uri: String, title: String) {
        val metadata = buildDidlLite(uri, title)
        val body = soapAction(
            "SetAVTransportURI",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "<InstanceID>0</InstanceID>" +
            "<CurrentURI>${escapeXml(uri)}</CurrentURI>" +
            "<CurrentURIMetaData>${escapeXml(metadata)}</CurrentURIMetaData>"
        )
        sendSoap(device.avTransportUrl,
            "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", body)
    }

    private fun play(device: DlnaDevice) {
        val body = soapAction("Play", "urn:schemas-upnp-org:service:AVTransport:1",
            "<InstanceID>0</InstanceID><Speed>1</Speed>")
        sendSoap(device.avTransportUrl,
            "urn:schemas-upnp-org:service:AVTransport:1#Play", body)
    }

    private fun sendSoap(url: String, action: String, body: String) {
        if (url.isBlank()) {
            Log.w(TAG, "sendSoap: empty control URL, skipping")
            return
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("SOAPAction", "\"$action\"")
            .post(body.toRequestBody(SOAP_TYPE))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "SOAP $action returned ${response.code}")
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun soapAction(action: String, serviceType: String, args: String): String =
        """<?xml version="1.0"?>""" +
        """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
        """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
        """<s:Body>""" +
        """<u:$action xmlns:u="$serviceType">$args</u:$action>""" +
        """</s:Body></s:Envelope>"""

    private fun buildDidlLite(uri: String, title: String): String =
        """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
        """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" """ +
        """xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">""" +
        """<item id="1" parentID="0" restricted="0">""" +
        """<dc:title>${escapeXml(title)}</dc:title>""" +
        """<res protocolInfo="http-get:*:video/mp4:*">${escapeXml(uri)}</res>""" +
        """<upnp:class>object.item.videoItem</upnp:class>""" +
        """</item></DIDL-Lite>"""

    private fun escapeXml(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun acquireMulticastLock(context: Context) {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifiManager.createMulticastLock("flow_dlna_discovery").also {
            it.setReferenceCounted(true)
            it.acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }
}
