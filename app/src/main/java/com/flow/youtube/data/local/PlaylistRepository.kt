package com.flow.youtube.data.local

import com.flow.youtube.data.local.dao.PlaylistDao
import com.flow.youtube.data.local.dao.PlaylistWithCount
import com.flow.youtube.data.local.dao.VideoDao
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.PlaylistVideoCrossRef
import com.flow.youtube.data.local.entity.VideoEntity
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.screens.playlists.PlaylistInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val videoDao: VideoDao
) {
    constructor(context: android.content.Context) : this(
        AppDatabase.getDatabase(context).playlistDao(),
        AppDatabase.getDatabase(context).videoDao()
    )
    // Watch Later Logic (using a special hardcoded playlist ID "watch_later")
    companion object {
        const val WATCH_LATER_ID = "watch_later"
        const val SAVED_SHORTS_ID = "saved_shorts"
    }

    // Saved Shorts Logic
    suspend fun addToSavedShorts(video: Video) {
        // Ensure saved shorts playlist exists
        val savedShorts = playlistDao.getPlaylist(SAVED_SHORTS_ID)
        if (savedShorts == null) {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    id = SAVED_SHORTS_ID,
                    name = "Saved Shorts",
                    description = "Your saved shorts",
                    thumbnailUrl = "",
                    isPrivate = true,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        
        // Save video
        videoDao.insertVideo(VideoEntity.fromDomain(video))
        
        // Add relationship
        val position = System.currentTimeMillis()
        playlistDao.insertPlaylistVideoCrossRef(
            PlaylistVideoCrossRef(
                playlistId = SAVED_SHORTS_ID,
                videoId = video.id,
                position = -position
            )
        )
    }

    suspend fun removeFromSavedShorts(videoId: String) {
        playlistDao.removeVideoFromPlaylist(SAVED_SHORTS_ID, videoId)
    }

    fun getSavedShortsFlow(): Flow<List<Video>> = 
        playlistDao.getVideosForPlaylist(SAVED_SHORTS_ID).map { entities ->
            entities.map { it.toDomain() }
        }

    fun getVideoOnlySavedShortsFlow(): Flow<List<Video>> = 
        getSavedShortsFlow().map { list -> list.filter { !it.isMusic } }

    suspend fun isInSavedShorts(videoId: String): Boolean {
        val videos = playlistDao.getVideosForPlaylist(SAVED_SHORTS_ID).firstOrNull() ?: emptyList()
        return videos.any { it.id == videoId }
    }

    suspend fun addToWatchLater(video: Video) {
        // Ensure watch later playlist exists (metadata)
        val watchLater = playlistDao.getPlaylist(WATCH_LATER_ID)
        if (watchLater == null) {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    id = WATCH_LATER_ID,
                    name = "Watch Later",
                    description = "Your watch later list",
                    thumbnailUrl = "",
                    isPrivate = true,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        
        // Save video
        videoDao.insertVideo(VideoEntity.fromDomain(video))
        
        // Add relationship
        // Logic to put at top: get current count and maybe use negative position or just time?
        // Simplifying: use current time as position for chronological ordering order
        val position = System.currentTimeMillis()
        playlistDao.insertPlaylistVideoCrossRef(
            PlaylistVideoCrossRef(
                playlistId = WATCH_LATER_ID,
                videoId = video.id,
                position = -position // Negative so that sorting by position ASC gives newest first (if larger abs value is newer)
                // Actually ASC means smallest first. If we want newest first, we want smallest position.
                // -System.currentTimeMillis() gets smaller as time goes on.
            )
        )
    }

    suspend fun removeFromWatchLater(videoId: String) {
        playlistDao.removeVideoFromPlaylist(WATCH_LATER_ID, videoId)
    }
    
    suspend fun clearWatchLater() {
        playlistDao.deletePlaylist(WATCH_LATER_ID)
    }

    fun getWatchLaterVideosFlow(): Flow<List<Video>> = 
        playlistDao.getVideosForPlaylist(WATCH_LATER_ID).map { entities ->
            entities.map { it.toDomain() }
        }

    fun getVideoOnlyWatchLaterFlow(): Flow<List<Video>> = 
        getWatchLaterVideosFlow().map { list -> list.filter { !it.isMusic } }
    
    fun getMusicOnlyWatchLaterFlow(): Flow<List<Video>> = 
        getWatchLaterVideosFlow().map { list -> list.filter { it.isMusic } }

    fun getWatchLaterIdsFlow(): Flow<Set<String>> = 
        playlistDao.getVideosForPlaylist(WATCH_LATER_ID).map { entities ->
            entities.map { it.id }.toSet()
        }

    suspend fun isInWatchLater(videoId: String): Boolean {
        val videos = playlistDao.getVideosForPlaylist(WATCH_LATER_ID).firstOrNull() ?: emptyList()
        return videos.any { it.id == videoId }
    }

    // Playlist Management
    suspend fun createPlaylist(playlistId: String, name: String, description: String, isPrivate: Boolean, isMusic: Boolean = false) {
        val entity = PlaylistEntity(
            id = playlistId,
            name = name,
            description = description,
            thumbnailUrl = "",
            isPrivate = isPrivate,
            createdAt = System.currentTimeMillis(),
            isMusic = isMusic
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun updatePlaylistName(playlistId: String, name: String) {
        playlistDao.updatePlaylistName(playlistId, name)
    }

    suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addVideoToPlaylist(playlistId: String, video: Video) {
        // Save video first
        videoDao.insertVideo(VideoEntity.fromDomain(video))
        
        // Add to playlist
        // Update thumbnail if it's the first one?
        // Room doesn't update fields automatically. We might want to update the playlist entity manually.
        val playlist = playlistDao.getPlaylist(playlistId)
        if (playlist != null && playlist.thumbnailUrl.isEmpty()) {
             playlistDao.insertPlaylist(playlist.copy(thumbnailUrl = video.thumbnailUrl))
        }
        
        // Add relation
        val position = -System.currentTimeMillis()
        playlistDao.insertPlaylistVideoCrossRef(
            PlaylistVideoCrossRef(
                playlistId = playlistId,
                videoId = video.id,
                position = position
            )
        )
    }

    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        playlistDao.removeVideoFromPlaylist(playlistId, videoId)
    }

    fun getAllPlaylistsFlow(): Flow<List<PlaylistInfo>> = playlistDao.getVideoPlaylistsWithCount().map { items ->
        items.map { item ->
            PlaylistInfo(
                id = item.playlist.id,
                name = item.playlist.name,
                description = item.playlist.description,
                videoCount = item.videoCount,
                thumbnailUrl = item.playlist.thumbnailUrl,
                isPrivate = item.playlist.isPrivate,
                createdAt = item.playlist.createdAt
            )
        }
    }

    fun getMusicPlaylistsFlow(): Flow<List<PlaylistInfo>> = playlistDao.getMusicPlaylistsWithCount().map { items ->
        items.map { item ->
            PlaylistInfo(
                id = item.playlist.id,
                name = item.playlist.name,
                description = item.playlist.description,
                videoCount = item.videoCount,
                thumbnailUrl = item.playlist.thumbnailUrl,
                isPrivate = item.playlist.isPrivate,
                createdAt = item.playlist.createdAt
            )
        }
    }

    fun getPlaylistVideosFlow(playlistId: String): Flow<List<Video>> =
        playlistDao.getVideosForPlaylist(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getPlaylistInfo(playlistId: String): PlaylistInfo? {
        val entity = playlistDao.getPlaylist(playlistId) ?: return null
        return PlaylistInfo(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            videoCount = 0,
            thumbnailUrl = entity.thumbnailUrl,
            isPrivate = entity.isPrivate,
            createdAt = entity.createdAt
        )
    }
}
