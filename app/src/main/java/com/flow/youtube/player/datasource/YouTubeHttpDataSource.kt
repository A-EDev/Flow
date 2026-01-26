package com.flow.youtube.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

/**
 * YouTube-specific HttpDataSource that implements stable networking via DefaultHttpDataSource wrapper.
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
        private var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"

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
        
        // REVERT: Use the stable sanitization logic from commit 4faed19
        val sanitizedUri = if (isYouTubeUri(dataSpec.uri)) {
            removeConflictingQueryParameters(dataSpec.uri)
        } else {
            dataSpec.uri
        }
        
        val enhancedDataSpec = dataSpec.buildUpon()
            .setUri(sanitizedUri)
            .build()

        val factory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(8000) // Stable timeout
            .setReadTimeoutMs(10000)   // Stable timeout
            .setAllowCrossProtocolRedirects(true)

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
        return host.contains("youtube.com") || host.contains("googlevideo.com")
    }

    private fun removeConflictingQueryParameters(uri: Uri): Uri {
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { name ->
            // Remove 'range' if ExoPlayer is going to handle it via DataSpec
            if (name != "range") {
                builder.appendQueryParameter(name, uri.getQueryParameter(name))
            }
        }
        return builder.build()
    }

    private fun addYouTubeHeaders(factory: androidx.media3.datasource.DefaultHttpDataSource.Factory) {
        val headers = mapOf(
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site"
        )
        factory.setDefaultRequestProperties(headers)
    }
}