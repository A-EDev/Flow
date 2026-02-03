package com.flow.youtube.data.download

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
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
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.flow.youtube.innertube.models.YouTubeClient.Companion.IPADOS
import com.flow.youtube.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.flow.youtube.innertube.models.YouTubeClient.Companion.MOBILE
import com.flow.youtube.innertube.models.YouTubeClient.Companion.TVHTML5
import com.flow.youtube.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.flow.youtube.innertube.models.YouTubeClient.Companion.IOS
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB
import com.flow.youtube.innertube.models.YouTubeClient.Companion.WEB_CREATOR
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
    private val songUrlCache = HashMap<String, Triple<String, String, Long>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    private fun createProxyAwareOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .build()
    }

    val dataSourceFactory = ResolvingDataSource.Factory(
        CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(createProxyAwareOkHttpClient()))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    ) { dataSpec ->
        val mediaId = dataSpec.key 
            ?: dataSpec.uri.host 
            ?: error("No media id in dataSpec: ${dataSpec.uri}")

        android.util.Log.d("DownloadUtil", "Resolving DataSpec for $mediaId. Key: ${dataSpec.key}, URI: ${dataSpec.uri}")

        try {
            val cachedSpans = downloadCache.getCachedSpans(mediaId)
            if (cachedSpans.isNotEmpty()) {
                android.util.Log.d("DownloadUtil", "Short-circuit resolution: content $mediaId is in downloadCache")
                return@Factory dataSpec
            }
        } catch (e: Exception) {
            android.util.Log.w("DownloadUtil", "Error checking cache spans for $mediaId: ${e.message}")
        }

        songUrlCache[mediaId]?.takeIf { it.third > System.currentTimeMillis() }?.let { (url, ua, _) ->
            android.util.Log.d("DownloadUtil", "Using memory-cached URL and UA for $mediaId")
            return@Factory dataSpec.buildUpon()
                .setUri(url.toUri())
                .setHttpRequestHeaders(mapOf("User-Agent" to ua))
                .build()
        }

        var audioUrl: String? = null
        var userAgent: String? = null
        var expiration: Long = 0L

        try {
            val playbackDataResult = runBlocking(Dispatchers.IO) {
                MusicPlayerUtils.playerResponseForPlayback(mediaId)
            }
            
            val playbackData = playbackDataResult.getOrThrow()
            audioUrl = playbackData.streamUrl
            userAgent = playbackData.usedClient.userAgent
            expiration = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds - 60) * 1000L
            
            android.util.Log.d("DownloadUtil", "Resolved using MusicPlayerUtils with client: ${playbackData.usedClient.clientName}")
            
        } catch (e: Exception) {
             android.util.Log.e("DownloadUtil", "MusicPlayerUtils resolution failed for $mediaId", e)
        }

        if (audioUrl == null || userAgent == null) {
             android.util.Log.e("DownloadUtil", "All clients failed to resolve $mediaId")
             throw IOException("Could not resolve URL for $mediaId after trying all clients")
        }
        
        songUrlCache[mediaId] = Triple(audioUrl!!, userAgent!!, expiration)

        val resolvedDataSpec = dataSpec.buildUpon()
            .setUri(audioUrl.toUri())
            .setHttpRequestHeaders(mapOf("User-Agent" to userAgent))
            .build()
        
        android.util.Log.d("DownloadUtil", "Final DataSpec for $mediaId: URI=${resolvedDataSpec.uri}")
        resolvedDataSpec
    }

    fun invalidateUrlCache(mediaId: String) {
        songUrlCache.remove(mediaId)
        android.util.Log.d("DownloadUtil", "Invalidated URL cache for $mediaId")
    }

    fun clearUrlCache() {
        songUrlCache.clear()
        android.util.Log.d("DownloadUtil", "Cleared all URL cache entries")
    }
    
    fun getPlayerDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(downloadCache) 
            .setCacheKeyFactory { dataSpec ->

                dataSpec.key ?: dataSpec.uri.host ?: dataSpec.uri.toString()
            }
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
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
