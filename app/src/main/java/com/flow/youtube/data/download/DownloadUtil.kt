package com.flow.youtube.data.download

import android.content.Context
import android.util.Log
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.flow.youtube.service.ExoDownloadService
import com.flow.youtube.di.DownloadCache
import com.flow.youtube.di.PlayerCache
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.utils.MusicPlayerUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import java.io.IOException

@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: DatabaseProvider,
    @DownloadCache private val downloadCache: SimpleCache,
    @PlayerCache private val playerCache: SimpleCache,
) {
    companion object {
        private const val TAG = "DownloadUtil"
        private const val CHUNK_LENGTH = 512 * 1024L // 512KB for cache check
    }

    private val songUrlCache = HashMap<String, Triple<String, String, Long>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * DataSource factory for DOWNLOADS - writes to downloadCache.
     * Used by DownloadManager for downloading tracks for offline playback.
     */
    val dataSourceFactory: ResolvingDataSource.Factory = ResolvingDataSource.Factory(
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(downloadCache))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    ) { dataSpec ->
        resolveDataSpec(dataSpec, "Download")
    }

    /**
     * Resolve DataSpec by looking up cached URL or fetching from network.
     */
    private fun resolveDataSpec(dataSpec: DataSpec, source: String): DataSpec {
        val mediaId = dataSpec.key ?: error("No media id (key) in dataSpec")

        Log.d(TAG, "[$source] Resolving for $mediaId")

        try {
            val cachedSpans = downloadCache.getCachedSpans(mediaId)
            if (cachedSpans.isNotEmpty()) {
                val totalCached = cachedSpans.sumOf { it.length }
                Log.d(TAG, "[$source] $mediaId found in downloadCache (${totalCached / 1024}KB)")
                return dataSpec
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$source] Error checking downloadCache for $mediaId: ${e.message}")
        }
        songUrlCache[mediaId]?.takeIf { it.third > System.currentTimeMillis() }?.let { (url, ua, _) ->
            Log.d(TAG, "[$source] Using cached URL for $mediaId")
            return dataSpec.buildUpon()
                .setUri(url.toUri())
                .setHttpRequestHeaders(mapOf("User-Agent" to ua))
                .build()
        }

        Log.d(TAG, "[$source] Resolving URL from network for $mediaId")
        val playbackData = runBlocking(Dispatchers.IO) {
            MusicPlayerUtils.playerResponseForPlayback(mediaId)
        }.getOrElse { e ->
            Log.e(TAG, "[$source] Failed to resolve $mediaId: ${e.message}")
            throw IOException("Could not resolve URL for $mediaId: ${e.message}", e)
        }

        val streamUrl = playbackData.streamUrl
        val userAgent = playbackData.usedClient.userAgent
        val expiration = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds - 60) * 1000L

        songUrlCache[mediaId] = Triple(streamUrl, userAgent, expiration)
        Log.d(TAG, "[$source] Resolved $mediaId via ${playbackData.usedClient.clientName}")

        return dataSpec.buildUpon()
            .setUri(streamUrl.toUri())
            .setHttpRequestHeaders(mapOf("User-Agent" to userAgent))
            .build()
    }

    /**
     * DataSource factory for PLAYBACK - reads from both caches.
     * Chain: downloadCache (read-only) -> playerCache (read-write) -> network
     */
    fun getPlayerDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val downloadCacheFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val playerCacheFactory = CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val cachedDataSourceFactory = downloadCacheFactory
            .setUpstreamDataSourceFactory(playerCacheFactory)

        return ResolvingDataSource.Factory(cachedDataSourceFactory) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id (key) in dataSpec")

            try {
                if (downloadCache.isCached(mediaId, dataSpec.position, maxOf(dataSpec.length, 1))) {
                    Log.d(TAG, "[Player] Serving from downloadCache: $mediaId")
                    return@Factory dataSpec
                }
            } catch (e: Exception) {
                Log.w(TAG, "[Player] downloadCache check error for $mediaId", e)
                try { downloadCache.removeResource(mediaId) } catch (_: Exception) {}
            }

            try {
                if (playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)) {
                    Log.d(TAG, "[Player] Serving from playerCache: $mediaId")
                    return@Factory dataSpec
                }
            } catch (e: Exception) {
                Log.w(TAG, "[Player] playerCache check error for $mediaId", e)
            }

            songUrlCache[mediaId]?.takeIf { it.third > System.currentTimeMillis() }?.let { (url, ua, _) ->
                Log.d(TAG, "[Player] Using cached URL for $mediaId")
                return@Factory dataSpec.buildUpon()
                    .setUri(url.toUri())
                    .setHttpRequestHeaders(mapOf("User-Agent" to ua))
                    .build()
            }

            val playbackData = runBlocking(Dispatchers.IO) {
                MusicPlayerUtils.playerResponseForPlayback(mediaId)
            }.getOrThrow()

            val streamUrl = playbackData.streamUrl
            val userAgent = playbackData.usedClient.userAgent
            val expiration = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds - 60) * 1000L

            songUrlCache[mediaId] = Triple(streamUrl, userAgent, expiration)
            Log.d(TAG, "[Player] Resolved $mediaId via ${playbackData.usedClient.clientName}")

            dataSpec.buildUpon()
                .setUri(streamUrl.toUri())
                .setHttpRequestHeaders(mapOf("User-Agent" to userAgent))
                .build()
        }
    }

    /**
     * Invalidate URL cache for a specific media ID.
     * Called during error recovery.
     */
    fun invalidateUrlCache(mediaId: String) {
        songUrlCache.remove(mediaId)
        Log.d(TAG, "Invalidated URL cache for $mediaId")
    }

    /**
     * Clear all URL cache entries.
     */
    fun clearUrlCache() {
        songUrlCache.clear()
        Log.d(TAG, "Cleared all URL cache entries")
    }

    /**
     * Aggressive cache clear for error recovery.
     * Clears URL cache, player cache, and triggers force refresh.
     */
    fun performAggressiveCacheClear(mediaId: String) {
        Log.d(TAG, "Performing aggressive cache clear for $mediaId")
        
        songUrlCache.remove(mediaId)
        
        try {
            playerCache.removeResource(mediaId)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing playerCache for $mediaId: ${e.message}")
        }
        
        MusicPlayerUtils.forceRefreshForVideo(mediaId)
    }

    /**
     * Check if a track is fully downloaded and available offline.
     */
    fun isFullyDownloaded(mediaId: String): Boolean {
        val download = downloads.value[mediaId] ?: return false
        return download.state == Download.STATE_COMPLETED
    }

    /**
     * Check if a track's audio is in the downloadCache and available for offline playback.
     * This checks the actual cache content, not just the download state.
     */
    fun isCachedForOffline(mediaId: String): Boolean {
        return try {
            val spans = downloadCache.getCachedSpans(mediaId)
            if (spans.isEmpty()) {
                false
            } else {
                val totalCached = spans.sumOf { it.length }
                Log.d(TAG, "[CacheCheck] $mediaId has ${totalCached / 1024}KB in cache")
                totalCached >= 100 * 1024
            }
        } catch (e: Exception) {
            Log.w(TAG, "[CacheCheck] Error checking cache for $mediaId: ${e.message}")
            false
        }
    }

    fun verifyCacheIntegrity(mediaId: String): Boolean {
        return try {
            val spans = downloadCache.getCachedSpans(mediaId)
            spans.isNotEmpty() && spans.all { span ->
                span.file?.exists() == true && span.length > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache integrity check failed for $mediaId", e)
            false
        }
    }

    val downloadNotificationHelper = DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    val downloadManager: DownloadManager = DownloadManager(
        context,
        databaseProvider,
        downloadCache,
        dataSourceFactory,
        Executor(Runnable::run)
    ).apply {
        maxParallelDownloads = 3
        addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                downloads.update { it.toMutableMap().apply { set(download.request.id, download) } }
            }
        })
    }
    
    init {
        val result = mutableMapOf<String, Download>()
        try {
            val cursor = downloadManager.downloadIndex.getDownloads()
            while (cursor.moveToNext()) {
                val download = cursor.download
                result[download.request.id] = download
            }
            cursor.close()
            downloads.value = result
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDownloadManagerInstance() = downloadManager
}
