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
 * Minimal headers to match NewPipe behavior
 */
class NewPipeDownloader private constructor() : Downloader() {
    
    companion object {
        private const val TAG = "NewPipeDownloader"
        
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
        
        val connection = URL(url).openConnection() as HttpURLConnection
        
        try {
            // Set timeouts
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            // Set method
            connection.requestMethod = httpMethod
            
            // Only apply headers that NewPipe explicitly sends
            for ((key, values) in headers) {
                for (value in values) {
                    connection.setRequestProperty(key, value)
                }
            }
            
            // Handle POST data if present
            if (dataToSend != null && dataToSend.isNotEmpty()) {
                connection.doOutput = true
                connection.outputStream.use { it.write(dataToSend) }
            }
            
            // Get response
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage ?: ""
            
            // Collect response headers
            val responseHeaders = mutableMapOf<String, List<String>>()
            for ((key, value) in connection.headerFields) {
                if (key != null) {
                    responseHeaders[key] = value ?: emptyList()
                }
            }
            
            // Read response body
            val responseBody = try {
                if (responseCode >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading response body", e)
                ""
            }
            
            return Response(
                responseCode,
                responseMessage,
                responseHeaders,
                responseBody,
                url
            )
            
        } finally {
            connection.disconnect()
        }
    }
}
