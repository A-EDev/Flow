package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.viewHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "view_history")

class ViewHistory private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: ViewHistory? = null
        
        fun getInstance(context: Context): ViewHistory {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ViewHistory(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Keys format: "video_{videoId}_position", "video_{videoId}_duration", "video_{videoId}_timestamp"
        private fun positionKey(videoId: String) = longPreferencesKey("video_${videoId}_position")
        private fun durationKey(videoId: String) = longPreferencesKey("video_${videoId}_duration")
        private fun timestampKey(videoId: String) = longPreferencesKey("video_${videoId}_timestamp")
        private fun titleKey(videoId: String) = stringPreferencesKey("video_${videoId}_title")
        private fun thumbnailKey(videoId: String) = stringPreferencesKey("video_${videoId}_thumbnail")
        private fun channelNameKey(videoId: String) = stringPreferencesKey("video_${videoId}_channel_name")
        private fun channelIdKey(videoId: String) = stringPreferencesKey("video_${videoId}_channel_id")
        private fun isMusicKey(videoId: String) = androidx.datastore.preferences.core.booleanPreferencesKey("video_${videoId}_is_music")
    }
    
    /**
     * Save video playback position
     */
    suspend fun savePlaybackPosition(
        videoId: String,
        position: Long,
        duration: Long,
        title: String = "",
        thumbnailUrl: String = "",
        channelName: String = "",
        channelId: String = "",
        isMusic: Boolean = false
    ) {
        context.viewHistoryDataStore.edit { preferences ->
            preferences[positionKey(videoId)] = position
            preferences[durationKey(videoId)] = duration
            preferences[timestampKey(videoId)] = System.currentTimeMillis()
            if (title.isNotEmpty()) preferences[titleKey(videoId)] = title
            if (thumbnailUrl.isNotEmpty()) preferences[thumbnailKey(videoId)] = thumbnailUrl
            if (channelName.isNotEmpty()) preferences[channelNameKey(videoId)] = channelName
            if (channelId.isNotEmpty()) preferences[channelIdKey(videoId)] = channelId
            preferences[isMusicKey(videoId)] = isMusic
        }
    }
    
    /**
     * Get saved playback position for a video
     */
    fun getPlaybackPosition(videoId: String): Flow<Long> {
        return context.viewHistoryDataStore.data.map { preferences ->
            preferences[positionKey(videoId)] ?: 0L
        }
    }
    
    /**
     * Get full video history entry
     */
    fun getVideoHistory(videoId: String): Flow<VideoHistoryEntry?> {
        return context.viewHistoryDataStore.data.map { preferences ->
            val position = preferences[positionKey(videoId)] ?: return@map null
            val duration = preferences[durationKey(videoId)] ?: 0L
            val timestamp = preferences[timestampKey(videoId)] ?: 0L
            val title = preferences[titleKey(videoId)] ?: ""
            val thumbnail = preferences[thumbnailKey(videoId)] ?: ""
            val channelName = preferences[channelNameKey(videoId)] ?: ""
            val channelId = preferences[channelIdKey(videoId)] ?: ""
            val isMusic = preferences[isMusicKey(videoId)] ?: false
            
            VideoHistoryEntry(
                videoId = videoId,
                position = position,
                duration = duration,
                timestamp = timestamp,
                title = title,
                thumbnailUrl = thumbnail,
                channelName = channelName,
                channelId = channelId,
                isMusic = isMusic
            )
        }
    }
    
    /**
     * Get all video history entries
     */
    fun getAllHistory(): Flow<List<VideoHistoryEntry>> {
        return context.viewHistoryDataStore.data.map { preferences ->
            val videoIds = mutableSetOf<String>()
            
            // Extract unique video IDs from keys
            preferences.asMap().keys.forEach { key ->
                val keyName = key.name
                if (keyName.startsWith("video_") && keyName.endsWith("_position")) {
                    val videoId = keyName.removePrefix("video_").removeSuffix("_position")
                    videoIds.add(videoId)
                }
            }
            
            // Build history entries
            videoIds.mapNotNull { videoId ->
                val position = preferences[positionKey(videoId)] ?: return@mapNotNull null
                val duration = preferences[durationKey(videoId)] ?: 0L
                val timestamp = preferences[timestampKey(videoId)] ?: 0L
                val title = preferences[titleKey(videoId)] ?: ""
                val thumbnail = preferences[thumbnailKey(videoId)] ?: ""
                val channelName = preferences[channelNameKey(videoId)] ?: ""
                val channelId = preferences[channelIdKey(videoId)] ?: ""
                val isMusic = preferences[isMusicKey(videoId)] ?: false
                
                VideoHistoryEntry(
                    videoId = videoId,
                    position = position,
                    duration = duration,
                    timestamp = timestamp,
                    title = title,
                    thumbnailUrl = thumbnail,
                    channelName = channelName,
                    channelId = channelId,
                    isMusic = isMusic
                )
            }.sortedByDescending { it.timestamp }
        }
    }

    fun getMusicHistoryFlow(): Flow<List<VideoHistoryEntry>> {
        return getAllHistory().map { list -> list.filter { it.isMusic } }
    }

    fun getVideoHistoryFlow(): Flow<List<VideoHistoryEntry>> {
        return getAllHistory().map { list -> list.filter { !it.isMusic } }
    }
    
    /**
     * Clear history for a specific video
     */
    suspend fun clearVideoHistory(videoId: String) {
        context.viewHistoryDataStore.edit { preferences ->
            preferences.remove(positionKey(videoId))
            preferences.remove(durationKey(videoId))
            preferences.remove(timestampKey(videoId))
            preferences.remove(titleKey(videoId))
            preferences.remove(thumbnailKey(videoId))
            preferences.remove(channelNameKey(videoId))
            preferences.remove(channelIdKey(videoId))
        }
    }
    
    /**
     * Clear all history
     */
    suspend fun clearAllHistory() {
        context.viewHistoryDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

data class VideoHistoryEntry(
    val videoId: String,
    val position: Long, // Position in milliseconds
    val duration: Long, // Total duration in milliseconds
    val timestamp: Long, // When it was last watched
    val title: String,
    val thumbnailUrl: String,
    val channelName: String = "",
    val channelId: String = "",
    val isMusic: Boolean = false
) {
    val progressPercentage: Float
        get() = if (duration > 0) (position.toFloat() / duration.toFloat()) * 100f else 0f
}
