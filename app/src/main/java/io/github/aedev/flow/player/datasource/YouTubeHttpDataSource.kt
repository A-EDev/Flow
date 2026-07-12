package io.github.aedev.flow.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.network.AppProxyManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * YouTube-specific HttpDataSource optimized for streaming performance.
 * 
 * Key optimizations:
 * - Longer timeouts (30s read) to handle YouTube's variable latency
 * - Proper YouTube headers to avoid bot detection
 * - Range parameter handling for DASH manifests
 * - Cross-protocol redirect support
 */
@UnstableApi
class YouTubeHttpDataSource private constructor(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String>
) : BaseDataSource(true), HttpDataSource {

    private var dataSource: DataSource? = null
    private var currentUri: Uri? = null

    class Factory : HttpDataSource.Factory {
        private val requestProperties = HashMap<String, String>()
        private var userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        override fun createDataSource(): HttpDataSource {
            return YouTubeHttpDataSource(userAgent, requestProperties.toMap())
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
            requestProperties.clear()
            requestProperties.putAll(defaultRequestProperties)
            return this
        }
    }

    companion object {
        private val clientLock = Any()

        @Volatile
        private var cachedClient: OkHttpClient? = null

        @Volatile
        private var cachedProxySignature: String = ""

        private fun sharedClient(): OkHttpClient {
            val proxySignature = AppProxyManager.currentSignature()
            cachedClient?.takeIf { cachedProxySignature == proxySignature }?.let { return it }

            return synchronized(clientLock) {
                cachedClient?.takeIf { cachedProxySignature == proxySignature } ?: run {
                    val client = AppProxyManager.applyTo(OkHttpClient.Builder())
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .retryOnConnectionFailure(true)
                        .build()
                    cachedProxySignature = proxySignature
                    cachedClient = client
                    client
                }
            }
        }
    }

    @UnstableApi
    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri

        val requestUserAgent = if (isYouTubeUri(dataSpec.uri)) {
            resolveYouTubeUserAgent(dataSpec.uri)
        } else {
            userAgent
        }
        val factory = OkHttpDataSource.Factory(sharedClient())
            .setUserAgent(requestUserAgent)

        val requestHeaders = LinkedHashMap<String, String>()
        requestHeaders.putAll(defaultRequestProperties)
        if (isYouTubeUri(dataSpec.uri)) {
            requestHeaders.putAll(youtubeHeaders())
        }
        if (requestHeaders.isNotEmpty()) {
            factory.setDefaultRequestProperties(requestHeaders)
        }

        dataSource = factory.createDataSource()
        return dataSource!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return dataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun close() {
        dataSource?.close()
        dataSource = null
    }

    override fun getUri(): Uri? = currentUri
    
    override fun getResponseCode(): Int = (dataSource as? HttpDataSource)?.responseCode ?: -1
    
    override fun getResponseHeaders(): Map<String, List<String>> = 
        (dataSource as? HttpDataSource)?.responseHeaders ?: emptyMap()
    
    override fun clearAllRequestProperties() {}
    override fun clearRequestProperty(name: String) {}
    override fun setRequestProperty(name: String, value: String) {}

    private fun isYouTubeUri(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.contains("youtube.com") || 
               host.contains("googlevideo.com") ||
               host.contains("ytimg.com")
    }

    // The fetching UA must match the client that minted the URL (`c=` param) — a mismatch is a
    // known cause of mid-stream 403s on googlevideo CDNs.
    private fun resolveYouTubeUserAgent(uri: Uri): String {
        return when (uri.getQueryParameter("c")?.uppercase()) {
            "IOS" -> "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)"
            "ANDROID", "ANDROID_CREATOR" -> "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip"
            "ANDROID_VR" -> "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"
            "VISIONOS" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
            "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER" ->
                "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15"
            "WEB", "MWEB", "WEB_REMIX" -> YouTubeClient.USER_AGENT_WEB
            else -> userAgent
        }
    }

    /**
     * Add headers that YouTube expects/requires for video streaming.
     * These help avoid bot detection and ensure proper CDN routing.
     */
    private fun youtubeHeaders(): Map<String, String> {
        return mapOf(
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            // Accept-Encoding helps with CDN optimization
            "Accept-Encoding" to "identity",
            // Accept header for video content
            "Accept" to "*/*"
        )
    }
}