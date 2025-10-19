package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.likedVideosDataStore: DataStore<Preferences> by preferencesDataStore(name = "liked_videos")

class LikedVideosRepository private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: LikedVideosRepository? = null
        
        fun getInstance(context: Context): LikedVideosRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LikedVideosRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Keys format: "video_{videoId}" -> JSON string with video info
        private fun videoKey(videoId: String) = stringPreferencesKey("video_$videoId")
        private fun likeStateKey(videoId: String) = stringPreferencesKey("like_state_$videoId")
        private const val LIKED_VIDEOS_ORDER_KEY = "liked_videos_order"
    }
    
    /**
     * Like a video
     */
    suspend fun likeVideo(videoInfo: LikedVideoInfo) {
        context.likedVideosDataStore.edit { preferences ->
            // Save video data
            preferences[videoKey(videoInfo.videoId)] = serializeVideo(videoInfo)
            preferences[likeStateKey(videoInfo.videoId)] = "LIKED"
            
            // Update order list
            val currentOrder = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] ?: ""
            val orderList = if (currentOrder.isEmpty()) {
                mutableListOf()
            } else {
                currentOrder.split(",").toMutableList()
            }
            
            if (!orderList.contains(videoInfo.videoId)) {
                orderList.add(0, videoInfo.videoId) // Add to front
                preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] = orderList.joinToString(",")
            }
        }
    }
    
    /**
     * Dislike a video (removes like if exists)
     */
    suspend fun dislikeVideo(videoId: String) {
        context.likedVideosDataStore.edit { preferences ->
            preferences[likeStateKey(videoId)] = "DISLIKED"
            
            // Remove from liked videos list
            val currentOrder = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] ?: ""
            if (currentOrder.isNotEmpty()) {
                val orderList = currentOrder.split(",").toMutableList()
                orderList.remove(videoId)
                preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] = orderList.joinToString(",")
            }
        }
    }
    
    /**
     * Remove like/dislike from a video
     */
    suspend fun removeLikeState(videoId: String) {
        context.likedVideosDataStore.edit { preferences ->
            preferences.remove(likeStateKey(videoId))
            
            // Remove from liked videos list
            val currentOrder = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] ?: ""
            if (currentOrder.isNotEmpty()) {
                val orderList = currentOrder.split(",").toMutableList()
                orderList.remove(videoId)
                preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] = orderList.joinToString(",")
            }
        }
    }
    
    /**
     * Get like state for a video (LIKED, DISLIKED, or null)
     */
    fun getLikeState(videoId: String): Flow<String?> {
        return context.likedVideosDataStore.data.map { preferences ->
            preferences[likeStateKey(videoId)]
        }
    }
    
    /**
     * Get all liked videos
     */
    fun getAllLikedVideos(): Flow<List<LikedVideoInfo>> {
        return context.likedVideosDataStore.data.map { preferences ->
            val orderString = preferences[stringPreferencesKey(LIKED_VIDEOS_ORDER_KEY)] ?: ""
            if (orderString.isEmpty()) {
                emptyList()
            } else {
                val orderList = orderString.split(",")
                orderList.mapNotNull { videoId ->
                    val videoData = preferences[videoKey(videoId)]
                    videoData?.let { deserializeVideo(it) }
                }
            }
        }
    }
    
    private fun serializeVideo(video: LikedVideoInfo): String {
        return "${video.videoId}|${video.title}|${video.thumbnail}|${video.channelName}|${video.likedAt}"
    }
    
    private fun deserializeVideo(data: String): LikedVideoInfo? {
        return try {
            val parts = data.split("|")
            if (parts.size >= 5) {
                LikedVideoInfo(
                    videoId = parts[0],
                    title = parts[1],
                    thumbnail = parts[2],
                    channelName = parts[3],
                    likedAt = parts[4].toLong()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class LikedVideoInfo(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val likedAt: Long = System.currentTimeMillis()
)
