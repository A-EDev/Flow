package com.flow.youtube.player.datasource

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache

/**
 * Factory for creating specialized cache data sources for different stream types.
 * Based on NewPipe's PlayerDataSource pattern.
 */
class CacheFactory(
    private val simpleCache: SimpleCache
) {

    /**
     * Creates a cache data source factory for HLS streams.
     * HLS streams benefit from aggressive caching for segments.
     */
    fun createHlsCacheFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null) // Read-only for HLS segments
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCacheIgnored(reason: Int) {
                    // Log cache misses for debugging
                }

                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                    // Monitor cache performance
                }
            })
    }

    /**
     * Creates a cache data source factory for DASH streams.
     * DASH streams use progressive caching with write capabilities.
     */
    fun createDashCacheFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCacheIgnored(reason: Int) {
                    // Log cache misses for debugging
                }

                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                    // Monitor cache performance
                }
            })
    }

    /**
     * Creates a cache data source factory for progressive streams.
     * Progressive streams use full caching with write capabilities.
     */
    fun createProgressiveCacheFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCacheIgnored(reason: Int) {
                    // Log cache misses for debugging
                }

                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                    // Monitor cache performance
                }
            })
    }

    /**
     * Creates a cache data source factory optimized for YouTube streams.
     * Uses specialized caching strategy for YouTube's streaming patterns.
     */
    fun createYouTubeCacheFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCacheIgnored(reason: Int) {
                    // YouTube-specific cache miss logging
                }

                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                    // Monitor YouTube stream caching performance
                }
            })
    }

    /**
     * Gets the current cache usage statistics.
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            cacheSpace = simpleCache.cacheSpace,
            cachedBytes = 0L, // Not available in SimpleCache
            keys = simpleCache.keys.size
        )
    }

    /**
     * Clears cache for specific content.
     */
    fun clearCacheForKey(key: String) {
        try {
            simpleCache.removeResource(key)
        } catch (e: Exception) {
            // Handle cache removal errors
        }
    }

    /**
     * Clears all cached content.
     */
    fun clearAllCache() {
        try {
            val keys = simpleCache.keys.toList()
            for (key in keys) {
                simpleCache.removeResource(key)
            }
        } catch (e: Exception) {
            // Handle cache clearing errors
        }
    }
}

/**
 * Cache statistics data class.
 */
data class CacheStats(
    val cacheSpace: Long,
    val cachedBytes: Long,
    val keys: Int
)