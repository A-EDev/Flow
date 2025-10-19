package com.flow.youtube.data.repository

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class NewPipeDownloader private constructor() : Downloader() {
    
    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        private const val CONNECT_TIMEOUT = 30
        private const val READ_TIMEOUT = 30
        
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
        val url = URL(request.url())
        val connection = url.openConnection() as HttpURLConnection
        
        connection.connectTimeout = TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT.toLong()).toInt()
        connection.readTimeout = TimeUnit.SECONDS.toMillis(READ_TIMEOUT.toLong()).toInt()
        connection.requestMethod = request.httpMethod()
        
        // Set default headers
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        
        // Set headers from request
        for ((key, values) in request.headers()) {
            for (value in values) {
                connection.setRequestProperty(key, value)
            }
        }
        
        // Set request body if present
        request.dataToSend()?.let { data ->
            connection.doOutput = true
            connection.outputStream.use { outputStream ->
                outputStream.write(data)
            }
        }
        
        val responseCode = connection.responseCode
        val responseMessage = connection.responseMessage
        val responseHeaders = connection.headerFields.mapValues { it.value ?: emptyList() }
        
        val responseBody = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        
        return Response(
            responseCode,
            responseMessage,
            responseHeaders,
            responseBody,
            request.url()
        )
    }
}
