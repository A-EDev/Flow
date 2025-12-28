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
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor

/**
 * Professional YouTube Music Service using NewPipe Extractor
 * Provides access to YouTube's music catalog with trending, search, genres, and playlists
 */
object YouTubeMusicService {
    
    private const val TAG = "YouTubeMusicService"
    
    // Curated Playlist IDs for high-quality music data
    private val CHART_GLOBAL_TOP_100 = "PL4fGSI1pDJn6jUjI2i02v9d8X7r5E8y7-"
    private val TRENDING_MUSIC = "PLFgquLnL59alW3ElNjauCx5hG308liqnW"
    private val NEW_RELEASES = "PLFi9SREyid_zhE_Uv5B_sQat4FjL8R5Pz"
    
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
    
    // Popular artist queries for quality content
    private val popularArtistQueries = listOf(
        "The Weeknd", "Taylor Swift", "Drake", "Ariana Grande", 
        "Ed Sheeran", "Billie Eilish", "Post Malone", "Dua Lipa", 
        "Bad Bunny", "SZA", "Kendrick Lamar", "Bruno Mars"
    )

    // Blacklist for non-music content and compilations
    private val blacklistKeywords = listOf(
        "top 50", "top 40", "top 100", "best of", "compilation", "full album", 
        "collection", "mix 2024", "mix 2025", "mashup", "mega mix", "nonstop", "best songs",
        "review", "tutorial", "update", "news", "gameplay", "walkthrough", "react", "reaction",
        "trailer", "teaser", "interview", "podcast", "live stream", "unboxing", "tech", "coding",
        "agent", "software", "chatgpt", "ai", "gemini", "claude", "copilot", "how to", "explained"
    )

    private val trendingMusicQueries = listOf(
        "official music video 2025",
        "new songs 2025 official",
        "top music hits 2025",
        "trending songs 2025"
    )
    
    /**
     * Fetch trending music tracks from YouTube
     * Prioritizes curated charts and trending playlists
     */
    suspend fun fetchTrendingMusic(limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        
        try {
            // Strategy 1: Fetch from Global Top 100 Chart
            tracks.addAll(fetchPlaylistTracks(CHART_GLOBAL_TOP_100))
            
            // Strategy 2: Add some variety via specific searches (what the user liked)
            if (tracks.size < limit) {
                val varietyQuery = trendingMusicQueries.random()
                tracks.addAll(searchMusic(varietyQuery, 20))
            }
            
            // Strategy 3: Trending Music Playlist
            if (tracks.size < limit) {
                tracks.addAll(fetchPlaylistTracks(TRENDING_MUSIC))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchTrendingMusic", e)
        }
        
        val result = tracks.distinctBy { it.videoId }.take(limit)
        Log.d(TAG, "Fetched ${result.size} trending music tracks")
        result
    }
    
    /**
     * Fetch new releases using curated playlists
     */
    suspend fun fetchNewReleases(limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val tracks = fetchPlaylistTracks(NEW_RELEASES).take(limit)
            if (tracks.isEmpty()) {
                // Fallback to search
                return@withContext searchMusic("new music releases 2025", limit)
            }
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching new releases", e)
            emptyList()
        }
    }
    
    /**
     * Search for music tracks on YouTube
     * Uses filters to get official audio/videos
     */
    suspend fun searchMusic(query: String, limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            
            // Refine query for better results if it's not already specific
            val refinedQuery = if (query.contains("official") || query.contains("music") || query.contains("song")) query else "$query song"
            
            val searchExtractor = service.getSearchExtractor(refinedQuery, emptyList(), "")
            searchExtractor.fetchPage()
            
            val tracks = searchExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { isMusicContent(it) }
                .take(limit)
                .mapNotNull { item ->
                    try {
                        convertToMusicTrack(item)
                    } catch (e: Exception) {
                        null
                    }
                }
            
            Log.d(TAG, "Found ${tracks.size} tracks for query: $query")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error searching music", e)
            emptyList()
        }
    }
    
    /**
     * Search for artists on YouTube
     */
    suspend fun searchArtists(query: String, limit: Int = 10): List<com.flow.youtube.ui.screens.music.ArtistDetails> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query, listOf("channel"), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items
                .filterIsInstance<org.schabi.newpipe.extractor.channel.ChannelInfoItem>()
                .take(limit)
                .map { item ->
                    val channelId = item.url.substringAfterLast("/")
                    com.flow.youtube.ui.screens.music.ArtistDetails(
                        name = item.name,
                        channelId = channelId,
                        thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url ?: "",
                        subscriberCount = item.subscriberCount,
                        description = item.description ?: "",
                        bannerUrl = "", // Not available in search results
                        topTracks = emptyList() // Not available in search results
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching artists", e)
            emptyList()
        }
    }

    /**
     * Fetch music tracks by genre/mood
     */
    suspend fun fetchMusicByGenre(genre: String, limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            // Strategic search for genres
            val query = when(genre.lowercase()) {
                "workout" -> "high energy workout music 2025"
                "relax" -> "chill lo-fi hip hop relax"
                "focus" -> "deep focus ambient music"
                "energize" -> "party hits 2025"
                else -> "$genre songs 2025"
            }
            
            searchMusic(query, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tracks by genre: $genre", e)
            emptyList()
        }
    }
    
    /**
     * Get detailed stream info
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
     * Get best audio stream URL
     */
    suspend fun getAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = getStreamInfo(videoId)
            val audioStream = streamInfo?.audioStreams
                ?.filter { !it.url.isNullOrEmpty() }
                ?.maxByOrNull { it.averageBitrate }
            
            audioStream?.url
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio URL", e)
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
            
            playlistInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter { isMusicContent(it) } // Filter out compilations in playlists
                .mapNotNull { convertToMusicTrack(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist tracks: $playlistId", e)
            emptyList()
        }
    }

    /**
     * Fetch full playlist details (info + tracks)
     */
    suspend fun fetchPlaylistFullDetails(playlistId: String): Pair<com.flow.youtube.ui.screens.music.MusicPlaylist, List<MusicTrack>>? = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            val playlistInfo = PlaylistInfo.getInfo(service, playlistUrl)
            
            val tracks = playlistInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { convertToMusicTrack(it) }
                
            val info = com.flow.youtube.ui.screens.music.MusicPlaylist(
                id = playlistId,
                title = playlistInfo.name,
                thumbnailUrl = playlistInfo.thumbnails?.maxByOrNull { it.height }?.url ?: "",
                trackCount = tracks.size,
                author = playlistInfo.uploaderName
            )
            
            Pair(info, tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist details: $playlistId", e)
            null
        }
    }
    
    /**
     * Get related/similar music tracks
     */
    suspend fun getRelatedMusic(videoId: String, limit: Int = 20): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val streamInfo = getStreamInfo(videoId)
            streamInfo?.relatedItems
                ?.filterIsInstance<StreamInfoItem>()
                ?.filter { isMusicContent(it) }
                ?.take(limit)
                ?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching related music", e)
            emptyList()
        }
    }

    /**
     * Fetch music from popular artists
     */
    suspend fun fetchPopularArtistMusic(limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val artistQueries = popularArtistQueries.shuffled().take(4)
            val tracks = mutableListOf<MusicTrack>()
            
            artistQueries.forEach { artist ->
                tracks.addAll(searchMusic("$artist hits", 10))
            }
            
            tracks.distinctBy { it.videoId }.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular artist music", e)
            emptyList()
        }
    }

    private fun isMusicContent(item: StreamInfoItem): Boolean {
        val title = item.name.lowercase()
        val duration = item.duration
        
        // Strict duration for individual songs (60s to 10 minutes)
        if (duration < 60 || duration > 600) return false
        
        // Blacklist keywords
        if (blacklistKeywords.any { title.contains(it) }) return false
        
        // Must contain music indicators
        val musicIndicators = listOf(
            "official video", "official music", "official audio", "official lyric",
            "music video", "lyric video", "audio", "visualizer", "mv", "feat.", "ft.",
            "prod.", "remix", "cover", "soundtrack", "ost"
        )
        
        // If it has a music indicator, it's likely music
        if (musicIndicators.any { title.contains(it) }) return true
        
        // If the uploader is a Topic channel or VEVO
        val uploader = item.uploaderName?.lowercase() ?: ""
        if (uploader.endsWith("topic") || uploader.endsWith("vevo")) return true
        
        return false
    }

    /**
     * Search for playlists (albums)
     */
    suspend fun searchPlaylists(query: String, limit: Int = 10): List<com.flow.youtube.ui.screens.music.MusicPlaylist> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query, emptyList(), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items
                .filterIsInstance<PlaylistInfoItem>()
                .take(limit)
                .map { item ->
                    com.flow.youtube.ui.screens.music.MusicPlaylist(
                        id = item.url.substringAfter("list="),
                        title = item.name,
                        thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url ?: "",
                        trackCount = item.streamCount.toInt(),
                        author = item.uploaderName
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching playlists", e)
            emptyList()
        }
    }

    /**
     * Fetch artist details including top tracks
     */
    suspend fun fetchArtistDetails(channelId: String): com.flow.youtube.ui.screens.music.ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val url = "https://www.youtube.com/channel/$channelId"
            val extractor = service.getChannelExtractor(url)
            extractor.fetchPage()
            
            val channelInfo = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(extractor)
            
            val name = channelInfo.name
            val thumbnail = channelInfo.avatars.maxByOrNull { it.height }?.url ?: ""
            val banner = channelInfo.banners.maxByOrNull { it.height }?.url ?: ""
            val subscriberCount = channelInfo.subscriberCount
            val description = channelInfo.description
            
            // Fetch items (videos) from the channel
            val initialPageItems = try {
                val method = extractor::class.java.methods.firstOrNull { it.name == "getInitialPage" || it.name == "getInitialItems" }
                if (method != null) {
                    val result = method.invoke(extractor)
                    val itemsField = result!!::class.java.getMethod("getItems")
                    @Suppress("UNCHECKED_CAST")
                    (itemsField.invoke(result) as? List<*>)?.filterIsInstance<StreamInfoItem>() ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList<StreamInfoItem>()
            }

            val relatedItems = initialPageItems
                .filter { isMusicContent(it) }
                .mapNotNull { convertToMusicTrack(it) }
                .take(20)
            
            // Fetch albums (playlists)
            val albums = try {
                searchPlaylists("$name albums", 10)
            } catch (e: Exception) {
                emptyList()
            }
                
            com.flow.youtube.ui.screens.music.ArtistDetails(
                name = name,
                channelId = channelId,
                thumbnailUrl = thumbnail,
                subscriberCount = subscriberCount,
                description = description,
                bannerUrl = banner,
                topTracks = relatedItems,
                albums = albums
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist details for $channelId", e)
            null
        }
    }
    
    /**
     * Convert StreamInfoItem to MusicTrack with better metadata extraction
     */
    private fun convertToMusicTrack(item: StreamInfoItem): MusicTrack? {
        try {
            val videoId = item.url.substringAfter("watch?v=").take(11)
            if (videoId.length < 11) return null
            
            val rawTitle = item.name
            val uploader = item.uploaderName ?: "Unknown Artist"
            
            // Improved artist extraction
            val (cleanedTitle, extractedArtist) = parseTitleAndArtist(rawTitle, uploader)
            val channelId = item.uploaderUrl?.substringAfterLast("/") ?: ""
            
            return MusicTrack(
                videoId = videoId,
                title = cleanedTitle,
                artist = extractedArtist,
                thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url ?: "",
                duration = item.duration.toInt(),
                views = item.viewCount,
                sourceUrl = item.url,
                album = "YouTube Music",
                channelId = channelId
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Parse title and artist from raw YouTube title and uploader name
     */
    private fun parseTitleAndArtist(rawTitle: String, uploader: String): Pair<String, String> {
        val delimiters = listOf(" - ", " – ", " — ", " | ", ": ")
        var title = rawTitle
        var artist = uploader
        
        // Remove common suffixes first
        title = cleanMusicTitle(title)
        
        for (delim in delimiters) {
            if (title.contains(delim)) {
                val parts = title.split(delim)
                if (parts.size >= 2) {
                    val potentialArtist = parts[0].trim()
                    val potentialTitle = parts[1].trim()
                    
                    // If uploader is in the first part, it's definitely Artist - Title
                    if (uploader.lowercase().contains(potentialArtist.lowercase()) || 
                        potentialArtist.lowercase().contains(uploader.lowercase().replace(" official", ""))) {
                        artist = potentialArtist
                        title = potentialTitle
                    } else {
                        // Otherwise, use the part that doesn't look like the uploader as title
                        title = potentialTitle
                        artist = potentialArtist
                    }
                    break
                }
            }
        }
        
        // Final polish for artist name
        artist = artist.replace(Regex(" - Topic$", RegexOption.IGNORE_CASE), "")
                      .replace(Regex(" Official$", RegexOption.IGNORE_CASE), "")
                      .replace(Regex(" VEVO$", RegexOption.IGNORE_CASE), "")
                      .trim()
                      
        return Pair(title, artist)
    }
    
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
            .replace(Regex("\\(.*?Video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?HD.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?4K.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+", RegexOption.IGNORE_CASE), " ")
            .trim()
    }
    
    fun getPopularGenres(): List<String> = musicGenres
    
    suspend fun fetchTopPicks(limit: Int = 20): List<MusicTrack> = fetchTrendingMusic(limit)
}
