package io.github.aedev.flow.player.sabr.network

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.github.aedev.flow.network.AppProxyManager
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * HTTP transport for SABR protocol.
 * Sends POST requests with protobuf bodies and provides streaming access
 * to the UMP response. Does NOT implement Media3's DataSource interface —
 * this is a raw transport layer used by SabrStreamController.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class SabrDataSource(
    private val userAgent: String,
    private val transferListener: TransferListener? = null,
) {
    companion object {
        private const val TAG = "SabrDataSource"
    }

    internal constructor(
        client: OkHttpClient,
        userAgent: String,
        transferListener: TransferListener? = null,
    ) : this(userAgent, transferListener) {
        this.client = client
    }

    private var client: OkHttpClient? = null
    private var currentCall: Call? = null
    private var currentResponse: Response? = null
    private var currentStream: InputStream? = null
    private var currentTransfer: NetworkTransferReporter? = null

    private fun getClient(): OkHttpClient =
        client ?: AppProxyManager
            .applyTo(OkHttpClient.Builder())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
            .also { client = it }

    /**
     * Opens a streaming POST request to the SABR endpoint.
     *
     * @param url The GVS SABR streaming URL (contains sabr=1 parameter)
     * @param body Serialized VideoPlaybackAbrRequest protobuf
     * @return InputStream for reading the UMP response
     */
    @Throws(IOException::class)
    fun open(
        url: String,
        body: ByteArray,
        visitorId: String? = null,
    ): InputStream {
        val request =
            Request
                .Builder()
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
                }.build()

        Log.d(TAG, "SABR POST: ${url.take(100)}... bodySize=${body.size}")

        val transfer = transferListener?.let { NetworkTransferReporter(it, url) }
        val call =
            synchronized(this) {
                close()
                getClient().newCall(request).also {
                    currentCall = it
                    currentTransfer = transfer
                    transfer?.initialize()
                }
            }
        var response: Response? = null
        try {
            val opened = call.execute()
            response = opened
            if (!opened.isSuccessful) {
                val errorBody = opened.peekBody(500).string()
                throw IOException("SABR request failed: HTTP ${opened.code} - $errorBody")
            }

            val contentType = opened.header("Content-Type") ?: ""
            if (!contentType.contains("application/vnd.yt-ump") && !contentType.contains("application/x-protobuf")) {
                Log.w(TAG, "Unexpected content-type: $contentType (expected application/vnd.yt-ump)")
            }

            return synchronized(this) {
                if (currentCall !== call || call.isCanceled()) throw IOException("SABR request cancelled")
                transfer?.start()
                currentResponse = opened
                opened.body.byteStream().let { bodyStream ->
                    val stream =
                        if (transfer == null) {
                            bodyStream
                        } else {
                            MeteredResponseStream(bodyStream, call, transfer)
                        }
                    currentStream = stream
                    stream
                }
            }
        } catch (error: Throwable) {
            call.cancel()
            transfer?.finish()
            try {
                response?.close()
            } catch (closeError: Exception) {
                error.addSuppressed(closeError)
            }
            synchronized(this) {
                if (currentCall === call) {
                    currentCall = null
                    currentResponse = null
                    currentStream = null
                    currentTransfer = null
                }
            }
            throw error
        }
    }

    @Synchronized
    fun close() {
        // Cancelled before anything is closed. Closing an OkHttp body drains whatever is left on
        // the socket so the connection can be pooled, and that read throws
        // NetworkOnMainThreadException when a release arrives on the main thread — which is where
        // ExoPlayer tears a media source down. Call.cancel() is documented safe from any thread and
        // severs the connection itself, so the closes below have nothing left to read.
        try {
            currentCall?.cancel()
        } catch (e: Exception) {
            Log.v(TAG, "Error cancelling call", e)
        }
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
        currentTransfer?.finish()
        currentStream = null
        currentResponse = null
        currentCall = null
        currentTransfer = null
    }

    @Synchronized
    fun release() {
        close()
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private class NetworkTransferReporter(
        listener: TransferListener,
        url: String,
    ) : BaseDataSource(true) {
        private val uri = Uri.parse(url)
        private val dataSpec =
            DataSpec
                .Builder()
                .setUri(uri)
                .setHttpMethod(DataSpec.HTTP_METHOD_POST)
                .build()
        private var started = false

        init {
            addTransferListener(listener)
        }

        @Synchronized
        fun initialize() {
            transferInitializing(dataSpec)
        }

        @Synchronized
        fun start() {
            if (!started) {
                started = true
                transferStarted(dataSpec)
            }
        }

        fun readSingleByte(readNetworkByte: () -> Int): Int = readNetworkBytes(readNetworkByte) { if (it >= 0) 1 else 0 }

        fun readBytes(readNetworkBytes: () -> Int): Int = readNetworkBytes(readNetworkBytes) { it }

        private fun readNetworkBytes(
            readNetworkBytes: () -> Int,
            byteCount: (Int) -> Int,
        ): Int {
            val result =
                try {
                    readNetworkBytes()
                } catch (error: Throwable) {
                    finish()
                    throw error
                }
            recordRead(byteCount(result), result == C.RESULT_END_OF_INPUT)
            return result
        }

        @Synchronized
        private fun recordRead(
            byteCount: Int,
            isEndOfInput: Boolean,
        ) {
            if (byteCount > 0 && started) bytesTransferred(byteCount)
            if (isEndOfInput) finishStartedTransfer()
        }

        @Synchronized
        fun finish() {
            finishStartedTransfer()
        }

        private fun finishStartedTransfer() {
            if (started) {
                started = false
                transferEnded()
            }
        }

        override fun open(dataSpec: DataSpec): Long = throw IOException("Metering source cannot be opened")

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = C.RESULT_END_OF_INPUT

        override fun getUri(): Uri = uri

        override fun close() = finish()
    }

    private class MeteredResponseStream(
        inputStream: InputStream,
        private val call: Call,
        private val transfer: NetworkTransferReporter,
    ) : FilterInputStream(inputStream) {
        override fun read(): Int = transfer.readSingleByte { super.read() }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = transfer.readBytes { super.read(buffer, offset, length) }

        override fun close() {
            call.cancel()
            try {
                super.close()
            } finally {
                transfer.finish()
            }
        }
    }
}
