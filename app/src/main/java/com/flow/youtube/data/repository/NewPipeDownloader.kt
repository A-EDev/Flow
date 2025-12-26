package com.flow.youtube.data.repository

import android.content.Context
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloader implementation for NewPipe Extractor.
 * Uses OkHttp for network requests.
 */
class NewPipeDownloader private constructor(context: Context) : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // .cache(Cache(File(context.cacheDir, "newpipe_http_cache"), 10 * 1024 * 1024))
        .build()

    companion object {
        @Volatile
        private var INSTANCE: NewPipeDownloader? = null

        fun getInstance(context: Context): NewPipeDownloader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NewPipeDownloader(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder()
            .url(url)

        // Add headers
        for ((key, list) in headers) {
            for (value in list) {
                builder.addHeader(key, value)
            }
        }

        // Set method and body
        if (httpMethod == "POST") {
            val body = if (dataToSend != null) {
                okhttp3.RequestBody.create(null, dataToSend)
            } else {
                okhttp3.RequestBody.create(null, ByteArray(0))
            }
            builder.post(body)
        } else {
            builder.get()
        }

        val call = client.newCall(builder.build())
        val response = call.execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBody = response.body
        val responseString = responseBody?.string() ?: ""

        val responseHeaders = mutableMapOf<String, List<String>>()
        for ((name, values) in response.headers.toMultimap()) {
             responseHeaders[name] = values
        }

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseString,
            url 
        )
    }
}
