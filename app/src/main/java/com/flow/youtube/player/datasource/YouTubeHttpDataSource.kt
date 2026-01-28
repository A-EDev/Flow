package com.flow.youtube.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

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
            return YouTubeHttpDataSource(userAgent, requestProperties)
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
            requestProperties.clear()
            requestProperties.putAll(defaultRequestProperties)
            return this
        }
    }

    @UnstableApi
    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        
        // Sanitize URI for YouTube to avoid conflicts with ExoPlayer's range handling
        val sanitizedUri = if (isYouTubeUri(dataSpec.uri)) {
            removeConflictingQueryParameters(dataSpec.uri)
        } else {
            dataSpec.uri
        }
        
        val enhancedDataSpec = dataSpec.buildUpon()
            .setUri(sanitizedUri)
            .build()

        // Optimized timeouts for YouTube streaming
        // YouTube can have variable latency, especially during peak hours
        // Longer timeouts prevent premature failures on slow networks
        val factory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(15_000) // 15s connect - balance between responsiveness and reliability
            .setReadTimeoutMs(30_000)    // 30s read - critical for DASH segment downloads
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)

        if (isYouTubeUri(dataSpec.uri)) {
            addYouTubeHeaders(factory)
        }

        dataSource = factory.createDataSource()
        return dataSource!!.open(enhancedDataSpec)
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

    /**
     * Remove query parameters that conflict with ExoPlayer's range handling.
     * ExoPlayer adds its own Range headers for DASH playback, and YouTube's
     * 'range' query parameter can cause conflicts.
     */
    private fun removeConflictingQueryParameters(uri: Uri): Uri {
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { name ->
            // Remove 'range' if ExoPlayer is going to handle it via DataSpec
            // Keep all other parameters (including 'n' for throttling deobfuscation)
            if (name != "range") {
                builder.appendQueryParameter(name, uri.getQueryParameter(name))
            }
        }
        return builder.build()
    }

    /**
     * Add headers that YouTube expects/requires for video streaming.
     * These help avoid bot detection and ensure proper CDN routing.
     */
    private fun addYouTubeHeaders(factory: androidx.media3.datasource.DefaultHttpDataSource.Factory) {
        val headers = mapOf(
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
        factory.setDefaultRequestProperties(headers)
    }
}