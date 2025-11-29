package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.screens.playlists.PlaylistInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.playlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "playlists_store")

class PlaylistRepository(private val context: Context) {
    companion object {
        private val WATCH_LATER_KEY = stringPreferencesKey("watch_later_videos")
        private val PLAYLISTS_META_KEY = stringPreferencesKey("playlists_metadata")
        private fun playlistVideosKey(playlistId: String) = stringPreferencesKey("playlist_videos_$playlistId")
    }

    private val ds = context.playlistDataStore
    private val gson = Gson()

    suspend fun addToWatchLater(video: Video) {
        ds.edit { prefs ->
            val json = prefs[WATCH_LATER_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<Video>>() {}.type
            val videos: MutableList<Video> = gson.fromJson(json, type) ?: mutableListOf()
            
            // Remove if already exists (to avoid duplicates)
            videos.removeAll { it.id == video.id }
            // Add to the beginning (most recent first)
            videos.add(0, video)
            
            prefs[WATCH_LATER_KEY] = gson.toJson(videos)
        }
    }

    suspend fun removeFromWatchLater(videoId: String) {
        ds.edit { prefs ->
            val json = prefs[WATCH_LATER_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<Video>>() {}.type
            val videos: MutableList<Video> = gson.fromJson(json, type) ?: mutableListOf()
            videos.removeAll { it.id == videoId }
            prefs[WATCH_LATER_KEY] = gson.toJson(videos)
        }
    }
    
    suspend fun clearWatchLater() {
        ds.edit { prefs ->
            prefs[WATCH_LATER_KEY] = "[]"
        }
    }

    fun getWatchLaterVideosFlow(): Flow<List<Video>> = ds.data.map { prefs ->
        val json = prefs[WATCH_LATER_KEY] ?: "[]"
        val type = object : TypeToken<List<Video>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    fun getWatchLaterIdsFlow(): Flow<Set<String>> = ds.data.map { prefs ->
        val json = prefs[WATCH_LATER_KEY] ?: "[]"
        val type = object : TypeToken<List<Video>>() {}.type
        val videos: List<Video> = gson.fromJson(json, type) ?: emptyList()
        videos.map { it.id }.toSet()
    }

    suspend fun isInWatchLater(videoId: String): Boolean {
        val prefs = ds.data.first()
        val json = prefs[WATCH_LATER_KEY] ?: "[]"
        val type = object : TypeToken<List<Video>>() {}.type
        val videos: List<Video> = gson.fromJson(json, type) ?: emptyList()
        return videos.any { it.id == videoId }
    }

    // Playlist Management
    suspend fun createPlaylist(playlistId: String, name: String, description: String, isPrivate: Boolean) {
        ds.edit { prefs ->
            val json = prefs[PLAYLISTS_META_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<PlaylistInfo>>() {}.type
            val playlists: MutableList<PlaylistInfo> = gson.fromJson(json, type) ?: mutableListOf()
            
            val newPlaylist = PlaylistInfo(
                id = playlistId,
                name = name,
                description = description,
                videoCount = 0,
                thumbnailUrl = "",
                isPrivate = isPrivate,
                createdAt = System.currentTimeMillis()
            )
            
            playlists.add(0, newPlaylist)
            prefs[PLAYLISTS_META_KEY] = gson.toJson(playlists)
            
            // Initialize empty video list for this playlist
            prefs[playlistVideosKey(playlistId)] = "[]"
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        ds.edit { prefs ->
            val json = prefs[PLAYLISTS_META_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<PlaylistInfo>>() {}.type
            val playlists: MutableList<PlaylistInfo> = gson.fromJson(json, type) ?: mutableListOf()
            
            playlists.removeAll { it.id == playlistId }
            prefs[PLAYLISTS_META_KEY] = gson.toJson(playlists)
            prefs.remove(playlistVideosKey(playlistId))
        }
    }

    suspend fun addVideoToPlaylist(playlistId: String, video: Video) {
        ds.edit { prefs ->
            // Add video to playlist
            val videosJson = prefs[playlistVideosKey(playlistId)] ?: "[]"
            val videosType = object : TypeToken<MutableList<Video>>() {}.type
            val videos: MutableList<Video> = gson.fromJson(videosJson, videosType) ?: mutableListOf()
            
            // Remove if already exists (to avoid duplicates)
            videos.removeAll { it.id == video.id }
            videos.add(0, video)
            
            prefs[playlistVideosKey(playlistId)] = gson.toJson(videos)
            
            // Update playlist metadata
            val metaJson = prefs[PLAYLISTS_META_KEY] ?: "[]"
            val metaType = object : TypeToken<MutableList<PlaylistInfo>>() {}.type
            val playlists: MutableList<PlaylistInfo> = gson.fromJson(metaJson, metaType) ?: mutableListOf()
            
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
            if (playlistIndex >= 0) {
                val playlist = playlists[playlistIndex]
                playlists[playlistIndex] = playlist.copy(
                    videoCount = videos.size,
                    thumbnailUrl = videos.firstOrNull()?.thumbnailUrl ?: ""
                )
                prefs[PLAYLISTS_META_KEY] = gson.toJson(playlists)
            }
        }
    }

    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        ds.edit { prefs ->
            val videosJson = prefs[playlistVideosKey(playlistId)] ?: "[]"
            val videosType = object : TypeToken<MutableList<Video>>() {}.type
            val videos: MutableList<Video> = gson.fromJson(videosJson, videosType) ?: mutableListOf()
            
            videos.removeAll { it.id == videoId }
            prefs[playlistVideosKey(playlistId)] = gson.toJson(videos)
            
            // Update playlist metadata
            val metaJson = prefs[PLAYLISTS_META_KEY] ?: "[]"
            val metaType = object : TypeToken<MutableList<PlaylistInfo>>() {}.type
            val playlists: MutableList<PlaylistInfo> = gson.fromJson(metaJson, metaType) ?: mutableListOf()
            
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
            if (playlistIndex >= 0) {
                val playlist = playlists[playlistIndex]
                playlists[playlistIndex] = playlist.copy(
                    videoCount = videos.size,
                    thumbnailUrl = videos.firstOrNull()?.thumbnailUrl ?: ""
                )
                prefs[PLAYLISTS_META_KEY] = gson.toJson(playlists)
            }
        }
    }

    fun getAllPlaylistsFlow(): Flow<List<PlaylistInfo>> = ds.data.map { prefs ->
        val json = prefs[PLAYLISTS_META_KEY] ?: "[]"
        val type = object : TypeToken<List<PlaylistInfo>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    fun getPlaylistVideosFlow(playlistId: String): Flow<List<Video>> = ds.data.map { prefs ->
        val json = prefs[playlistVideosKey(playlistId)] ?: "[]"
        val type = object : TypeToken<List<Video>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    suspend fun getPlaylistInfo(playlistId: String): PlaylistInfo? {
        val prefs = ds.data.first()
        val json = prefs[PLAYLISTS_META_KEY] ?: "[]"
        val type = object : TypeToken<List<PlaylistInfo>>() {}.type
        val playlists: List<PlaylistInfo> = gson.fromJson(json, type) ?: emptyList()
        return playlists.firstOrNull { it.id == playlistId }
    }
}
