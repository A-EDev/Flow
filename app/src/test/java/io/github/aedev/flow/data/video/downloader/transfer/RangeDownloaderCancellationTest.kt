package io.github.aedev.flow.data.video.downloader.transfer

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RangeDownloaderCancellationTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `stopping a download cancels a stalled length probe`() =
        runBlocking {
            stalledProbe { downloader, job, accepted ->
                val transfer = async(Dispatchers.IO) { downloader.run(job) }
                accepted.get(3, TimeUnit.SECONDS)
                job.stop()
                assertThat(withTimeout(3_000) { transfer.await() }).isEqualTo(TransferResult.Stopped)
                assertThat(
                    job.streams
                        .single()
                        .file
                        .length(),
                ).isEqualTo(0L)
            }
        }

    @Test
    fun `coroutine cancellation releases a stalled length probe`() =
        runBlocking {
            stalledProbe { downloader, job, accepted ->
                val transfer = async(Dispatchers.IO) { downloader.run(job) }
                accepted.get(3, TimeUnit.SECONDS)
                withTimeout(3_000) { transfer.cancelAndJoin() }
                assertThat(job.status).isEqualTo(TransferStatus.STOPPED)
            }
        }

    private suspend fun stalledProbe(work: suspend (RangeDownloader, TransferJob, CompletableFuture<Socket>) -> Unit) {
        val server = ServerSocket(0)
        val accepted = CompletableFuture<Socket>()
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            try {
                accepted.complete(server.accept())
            } catch (e: Exception) {
                accepted.completeExceptionally(e)
            }
        }
        val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
        val job =
            TransferJob(
                "probe",
                listOf(TransferStream(StreamRole.VIDEO, "http://127.0.0.1:${server.localPort}/stream", folder.newFile(), 137, 0L)),
                1,
                "ua",
            )
        try {
            work(RangeDownloader({ client }, Dispatchers.IO), job, accepted)
        } finally {
            job.stop()
            server.close()
            accepted.getNow(null)?.close()
            executor.shutdownNow()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }
}
