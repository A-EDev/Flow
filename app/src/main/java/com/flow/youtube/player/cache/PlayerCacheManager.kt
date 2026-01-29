package com.flow.youtube.player.cache

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import com.flow.youtube.player.config.PlayerConfig
import com.flow.youtube.player.datasource.YouTubeHttpDataSource
import java.io.File

@UnstableApi
class PlayerCacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PlayerCacheManager"
    }
    
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var cache: SimpleCache? = null
    
    // Data source factories
    private var sharedDataSourceFactory: DataSource.Factory? = null
    private var sharedDashDataSourceFactory: DataSource.Factory? = null
    private var sharedProgressiveDataSourceFactory: DataSource.Factory? = null
    private var sharedHlsDataSourceFactory: DataSource.Factory? = null
    
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
        
        // Legacy/Fallback
        val legacyHttpFactory = YouTubeHttpDataSource.Factory()
        val upstream = DefaultDataSource.Factory(context, legacyHttpFactory)

        try {
            databaseProvider = StandaloneDatabaseProvider(context)
            val cacheDir = File(context.cacheDir, PlayerConfig.CACHE_DIR_NAME)
            val evictor = LeastRecentlyUsedCacheEvictor(PlayerConfig.CACHE_SIZE_BYTES)
            cache = SimpleCache(cacheDir, evictor, databaseProvider!!)
            
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
     * Get the progressive media data source factory.
     */
    fun getProgressiveDataSourceFactory(): DataSource.Factory? = sharedProgressiveDataSourceFactory
    
    /**
     * Get the HLS data source factory.
     */
    fun getHlsDataSourceFactory(): DataSource.Factory? = sharedHlsDataSourceFactory
    
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
            cache?.release()
            cache = null
            databaseProvider = null
            sharedDataSourceFactory = null
            sharedDashDataSourceFactory = null
            sharedProgressiveDataSourceFactory = null
            sharedHlsDataSourceFactory = null
            Log.d(TAG, "Cache resources released")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing cache", e)
        }
    }
    
    /**
     * Check if cache is initialized and available.
     */
    fun isCacheAvailable(): Boolean = cache != null
}
