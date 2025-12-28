package com.flow.youtube.data.music

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.ui.screens.music.MusicTrack
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.playlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "playlists")

/**
 * Repository for managing playlists and favorites
 */
class PlaylistRepository(private val context: Context) {
    private val gson = Gson()
    
    companion object {
        private val PLAYLISTS_KEY = stringPreferencesKey("playlists")
        private val FAVORITES_KEY = stringPreferencesKey("favorites")
        private val HISTORY_KEY = stringPreferencesKey("history")
    }
    
    // Get all playlists
    val playlists: Flow<List<Playlist>> = context.playlistDataStore.data.map { prefs ->
        val json = prefs[PLAYLISTS_KEY] ?: "[]"
        val type = object : TypeToken<List<Playlist>>() {}.type
        gson.fromJson(json, type)
    }
    
    // Get favorites
    val favorites: Flow<List<MusicTrack>> = context.playlistDataStore.data.map { prefs ->
        val json = prefs[FAVORITES_KEY] ?: "[]"
        val type = object : TypeToken<List<MusicTrack>>() {}.type
        gson.fromJson(json, type)
    }

    // Get history
    val history: Flow<List<MusicTrack>> = context.playlistDataStore.data.map { prefs ->
        val json = prefs[HISTORY_KEY] ?: "[]"
        val type = object : TypeToken<List<MusicTrack>>() {}.type
        gson.fromJson(json, type)
    }
    
    // Create playlist
    suspend fun createPlaylist(name: String, description: String = ""): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            tracks = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        
        val currentPlaylists = playlists.first().toMutableList()
        currentPlaylists.add(playlist)
        savePlaylists(currentPlaylists)
        
        return playlist
    }
    
    // Update playlist
    suspend fun updatePlaylist(playlist: Playlist) {
        val currentPlaylists = playlists.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            currentPlaylists[index] = playlist
            savePlaylists(currentPlaylists)
        }
    }
    
    // Delete playlist
    suspend fun deletePlaylist(playlistId: String) {
        val currentPlaylists = playlists.first().toMutableList()
        currentPlaylists.removeIf { it.id == playlistId }
        savePlaylists(currentPlaylists)
    }
    
    // Add track to playlist
    suspend fun addTrackToPlaylist(playlistId: String, track: MusicTrack) {
        val currentPlaylists = playlists.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = currentPlaylists[index]
            val updatedTracks = playlist.tracks.toMutableList()
            if (!updatedTracks.any { it.videoId == track.videoId }) {
                updatedTracks.add(track)
                currentPlaylists[index] = playlist.copy(tracks = updatedTracks)
                savePlaylists(currentPlaylists)
            }
        }
    }
    
    // Remove track from playlist
    suspend fun removeTrackFromPlaylist(playlistId: String, trackVideoId: String) {
        val currentPlaylists = playlists.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = currentPlaylists[index]
            val updatedTracks = playlist.tracks.filter { it.videoId != trackVideoId }
            currentPlaylists[index] = playlist.copy(tracks = updatedTracks)
            savePlaylists(currentPlaylists)
        }
    }
    
    // Add to favorites
    suspend fun addToFavorites(track: MusicTrack) {
        val currentFavorites = favorites.first().toMutableList()
        if (!currentFavorites.any { it.videoId == track.videoId }) {
            currentFavorites.add(0, track) // Add to beginning
            saveFavorites(currentFavorites)
        }
    }
    
    // Remove from favorites
    suspend fun removeFromFavorites(trackVideoId: String) {
        val currentFavorites = favorites.first().toMutableList()
        currentFavorites.removeIf { it.videoId == trackVideoId }
        saveFavorites(currentFavorites)
    }
    
    // Check if track is favorite
    suspend fun isFavorite(trackVideoId: String): Boolean {
        return favorites.first().any { it.videoId == trackVideoId }
    }
    
    // Toggle favorite
    suspend fun toggleFavorite(track: MusicTrack): Boolean {
        return if (isFavorite(track.videoId)) {
            removeFromFavorites(track.videoId)
            false
        } else {
            addToFavorites(track)
            true
        }
    }

    // Add to history
    suspend fun addToHistory(track: MusicTrack) {
        android.util.Log.d("PlaylistRepository", "Adding to history: ${track.title}")
        val currentHistory = history.first().toMutableList()
        // Remove if exists (to move to top)
        currentHistory.removeIf { it.videoId == track.videoId }
        currentHistory.add(0, track)
        // Limit history size (e.g. 100)
        if (currentHistory.size > 100) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }
        saveHistory(currentHistory)
        android.util.Log.d("PlaylistRepository", "History saved. Size: ${currentHistory.size}")
    }

    suspend fun clearHistory() {
        saveHistory(emptyList())
    }
    
    private suspend fun savePlaylists(playlists: List<Playlist>) {
        android.util.Log.d("PlaylistRepository", "Saving playlists: ${playlists.size}")
        context.playlistDataStore.edit { prefs ->
            prefs[PLAYLISTS_KEY] = gson.toJson(playlists)
        }
    }
    
    private suspend fun saveFavorites(favorites: List<MusicTrack>) {
        android.util.Log.d("PlaylistRepository", "Saving favorites: ${favorites.size}")
        context.playlistDataStore.edit { prefs ->
            prefs[FAVORITES_KEY] = gson.toJson(favorites)
        }
    }

    private suspend fun saveHistory(history: List<MusicTrack>) {
        context.playlistDataStore.edit { prefs ->
            prefs[HISTORY_KEY] = gson.toJson(history)
        }
    }
}

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val tracks: List<MusicTrack> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val thumbnailUrl: String = tracks.firstOrNull()?.thumbnailUrl ?: ""
) {
    val trackCount: Int get() = tracks.size
    val duration: Int get() = tracks.sumOf { it.duration }
}
