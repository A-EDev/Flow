package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.network.AppProxyManager
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place the app fetches a YouTube channel's RSS feed.
 *
 * Both the subscription feed and the background new-upload check go through here, so there is one
 * connection pool, one timeout policy and one parser for the same endpoint.
 */
@Singleton
class ChannelRssClient internal constructor(
    private val httpClient: () -> OkHttpClient,
    private val sleep: suspend (Long) -> Unit,
) {
    @Inject
    constructor() : this(ProxyAwareClient()::get, { delay(it) })

    /**
     * A [Result] rather than a nullable feed: the callers distinguish "this channel has nothing
     * new" from "this channel could not be reached", and only the latter is worth surfacing.
     *
     * A sweep over hundreds of channels meets the odd 429 or 5xx; one spaced retry turns most of
     * them into a feed instead of a channel whose uploads vanish until the next refresh.
     */
    suspend fun fetch(channelId: String): Result<ChannelRssFeed> =
        withContext(PerformanceDispatcher.networkIO) {
            val first = attempt(channelId)
            val retryAfterMs = first.retryAfterMs ?: return@withContext first.result
            sleep(retryAfterMs)
            attempt(channelId).result
        }

    private class Attempt(
        val result: Result<ChannelRssFeed>,
        val retryAfterMs: Long? = null,
    )

    private fun attempt(channelId: String): Attempt =
        try {
            val request = Request.Builder().url(String.format(RSS_URL_FORMAT, channelId)).build()
            httpClient().newCall(request).execute().use { response ->
                val code = response.code
                when {
                    code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR -> {
                        Attempt(
                            result = Result.failure(IOException("HTTP $code for channel $channelId")),
                            retryAfterMs = retryDelayMs(response.header("Retry-After")),
                        )
                    }

                    !response.isSuccessful -> {
                        Attempt(Result.failure(IOException("HTTP $code for channel $channelId")))
                    }

                    else -> {
                        val body = response.body.string()
                        if (body.isEmpty()) {
                            Attempt(Result.failure(IOException("Empty RSS body for channel $channelId")))
                        } else {
                            Attempt(runCatching { ChannelRssParser.parse(body) })
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Attempt(Result.failure(e), retryAfterMs = DEFAULT_RETRY_DELAY_MS)
        } catch (e: RuntimeException) {
            Attempt(Result.failure(e))
        }

    private fun retryDelayMs(retryAfterHeader: String?): Long =
        retryAfterHeader
            ?.trim()
            ?.toLongOrNull()
            ?.times(1000L)
            ?.coerceIn(DEFAULT_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)
            ?: DEFAULT_RETRY_DELAY_MS

    /**
     * Rebuilt only when the proxy configuration changes, so a refresh over hundreds of channels
     * reuses one connection pool while a proxy switch still takes effect without a restart.
     */
    private class ProxyAwareClient {
        private val lock = Any()

        @Volatile
        private var cachedClient: OkHttpClient? = null

        @Volatile
        private var cachedProxySignature: String? = null

        fun get(): OkHttpClient {
            val signature = AppProxyManager.currentSignature()
            cachedClient?.let { if (cachedProxySignature == signature) return it }
            return synchronized(lock) {
                cachedClient?.let { if (cachedProxySignature == signature) return it }
                AppProxyManager
                    .applyTo(OkHttpClient.Builder())
                    .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
                    .also {
                        cachedClient = it
                        cachedProxySignature = signature
                    }
            }
        }
    }

    private companion object {
        const val RSS_URL_FORMAT = "https://www.youtube.com/feeds/videos.xml?channel_id=%s"
        const val TIMEOUT_SECONDS = 30L
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        const val DEFAULT_RETRY_DELAY_MS = 1_500L
        const val MAX_RETRY_DELAY_MS = 10_000L
    }
}
