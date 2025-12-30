package com.flow.youtube.data.newmusic

import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.YouTubeLocale
import com.flow.youtube.innertube.models.YTItem
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.innertube.YouTube.SearchFilter
import com.flow.youtube.ui.screens.music.MusicTrack
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
            result.getOrNull()?.let { page ->
                page.sections.flatMap { it.items }
                    .mapNotNull { convertToMusicTrack(it) }
            } ?: emptyList()
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
            result.getOrNull()?.let { page ->
                page.items.mapNotNull { convertToMusicTrack(it) }
            } ?: emptyList()
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
            
            page.sections.forEach { section ->
                val title = section.title.lowercase()
                when {
                    title.contains("songs") || title.contains("popular") -> {
                        topTracks = section.items.filterIsInstance<SongItem>().mapNotNull { convertToMusicTrack(it) }
                    }
                    title.contains("albums") -> {
                        albums = section.items.filterIsInstance<com.flow.youtube.innertube.models.AlbumItem>().map { convertAlbumToPlaylist(it) }
                    }
                    title.contains("singles") || title.contains("ep") -> {
                        singles = section.items.filterIsInstance<com.flow.youtube.innertube.models.AlbumItem>().map { convertAlbumToPlaylist(it) }
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
                isSubscribed = false // We'll need another way to check this or sync with local DB
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
            subscriberCount = 0,
            description = "",
            bannerUrl = "",
            topTracks = emptyList()
        )
    }

    private fun convertToMusicTrack(item: YTItem): MusicTrack? {
        return when (item) {
            is SongItem -> {
                MusicTrack(
                    videoId = item.id,
                    title = item.title,
                    artist = item.artists.joinToString(", ") { it.name },
                    thumbnailUrl = item.thumbnail ?: "",
                    duration = item.duration ?: 0,
                    sourceUrl = "https://youtube.com/watch?v=${item.id}",
                    album = item.album?.name ?: "",
                    channelId = item.artists.firstOrNull()?.id ?: "",
                    isExplicit = item.explicit,
                    views = parseViewCount(item.viewCountText)
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
