package com.flow.youtube.data.shorts

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.InterestProfile
import com.flow.youtube.data.repository.YouTubeRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.random.Random

private val Context.shortsDataStore: DataStore<Preferences> by preferencesDataStore(name = "flow_shorts")

/**
 * Enhanced Shorts Repository
 * 
 * Provides variety-focused shorts fetching with:
 * - Multi-query rotation (trending, topic-based, viral)
 * - Session-based caching with shuffle on launch
 * - Interest profile integration for personalized shorts
 * - Recently shown tracking to prevent repeats
 */
class ShortsRepository private constructor(private val context: Context) {
    
    private val youtubeRepository = YouTubeRepository.getInstance()
    private val interestProfile = InterestProfile.getInstance(context)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "ShortsRepository"
        
        // DataStore keys
        private val CACHED_SHORTS_KEY = stringPreferencesKey("cached_shorts")
        private val LAST_FETCH_KEY = longPreferencesKey("last_shorts_fetch")
        private val SESSION_ID_KEY = longPreferencesKey("shorts_session_id")
        private val RECENTLY_SHOWN_KEY = stringSetPreferencesKey("recently_shown_shorts")
        
        // Configuration
        private const val CACHE_DURATION_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val MAX_RECENTLY_SHOWN = 50
        private const val SHORTS_PER_QUERY = 15
        private const val HOME_SHORTS_COUNT = 8
        
        // Variety queries for shorts discovery
        private val BASE_QUERIES = listOf(
            "#shorts",
            "shorts trending",
            "viral shorts 2024",
            "funny shorts",
            "satisfying shorts",
            "shorts compilation"
        )
        
        // Topic-specific queries for personalization
        private val TOPIC_SHORTS_QUERIES = mapOf(
            "gaming" to listOf("gaming shorts", "minecraft shorts", "fortnite shorts"),
            "music" to listOf("music shorts", "song shorts", "dance shorts"),
            "comedy" to listOf("funny shorts", "comedy shorts", "meme shorts"),
            "tech" to listOf("tech shorts", "coding shorts", "gadget shorts"),
            "sports" to listOf("sports shorts", "football shorts", "basketball shorts"),
            "food" to listOf("food shorts", "cooking shorts", "recipe shorts"),
            "fitness" to listOf("fitness shorts", "workout shorts", "gym shorts")
        )
        
        @Volatile
        private var INSTANCE: ShortsRepository? = null
        
        fun getInstance(context: Context): ShortsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShortsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Get shorts for home screen display with variety
     */
    suspend fun getHomeFeedShorts(): List<Video> = withContext(Dispatchers.IO) {
        try {
            val currentSession = getOrCreateSessionId()
            
            // Check if we need fresh shorts
            val cached = getCachedShorts()
            val lastFetch = getLastFetchTime()
            val cacheAge = System.currentTimeMillis() - lastFetch
            
            val shorts = if (cached.isNotEmpty() && cacheAge < CACHE_DURATION_MS) {
                Log.d(TAG, "Using cached shorts (${cached.size} items, age: ${cacheAge / 1000}s)")
                // Shuffle based on session to provide variety on each launch
                shuffleForSession(cached, currentSession)
            } else {
                Log.d(TAG, "Fetching fresh shorts...")
                fetchAndCacheShorts()
            }
            
            // Filter out recently shown and return
            val recentlyShown = getRecentlyShownIds()
            val filtered = shorts.filter { it.id !in recentlyShown }
            
            val result = if (filtered.size >= HOME_SHORTS_COUNT) {
                filtered.take(HOME_SHORTS_COUNT)
            } else {
                // Not enough after filtering, include some from recently shown
                (filtered + shorts.filter { it.id in recentlyShown }.shuffled())
                    .distinctBy { it.id }
                    .take(HOME_SHORTS_COUNT)
            }
            
            // Mark these as recently shown
            markAsShown(result.map { it.id })
            
            Log.d(TAG, "Returning ${result.size} shorts for home feed")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get home shorts", e)
            // Fallback: try basic fetch
            try {
                val (shorts, _) = youtubeRepository.getShorts()
                shorts.take(HOME_SHORTS_COUNT)
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Fetch shorts using multiple strategies for variety
     */
    private suspend fun fetchAndCacheShorts(): List<Video> = coroutineScope {
        Log.d(TAG, "============================================")
        Log.d(TAG, "Starting Multi-Query Shorts Fetch")
        Log.d(TAG, "============================================")
        
        // Get user's top interests for personalization
        val topGenres = interestProfile.getTopGenres(3)
        Log.d(TAG, "User top genres: $topGenres")
        
        // Build query list: base queries + personalized queries
        val queries = mutableListOf<String>()
        
        // Add 2-3 random base queries
        queries.addAll(BASE_QUERIES.shuffled().take(3))
        
        // Add topic-specific queries based on user interests
        topGenres.forEach { genre ->
            TOPIC_SHORTS_QUERIES[genre.lowercase()]?.let { topicQueries ->
                queries.add(topicQueries.random())
            }
        }
        
        // If no interests, add more variety from base
        if (topGenres.isEmpty()) {
            queries.addAll(BASE_QUERIES.shuffled().take(2))
        }
        
        Log.d(TAG, "Fetching shorts with queries: $queries")
        
        // Fetch in parallel
        val results = queries.map { query ->
            async {
                try {
                    fetchShortsWithQuery(query)
                } catch (e: Exception) {
                    Log.e(TAG, "Query '$query' failed: ${e.message}")
                    emptyList()
                }
            }
        }.awaitAll()
        
        // Merge, deduplicate, and shuffle
        val allShorts = results.flatten()
            .distinctBy { it.id }
            .filter { it.duration in 1..60 } // Ensure they're actually shorts
            .shuffled()
        
        Log.d(TAG, "Fetched ${allShorts.size} unique shorts")
        
        // Cache the results
        cacheShorts(allShorts)
        
        allShorts
    }
    
    /**
     * Fetch shorts using a specific search query
     */
    private suspend fun fetchShortsWithQuery(query: String): List<Video> = withContext(Dispatchers.IO) {
        try {
            val (videos, _) = youtubeRepository.searchVideos(query)
            videos
                .filter { it.duration in 1..60 } // Filter to shorts only
                .take(SHORTS_PER_QUERY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch shorts for '$query'", e)
            emptyList()
        }
    }
    
    /**
     * Shuffle shorts deterministically based on session ID
     */
    private fun shuffleForSession(shorts: List<Video>, sessionId: Long): List<Video> {
        val random = Random(sessionId)
        return shorts.shuffled(random)
    }
    
    /**
     * Get or create a session ID (changes on each app launch)
     */
    private suspend fun getOrCreateSessionId(): Long {
        val prefs = context.shortsDataStore.data.first()
        val storedSession = prefs[SESSION_ID_KEY]
        
        // Generate new session ID based on current time bucket (changes every 30 min)
        val currentBucket = System.currentTimeMillis() / (30 * 60 * 1000L)
        
        if (storedSession == null || storedSession != currentBucket) {
            context.shortsDataStore.edit { it[SESSION_ID_KEY] = currentBucket }
            return currentBucket
        }
        
        return storedSession
    }
    
    /**
     * Cache shorts to DataStore
     */
    private suspend fun cacheShorts(shorts: List<Video>) {
        context.shortsDataStore.edit { prefs ->
            val cacheEntries = shorts.map { CachedShort.fromVideo(it) }
            prefs[CACHED_SHORTS_KEY] = gson.toJson(cacheEntries)
            prefs[LAST_FETCH_KEY] = System.currentTimeMillis()
        }
        Log.d(TAG, "💾 Cached ${shorts.size} shorts")
    }
    
    /**
     * Get cached shorts
     */
    private suspend fun getCachedShorts(): List<Video> {
        val prefs = context.shortsDataStore.data.first()
        val json = prefs[CACHED_SHORTS_KEY] ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<CachedShort>>() {}.type
            val cached: List<CachedShort> = gson.fromJson(json, type)
            cached.map { it.toVideo() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached shorts", e)
            emptyList()
        }
    }
    
    /**
     * Get last fetch timestamp
     */
    private suspend fun getLastFetchTime(): Long {
        return context.shortsDataStore.data.first()[LAST_FETCH_KEY] ?: 0L
    }
    
    /**
     * Get recently shown short IDs
     */
    private suspend fun getRecentlyShownIds(): Set<String> {
        return context.shortsDataStore.data.first()[RECENTLY_SHOWN_KEY] ?: emptySet()
    }
    
    /**
     * Mark shorts as recently shown
     */
    private suspend fun markAsShown(ids: List<String>) {
        context.shortsDataStore.edit { prefs ->
            val current = prefs[RECENTLY_SHOWN_KEY] ?: emptySet()
            val updated = (current + ids).toList().takeLast(MAX_RECENTLY_SHOWN).toSet()
            prefs[RECENTLY_SHOWN_KEY] = updated
        }
    }
    
    /**
     * Clear shorts cache (for testing/debugging)
     */
    suspend fun clearCache() {
        context.shortsDataStore.edit { prefs ->
            prefs.remove(CACHED_SHORTS_KEY)
            prefs.remove(LAST_FETCH_KEY)
            prefs.remove(RECENTLY_SHOWN_KEY)
        }
    }
    
    /**
     * Force refresh shorts
     */
    suspend fun forceRefresh(): List<Video> = withContext(Dispatchers.IO) {
        clearCache()
        getHomeFeedShorts()
    }
}

/**
 * Serializable cached short for DataStore
 */
private data class CachedShort(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val uploadDate: String,
    val channelThumbnailUrl: String,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toVideo(): Video = Video(
        id = id,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        viewCount = viewCount,
        uploadDate = uploadDate,
        channelThumbnailUrl = channelThumbnailUrl
    )
    
    companion object {
        fun fromVideo(video: Video): CachedShort = CachedShort(
            id = video.id,
            title = video.title,
            channelName = video.channelName,
            channelId = video.channelId,
            thumbnailUrl = video.thumbnailUrl,
            duration = video.duration,
            viewCount = video.viewCount,
            uploadDate = video.uploadDate,
            channelThumbnailUrl = video.channelThumbnailUrl
        )
    }
}
