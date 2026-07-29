package io.github.aedev.flow.player.cache

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.datasource.YouTubeHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@UnstableApi
class PlayerCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "PlayerCacheManager"

        /**
         * Cache size resolved by the last [preload], or null if preload has not run.
         *
         * This is what keeps [initialize] off DataStore on the cold-start path: the preference
         * has already been read on a background thread by the time the player is built.
         */
        @Volatile
        private var preloadedCacheSizeBytes: Long? = null

        /**
         * Warms the cache-size preference and the shared [SimpleCache] on [Dispatchers.IO].
         *
         * Both are disk-bound — DataStore reads and parses its backing file, and SimpleCache
         * opens a SQLite index and scans the cache directory — so neither belongs on the
         * main thread during startup. Call this before [initialize] on any latency-sensitive
         * path; [initialize] stays correct without it, just blocking.
         */
        suspend fun preload(context: Context): Unit = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val configured = runCatching {
                PlayerConfig.cacheSizeMbToBytes(PlayerPreferences(appContext).mediaCacheSizeMb.first())
            }.getOrDefault(0L)
            val resolved = if (configured <= 0) PlayerConfig.CACHE_SIZE_BYTES else configured
            preloadedCacheSizeBytes = resolved
            runCatching { SharedPlayerCacheProvider.getOrCreate(appContext, maxCacheSizeBytes = resolved) }
                .onFailure { Log.w(TAG, "Cache preload failed; initialize() will retry", it) }
        }
    }

    private var cache: SimpleCache? = null
    
    // Data source factories
    private var sharedDataSourceFactory: DataSource.Factory? = null
    private var sharedDashDataSourceFactory: DataSource.Factory? = null
    private var sharedProgressiveDataSourceFactory: DataSource.Factory? = null
    private var sharedHlsDataSourceFactory: DataSource.Factory? = null
    private var sharedLiveDashDataSourceFactory: DataSource.Factory? = null
    private var sharedLiveHlsDataSourceFactory: DataSource.Factory? = null
    
    /**
     * Initialize cache and data source factories.
     */
    fun initialize(): Boolean {
        // Build shared DataSource Factories (NewPipe Architecture)
        val dashHttpFactory = YouTubeHttpDataSource.Factory()
        val progressiveHttpFactory = YouTubeHttpDataSource.Factory()
        val hlsHttpFactory = YouTubeHttpDataSource.Factory()

        val dashUpstream = DefaultDataSource.Factory(context, dashHttpFactory)
        val progressiveUpstream = DefaultDataSource.Factory(context, progressiveHttpFactory)
        val hlsUpstream = DefaultDataSource.Factory(context, hlsHttpFactory)
        sharedLiveDashDataSourceFactory = dashUpstream
        sharedLiveHlsDataSourceFactory = hlsUpstream
        
        // Legacy/Fallback
        val legacyHttpFactory = YouTubeHttpDataSource.Factory()
        val upstream = DefaultDataSource.Factory(context, legacyHttpFactory)

        try {
            // Falls back to a blocking read only when preload() has not run — the media service
            // can build a player without going through the cold-start path.
            val cacheSizeBytes = preloadedCacheSizeBytes ?: kotlinx.coroutines.runBlocking {
                PlayerConfig.cacheSizeMbToBytes(PlayerPreferences(context).mediaCacheSizeMb.first())
            }
            cache = SharedPlayerCacheProvider.getOrCreate(
                context,
                maxCacheSizeBytes = if (cacheSizeBytes <= 0) PlayerConfig.CACHE_SIZE_BYTES else cacheSizeBytes
            )
            
            val cacheFactory = CacheDataSource.Factory()
                .setCache(cache!!)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            // Create the 3 specific factories
            sharedDashDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache!!)
                .setUpstreamDataSourceFactory(dashUpstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                
            sharedProgressiveDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache!!)
                .setUpstreamDataSourceFactory(progressiveUpstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                
            sharedHlsDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache!!)
                .setUpstreamDataSourceFactory(hlsUpstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            sharedDataSourceFactory = cacheFactory.setUpstreamDataSourceFactory(upstream)
            
            Log.d(TAG, "Cache initialized successfully")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Cache not available, using upstream only", e)
            sharedDataSourceFactory = upstream
            sharedDashDataSourceFactory = dashUpstream
            sharedProgressiveDataSourceFactory = progressiveUpstream
            sharedHlsDataSourceFactory = hlsUpstream
            sharedLiveDashDataSourceFactory = dashUpstream
            sharedLiveHlsDataSourceFactory = hlsUpstream
            return false
        }
    }
    
    /**
     * Get the legacy/default data source factory.
     */
    fun getDataSourceFactory(): DataSource.Factory? = sharedDataSourceFactory
    
    /**
     * Get the DASH-specific data source factory.
     */
    fun getDashDataSourceFactory(): DataSource.Factory? = sharedDashDataSourceFactory

    /**
     * Live DASH manifests are timeline data, so keep them off the persistent media cache.
     */
    fun getLiveDashDataSourceFactory(): DataSource.Factory? = sharedLiveDashDataSourceFactory
    
    /**
     * Get the progressive media data source factory.
     */
    fun getProgressiveDataSourceFactory(): DataSource.Factory? = sharedProgressiveDataSourceFactory
    
    /**
     * Get the HLS data source factory.
     */
    fun getHlsDataSourceFactory(): DataSource.Factory? = sharedHlsDataSourceFactory

    /**
     * Live HLS playlists move constantly, so use an uncached upstream source for them.
     */
    fun getLiveHlsDataSourceFactory(): DataSource.Factory? = sharedLiveHlsDataSourceFactory
    
    /**
     * Get cache size in bytes.
     */
    fun getCacheSize(): Long = cache?.cacheSpace ?: 0L
    
    /**
     * Clear all cached data.
     */
    fun clearCache() {
        try {
            cache?.let { c ->
                val keys = c.keys
                for (key in keys) {
                    c.removeResource(key)
                }
            }
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
    
    /**
     * Release cache resources.
     */
    fun release() {
        try {
            cache = null
            sharedDataSourceFactory = null
            sharedDashDataSourceFactory = null
            sharedProgressiveDataSourceFactory = null
            sharedHlsDataSourceFactory = null
            sharedLiveDashDataSourceFactory = null
            sharedLiveHlsDataSourceFactory = null
            Log.d(TAG, "Cache references cleared (SimpleCache lifecycle managed by SharedPlayerCacheProvider)")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing cache", e)
        }
    }
    
    /**
     * Check if cache is initialized and available.
     */
    fun isCacheAvailable(): Boolean = cache != null
}
