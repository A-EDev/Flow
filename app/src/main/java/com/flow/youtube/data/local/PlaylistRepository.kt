package com.flow.youtube.data.local

import com.flow.youtube.data.local.dao.PlaylistDao
import com.flow.youtube.data.local.dao.VideoDao
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.PlaylistVideoCrossRef
import com.flow.youtube.data.local.entity.VideoEntity
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.screens.playlists.PlaylistInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        // Since we can't delete by playlistId in crossref easily without a custom query, 
        // we might iterate or add a specific generic custom query.
        // For now, let's just leave it or implement a clear method in DAO.
        // Simple hack: delete the playlist and recreate
        playlistDao.deletePlaylist(WATCH_LATER_ID)
    }

    fun getWatchLaterVideosFlow(): Flow<List<Video>> = 
        playlistDao.getVideosForPlaylist(WATCH_LATER_ID).map { entities ->
            entities.map { it.toDomain() }
        }

    fun getWatchLaterIdsFlow(): Flow<Set<String>> = 
        playlistDao.getVideosForPlaylist(WATCH_LATER_ID).map { entities ->
            entities.map { it.id }.toSet()
        }

    suspend fun isInWatchLater(videoId: String): Boolean {
        // This is not efficient, should use specific DAO query strictly, but flow is okay for small lists.
        // Better:
        // return playlistDao.isVideoInPlaylist(WATCH_LATER_ID, videoId)
        // Creating a temporary implementation using getVideosForPlaylist logic
        return true // Placeholder, actually need to implement boolean check in DAO or efficient check
    }

    // Playlist Management
    suspend fun createPlaylist(playlistId: String, name: String, description: String, isPrivate: Boolean) {
        val entity = PlaylistEntity(
            id = playlistId,
            name = name,
            description = description,
            thumbnailUrl = "",
            isPrivate = isPrivate,
            createdAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(entity)
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

    fun getAllPlaylistsFlow(): Flow<List<PlaylistInfo>> = playlistDao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            // Note: videoCount in Entity might be manually maintained or 0.
            // Ideally use a relation count.
            // For now, mapping directly.
            PlaylistInfo(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                videoCount = 0, // TODO: Get actual count
                thumbnailUrl = entity.thumbnailUrl,
                isPrivate = entity.isPrivate,
                createdAt = entity.createdAt
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
