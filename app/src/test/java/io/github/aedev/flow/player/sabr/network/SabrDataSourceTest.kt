package io.github.aedev.flow.player.sabr.network

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SabrDataSourceTest {
    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk<Uri>(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private class RecordingTransferListener : TransferListener {
        val initializingCount = AtomicInteger()
        val startedCount = AtomicInteger()
        val endedCount = AtomicInteger()
        val transferredBytes = AtomicLong()

        override fun onTransferInitializing(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
        ) {
            assertThat(isNetwork).isTrue()
            initializingCount.incrementAndGet()
        }

        override fun onTransferStart(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
        ) {
            assertThat(isNetwork).isTrue()
            startedCount.incrementAndGet()
        }

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int,
        ) {
            assertThat(isNetwork).isTrue()
            transferredBytes.addAndGet(bytesTransferred.toLong())
        }

        override fun onTransferEnd(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
        ) {
            assertThat(isNetwork).isTrue()
            endedCount.incrementAndGet()
        }

        fun assertBalanced() {
            assertThat(startedCount.get()).isEqualTo(endedCount.get())
        }
    }

    private class Body(
        bytes: ByteArray,
    ) : ResponseBody() {
        constructor(text: String) : this(text.toByteArray())

        val buffer = Buffer().write(bytes)
        var closed = false

        override fun contentType() = null

        override fun contentLength() = buffer.size

        override fun source(): BufferedSource = buffer

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class BlockingBody(
        val readStarted: CountDownLatch,
        val allowRead: CountDownLatch,
    ) : ResponseBody() {
        private val source =
            object : Source {
                @Volatile
                private var closed = false

                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    readStarted.countDown()
                    if (!allowRead.await(5, TimeUnit.SECONDS)) throw IOException("Timed out waiting to read")
                    if (closed) return -1L
                    sink.writeByte(0x41)
                    return 1L
                }

                override fun timeout(): Timeout = Timeout.NONE

                override fun close() {
                    closed = true
                }
            }.buffer()

        override fun contentType() = null

        override fun contentLength() = -1L

        override fun source(): BufferedSource = source
    }

    private fun response(
        body: ResponseBody,
        code: Int = 200,
    ): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://example.com/sabr").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .header("Content-Type", "application/vnd.yt-ump")
            .body(body)
            .build()

    @Test
    fun `a response arriving after close is rejected and closed`() {
        val executing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val body = Body("media")
        val listener = RecordingTransferListener()
        val call = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returns call
        every { call.execute() } answers {
            executing.countDown()
            check(finish.await(3, TimeUnit.SECONDS))
            response(body)
        }
        val source = SabrDataSource(client, "ua", listener)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result =
                executor.submit<Throwable?> {
                    try {
                        source.open("https://example.com/sabr", byteArrayOf())
                        null
                    } catch (e: IOException) {
                        e
                    }
                }
            check(executing.await(3, TimeUnit.SECONDS))
            source.close()
            finish.countDown()
            assertThat(result.get(3, TimeUnit.SECONDS)).isInstanceOf(IOException::class.java)
            assertThat(body.closed).isTrue()
            verify(atLeast = 1) { call.cancel() }
            listener.assertBalanced()
            assertThat(listener.initializingCount.get()).isEqualTo(1)
            assertThat(listener.startedCount.get()).isEqualTo(0)
            assertThat(listener.transferredBytes.get()).isEqualTo(0L)
        } finally {
            finish.countDown()
            source.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `an HTTP error reads only a bounded diagnostic and closes its body`() {
        val body = Body("x".repeat(1_000_000))
        val listener = RecordingTransferListener()
        val call = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response(body, 403)
        val source = SabrDataSource(client, "ua", listener)
        try {
            source.open("https://example.com/sabr", byteArrayOf())
            error("Expected HTTP rejection")
        } catch (e: IOException) {
            assertThat(e.message).contains("HTTP 403")
            assertThat(body.buffer.size).isAtLeast(990_000L)
            assertThat(body.closed).isTrue()
            verify { call.cancel() }
            listener.assertBalanced()
            assertThat(listener.initializingCount.get()).isEqualTo(1)
            assertThat(listener.transferredBytes.get()).isEqualTo(0L)
        } finally {
            source.close()
        }
    }

    @Test
    fun `a failed execute ends without an unmatched transfer`() {
        val listener = RecordingTransferListener()
        val call = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returns call
        every { call.execute() } throws IOException("offline")
        val source = SabrDataSource(client, "ua", listener)
        try {
            source.open("https://example.com/sabr", byteArrayOf())
            error("Expected request failure")
        } catch (error: IOException) {
            assertThat(error).hasMessageThat().isEqualTo("offline")
            verify { call.cancel() }
            listener.assertBalanced()
            assertThat(listener.initializingCount.get()).isEqualTo(1)
            assertThat(listener.transferredBytes.get()).isEqualTo(0L)
        } finally {
            source.close()
        }
    }

    @Test
    fun `closing an open stream cancels the call and closes the response`() {
        val body = Body("media")
        val listener = RecordingTransferListener()
        val call = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response(body)
        every { call.isCanceled() } returns false
        val source = SabrDataSource(client, "ua", listener)
        assertThat(source.open("https://example.com/sabr", byteArrayOf()).read()).isEqualTo('m'.code)
        source.close()
        assertThat(body.closed).isTrue()
        verify { call.cancel() }
        listener.assertBalanced()
        assertThat(listener.initializingCount.get()).isEqualTo(1)
        assertThat(listener.startedCount.get()).isEqualTo(1)
        assertThat(listener.transferredBytes.get()).isEqualTo(1L)
    }

    @Test
    fun `a superseded request cannot clear a newer open stream`() {
        val executing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val oldBody = Body("old")
        val newBody = Body("new")
        val oldCall = mockk<Call>(relaxed = true)
        val newCall = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returnsMany listOf(oldCall, newCall)
        every { oldCall.execute() } answers {
            executing.countDown()
            check(finish.await(3, TimeUnit.SECONDS))
            response(oldBody)
        }
        every { newCall.execute() } returns response(newBody)
        every { newCall.isCanceled() } returns false
        val source = SabrDataSource(client, "ua")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val old =
                executor.submit<Throwable?> {
                    try {
                        source.open("https://example.com/old", byteArrayOf())
                        null
                    } catch (e: IOException) {
                        e
                    }
                }
            check(executing.await(3, TimeUnit.SECONDS))
            val current = source.open("https://example.com/new", byteArrayOf())
            finish.countDown()
            assertThat(old.get(3, TimeUnit.SECONDS)).isInstanceOf(IOException::class.java)
            assertThat(oldBody.closed).isTrue()
            assertThat(newBody.closed).isFalse()
            assertThat(current.read()).isEqualTo('n'.code)
            source.close()
            assertThat(newBody.closed).isTrue()
            verify { newCall.cancel() }
        } finally {
            finish.countDown()
            source.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `successful body reads report exact network bytes and end at EOF`() {
        val body = Body("media")
        val listener = RecordingTransferListener()
        val call = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response(body)
        every { call.isCanceled() } returns false
        val source = SabrDataSource(client, "ua", listener)
        try {
            val responseText = source.open("https://example.com/sabr", byteArrayOf()).readBytes().decodeToString()
            assertThat(responseText).isEqualTo("media")
            listener.assertBalanced()
            assertThat(listener.initializingCount.get()).isEqualTo(1)
            assertThat(listener.startedCount.get()).isEqualTo(1)
            assertThat(listener.endedCount.get()).isEqualTo(1)
            assertThat(listener.transferredBytes.get()).isEqualTo(5L)
        } finally {
            source.close()
        }
    }

    @Test
    fun `single byte reads count one for zero and unsigned byte values`() {
        val body = Body(byteArrayOf(0xff.toByte(), 0x00))
        val listener = RecordingTransferListener()
        val call = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response(body)
        every { call.isCanceled() } returns false
        val source = SabrDataSource(client, "ua", listener)
        try {
            val stream = source.open("https://example.com/sabr", byteArrayOf())
            assertThat(stream.read()).isEqualTo(0xff)
            assertThat(stream.read()).isEqualTo(0x00)
            assertThat(stream.read()).isEqualTo(-1)
            listener.assertBalanced()
            assertThat(listener.transferredBytes.get()).isEqualTo(2L)
        } finally {
            source.close()
        }
    }

    @Test
    fun `closing does not wait for a blocked response body read`() {
        val readStarted = CountDownLatch(1)
        val allowRead = CountDownLatch(1)
        val body = BlockingBody(readStarted, allowRead)
        val listener = RecordingTransferListener()
        val call = mockk<Call>(relaxed = true)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response(body)
        every { call.isCanceled() } returns false
        val source = SabrDataSource(client, "ua", listener)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val stream = source.open("https://example.com/sabr", byteArrayOf())
            val read = executor.submit<Int> { stream.read() }
            check(readStarted.await(3, TimeUnit.SECONDS))

            val startedAt = System.nanoTime()
            source.close()
            val closeElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

            assertThat(closeElapsedMs).isLessThan(1_000L)
            assertThat(listener.startedCount.get()).isEqualTo(1)
            assertThat(listener.endedCount.get()).isEqualTo(1)
            allowRead.countDown()
            assertThat(read.get(3, TimeUnit.SECONDS)).isEqualTo(-1)
            listener.assertBalanced()
        } finally {
            allowRead.countDown()
            source.close()
            executor.shutdownNow()
        }
    }
}
