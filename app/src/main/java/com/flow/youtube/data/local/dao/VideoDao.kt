package com.flow.youtube.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flow.youtube.data.local.entity.VideoEntity

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideoOrIgnore(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideosOrIgnore(videos: List<VideoEntity>)

    /**
     * Update only the metadata columns of an existing video — does NOT do DELETE+INSERT,
     * so PlaylistVideoCrossRef CASCADE is never triggered.
     * isMusic is intentionally NOT updated here — the stub's value is the source of truth.
     */
    @Query("""
        UPDATE videos
        SET title = :title,
            channelName = :channelName,
            channelId = :channelId,
            thumbnailUrl = :thumbnailUrl,
            duration = :duration,
            viewCount = :viewCount,
            uploadDate = :uploadDate,
            description = :description,
            channelThumbnailUrl = :channelThumbnailUrl
        WHERE id = :id
    """)
    suspend fun updateVideoMetadata(
        id: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String,
        duration: Int,
        viewCount: Long,
        uploadDate: String,
        description: String,
        channelThumbnailUrl: String
    )

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideo(id: String): VideoEntity?

    @Query("SELECT * FROM videos")
    suspend fun getAllVideos(): List<VideoEntity>
}
