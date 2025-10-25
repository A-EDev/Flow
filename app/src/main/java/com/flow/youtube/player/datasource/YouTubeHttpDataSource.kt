package com.flow.youtube.player.datasource

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * YouTube-specific HTTP data source that adds YouTube-specific headers and parameters
 * for better compatibility and streaming performance.
 *
 * Based on NewPipe's YoutubeHttpDataSource implementation.
 */
@UnstableApi
class YouTubeHttpDataSource private constructor(
    private val userAgent: String,
    private val clientType: YouTubeClientType,
    private val allowCrossProtocolRedirects: Boolean,
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val requestProperties: MutableMap<String, String>
) : HttpDataSource {

    private var dataSource: DefaultHttpDataSource? = null
    private var currentUri: Uri? = null

    enum class YouTubeClientType(val clientName: String, val clientVersion: String) {
        ANDROID("ANDROID", "17.31.35"),
        IOS("IOS", "17.33.2"),
        WEB("WEB", "2.20210721.00.00"),
        TVHTML5_SIMPLY_EMBEDDED_PLAYER("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0")
    }

    class Factory(
        private var clientType: YouTubeClientType = YouTubeClientType.ANDROID,
        private var connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        private var readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
        private var allowCrossProtocolRedirects: Boolean = true
    ) : HttpDataSource.Factory {

        private val requestProperties = mutableMapOf<String, String>()

        fun setConnectTimeoutMs(timeoutMs: Int): Factory {
            this.connectTimeoutMillis = timeoutMs
            return this
        }

        fun setReadTimeoutMs(timeoutMs: Int): Factory {
            this.readTimeoutMillis = timeoutMs
            return this
        }

        fun setAllowCrossProtocolRedirects(allow: Boolean): Factory {
            this.allowCrossProtocolRedirects = allow
            return this
        }

        override fun createDataSource(): HttpDataSource {
            return YouTubeHttpDataSource(
                getUserAgent(clientType),
                clientType,
                allowCrossProtocolRedirects,
                connectTimeoutMillis,
                readTimeoutMillis,
                requestProperties.toMutableMap()
            )
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
            requestProperties.clear()
            requestProperties.putAll(defaultRequestProperties)
            return this
        }

        private fun getUserAgent(clientType: YouTubeClientType): String {
            return when (clientType) {
                YouTubeClientType.ANDROID -> "com.google.android.youtube/${clientType.clientVersion} (Linux; U; Android 11; $clientType Build/HTS3C) gzip"
                YouTubeClientType.IOS -> "com.google.ios.youtube/${clientType.clientVersion} (${clientType}; U; CPU iOS 15_6 like Mac OS X; en_US)"
                YouTubeClientType.WEB -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                YouTubeClientType.TVHTML5_SIMPLY_EMBEDDED_PLAYER -> "Mozilla/5.0 (PlayStation; PlayStation 4/11.00) AppleWebKit/605.1.15 (KHTML, like Gecko)"
            }
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
    val enhancedDataSpec = enhanceDataSpec(dataSpec)

        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(connectTimeoutMillis)
            .setReadTimeoutMs(readTimeoutMillis)
            .setAllowCrossProtocolRedirects(allowCrossProtocolRedirects)

        // Add YouTube-specific headers
        if (isYouTubeUri(dataSpec.uri)) {
            addYouTubeHeaders(factory)
        }

        // Set default request properties
        factory.setDefaultRequestProperties(requestProperties)

        dataSource = factory.createDataSource()

        return dataSource!!.open(enhancedDataSpec)
    }

    private fun enhanceDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (!isYouTubeUri(uri)) {
            return dataSpec
        }

        val sanitizedUri = removeConflictingQueryParameters(uri)
        return dataSpec.buildUpon()
            .setUri(sanitizedUri)
            .build()
    }

    private fun addYouTubeHeaders(factory: DefaultHttpDataSource.Factory) {
        // Add YouTube-specific headers that are required for proper streaming
        factory.setDefaultRequestProperties(mapOf(
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
            "X-YouTube-Client-Name" to when (clientType) {
                YouTubeClientType.ANDROID -> "3"
                YouTubeClientType.IOS -> "5"
                YouTubeClientType.WEB -> "1"
                YouTubeClientType.TVHTML5_SIMPLY_EMBEDDED_PLAYER -> "7"
            },
            "X-YouTube-Client-Version" to clientType.clientVersion
        ))
    }

    private fun removeConflictingQueryParameters(uri: Uri): Uri {
        // YouTube progressive streams sometimes ship with an initial range query like "range=0-".
        // ExoPlayer already manages byte ranges through request headers, so we remove the
        // parameter entirely to avoid Range header/query mismatches that trigger HTTP 416 errors.
        if (!uri.isHierarchical || uri.query.isNullOrEmpty()) {
            return uri
        }

        val builder = uri.buildUpon().clearQuery()
        val paramNames = uri.queryParameterNames
        for (name in paramNames) {
            if (name.equals("range", ignoreCase = true)) {
                continue
            }
            val values = uri.getQueryParameters(name)
            for (value in values) {
                builder.appendQueryParameter(name, value)
            }
        }
        return builder.build()
    }

    private fun isYouTubeUri(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.contains("youtube.com") ||
               host.contains("googlevideo.com") ||
               host.contains("youtubei.googleapis.com")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return dataSource?.read(buffer, offset, length) ?: -1
    }

    override fun getUri(): Uri? {
        return dataSource?.uri ?: currentUri
    }

    override fun getResponseHeaders(): MutableMap<String, MutableList<String>> {
        return dataSource?.responseHeaders ?: mutableMapOf()
    }

    override fun close() {
        dataSource?.close()
        dataSource = null
        currentUri = null
    }

    override fun setRequestProperty(name: String, value: String) {
        requestProperties[name] = value
    }

    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    override fun getResponseCode(): Int {
        return dataSource?.responseCode ?: -1
    }

    override fun addTransferListener(transferListener: TransferListener) {
        dataSource?.addTransferListener(transferListener)
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 5 * 1000  // Reduced from 8s to 5s for faster failure detection
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 5 * 1000     // Reduced from 8s to 5s
    }
}