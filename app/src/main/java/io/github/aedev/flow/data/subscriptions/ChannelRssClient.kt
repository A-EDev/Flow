package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.network.AppProxyManager
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
class ChannelRssClient
    @Inject
    constructor() {
        private companion object {
            const val RSS_URL_FORMAT = "https://www.youtube.com/feeds/videos.xml?channel_id=%s"
            const val TIMEOUT_SECONDS = 30L
        }

        private val lock = Any()

        @Volatile
        private var cachedClient: OkHttpClient? = null

        @Volatile
        private var cachedProxySignature: String? = null

        /**
         * Rebuilt only when the proxy configuration changes, so a refresh over hundreds of channels
         * reuses one connection pool while a proxy switch still takes effect without a restart.
         */
        private fun client(): OkHttpClient {
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

        /**
         * A [Result] rather than a nullable feed: the callers distinguish "this channel has nothing
         * new" from "this channel could not be reached", and only the latter is worth surfacing.
         */
        suspend fun fetch(channelId: String): Result<ChannelRssFeed> =
            withContext(PerformanceDispatcher.networkIO) {
                runCatching {
                    val request = Request.Builder().url(String.format(RSS_URL_FORMAT, channelId)).build()
                    client().newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code} for channel $channelId")
                        }
                        val body = response.body?.string()
                        if (body.isNullOrEmpty()) {
                            error("Empty RSS body for channel $channelId")
                        }
                        ChannelRssParser.parse(body)
                    }
                }
            }
    }
