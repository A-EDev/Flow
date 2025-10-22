package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.playlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "playlists_store")

class PlaylistRepository(private val context: Context) {
    companion object {
        private val WATCH_LATER_KEY = stringSetPreferencesKey("watch_later_ids")
        private val PLAYLIST_PREFIX = "playlist_" // keys will be playlist_{id}
    }

    private val ds = context.playlistDataStore

    suspend fun addToWatchLater(video: Video) {
        ds.edit { prefs ->
            val set = prefs[WATCH_LATER_KEY]?.toMutableSet() ?: mutableSetOf()
            set.add(video.id)
            prefs[WATCH_LATER_KEY] = set
        }
    }

    suspend fun removeFromWatchLater(videoId: String) {
        ds.edit { prefs ->
            val set = prefs[WATCH_LATER_KEY]?.toMutableSet() ?: mutableSetOf()
            set.remove(videoId)
            prefs[WATCH_LATER_KEY] = set
        }
    }

    fun getWatchLaterIdsFlow(): Flow<Set<String>> = ds.data.map { prefs ->
        prefs[WATCH_LATER_KEY] ?: emptySet()
    }

    suspend fun isInWatchLater(videoId: String): Boolean {
        val prefs = ds.data.first()
        val set = prefs[WATCH_LATER_KEY] ?: emptySet()
        return set.contains(videoId)
    }

    // Simple playlist creation: save a set of video ids under a playlist key
    suspend fun createPlaylist(playlistId: String, initialVideoIds: Set<String> = emptySet()) {
        val key = stringSetPreferencesKey(PLAYLIST_PREFIX + playlistId)
        ds.edit { prefs ->
            prefs[key] = initialVideoIds
        }
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String) {
        val key = stringSetPreferencesKey(PLAYLIST_PREFIX + playlistId)
        ds.edit { prefs ->
            val set = prefs[key]?.toMutableSet() ?: mutableSetOf()
            set.add(videoId)
            prefs[key] = set
        }
    }

    fun getPlaylistIdsFlow(): Flow<Set<String>> = ds.data.map { prefs ->
        prefs.asMap().keys.mapNotNull { k ->
            val name = k.name
            if (name.startsWith(PLAYLIST_PREFIX)) name.removePrefix(PLAYLIST_PREFIX) else null
        }.toSet()
    }

    fun getPlaylistFlow(playlistId: String): Flow<Set<String>> = ds.data.map { prefs ->
        val key = stringSetPreferencesKey(PLAYLIST_PREFIX + playlistId)
        prefs[key] ?: emptySet()
    }
}
