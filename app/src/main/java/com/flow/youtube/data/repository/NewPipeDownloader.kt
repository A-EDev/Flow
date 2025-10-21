package com.flow.youtube.data.repository

import android.util.Log
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple NewPipe Downloader implementation
 * Minimal headers to match NewPipe behavior and ensure device compatibility
 */
class NewPipeDownloader private constructor() : Downloader() {
    
    companion object {
        private const val TAG = "NewPipeDownloader"
        private const val CONNECT_TIMEOUT = 30000 // 30 seconds
        private const val READ_TIMEOUT = 30000 // 30 seconds
        
        @Volatile
        private var instance: NewPipeDownloader? = null
        
        fun getInstance(): NewPipeDownloader {
            return instance ?: synchronized(this) {
                instance ?: NewPipeDownloader().also { instance = it }
            }
        }
    }
    
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()
        
        var connection: HttpURLConnection? = null
        
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            
            // Set timeouts to prevent hanging on slow networks
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            // Set HTTP method
            connection.requestMethod = httpMethod
            
            // Apply headers from NewPipe request (don't add custom headers)
            for ((key, values) in headers) {
                for (value in values) {
                    connection.setRequestProperty(key, value)
                }
            }
            
            // Handle POST data if present
            if (dataToSend != null && dataToSend.isNotEmpty()) {
                connection.doOutput = true
                connection.outputStream.use { outputStream ->
                    outputStream.write(dataToSend)
                    outputStream.flush()
                }
            }
            
            // Get response code
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage ?: ""
            
            // Collect response headers
            val responseHeaders = mutableMapOf<String, List<String>>()
            for ((key, value) in connection.headerFields) {
                if (key != null) {
                    responseHeaders[key] = value ?: emptyList()
                }
            }
            
            // Read response body with proper error handling
            val responseBody = try {
                if (responseCode >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } else {
                    connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading response body from $url", e)
                ""
            }
            
            Log.d(TAG, "Request to $url completed with code $responseCode")
            
            return Response(
                responseCode,
                responseMessage,
                responseHeaders,
                responseBody,
                url
            )
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error executing request to $url", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error executing request to $url", e)
            throw IOException("Request failed: ${e.message}", e)
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting connection", e)
            }
        }
    }
}
