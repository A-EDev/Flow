package com.flow.youtube.data.local.dao

import androidx.room.*
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.PlaylistVideoCrossRef
import com.flow.youtube.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "video_count") val videoCount: Int
)

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE isMusic = 1 GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getMusicPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT playlists.*, COUNT(playlist_video_cross_ref.videoId) as video_count FROM playlists LEFT JOIN playlist_video_cross_ref ON playlists.id = playlist_video_cross_ref.playlistId WHERE isMusic = 0 GROUP BY playlists.id ORDER BY createdAt DESC")
    fun getVideoPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isMusic = 1 ORDER BY createdAt DESC")
    fun getMusicPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isMusic = 0 ORDER BY createdAt DESC")
    fun getVideoPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: String): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun updatePlaylistName(id: String, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistVideoCrossRef(crossRef: PlaylistVideoCrossRef)

    @Query("DELETE FROM playlist_video_cross_ref WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String)

    @Transaction
    @Query("SELECT videos.* FROM videos INNER JOIN playlist_video_cross_ref ON videos.id = playlist_video_cross_ref.videoId WHERE playlist_video_cross_ref.playlistId = :playlistId ORDER BY playlist_video_cross_ref.position ASC")
    fun getVideosForPlaylist(playlistId: String): Flow<List<VideoEntity>>
    
    @Query("SELECT COUNT(*) FROM playlist_video_cross_ref WHERE playlistId = :playlistId")
    fun getPlaylistVideoCount(playlistId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_video_cross_ref WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun isVideoInPlaylist(playlistId: String, videoId: String): Int

    @Query("SELECT * FROM playlist_video_cross_ref")
    suspend fun getAllPlaylistVideoCrossRefs(): List<PlaylistVideoCrossRef>
}
