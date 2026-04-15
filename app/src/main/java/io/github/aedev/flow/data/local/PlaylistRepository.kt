package io.github.aedev.flow.data.local

import io.github.aedev.flow.data.local.dao.PlaylistDao
import io.github.aedev.flow.data.local.dao.PlaylistWithCount
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.playlists.PlaylistInfo
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
        try {
            android.util.Log.d("PlaylistRepository", "Adding video to Watch Later: ${video.id}")
            val watchLater = playlistDao.getPlaylist(WATCH_LATER_ID)
            if (watchLater == null) {
                android.util.Log.d("PlaylistRepository", "Creating Watch Later playlist")
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
            android.util.Log.d("PlaylistRepository", "Inserting video metadata")
            videoDao.insertVideo(VideoEntity.fromDomain(video))
            
            // Add relationship
            val position = System.currentTimeMillis()
            android.util.Log.d("PlaylistRepository", "Inserting cross-ref")
            playlistDao.insertPlaylistVideoCrossRef(
                PlaylistVideoCrossRef(
                    playlistId = WATCH_LATER_ID,
                    videoId = video.id,
                    position = -position
                )
            )
            android.util.Log.d("PlaylistRepository", "Successfully added to Watch Later")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Failed to add to Watch Later", e)
            throw e
        }
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
        return try {
            playlistDao.isVideoInPlaylist(WATCH_LATER_ID, videoId) > 0
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Error checking watch later status", e)
            false
        }
    }

    suspend fun isVideoInPlaylist(playlistId: String, videoId: String): Boolean {
        return try {
            playlistDao.isVideoInPlaylist(playlistId, videoId) > 0
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Error checking playlist status", e)
            false
        }
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
            isMusic = isMusic,
            isUserCreated = true
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun saveExternalVideoPlaylist(id: String, name: String, description: String, thumbnailUrl: String) {
        val entity = PlaylistEntity(
            id = id,
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            isPrivate = false,
            createdAt = System.currentTimeMillis(),
            isMusic = false,
            isUserCreated = false
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun saveExternalMusicPlaylist(id: String, name: String, description: String, thumbnailUrl: String) {
        val entity = PlaylistEntity(
            id = id,
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            isPrivate = false,
            createdAt = System.currentTimeMillis(),
            isMusic = true,
            isUserCreated = false
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun unsaveExternalPlaylist(playlistId: String) {
        val entity = playlistDao.getPlaylist(playlistId)
        if (entity != null && !entity.isUserCreated) {
            playlistDao.deletePlaylist(playlistId)
        }
    }

    suspend fun isExternalPlaylistSaved(playlistId: String): Boolean {
        return playlistDao.isSavedExternalPlaylist(playlistId) > 0
    }

    suspend fun updatePlaylistName(playlistId: String, name: String) {
        playlistDao.updatePlaylistName(playlistId, name)
    }

    suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addVideoToPlaylist(playlistId: String, video: Video) {
        try {
            android.util.Log.d("PlaylistRepository", "Adding video ${video.id} to playlist $playlistId")
            // Save video first
            videoDao.insertVideo(VideoEntity.fromDomain(video))
            
            // Add to playlist
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
            android.util.Log.d("PlaylistRepository", "Successfully added to playlist $playlistId")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Failed to add to playlist $playlistId", e)
            throw e
        }
    }

    suspend fun addVideosToPlaylist(targetPlaylistId: String, videos: List<Video>) {
        videos.forEach { video -> addVideoToPlaylist(targetPlaylistId, video) }
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

    fun getUserCreatedVideoPlaylistsFlow(): Flow<List<PlaylistInfo>> = playlistDao.getUserCreatedVideoPlaylistsWithCount().map { items ->
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

    fun getSavedVideoPlaylistsFlow(): Flow<List<PlaylistInfo>> = playlistDao.getSavedVideoPlaylistsWithCount().map { items ->
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

    fun getUserCreatedMusicPlaylistsFlow(): Flow<List<PlaylistInfo>> = playlistDao.getUserCreatedMusicPlaylistsWithCount().map { items ->
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

    fun getSavedMusicPlaylistsFlow(): Flow<List<PlaylistInfo>> = playlistDao.getSavedMusicPlaylistsWithCount().map { items ->
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
