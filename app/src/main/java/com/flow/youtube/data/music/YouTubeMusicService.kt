package com.flow.youtube.data.music

import android.util.Log
import com.flow.youtube.ui.screens.music.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

/**
 * Professional YouTube Music Service using NewPipe Extractor
 * Provides access to YouTube's music catalog with trending, search, genres, and playlists
 */
object YouTubeMusicService {
    
    private const val TAG = "YouTubeMusicService"
    
    // Popular music categories and search queries
    private val musicGenres = listOf(
        "Pop Music",
        "Hip Hop Music",
        "Rock Music",
        "Electronic Music",
        "R&B Music",
        "Jazz Music",
        "Classical Music",
        "Country Music",
        "Indie Music",
        "Latin Music",
        "K-Pop Music",
        "Dance Music",
        "Reggae Music",
        "Blues Music",
        "Metal Music"
    )
    
    // Curated search queries for trending and popular music
    private val trendingMusicQueries = listOf(
        "official music video 2025",
        "top 100 songs 2025",
        "billboard hot 100",
        "viral music hits",
        "popular songs 2025"
    )
    
    // Popular artist queries for quality content
    private val popularArtistQueries = listOf(
        "Taylor Swift official",
        "Drake official",
        "The Weeknd official",
        "Ariana Grande official",
        "Ed Sheeran official",
        "Billie Eilish official",
        "Post Malone official",
        "Dua Lipa official",
        "Bad Bunny official",
        "SZA official"
    )
    
    /**
     * Fetch trending music tracks from YouTube
     * Uses multiple high-quality search queries to get popular and trending music
     */
    suspend fun fetchTrendingMusic(limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val tracks = mutableListOf<MusicTrack>()
            
            // Strategy 1: Get official music videos from trending query
            try {
                val trendingExtractor = service.getSearchExtractor(
                    "official music video 2025", 
                    emptyList(), 
                    ""
                )
                trendingExtractor.fetchPage()
                
                val trendingTracks = trendingExtractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .filter { item ->
                        val duration = item.duration
                        val title = item.name.lowercase()
                        // Filter for music: proper length and contains music keywords
                        duration in 120..600 && // 2-10 minutes (typical music video length)
                        (title.contains("official") || 
                         title.contains("music video") ||
                         title.contains("mv") ||
                         item.viewCount > 1000000) // Popular videos
                    }
                    .take(20)
                    .mapNotNull { item ->
                        try {
                            convertToMusicTrack(item)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting track: ${e.message}")
                            null
                        }
                    }
                
                tracks.addAll(trendingTracks)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching official music videos", e)
            }
            
            // Strategy 2: Get from popular artists if we need more
            if (tracks.size < limit / 2) {
                try {
                    val artistQuery = popularArtistQueries.random()
                    val artistExtractor = service.getSearchExtractor(artistQuery, emptyList(), "")
                    artistExtractor.fetchPage()
                    
                    val artistTracks = artistExtractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                        .filter { it.duration in 120..600 }
                        .take(15)
                        .mapNotNull { item ->
                            try {
                                convertToMusicTrack(item)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    
                    tracks.addAll(artistTracks)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching artist tracks", e)
                }
            }
            
            // Strategy 3: Get top hits to fill remaining slots
            if (tracks.size < limit) {
                try {
                    val hitsExtractor = service.getSearchExtractor(
                        "top music hits 2025", 
                        emptyList(), 
                        ""
                    )
                    hitsExtractor.fetchPage()
                    
                    val hitTracks = hitsExtractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                        .filter { it.duration in 120..600 && it.viewCount > 500000 }
                        .take(limit - tracks.size)
                        .mapNotNull { item ->
                            try {
                                convertToMusicTrack(item)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    
                    tracks.addAll(hitTracks)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching top hits", e)
                }
            }
            
            val uniqueTracks = tracks.distinctBy { it.videoId }.take(limit)
            Log.d(TAG, "Fetched ${uniqueTracks.size} trending music tracks from YouTube")
            uniqueTracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending music", e)
            emptyList()
        }
    }
    
    /**
     * Search for music tracks on YouTube
     * Filters results to prioritize audio/music content
     */
    suspend fun searchMusic(query: String, limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            
            // Add "music" or "audio" to query if not present to get better results
            val musicQuery = if (query.lowercase().contains("music") || 
                                 query.lowercase().contains("song") ||
                                 query.lowercase().contains("audio")) {
                query
            } else {
                "$query music"
            }
            
            val searchExtractor = service.getSearchExtractor(musicQuery, emptyList(), "")
            searchExtractor.fetchPage()
            
            val tracks = searchExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { 
                    // Filter for music content
                    it.duration in 60..900 || // 1-15 minutes (typical music length)
                    it.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_STREAM
                }
                .take(limit)
                .mapNotNull { item ->
                    try {
                        convertToMusicTrack(item)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting search result: ${e.message}")
                        null
                    }
                }
            
            Log.d(TAG, "Found ${tracks.size} music tracks for query: $query")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error searching music", e)
            emptyList()
        }
    }
    
    /**
     * Fetch music tracks by genre
     * Uses genre-specific search queries to get relevant tracks
     */
    suspend fun fetchMusicByGenre(genre: String, limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            
            // Create genre-specific search query
            val genreQuery = "$genre songs 2025"
            val searchExtractor = service.getSearchExtractor(genreQuery, emptyList(), "")
            searchExtractor.fetchPage()
            
            val tracks = searchExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 60..900 } // Music length filter
                .take(limit)
                .mapNotNull { item ->
                    try {
                        convertToMusicTrack(item)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting genre track: ${e.message}")
                        null
                    }
                }
            
            Log.d(TAG, "Fetched ${tracks.size} tracks for genre: $genre")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tracks by genre", e)
            emptyList()
        }
    }
    
    /**
     * Get detailed stream info for a music track
     */
    suspend fun getStreamInfo(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val url = "https://www.youtube.com/watch?v=$videoId"
            StreamInfo.getInfo(service, url)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stream info for $videoId", e)
            null
        }
    }
    
    /**
     * Get best audio stream URL for a music track
     * Prioritizes high-quality audio streams
     */
    suspend fun getAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = getStreamInfo(videoId)
            
            // Get best quality audio stream
            val audioStream = streamInfo?.audioStreams
                ?.filter { stream -> 
                    val url = stream.url
                    url != null && url.isNotEmpty()
                }
                ?.maxByOrNull { it.averageBitrate }
            
            val audioUrl = audioStream?.url
            
            if (audioUrl != null && audioUrl.isNotEmpty()) {
                Log.d(TAG, "Found audio URL for $videoId: ${audioStream.averageBitrate} kbps")
            } else {
                Log.w(TAG, "No audio stream found for $videoId")
            }
            
            audioUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio URL for $videoId", e)
            null
        }
    }
    
    /**
     * Fetch tracks from a YouTube playlist
     */
    suspend fun fetchPlaylistTracks(playlistId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            val playlistInfo = PlaylistInfo.getInfo(service, playlistUrl)
            
            val tracks = playlistInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item ->
                    try {
                        convertToMusicTrack(item)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting playlist track: ${e.message}")
                        null
                    }
                }
            
            Log.d(TAG, "Fetched ${tracks.size} tracks from playlist")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist tracks", e)
            emptyList()
        }
    }
    
    /**
     * Get popular music genres
     */
    fun getPopularGenres(): List<String> = musicGenres
    
    /**
     * Get related/similar music tracks
     * Uses YouTube's related videos feature
     */
    suspend fun getRelatedMusic(videoId: String, limit: Int = 20): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val streamInfo = getStreamInfo(videoId)
            
            val relatedTracks = streamInfo?.relatedItems
                ?.filterIsInstance<StreamInfoItem>()
                ?.filter { it.duration in 60..900 } // Filter for music length
                ?.take(limit)
                ?.mapNotNull { item ->
                    try {
                        convertToMusicTrack(item)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting related track: ${e.message}")
                        null
                    }
                } ?: emptyList()
            
            Log.d(TAG, "Fetched ${relatedTracks.size} related tracks for $videoId")
            relatedTracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching related music", e)
            emptyList()
        }
    }
    
    /**
     * Fetch top picks / featured music
     * Uses popular artists and curated queries for high-quality results
     */
    suspend fun fetchTopPicks(limit: Int = 20): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val tracks = mutableListOf<MusicTrack>()
            
            // Get tracks from multiple popular artists
            val artistsToQuery = popularArtistQueries.shuffled().take(3)
            
            artistsToQuery.forEach { artistQuery ->
                try {
                    val searchExtractor = service.getSearchExtractor(artistQuery, emptyList(), "")
                    searchExtractor.fetchPage()
                    
                    val artistTracks = searchExtractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                        .filter { item ->
                            item.duration in 120..600 && // 2-10 minutes
                            item.viewCount > 1000000 // Popular tracks only
                        }
                        .take(7)
                        .mapNotNull { item ->
                            try {
                                convertToMusicTrack(item)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    
                    tracks.addAll(artistTracks)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching from artist: $artistQuery", e)
                }
            }
            
            val uniqueTracks = tracks.distinctBy { it.videoId }.take(limit)
            Log.d(TAG, "Fetched ${uniqueTracks.size} top picks from popular artists")
            uniqueTracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching top picks", e)
            emptyList()
        }
    }
    
    /**
     * Fetch music from popular artists
     * Returns tracks from well-known, popular artists
     */
    suspend fun fetchPopularArtistMusic(limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val tracks = mutableListOf<MusicTrack>()
            
            // Randomly select popular artists
            val selectedArtists = popularArtistQueries.shuffled().take(5)
            
            selectedArtists.forEach { artistQuery ->
                try {
                    val searchExtractor = service.getSearchExtractor(artistQuery, emptyList(), "")
                    searchExtractor.fetchPage()
                    
                    val artistTracks = searchExtractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                        .filter { it.duration in 120..600 }
                        .take(6)
                        .mapNotNull { item ->
                            try {
                                convertToMusicTrack(item)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    
                    tracks.addAll(artistTracks)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching artist tracks", e)
                }
            }
            
            val uniqueTracks = tracks.distinctBy { it.videoId }.take(limit)
            Log.d(TAG, "Fetched ${uniqueTracks.size} tracks from popular artists")
            uniqueTracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular artist music", e)
            emptyList()
        }
    }
    
    /**
     * Convert StreamInfoItem to MusicTrack
     */
    private fun convertToMusicTrack(item: StreamInfoItem): MusicTrack {
        // Extract video ID from URL
        val videoId = item.url.substringAfter("watch?v=").take(11)
        
        // Extract artist from uploader name or title
        val title = item.name
        val artist = item.uploaderName ?: extractArtistFromTitle(title)
        
        return MusicTrack(
            videoId = videoId,
            title = cleanMusicTitle(title),
            artist = artist,
            thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url ?: "",
            duration = item.duration.toInt(),
            views = item.viewCount,
            sourceUrl = item.url
        )
    }
    
    /**
     * Clean music title by removing common suffixes
     */
    private fun cleanMusicTitle(title: String): String {
        return title
            .replace(Regex("\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Official.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Lyric.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Lyric.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Audio.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Audio.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Music.*?Video\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Music.*?Video\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?HD.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+", RegexOption.IGNORE_CASE), " ")
            .trim()
    }
    
    /**
     * Extract artist from title if not available
     */
    private fun extractArtistFromTitle(title: String): String {
        // Try to extract artist from "Artist - Song" format
        val parts = title.split("-", "–", "—")
        return if (parts.size >= 2) {
            parts[0].trim()
        } else {
            "Unknown Artist"
        }
    }
}
