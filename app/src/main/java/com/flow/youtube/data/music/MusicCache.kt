package com.flow.youtube.data.music

import com.flow.youtube.ui.screens.music.MusicTrack
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Memory cache for music data to enable instant loading
 */
object MusicCache {
    private val mutex = Mutex()
    
    // Cache with timestamp for expiration
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes
    
    private val trendingCache = ConcurrentHashMap<String, CacheEntry<List<MusicTrack>>>()
    private val genreCache = ConcurrentHashMap<String, CacheEntry<List<MusicTrack>>>()
    private val searchCache = ConcurrentHashMap<String, CacheEntry<List<MusicTrack>>>()
    private val relatedCache = ConcurrentHashMap<String, CacheEntry<List<MusicTrack>>>()
    
    // Check if cache entry is still valid
    private fun <T> CacheEntry<T>.isValid(): Boolean {
        return System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS
    }
    
    // Trending music
    suspend fun getTrendingMusic(limit: Int): List<MusicTrack>? = mutex.withLock {
        val key = "trending_$limit"
        trendingCache[key]?.takeIf { it.isValid() }?.data
    }
    
    suspend fun cacheTrendingMusic(limit: Int, tracks: List<MusicTrack>) = mutex.withLock {
        val key = "trending_$limit"
        trendingCache[key] = CacheEntry(tracks)
    }
    
    // Genre tracks
    suspend fun getGenreTracks(genre: String, limit: Int): List<MusicTrack>? = mutex.withLock {
        val key = "${genre}_$limit"
        genreCache[key]?.takeIf { it.isValid() }?.data
    }
    
    suspend fun cacheGenreTracks(genre: String, limit: Int, tracks: List<MusicTrack>) = mutex.withLock {
        val key = "${genre}_$limit"
        genreCache[key] = CacheEntry(tracks)
    }
    
    // Search results
    suspend fun getSearchResults(query: String): List<MusicTrack>? = mutex.withLock {
        val key = query.lowercase().trim()
        searchCache[key]?.takeIf { it.isValid() }?.data
    }
    
    suspend fun cacheSearchResults(query: String, tracks: List<MusicTrack>) = mutex.withLock {
        val key = query.lowercase().trim()
        searchCache[key] = CacheEntry(tracks)
    }
    
    // Related music
    suspend fun getRelatedMusic(videoId: String): List<MusicTrack>? = mutex.withLock {
        relatedCache[videoId]?.takeIf { it.isValid() }?.data
    }
    
    suspend fun cacheRelatedMusic(videoId: String, tracks: List<MusicTrack>) = mutex.withLock {
        relatedCache[videoId] = CacheEntry(tracks)
    }
    
    // Clear specific cache
    suspend fun clearTrendingCache() = mutex.withLock {
        trendingCache.clear()
    }
    
    suspend fun clearGenreCache() = mutex.withLock {
        genreCache.clear()
    }
    
    suspend fun clearSearchCache() = mutex.withLock {
        searchCache.clear()
    }
    
    suspend fun clearRelatedCache() = mutex.withLock {
        relatedCache.clear()
    }
    
    // Clear all caches
    suspend fun clearAll() = mutex.withLock {
        trendingCache.clear()
        genreCache.clear()
        searchCache.clear()
        relatedCache.clear()
    }
    
    // Get cache stats for debugging
    fun getCacheStats(): CacheStats {
        return CacheStats(
            trendingCount = trendingCache.size,
            genreCount = genreCache.size,
            searchCount = searchCache.size,
            relatedCount = relatedCache.size
        )
    }
}

data class CacheStats(
    val trendingCount: Int,
    val genreCount: Int,
    val searchCount: Int,
    val relatedCount: Int
)
