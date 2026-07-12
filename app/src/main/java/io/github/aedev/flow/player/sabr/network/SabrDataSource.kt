package io.github.aedev.flow.player.sabr.network

import android.util.Log
import io.github.aedev.flow.network.AppProxyManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * HTTP transport for SABR protocol.
 * Sends POST requests with protobuf bodies and provides streaming access
 * to the UMP response. Does NOT implement Media3's DataSource interface —
 * this is a raw transport layer used by SabrStreamController.
 */
class SabrDataSource(
    private val userAgent: String
) {
    companion object {
        private const val TAG = "SabrDataSource"
    }

    private var client: OkHttpClient? = null
    private var currentResponse: Response? = null
    private var currentStream: InputStream? = null

    private fun getClient(): OkHttpClient {
        return client ?: AppProxyManager.applyTo(OkHttpClient.Builder())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
            .also { client = it }
    }

    /**
     * Opens a streaming POST request to the SABR endpoint.
     *
     * @param url The GVS SABR streaming URL (contains sabr=1 parameter)
     * @param body Serialized VideoPlaybackAbrRequest protobuf
     * @return InputStream for reading the UMP response, or null on failure
     */
    @Throws(IOException::class)
    fun open(url: String, body: ByteArray, visitorId: String? = null): InputStream {
        close()

        val request = Request.Builder()
            .url(url)
            // WEB SABR sends a binary body without a Content-Type header.
            .post(body.toRequestBody(null))
            .header("User-Agent", userAgent)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "cross-site")
            .header("Accept", "*/*")
            .apply {
                // Sent on every SABR POST so GVS can link the request to the attested visitor
                // session — the working desktop implementation does the same.
                if (!visitorId.isNullOrEmpty()) header("X-Goog-Visitor-Id", visitorId)
            }
            .build()

        Log.d(TAG, "SABR POST: ${url.take(100)}... bodySize=${body.size}")

        val response = getClient().newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()?.take(500) ?: ""
            response.close()
            throw IOException("SABR request failed: HTTP ${response.code} - $errorBody")
        }

        val contentType = response.header("Content-Type") ?: ""
        if (!contentType.contains("application/vnd.yt-ump") && !contentType.contains("application/x-protobuf")) {
            Log.w(TAG, "Unexpected content-type: $contentType (expected application/vnd.yt-ump)")
        }

        currentResponse = response
        currentStream = response.body?.byteStream()
            ?: throw IOException("SABR response has no body")

        Log.d(TAG, "SABR stream opened: content-type=$contentType")
        return currentStream!!
    }

    fun close() {
        try {
            currentStream?.close()
        } catch (e: Exception) {
            Log.v(TAG, "Error closing stream", e)
        }
        try {
            currentResponse?.close()
        } catch (e: Exception) {
            Log.v(TAG, "Error closing response", e)
        }
        currentStream = null
        currentResponse = null
    }

    fun release() {
        close()
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }
}
