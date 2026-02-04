package com.flow.youtube.data.newmusic

import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.YouTubeLocale
import com.flow.youtube.innertube.models.YTItem
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.innertube.YouTube.SearchFilter
import com.flow.youtube.innertube.pages.ExplorePage
import com.flow.youtube.innertube.models.SearchSuggestions
import com.flow.youtube.innertube.pages.SearchSummaryPage
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.PlaylistDetails
import com.flow.youtube.ui.screens.music.MusicPlaylist
import com.flow.youtube.ui.screens.music.ArtistDetails
import com.flow.youtube.innertube.pages.AlbumPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Hybrid Music Service using Innertube for metadata and discovery.
 * Inspired by Metrolist's implementation.
 */
object InnertubeMusicService {
    
    init {
        // Initialize Innertube locale
        try {
            YouTube.locale = YouTubeLocale(
                gl = Locale.getDefault().country,
                hl = Locale.getDefault().toLanguageTag()
            )
        } catch (e: Exception) {
            // Fallback if Locale fails
            YouTube.locale = YouTubeLocale(gl = "US", hl = "en")
        }
    }

    /**
     * Fetch trending music tracks from Innertube's Home/Music page.
     * This returns a list of individual tracks found in the home sections.
     */
    suspend fun fetchTrendingMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.home()
            result.getOrNull()?.sections?.flatMap { it.items }
                ?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchExplore(): ExplorePage? = withContext(Dispatchers.IO) {
        try {
            YouTube.explore().getOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchMoodAndGenres(): List<com.flow.youtube.innertube.pages.MoodAndGenres> = withContext(Dispatchers.IO) {
        try {
            YouTube.moodAndGenres().getOrNull() ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Search for songs using Innertube
     */
    suspend fun searchMusic(query: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.search(query, SearchFilter.FILTER_SONG)
            result.getOrNull()?.items?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get search suggestions from Innertube
     */
    suspend fun getSearchSuggestions(query: String): SearchSuggestions? = withContext(Dispatchers.IO) {
        try {
            YouTube.searchSuggestions(query).getOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Search with summary (Top result + categories)
     */
    suspend fun searchWithSummary(query: String): SearchSummaryPage? = withContext(Dispatchers.IO) {
        try {
            YouTube.searchSummary(query).getOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Search for playlists using Innertube
     */
    suspend fun searchPlaylists(query: String): List<MusicPlaylist> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.search(query, SearchFilter.FILTER_FEATURED_PLAYLIST)
            result.getOrNull()?.items?.filterIsInstance<com.flow.youtube.innertube.models.PlaylistItem>()
                ?.map { convertPlaylistToMusicPlaylist(it) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch new release albums from Innertube
     */
    suspend fun fetchNewReleases(): List<MusicPlaylist> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.newReleaseAlbums()
            result.getOrNull()?.map { convertAlbumToPlaylist(it) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch playlist details using Innertube
     */
    suspend fun fetchPlaylistDetails(playlistId: String): PlaylistDetails? = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.playlist(playlistId)
            val page = result.getOrNull() ?: return@withContext null
            
            val tracks = page.songs.mapNotNull { convertToMusicTrack(it) }
            
            PlaylistDetails(
                id = page.playlist.id ?: playlistId,
                title = page.playlist.title,
                thumbnailUrl = page.playlist.thumbnail ?: "",
                author = page.playlist.author?.name ?: "",
                authorId = page.playlist.author?.id,
                trackCount = tracks.size,
                description = null,
                tracks = tracks,
                continuation = page.continuation
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    /**
     * Fetch album details using Innertube
     */
    suspend fun fetchAlbum(albumId: String): PlaylistDetails? = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.album(albumId)
            val page = result.getOrNull() ?: return@withContext null
            
            val tracks = page.songs.mapNotNull { convertToMusicTrack(it) }
            
            PlaylistDetails(
                id = page.album.browseId ?: albumId,
                title = page.album.title ?: "",
                thumbnailUrl = page.album.thumbnail ?: "",
                author = page.album.artists?.joinToString(", ") { it.name } ?: "",
                authorId = page.album.artists?.firstOrNull()?.id,
                trackCount = tracks.size,
                description = page.album.year?.toString(),
                tracks = tracks,
                continuation = null // AlbumPage doesn't have continuation
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get related music using Innertube next endpoint
     */
    suspend fun getRelatedMusic(videoId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val nextResult = YouTube.next(com.flow.youtube.innertube.models.WatchEndpoint(videoId = videoId)).getOrNull()
            val relatedEndpoint = nextResult?.relatedEndpoint
            if (relatedEndpoint != null) {
                val related = YouTube.related(relatedEndpoint).getOrNull()
                related?.songs?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch charts from Innertube
     */
    suspend fun fetchCharts(): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.getChartsPage()
            result.getOrNull()?.sections?.flatMap { it.items }
                ?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch detailed artist information including albums, singles, videos, etc.
     */
    suspend fun fetchArtistDetails(channelId: String): com.flow.youtube.ui.screens.music.ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.artist(channelId)
            val page = result.getOrNull() ?: return@withContext null
            
            val artistItem = page.artist
            
            // Map sections
            var topTracks: List<MusicTrack> = emptyList()
            var albums: List<com.flow.youtube.ui.screens.music.MusicPlaylist> = emptyList()
            var singles: List<com.flow.youtube.ui.screens.music.MusicPlaylist> = emptyList()
            var videos: List<MusicTrack> = emptyList()
            var relatedArtists: List<com.flow.youtube.ui.screens.music.ArtistDetails> = emptyList()
            var featuredOn: List<com.flow.youtube.ui.screens.music.MusicPlaylist> = emptyList()
            
            var albumsBrowseId: String? = null
            var albumsParams: String? = null
            var singlesBrowseId: String? = null
            var singlesParams: String? = null
            
            page.sections.forEach { section ->
                val title = section.title.lowercase()
                when {
                    title.contains("songs") || title.contains("popular") -> {
                        topTracks = section.items.filterIsInstance<SongItem>().mapNotNull { convertToMusicTrack(it) }
                    }
                    title.contains("albums") -> {
                        albums = section.items.filterIsInstance<com.flow.youtube.innertube.models.AlbumItem>().map { convertAlbumToPlaylist(it) }
                        albumsBrowseId = section.moreEndpoint?.browseId
                        albumsParams = section.moreEndpoint?.params
                    }
                    title.contains("singles") || title.contains("ep") -> {
                        singles = section.items.filterIsInstance<com.flow.youtube.innertube.models.AlbumItem>().map { convertAlbumToPlaylist(it) }
                        singlesBrowseId = section.moreEndpoint?.browseId
                        singlesParams = section.moreEndpoint?.params
                    }
                    title.contains("videos") -> {
                        // Videos are often SongItems or video items in Innertube
                        videos = section.items.filterIsInstance<SongItem>().mapNotNull { convertToMusicTrack(it) }
                    }
                    title.contains("fans might also like") || title.contains("related") -> {
                        relatedArtists = section.items.filterIsInstance<com.flow.youtube.innertube.models.ArtistItem>().map { convertArtistItemToDetails(it) }
                    }
                    title.contains("featured on") || title.contains("playlists") -> {
                        featuredOn = section.items.filterIsInstance<com.flow.youtube.innertube.models.PlaylistItem>().map { convertPlaylistToMusicPlaylist(it) }
                    }
                }
            }
            
            com.flow.youtube.ui.screens.music.ArtistDetails(
                name = artistItem.title ?: "Unknown Artist",
                channelId = artistItem.id ?: channelId,
                thumbnailUrl = artistItem.thumbnail ?: "",
                subscriberCount = 0L, // Innertube artist endpoint often doesn't give exact sub count in header
                description = page.description ?: "",
                bannerUrl = "", // Innertube doesn't always strictly give banner in the same way, we'll try to use thumbnail as fallback in UI
                topTracks = topTracks,
                albums = albums,
                singles = singles,
                videos = videos,
                relatedArtists = relatedArtists,
                featuredOn = featuredOn,
                isSubscribed = false,
                albumsBrowseId = albumsBrowseId,
                albumsParams = albumsParams,
                singlesBrowseId = singlesBrowseId,
                singlesParams = singlesParams
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    /**
     * Fetch all items (Albums, Singles, etc.) for a specific artist section
     */
    suspend fun fetchArtistItems(browseId: String, params: String?): List<MusicPlaylist> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.artistItems(com.flow.youtube.innertube.models.BrowseEndpoint(browseId, params))
            result.getOrNull()?.items?.filterIsInstance<com.flow.youtube.innertube.models.AlbumItem>()
                ?.map { convertAlbumToPlaylist(it) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch continuation items for a playlist
     */
    suspend fun fetchPlaylistContinuation(playlistId: String, continuation: String): Pair<List<MusicTrack>, String?> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.playlistContinuation(continuation)
            val page = result.getOrNull() ?: return@withContext emptyList<MusicTrack>() to null
            
            val tracks = page.songs.mapNotNull { convertToMusicTrack(it) }
            tracks to page.continuation
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList<MusicTrack>() to null
        }
    }

    /**
     * Fetch lyrics for a song
     */
    suspend fun fetchLyrics(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val nextResult = YouTube.next(com.flow.youtube.innertube.models.WatchEndpoint(videoId = videoId)).getOrNull()
            val lyricsEndpoint = nextResult?.lyricsEndpoint ?: return@withContext null
            YouTube.lyrics(lyricsEndpoint).getOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch queue metadata for video IDs or a playlist
     * Uses YouTube.queue() for faster queue loading compared to next()
     */
    suspend fun fetchQueue(videoIds: List<String>? = null, playlistId: String? = null): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.queue(videoIds, playlistId)
            result.getOrNull()?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun convertAlbumToPlaylist(item: com.flow.youtube.innertube.models.AlbumItem): com.flow.youtube.ui.screens.music.MusicPlaylist {
        return com.flow.youtube.ui.screens.music.MusicPlaylist(
            id = item.browseId ?: "",
            title = item.title ?: "",
            thumbnailUrl = item.thumbnail ?: "",
            trackCount = 0, // Not always available in list view
            author = item.year?.toString() ?: "" // Resusing author field for Year/Subtitle
        )
    }

    private fun convertPlaylistToMusicPlaylist(item: com.flow.youtube.innertube.models.PlaylistItem): com.flow.youtube.ui.screens.music.MusicPlaylist {
        return com.flow.youtube.ui.screens.music.MusicPlaylist(
            id = item.id ?: "",
            title = item.title ?: "",
            thumbnailUrl = item.thumbnail ?: "",
            trackCount = item.songCountText?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
            author = item.author?.name ?: ""
        )
    }
    
    private fun convertArtistItemToDetails(item: com.flow.youtube.innertube.models.ArtistItem): com.flow.youtube.ui.screens.music.ArtistDetails {
        return com.flow.youtube.ui.screens.music.ArtistDetails(
            name = item.title ?: "",
            channelId = item.id ?: "",
            thumbnailUrl = item.thumbnail ?: "",
            subscriberCount = 0L,
            topTracks = emptyList()
        )
    }

    fun convertToMusicTrack(item: YTItem): MusicTrack? {
        return when (item) {
            is SongItem -> {
                MusicTrack(
                    videoId = item.id,
                    title = item.title,
                    artist = item.artists.firstOrNull()?.name ?: "Unknown Artist",
                    thumbnailUrl = item.thumbnail,
                    duration = item.duration ?: 0,
                    album = item.album?.name ?: "",
                    channelId = item.artists.firstOrNull()?.id ?: ""
                )
            }
            // We can add support for VideoItem or others here if needed
            else -> null
        }
    }

    private fun parseViewCount(text: String?): Long {
        if (text == null) return 0
        val cleanText = text.split(" ").firstOrNull() ?: return 0
        return try {
            when {
                cleanText.endsWith("B", ignoreCase = true) -> (cleanText.dropLast(1).toDouble() * 1_000_000_000).toLong()
                cleanText.endsWith("M", ignoreCase = true) -> (cleanText.dropLast(1).toDouble() * 1_000_000).toLong()
                cleanText.endsWith("K", ignoreCase = true) -> (cleanText.dropLast(1).toDouble() * 1_000).toLong()
                else -> cleanText.replace(",", "").toLong()
            }
        } catch (e: Exception) {
            0
        }
    }
}
