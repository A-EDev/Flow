package io.github.aedev.flow.data.video.downloader.transfer

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Drives the range downloader against an in-process googlevideo stand-in, so no socket is opened. */
@OptIn(ExperimentalCoroutinesApi::class)
class RangeDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val content = Random(7).nextBytes((RangeDownloader.BLOCK_SIZE * 2 + 12_345).toInt())

    private class FakeCdn(
        private val content: ByteArray,
        private val respond: (range: LongRange, attempt: Int) -> Int = { _, _ -> 200 },
        private val truncate: Boolean = false,
        private val ignoreRange: Boolean = false,
        private val contentRangeStart: Long? = null,
    ) : Interceptor {
        val requests = AtomicInteger()

        override fun intercept(chain: Interceptor.Chain): Response {
            val attempt = requests.incrementAndGet()
            val request = chain.request()
            val range =
                request.url
                    .queryParameter("range")
                    ?.split('-')
                    ?.let { (from, to) -> from.toLong()..to.toLong() }
                    ?: 0L..content.lastIndex.toLong()
            val code = respond(range, attempt)
            val builder =
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("fake")
            if (code != 200) return builder.body(ByteArray(0).toResponseBody()).build()
            if (ignoreRange) {
                // A 200 carrying the whole file and no Content-Range: the CDN ignored the range.
                return builder
                    .body(content.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            val end = minOf(range.last, content.lastIndex.toLong()).toInt()
            val slice = content.copyOfRange(range.first.toInt(), end + 1)
            val served = if (truncate) slice.copyOf(slice.size / 2) else slice
            val start = contentRangeStart ?: range.first
            return builder
                .header("Content-Range", "bytes $start-$end/${content.size}")
                .body(served.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }
    }

    private fun downloader(cdn: FakeCdn) = RangeDownloader({ OkHttpClient.Builder().addInterceptor(cdn).build() }, dispatcher)

    private fun job(
        withClen: Boolean = true,
        threads: Int = 2,
    ): TransferJob {
        val url =
            "https://rr1---sn.googlevideo.com/videoplayback?itag=140&expire=9999999999" + if (withClen) "&clen=${content.size}" else ""
        return TransferJob("vid", listOf(TransferStream(StreamRole.AUDIO, url, folder.newFile("audio.part"), 140, 0L)), threads, "ua")
    }

    @Test
    fun `a stream is fetched block by block into its part file`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content)
            val job = job()

            val result = downloader(cdn).run(job)

            assertThat(result).isEqualTo(TransferResult.Completed)
            assertThat(
                job.streams
                    .single()
                    .file
                    .readBytes(),
            ).isEqualTo(content)
            assertThat(cdn.requests.get()).isEqualTo(RangeDownloader.blockCount(content.size.toLong()))
            assertThat(job.downloadedBytes).isEqualTo(content.size.toLong())
        }

    @Test
    fun `a resumed stream fetches only the blocks it is missing`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content)
            val job = job()
            val stream = job.streams.single()
            stream.file.writeBytes(content)
            stream.completedBlocks.addAll(listOf(0, 1))

            val result = downloader(cdn).run(job)

            assertThat(result).isEqualTo(TransferResult.Completed)
            assertThat(cdn.requests.get()).isEqualTo(1)
            assertThat(stream.file.readBytes()).isEqualTo(content)
        }

    @Test
    fun `a refused url stops the job and names the url`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content, respond = { _, _ -> 403 })
            val job = job()

            val result = downloader(cdn).run(job)

            assertThat(result).isInstanceOf(TransferResult.Denied::class.java)
            assertThat((result as TransferResult.Denied).url).contains("itag=140")
            assertThat(cdn.requests.get()).isAtMost(2)
        }

    @Test
    fun `a server that keeps cutting blocks short fails the job instead of writing holes`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content, truncate = true)
            val job = job(threads = 1)

            val result = downloader(cdn).run(job)

            assertThat(result).isInstanceOf(TransferResult.Failed::class.java)
            assertThat((result as TransferResult.Failed).reason).isEqualTo(TransferFailure.BLOCK_FAILED)
            assertThat(job.streams.single().completedBlocks).isEmpty()
        }

    @Test
    fun `a transient server error is retried`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content, respond = { _, attempt -> if (attempt == 1) 503 else 200 })
            val job = job(threads = 1)

            assertThat(downloader(cdn).run(job)).isEqualTo(TransferResult.Completed)
            assertThat(
                job.streams
                    .single()
                    .file
                    .readBytes(),
            ).isEqualTo(content)
        }

    @Test
    fun `a stop keeps the finished blocks for a later resume`() =
        runTest(dispatcher) {
            lateinit var job: TransferJob
            val cdn =
                FakeCdn(content, respond = { _, attempt ->
                    if (attempt == 2) job.stop()
                    200
                })
            job = job(threads = 1)

            val result = downloader(cdn).run(job)

            assertThat(result).isEqualTo(TransferResult.Stopped)
            assertThat(job.streams.single().completedBlocks).containsExactly(0)
        }

    @Test
    fun `without clen the size comes from a status-checked probe`() =
        runTest(dispatcher) {
            val job = job(withClen = false)

            assertThat(downloader(FakeCdn(content)).run(job)).isEqualTo(TransferResult.Completed)
            assertThat(job.streams.single().totalBytes).isEqualTo(content.size.toLong())
        }

    @Test
    fun `a probe the server refuses reports the refusal rather than a size`() =
        runTest(dispatcher) {
            val job = job(withClen = false)

            assertThat(downloader(FakeCdn(content, respond = { _, _ -> 403 })).run(job)).isInstanceOf(TransferResult.Denied::class.java)
        }

    @Test
    fun `saved blocks are restored after a missing size is probed`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content)
            val job = job(withClen = false, threads = 1)
            val stream = job.streams.single()
            stream.file.writeBytes(content)
            val saved =
                TransferState(
                    listOf(
                        TransferState.StreamState(
                            StreamRole.AUDIO,
                            140,
                            content.size.toLong(),
                            listOf(0, 1),
                            emptyMap(),
                        ),
                    ),
                )

            assertThat(downloader(cdn).run(job, saved)).isEqualTo(TransferResult.Completed)
            assertThat(cdn.requests.get()).isEqualTo(2)
            assertThat(stream.file.readBytes()).isEqualTo(content)
            assertThat(job.downloadedBytes).isEqualTo(content.size.toLong())
        }

    @Test
    fun `a probed size mismatch discards the saved blocks`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content)
            val job = job(withClen = false, threads = 1)
            val stream = job.streams.single()
            stream.file.writeBytes(ByteArray(content.size - 1))
            val saved =
                TransferState(
                    listOf(
                        TransferState.StreamState(
                            StreamRole.AUDIO,
                            140,
                            content.size.toLong() - 1,
                            listOf(0, 1),
                            emptyMap(),
                        ),
                    ),
                )

            assertThat(downloader(cdn).run(job, saved)).isEqualTo(TransferResult.Completed)
            assertThat(cdn.requests.get()).isEqualTo(1 + RangeDownloader.blockCount(content.size.toLong()))
            assertThat(stream.file.readBytes()).isEqualTo(content)
        }

    @Test
    fun `a 200 that ignores the range fails instead of corrupting the file`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content, ignoreRange = true)
            val job = job(threads = 1)

            val result = downloader(cdn).run(job)

            assertThat(result).isInstanceOf(TransferResult.Failed::class.java)
            // The block from byte 0 is written correctly; the misaligned blocks must never be marked done.
            assertThat(job.streams.single().completedBlocks).doesNotContain(1)
            assertThat(
                job.streams
                    .single()
                    .file
                    .readBytes(),
            ).isNotEqualTo(content)
        }

    @Test
    fun `a 200 whose Content-Range starts elsewhere is not written`() =
        runTest(dispatcher) {
            val cdn = FakeCdn(content, contentRangeStart = 0L)
            val job = job(threads = 1)

            val result = downloader(cdn).run(job)

            assertThat(result).isInstanceOf(TransferResult.Failed::class.java)
            assertThat(job.streams.single().completedBlocks).doesNotContain(1)
        }
}
