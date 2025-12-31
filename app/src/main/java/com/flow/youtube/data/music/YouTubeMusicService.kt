package com.flow.youtube.data.music

import android.util.Log
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.SongItem
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
     * Uses Innertube (Hybrid approach) for better metadata
     */
    suspend fun fetchTrendingMusic(limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        
        try {
            // Priority: Innertube Home Data (Official YT Music Home)
            val innertubeTracks = com.flow.youtube.data.newmusic.InnertubeMusicService.fetchTrendingMusic()
            if (innertubeTracks.isNotEmpty()) {
                Log.d(TAG, "Fetched ${innertubeTracks.size} tracks from Innertube")
                tracks.addAll(innertubeTracks)
            } else {
                // Fallback to NewPipe Strategy
                // Strategy 1: Fetch from Global Top 100 Chart
                tracks.addAll(fetchPlaylistTracks(CHART_GLOBAL_TOP_100))
                
                // Strategy 2: Trending Music Playlist
                if (tracks.size < limit) {
                    tracks.addAll(fetchPlaylistTracks(TRENDING_MUSIC))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchTrendingMusic", e)
             // Fallback to NewPipe Strategy on error
            try {
                tracks.addAll(fetchPlaylistTracks(CHART_GLOBAL_TOP_100))
            } catch (ignore: Exception) {}
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
     * Uses Innertube for better results
     */
    suspend fun searchMusic(query: String, limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            // Try Innertube first
            val innertubeResults = com.flow.youtube.data.newmusic.InnertubeMusicService.searchMusic(query)
            if (innertubeResults.isNotEmpty()) {
                return@withContext innertubeResults.take(limit)
            }
        
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
     * Get best video stream URL (synced with audio)
     */
    suspend fun getVideoUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = getStreamInfo(videoId)
            // Prefer video streams that have both audio and video if possible, 
            // but usually we want the highest quality video stream
            val videoStream = streamInfo?.videoStreams
                ?.filter { !it.url.isNullOrEmpty() }
                ?.maxByOrNull { it.bitrate }
            
            videoStream?.url
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video URL", e)
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
    suspend fun fetchPlaylistDetails(playlistId: String): com.flow.youtube.ui.screens.music.PlaylistDetails? = withContext(Dispatchers.IO) {
        try {
            // Check if it's an album/single (browseId) or a playlist
            if (playlistId.startsWith("MPREb_") || playlistId.startsWith("FMDM") || playlistId.startsWith("OLAK")) {
                val albumResult = YouTube.album(playlistId)
                val albumPage = albumResult.getOrNull()
                if (albumPage != null) {
                    val tracks = albumPage.songs.mapNotNull { convertSongItemToMusicTrack(it) }
                    val totalSeconds = tracks.sumOf { it.duration }
                    val durationText = formatTotalDuration(totalSeconds)
                    
                    return@withContext com.flow.youtube.ui.screens.music.PlaylistDetails(
                        id = playlistId,
                        title = albumPage.album.title,
                        thumbnailUrl = albumPage.album.thumbnail,
                        author = albumPage.album.artists?.firstOrNull()?.name ?: "Unknown Artist",
                        authorId = albumPage.album.artists?.firstOrNull()?.id,
                        authorAvatarUrl = null,
                        trackCount = tracks.size,
                        description = null,
                        views = null,
                        durationText = durationText,
                        dateText = albumPage.album.year?.toString(),
                        tracks = tracks
                    )
                }
            }

            // Fallback to Playlist strategy
            val service = ServiceList.YouTube
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            val playlistInfo = PlaylistInfo.getInfo(service, playlistUrl)
            
            val tracks = playlistInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { convertToMusicTrack(it) }
            
            val totalSeconds = tracks.sumOf { it.duration }
            val durationText = formatTotalDuration(totalSeconds)

            com.flow.youtube.ui.screens.music.PlaylistDetails(
                id = playlistId,
                title = playlistInfo.name,
                thumbnailUrl = playlistInfo.thumbnails?.maxByOrNull { it.height }?.url ?: "",
                author = playlistInfo.uploaderName ?: "Unknown",
                authorId = playlistInfo.uploaderUrl?.substringAfterLast("/"),
                authorAvatarUrl = null,
                trackCount = tracks.size,
                description = playlistInfo.description?.content,
                views = null,
                durationText = durationText,
                dateText = null,
                tracks = tracks
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist details: $playlistId", e)
            null
        }
    }

    private fun formatTotalDuration(totalSeconds: Int): String {
        if (totalSeconds <= 0) return ""
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "$hours hr $minutes min" else "$minutes min $seconds sec"
    }

    private fun convertSongItemToMusicTrack(item: com.flow.youtube.innertube.models.SongItem): MusicTrack? {
        return MusicTrack(
            videoId = item.id,
            title = item.title,
            artist = item.artists.firstOrNull()?.name ?: "Unknown Artist",
            thumbnailUrl = item.thumbnail,
            duration = item.duration ?: 0,
            views = 0,
            album = item.album?.name ?: "",
            channelId = item.artists.firstOrNull()?.id ?: "",
            isExplicit = item.explicit
        )
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
        
        // Relaxed duration for music content (30s to 20 minutes)
        if (duration < 30 || duration > 1200) return false
        
        // Blacklist keywords
        if (blacklistKeywords.any { title.contains(it) }) return false
        
        // Must contain music indicators
        val musicIndicators = listOf(
            "official video", "official music", "official audio", "official lyric",
            "music video", "lyric video", "audio", "visualizer", "mv", "feat.", "ft.",
            "prod.", "remix", "cover", "soundtrack", "ost", "song", "track", "single"
        )
        
        // If it has a music indicator, it's likely music
        if (musicIndicators.any { title.contains(it) }) return true
        
        // If the uploader is a Topic channel or VEVO
        val uploader = item.uploaderName?.lowercase() ?: ""
        if (uploader.endsWith("topic") || uploader.endsWith("vevo") || uploader.contains("records")) return true
        
        // Most music videos are between 2 and 5 minutes
        return duration in 120..420
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
    /**
     * Fetch artist details including top tracks
     */
    suspend fun fetchArtistDetails(channelId: String): com.flow.youtube.ui.screens.music.ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            // Priority: Innertube Data (Rich Metadata)
            val innertubeDetails = com.flow.youtube.data.newmusic.InnertubeMusicService.fetchArtistDetails(channelId)
            if (innertubeDetails != null) {
                return@withContext innertubeDetails
            }
            
            // Fallback: NewPipe Strategy
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
