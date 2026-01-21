package com.flow.youtube.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.Collections

/**
 * YouTube-specific HttpDataSource that implements NewPipe's exact logic for:
 * 1. Range parameters (to avoid throttling)
 * 2. Header spoofing (Origin, Referer, Sec-Fetch-*)
 * 3. POST method enforcement for videoplayback
 * 4. Correctly handling 200 OK responses for range requests (The "Phantom Buffer" fix)
 */
@UnstableApi
class YouTubeHttpDataSource private constructor(
    private val callFactory: Call.Factory,
    private val clientType: YouTubeClientType,
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String>,
    private val rangeParameterEnabled: Boolean,
    private val rnParameterEnabled: Boolean
) : BaseDataSource(true), HttpDataSource {

    private var currentDataSpec: DataSpec? = null
    private var response: Response? = null
    private var inputStream: InputStream? = null
    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0
    private var opened = false
    
    // Request number to bypass throttling (mimics NewPipe structure)
    private var requestNumber: Long = 0

    enum class YouTubeClientType(val clientName: String, val clientVersion: String) {
        ANDROID("ANDROID", "19.29.35"),
        IOS("IOS", "19.29.1"),
        WEB("WEB", "2.20230728.00.00"),
        TVHTML5("TVHTML5", "7.0")
    }

    class Factory(
        private val callFactory: Call.Factory? = null,
        private val clientType: YouTubeClientType = YouTubeClientType.ANDROID
    ) : HttpDataSource.Factory {
        
        private val requestProperties = HashMap<String, String>()
        private var rangeParameterEnabled = false
        private var rnParameterEnabled = false

        fun setRangeParameterEnabled(enabled: Boolean): Factory {
            this.rangeParameterEnabled = enabled
            return this
        }

        fun setRnParameterEnabled(enabled: Boolean): Factory {
            this.rnParameterEnabled = enabled
            return this
        }

        override fun createDataSource(): HttpDataSource {
            val finalCallFactory = callFactory ?: OkHttpClient.Builder().build()
            
            // Generate User Agent based on client type (NewPipe logic)
            val userAgent = when (clientType) {
                YouTubeClientType.ANDROID -> "com.google.android.youtube/${clientType.clientVersion} (Linux; Android 14; en_US) gzip"
                YouTubeClientType.IOS -> "com.google.ios.youtube/${clientType.clientVersion} (iPhone; CPU iPhone OS 16_5 like Mac OS X; en_US)"
                YouTubeClientType.WEB -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
                YouTubeClientType.TVHTML5 -> "Mozilla/5.0 (Chromium/5.0; Linux; Android 10; BRAVIA 4K 2020 Build/QTG3.200305.006.S35) AppleWebKit/537.36 (KHTML, like Gecke) app_name/youtube_tv_script_20230728_00_RC00"
            }

            return YouTubeHttpDataSource(
                finalCallFactory,
                clientType,
                userAgent,
                requestProperties,
                rangeParameterEnabled,
                rnParameterEnabled
            )
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
            requestProperties.clear()
            requestProperties.putAll(defaultRequestProperties)
            return this
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        transferInitializing(dataSpec)
        
        var url = dataSpec.uri.toString()
        val isVideoPlayback = url.contains("/videoplayback")

        // 1. Appending 'rn' (Request Number) Logic
        if (isVideoPlayback && rnParameterEnabled && !url.contains("rn=")) {
             url += "&rn=$requestNumber"
             requestNumber++
        }

        // 2. Appending Range Parameter logic (The Fix)
        val position = dataSpec.position
        val length = dataSpec.length
        
        // Critical: Compare Long to Long
        val lengthUnset = C.LENGTH_UNSET.toLong()
        
        // Only append range if we are requesting a specific slice AND parameter is enabled
        if (rangeParameterEnabled && (position > 0 || length != lengthUnset)) {
            val end = if (length != lengthUnset) position + length - 1 else null
            // Check if URL already has range? usually not for ExoPlayer requests
            if (!url.contains("range=")) {
                url += "&range=$position-${end ?: ""}"
            }
        }

        // 3. Build Request
        val builder = Request.Builder().url(url)

        // Headers
        val headers = HashMap<String, String>()
        headers.putAll(defaultRequestProperties)
        
        // Add Standard Range Header if parameter mode is DISABLED
        if (!rangeParameterEnabled && (position > 0 || length != lengthUnset)) {
            val end = if (length != lengthUnset) position + length - 1 else ""
            headers["Range"] = "bytes=$position-$end"
        }
        
        // Add YouTube Specific headers (NewPipe logic)
        if (url.contains("youtube.com") || url.contains("googlevideo.com")) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com/"
            headers["User-Agent"] = userAgent
            
            // NewPipe extra headers
            headers["Sec-Fetch-Dest"] = "empty"
            headers["Sec-Fetch-Mode"] = "cors"
            headers["Sec-Fetch-Site"] = "cross-site"
            headers["TE"] = "trailers"
        }
        
        // Apply headers to request
        for ((key, value) in headers) {
            builder.header(key, value)
        }

        // CRITICAL: Force POST for videoplayback
        // NewPipe uses body 0x78, 0x00
        if (isVideoPlayback) {
            builder.method("POST", byteArrayOf(0x78, 0x00).toRequestBody(null))
        }

        // NOTE: We do NOT set the "Range" header here because we used the URL parameter.
        // This prevents the double-range confusion.

        val request = builder.build()

        try {
            response = callFactory.newCall(request).execute()
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(e, dataSpec, HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        }

        val response = this.response!!
        val responseCode = response.code

        // 4. Validate Response
        if (responseCode !in 200..299) {
            val responseHeaders = response.headers.toMultimap()
            response.close()
            throw HttpDataSource.InvalidResponseCodeException(
                responseCode, 
                response.message, 
                null, 
                responseHeaders, 
                dataSpec, 
                byteArrayOf()
            )
        }

        // 5. Bytes Calculation & Skipping Logic
        val responseBody = response.body
        val contentLength = responseBody?.contentLength() ?: -1L
        
        // Standard HTTP behavior: 200 OK means full content, 206 means partial.
        // If we requested a range but got 200, we must skip to the position manually.
        val bytesToSkip = if (!rangeParameterEnabled && responseCode == 200 && position != 0L) position else 0L

        // If length was requested, use it. Otherwise use response length.
        bytesToRead = if (length != lengthUnset) {
            length
        } else if (contentLength != -1L) {
             // If we are skipping, the available bytes are reduced
            contentLength - bytesToSkip
        } else {
            lengthUnset
        }

        inputStream = responseBody?.byteStream()
        
        if (bytesToSkip > 0) {
            try {
                skipFully(inputStream!!, bytesToSkip)
            } catch (e: IOException) {
                 throw HttpDataSource.HttpDataSourceException.createForIOException(e, dataSpec, HttpDataSource.HttpDataSourceException.TYPE_OPEN)
            }
        }
        
        opened = true
        transferStarted(dataSpec)
        
        return bytesToRead
    }

    private fun skipFully(inputStream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                // Skip returned 0 or -1, try reading a byte to see if we reached EOF
                if (inputStream.read() == -1) {
                    throw java.io.EOFException("End of stream reached before skipping requested bytes")
                }
                remaining--
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        
        // Standard check: if we know the length and we are done, return EOF
        val lengthUnset = C.LENGTH_UNSET.toLong()
        if (bytesToRead != lengthUnset && bytesToRead - bytesRead == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToReadThisTime = if (bytesToRead == lengthUnset) 
            readLength.toLong() 
        else 
            (bytesToRead - bytesRead).coerceAtMost(readLength.toLong())
        
        val bytesReadNow: Int
        try {
             bytesReadNow = inputStream!!.read(buffer, offset, bytesToReadThisTime.toInt())
        } catch (e: java.net.SocketTimeoutException) {
             // THE FIX: The 2-second timeout triggered!
             // If we have read ANY bytes in this session, we consider this chunk "Done".
             // We return END_OF_INPUT so ExoPlayer stitches it and requests the next chunk immediately.
             if (bytesRead > 0) {
                 return C.RESULT_END_OF_INPUT
             }
             // If we read nothing and timed out, that's a real error.
             throw HttpDataSource.HttpDataSourceException.createForIOException(e, currentDataSpec!!, HttpDataSource.HttpDataSourceException.TYPE_READ)
        } catch (e: IOException) {
             // CRITICAL FIX: Handle OkHttp's strict enforcement
             if (e is java.net.ProtocolException && e.message == "unexpected end of stream") {
                 return C.RESULT_END_OF_INPUT
             }
             if (e.message?.contains("unexpected end of stream") == true) {
                  return C.RESULT_END_OF_INPUT
             }
             throw HttpDataSource.HttpDataSourceException.createForIOException(e, currentDataSpec!!, HttpDataSource.HttpDataSourceException.TYPE_READ)
        }

        if (bytesReadNow == -1) {
             // CRITICAL FIX: "Phantom Buffering" Workaround
             return C.RESULT_END_OF_INPUT
        }

        bytesRead += bytesReadNow
        bytesTransferred(bytesReadNow)
        return bytesReadNow
    }

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        inputStream?.close()
        response?.close()
        inputStream = null
        response = null
        currentDataSpec = null
    }

    override fun getUri(): Uri? = currentDataSpec?.uri
    
    override fun getResponseCode(): Int = response?.code ?: -1
    
    override fun getResponseHeaders(): Map<String, List<String>> = response?.headers?.toMultimap() ?: emptyMap()
    
    override fun clearAllRequestProperties() {}
    override fun clearRequestProperty(name: String) {}
    override fun setRequestProperty(name: String, value: String) {}
}