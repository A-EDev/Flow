package com.flow.youtube.data.soundcloud

import android.util.Log
import com.flow.youtube.ui.screens.music.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Service for fetching music from SoundCloud using NewPipe Extractor
 */
object SoundCloudMusicService {
    
    private const val TAG = "SoundCloudMusicService"
    
    /**
     * Fetch trending tracks from SoundCloud
     */
    suspend fun fetchTrendingTracks(limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.SoundCloud
            val kioskList = service.kioskList
            val kiosk = kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            
            val tracks = kiosk.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .mapNotNull { item ->
                    try {
                        convertToMusicTrack(item)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting track: ${e.message}")
                        null
                    }
                }
            
            Log.d(TAG, "Fetched ${tracks.size} trending tracks from SoundCloud")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending tracks", e)
            emptyList()
        }
    }
    
    /**
     * Search for tracks on SoundCloud
     */
    suspend fun searchTracks(query: String, limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.SoundCloud
            val searchExtractor = service.getSearchExtractor(query, emptyList(), "")
            searchExtractor.fetchPage()
            
            val tracks = searchExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .mapNotNull { item ->
                    try {
                        convertToMusicTrack(item)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting search result: ${e.message}")
                        null
                    }
                }
            
            Log.d(TAG, "Found ${tracks.size} tracks for query: $query")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error searching tracks", e)
            emptyList()
        }
    }
    
    /**
     * Fetch top tracks by genre
     */
    suspend fun fetchTracksByGenre(genre: String, limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.SoundCloud
            val searchQuery = "genre:$genre"
            val searchExtractor = service.getSearchExtractor(searchQuery, emptyList(), "")
            searchExtractor.fetchPage()
            
            val tracks = searchExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
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
     * Get detailed stream info and audio URL for a track
     */
    suspend fun getStreamInfo(trackUrl: String): StreamInfo? = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.SoundCloud
            StreamInfo.getInfo(service, trackUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stream info", e)
            null
        }
    }
    
    /**
     * Get best audio stream URL for a track
     */
    suspend fun getAudioUrl(trackUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = getStreamInfo(trackUrl)
            
            // SoundCloud typically provides direct audio URLs
            streamInfo?.audioStreams
                ?.maxByOrNull { it.averageBitrate }
                ?.url
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio URL", e)
            null
        }
    }
    
    /**
     * Fetch playlist tracks
     */
    suspend fun fetchPlaylistTracks(playlistUrl: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.SoundCloud
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
     * Get popular genres
     */
    fun getPopularGenres(): List<String> {
        return listOf(
            "Electronic",
            "Hip Hop",
            "Pop",
            "Rock",
            "Ambient",
            "Classical",
            "Jazz",
            "R&B",
            "Indie",
            "Dance",
            "Dubstep",
            "Trap",
            "House",
            "Techno",
            "Chill"
        )
    }
    
    /**
     * Convert StreamInfoItem to MusicTrack
     */
    private fun convertToMusicTrack(item: StreamInfoItem): MusicTrack {
        // Extract track ID from URL
        val trackId = item.url.substringAfterLast("/")
        
        return MusicTrack(
            videoId = trackId,
            title = item.name,
            artist = item.uploaderName ?: "Unknown Artist",
            thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url ?: "",
            duration = item.duration.toInt(),
            views = item.viewCount,
            sourceUrl = item.url
        )
    }
    
    /**
     * Get related tracks based on a track
     */
    suspend fun getRelatedTracks(trackUrl: String, limit: Int = 20): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val streamInfo = getStreamInfo(trackUrl)
            
            streamInfo?.relatedItems
                ?.filterIsInstance<StreamInfoItem>()
                ?.take(limit)
                ?.mapNotNull { item ->
                    try {
                        convertToMusicTrack(item)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting related track: ${e.message}")
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching related tracks", e)
            emptyList()
        }
    }
}
