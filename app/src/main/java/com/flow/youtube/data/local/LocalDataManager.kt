package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.data.model.Channel
import com.flow.youtube.data.model.Playlist
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.theme.ThemeMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flow_preferences")

class LocalDataManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val SUBSCRIPTIONS = stringPreferencesKey("subscriptions")
        private val WATCH_HISTORY = stringPreferencesKey("watch_history")
        private val LIKED_VIDEOS = stringPreferencesKey("liked_videos")
        private val PLAYLISTS = stringPreferencesKey("playlists")
        private val SEARCH_HISTORY = stringSetPreferencesKey("search_history")
        private val VIDEO_QUALITY_WIFI = stringPreferencesKey("quality_wifi")
        private val VIDEO_QUALITY_CELLULAR = stringPreferencesKey("quality_cellular")
        private val BACKGROUND_PLAY = stringPreferencesKey("background_play")
        private val TRENDING_REGION = stringPreferencesKey("trending_region")
    }

    // Theme Settings
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        try {
            prefs[THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.LIGHT
        } catch (e: Exception) {
            ThemeMode.LIGHT
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }

    // Subscriptions
    val subscriptions: Flow<List<Channel>> = context.dataStore.data.map { prefs ->
        val json = prefs[SUBSCRIPTIONS] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Channel>>() {}.type)
    }

    suspend fun addSubscription(channel: Channel) {
        context.dataStore.edit { prefs ->
            val current: List<Channel> = gson.fromJson(
                prefs[SUBSCRIPTIONS] ?: "[]",
                object : TypeToken<List<Channel>>() {}.type
            )
            val updated = current.toMutableList()
            if (updated.none { it.id == channel.id }) {
                updated.add(channel)
                prefs[SUBSCRIPTIONS] = gson.toJson(updated)
            }
        }
    }

    suspend fun removeSubscription(channelId: String) {
        context.dataStore.edit { prefs ->
            val current: List<Channel> = gson.fromJson(
                prefs[SUBSCRIPTIONS] ?: "[]",
                object : TypeToken<List<Channel>>() {}.type
            )
            val updated = current.filter { it.id != channelId }
            prefs[SUBSCRIPTIONS] = gson.toJson(updated)
        }
    }

    // Watch History
    val watchHistory: Flow<List<Video>> = context.dataStore.data.map { prefs ->
        val json = prefs[WATCH_HISTORY] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Video>>() {}.type)
    }

    suspend fun addToWatchHistory(video: Video) {
        context.dataStore.edit { prefs ->
            val current: List<Video> = gson.fromJson(
                prefs[WATCH_HISTORY] ?: "[]",
                object : TypeToken<List<Video>>() {}.type
            )
            val updated = current.toMutableList()
            updated.removeAll { it.id == video.id }
            updated.add(0, video)
            if (updated.size > 500) {
                updated.removeAt(updated.size - 1)
            }
            prefs[WATCH_HISTORY] = gson.toJson(updated)
        }
    }

    suspend fun clearWatchHistory() {
        context.dataStore.edit { prefs ->
            prefs[WATCH_HISTORY] = "[]"
        }
    }

    // Liked Videos
    val likedVideos: Flow<List<Video>> = context.dataStore.data.map { prefs ->
        val json = prefs[LIKED_VIDEOS] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Video>>() {}.type)
    }

    suspend fun toggleLike(video: Video) {
        context.dataStore.edit { prefs ->
            val current: List<Video> = gson.fromJson(
                prefs[LIKED_VIDEOS] ?: "[]",
                object : TypeToken<List<Video>>() {}.type
            )
            val updated = current.toMutableList()
            if (updated.any { it.id == video.id }) {
                updated.removeAll { it.id == video.id }
            } else {
                updated.add(0, video)
            }
            prefs[LIKED_VIDEOS] = gson.toJson(updated)
        }
    }

    // Playlists
    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { prefs ->
        val json = prefs[PLAYLISTS] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Playlist>>() {}.type)
    }

    suspend fun createPlaylist(name: String): Playlist {
        val newPlaylist = Playlist(
            id = "local_${System.currentTimeMillis()}",
            name = name,
            thumbnailUrl = "",
            videoCount = 0,
            isLocal = true
        )
        context.dataStore.edit { prefs ->
            val current: List<Playlist> = gson.fromJson(
                prefs[PLAYLISTS] ?: "[]",
                object : TypeToken<List<Playlist>>() {}.type
            )
            val updated = current.toMutableList()
            updated.add(newPlaylist)
            prefs[PLAYLISTS] = gson.toJson(updated)
        }
        return newPlaylist
    }

    suspend fun addVideoToPlaylist(playlistId: String, video: Video) {
        context.dataStore.edit { prefs ->
            val current: List<Playlist> = gson.fromJson(
                prefs[PLAYLISTS] ?: "[]",
                object : TypeToken<List<Playlist>>() {}.type
            )
            val updated = current.map { playlist ->
                if (playlist.id == playlistId) {
                    val videos = playlist.videos.toMutableList()
                    if (videos.none { it.id == video.id }) {
                        videos.add(video)
                    }
                    playlist.copy(
                        videos = videos,
                        videoCount = videos.size,
                        thumbnailUrl = videos.firstOrNull()?.thumbnailUrl ?: ""
                    )
                } else {
                    playlist
                }
            }
            prefs[PLAYLISTS] = gson.toJson(updated)
        }
    }

    // Search History
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[SEARCH_HISTORY]?.toList() ?: emptyList()
    }

    suspend fun addSearchQuery(query: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[SEARCH_HISTORY]?.toMutableSet() ?: mutableSetOf()
            current.add(query)
            if (current.size > 20) {
                current.remove(current.first())
            }
            prefs[SEARCH_HISTORY] = current
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs ->
            prefs[SEARCH_HISTORY] = emptySet()
        }
    }

    // Settings
    val trendingRegion: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TRENDING_REGION] ?: "US"
    }

    suspend fun setTrendingRegion(region: String) {
        context.dataStore.edit { prefs ->
            prefs[TRENDING_REGION] = region
        }
    }
}

