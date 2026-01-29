package com.flow.youtube.data.recommendation

import android.content.Context
import android.util.Log
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.music.PlaylistRepository
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.innertube.models.AlbumItem
import com.flow.youtube.innertube.models.PlaylistItem
import com.flow.youtube.innertube.models.YTItem
import com.flow.youtube.innertube.models.WatchEndpoint
import com.flow.youtube.innertube.pages.HomePage
import com.flow.youtube.ui.screens.music.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class MusicSection(
    val title: String,
    val subtitle: String? = null,
    val tracks: List<MusicTrack>
)

/**
 * Advanced Music Recommendation Algorithm (FlowMusicAlgorithm)
 * 
 * A hybrid recommendation engine that combines:
 * 1. YouTube Music's native Home Feed (Gold Standard)
 * 2. Collaborative Filtering (Seeds + Related)
 * 3. Global Trends & Charts
 * 4. User Library Signals (History, Favorites)
 */
@Singleton
class MusicRecommendationAlgorithm @Inject constructor(
    private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val likedVideosRepository: LikedVideosRepository,
    private val subscriptionRepository: com.flow.youtube.data.local.SubscriptionRepository,
    private val viewHistory: ViewHistory,
    private val youTube: YouTube,
    private val cacheDao: com.flow.youtube.data.local.dao.CacheDao
) {

    companion object {
        private const val TAG = "MusicRecAlgo"
    }

    /**
     * Loads the full Music Home experience, including dynamic sections.
     *  INSTANT LOAD: Returns cached data immediately if available.
     * Then refreshes from network in background (ViewModel handles flow/state).
     */
    suspend fun loadMusicHome(): List<MusicSection> = withContext(Dispatchers.IO) {
        val cachedSections = cacheDao.getMusicHomeSections().firstOrNull()
        if (cachedSections != null && cachedSections.isNotEmpty()) {
            Log.d(TAG, "Loaded ${cachedSections.size} sections from cache")
            // Deserialize and return immediately
            val musicSections = cachedSections.map { entity ->
                MusicSection(
                    title = entity.title,
                    subtitle = entity.subtitle,
                    tracks = deserializeTracks(entity.tracksJson)
                )
            }
            return@withContext musicSections
        }

        return@withContext fetchAndCacheHome()
    }
    
    /**
     * Force refresh content from network and update cache
     */
    suspend fun refreshMusicHome(): List<MusicSection> = withContext(Dispatchers.IO) {
        fetchAndCacheHome()
    }

    private suspend fun fetchAndCacheHome(): List<MusicSection> {
        try {
            val homePage = youTube.home().getOrNull()
            if (homePage != null) {
                val sections = parseHomeSections(homePage)
                
                // Cache them
                val entities = sections.mapIndexed { index, section ->
                    com.flow.youtube.data.local.entity.MusicHomeCacheEntity(
                        sectionId = "section_$index",
                        title = section.title,
                        subtitle = section.subtitle,
                        tracksJson = serializeTracks(section.tracks),
                        orderBy = index
                    )
                }
                cacheDao.clearMusicHomeCache()
                cacheDao.insertMusicHomeSections(entities)
                
                return sections
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Music Home", e)
        }
        return emptyList()
    }

    private fun serializeTracks(tracks: List<MusicTrack>): String {
        val jsonArray = org.json.JSONArray()
        tracks.forEach { track ->
            val obj = org.json.JSONObject()
            obj.put("id", track.videoId)
            obj.put("title", track.title)
            obj.put("artist", track.artist)
            obj.put("thumb", track.thumbnailUrl)
            obj.put("dur", track.duration)
            obj.put("cid", track.channelId)
            obj.put("views", track.views)
            obj.put("album", track.album)
            obj.put("expl", track.isExplicit)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
    
    private fun deserializeTracks(json: String): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                tracks.add(MusicTrack(
                    videoId = obj.optString("id"),
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    thumbnailUrl = obj.optString("thumb"),
                    duration = obj.optInt("dur"),
                    channelId = obj.optString("cid"),
                    views = obj.optLong("views"),
                    album = obj.optString("album"),
                    isExplicit = obj.optBoolean("expl")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing tracks", e)
        }
        return tracks
    }

    /**
     * Generate personalized music recommendations (Quick Picks / For You).
     * Tries to use the official "Quick Picks" or "Start Radio" from Home first.
     * Falls back to internal algorithm if Home is unavailable.
     */
    suspend fun getRecommendations(limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        // 1. Try to get from Home Page "Quick Picks" or similar
        try {
            val homePage = youTube.home().getOrNull()
            if (homePage != null) {
                val quickPicks = homePage.sections.find { 
                    it.title.contains("Quick picks", true) || 
                    it.title.contains("Start radio", true) ||
                    it.title.contains("Mixed for you", true) ||
                    it.title.contains("Recommended", true) ||
                    it.title.contains("Listen again", true)
                }
                
                if (quickPicks != null) {
                    val tracks = quickPicks.items.filterIsInstance<SongItem>().map { mapSongItem(it) }
                    if (tracks.isNotEmpty()) {
                        Log.d(TAG, "Using Home Page Quick Picks: ${tracks.size}")
                        return@withContext tracks.take(limit)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Home for recommendations", e)
        }

        // 2. Fallback: Internal Hybrid Algorithm
        return@withContext generateFallbackRecommendations(limit)
    }

    private suspend fun generateFallbackRecommendations(limit: Int): List<MusicTrack> = coroutineScope {
        val candidates = mutableListOf<MusicTrack>()
        val seenIds = mutableSetOf<String>()

        // Gather User Signals
        val favorites = likedVideosRepository.getAllLikedVideos().firstOrNull()?.map { 
             MusicTrack(
                 videoId = it.videoId,
                 title = it.title,
                 artist = it.channelName,
                 thumbnailUrl = it.thumbnail,
                 duration = 0,
                 channelId = "",
                 views = 0L
             )
        } ?: emptyList()
        
        val history = playlistRepository.history.firstOrNull() ?: emptyList()
        
        // Seeds: Mix of history and favorites
        val seeds = (history.take(5) + favorites.take(5)).shuffled().take(4)
        
        val deferreds = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

        // A. Related to Seeds
        seeds.forEach { seed ->
            deferreds.add(async {
                try {
                    val nextResult = youTube.next(WatchEndpoint(videoId = seed.videoId)).getOrNull()
                    val relatedEndpoint = nextResult?.relatedEndpoint
                    if (relatedEndpoint != null) {
                        val related = youTube.related(relatedEndpoint).getOrNull()
                        related?.songs?.forEach { song ->
                            addCandidate(song, candidates, seenIds)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching related for ${seed.title}", e)
                }
                Unit
            })
        }

        // B. Charts (Trending)
        deferreds.add(async {
            try {
                val charts = youTube.getChartsPage().getOrNull()
                charts?.sections?.forEach { section ->
                    if (section.title.contains("Top", true) || section.title.contains("Trending", true)) {
                            section.items.filterIsInstance<SongItem>().forEach { song ->
                                addCandidate(song, candidates, seenIds)
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching charts", e)
            }
            Unit
        })

        deferreds.awaitAll()

        // Scoring & Ranking (Simple version: Shuffle for now, but could be enhanced)
        val finalRecommendations = candidates.shuffled().take(limit)
        Log.d(TAG, "Generated ${finalRecommendations.size} fallback recommendations")
        return@coroutineScope finalRecommendations
    }

    private fun parseHomeSections(homePage: HomePage): List<MusicSection> {
        return homePage.sections.mapNotNull { section ->
            // Broaden filter to include Songs, Albums, Playlists
            val tracks = section.items.mapNotNull { mapYTItem(it) }
            
            if (tracks.isNotEmpty()) {
                MusicSection(
                    title = section.title,
                    subtitle = section.label,
                    tracks = tracks
                )
            } else {
                null
            }
        }
    }

    private fun mapYTItem(item: YTItem): MusicTrack? {
        return when (item) {
            is SongItem -> mapSongItem(item)
            is AlbumItem -> MusicTrack(
                videoId = item.id,
                title = item.title,
                artist = item.artists?.joinToString(", ") { it.name } ?: "",
                thumbnailUrl = item.thumbnail,
                duration = 0,
                channelId = "",
                views = 0L,
                album = "Album",
                isExplicit = item.explicit
            )
            is PlaylistItem -> MusicTrack(
                videoId = item.id, // Playlist ID
                title = item.title,
                artist = item.author?.name ?: "",
                thumbnailUrl = item.thumbnail ?: "",
                duration = 0,
                channelId = "",
                views = 0L,
                album = "Playlist"
            )
            else -> null
        }
    }

    private fun addCandidate(
        song: SongItem, 
        candidates: MutableList<MusicTrack>, 
        seenIds: MutableSet<String>
    ) {
        synchronized(seenIds) {
            if (song.id !in seenIds) {
                seenIds.add(song.id)
                candidates.add(mapSongItem(song))
            }
        }
    }

    private fun mapSongItem(song: SongItem): MusicTrack {
        return MusicTrack(
            videoId = song.id,
            title = song.title,
            artist = song.artists.joinToString(", ") { it.name },
            thumbnailUrl = song.thumbnail,
            duration = song.duration ?: 0,
            channelId = song.artists.firstOrNull()?.id ?: "",
            views = parseViewCount(song.viewCountText),
            album = song.album?.name ?: "",
            isExplicit = song.explicit
        )
    }

    suspend fun getGenreContent(genre: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            // Search for the genre to get relevant songs
            val searchResults = youTube.search(query = "$genre music", filter = YouTube.SearchFilter.FILTER_SONG).getOrNull()
            if (searchResults != null) {
                return@withContext searchResults.items
                    .filterIsInstance<SongItem>()
                    .map { mapSongItem(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching genre content for $genre", e)
        }
        return@withContext emptyList()
    }

    private fun parseViewCount(text: String?): Long {
        if (text.isNullOrEmpty()) return 0L
        // Remove "views" and commas
        val cleanText = text.replace(" views", "", ignoreCase = true)
            .replace(" view", "", ignoreCase = true)
            .replace(",", "")
            .trim()
            
        return try {
            when {
                cleanText.endsWith("M", ignoreCase = true) -> {
                    (cleanText.dropLast(1).toDouble() * 1_000_000).toLong()
                }
                cleanText.endsWith("K", ignoreCase = true) -> {
                    (cleanText.dropLast(1).toDouble() * 1_000).toLong()
                }
                cleanText.endsWith("B", ignoreCase = true) -> {
                    (cleanText.dropLast(1).toDouble() * 1_000_000_000).toLong()
                }
                else -> cleanText.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
