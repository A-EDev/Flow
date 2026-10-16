package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OriginalTitleRepositoryTest {
    private val requests = AtomicInteger()

    private fun repository(
        gate: CountDownLatch? = null,
        answer: (String) -> Pair<Int, String>,
    ): OriginalTitleRepository {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    requests.incrementAndGet()
                    gate?.await(5, TimeUnit.SECONDS)
                    val videoUrl =
                        chain
                            .request()
                            .url
                            .queryParameter("url")
                            .orEmpty()
                    val (code, body) = answer(videoUrl)
                    if (code < 0) throw IOException("offline")
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return OriginalTitleRepository(client, Dispatchers.IO)
    }

    @Test
    fun `the oEmbed title is the original one`() =
        runBlocking {
            val titles = repository { 200 to """{"title":"XIAOMI 17T - ПРОСТОЙ, ПРИЯТНЫЙ ФЛАГМАН!","author_name":"DimaViper"}""" }

            assertThat(titles.originalTitle("2BAEyzqxAME")).isEqualTo("XIAOMI 17T - ПРОСТОЙ, ПРИЯТНЫЙ ФЛАГМАН!")
            assertThat(titles.cached("2BAEyzqxAME")).isEqualTo("XIAOMI 17T - ПРОСТОЙ, ПРИЯТНЫЙ ФЛАГМАН!")
        }

    @Test
    fun `it asks for the watch url of the video`() =
        runBlocking {
            var asked = ""
            val titles = repository { url -> (200 to """{"title":"t"}""").also { asked = url } }

            titles.originalTitle("abc123def45")

            assertThat(asked).isEqualTo("https://www.youtube.com/watch?v=abc123def45")
        }

    @Test
    fun `a page watching the cache sees a lookup started elsewhere`() =
        runBlocking {
            val titles = repository { 200 to """{"title":"Нагиев – пенсии, стих в Кремле / вДудь"}""" }
            val seen = async { titles.cachedUpdates("8sWj1Q2SQGQ").take(2).toList() }
            yield()

            titles.originalTitle("8sWj1Q2SQGQ")

            assertThat(withTimeout(5_000) { seen.await() })
                .containsExactly(null, "Нагиев – пенсии, стих в Кремле / вДудь")
                .inOrder()
            assertThat(requests.get()).isEqualTo(1)
        }

    @Test
    fun `a video is fetched once however often it is asked for`() =
        runBlocking {
            val titles = repository { 200 to """{"title":"t"}""" }

            repeat(3) { titles.originalTitle("abc123def45") }

            assertThat(requests.get()).isEqualTo(1)
        }

    @Test
    fun `cards asking at the same time share one request`() =
        runBlocking {
            val gate = CountDownLatch(1)
            val titles = repository(gate) { 200 to """{"title":"t"}""" }

            val asks = (1..5).map { async(Dispatchers.IO) { titles.originalTitle("abc123def45") } }
            Thread.sleep(200)
            gate.countDown()

            assertThat(asks.awaitAll()).containsExactly("t", "t", "t", "t", "t")
            assertThat(requests.get()).isEqualTo(1)
        }

    @Test
    fun `embedding turned off is remembered as no original`() =
        runBlocking {
            val titles = repository { 401 to "Unauthorized" }

            assertThat(titles.originalTitle("abc123def45")).isNull()
            assertThat(titles.originalTitle("abc123def45")).isNull()
            assertThat(requests.get()).isEqualTo(1)
        }

    @Test
    fun `a network failure is asked again next time`() =
        runBlocking {
            val titles = repository { -1 to "" }

            assertThat(titles.originalTitle("abc123def45")).isNull()
            assertThat(titles.originalTitle("abc123def45")).isNull()
            assertThat(requests.get()).isEqualTo(2)
        }

    @Test
    fun `a body without a title is no original`() {
        assertThat(OriginalTitleRepository.parseTitle("""{"author_name":"x"}""")).isNull()
        assertThat(OriginalTitleRepository.parseTitle("""{"title":"  "}""")).isNull()
        assertThat(OriginalTitleRepository.parseTitle("<html>")).isNull()
    }
}
