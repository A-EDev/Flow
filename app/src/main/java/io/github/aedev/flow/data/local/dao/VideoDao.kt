package io.github.aedev.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.aedev.flow.data.local.entity.VideoEntity

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
    @Query(
        """
        UPDATE videos
        SET title = :title,
            channelName = :channelName,
            channelId = :channelId,
            thumbnailUrl = :thumbnailUrl,
            duration = :duration,
            viewCount = :viewCount,
            uploadDate = :uploadDate,
            timestamp = :timestamp,
            description = :description,
            channelThumbnailUrl = :channelThumbnailUrl
        WHERE id = :id
    """,
    )
    suspend fun updateVideoMetadata(
        id: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String,
        duration: Int,
        viewCount: Long,
        uploadDate: String,
        timestamp: Long,
        description: String,
        channelThumbnailUrl: String,
    )

    /**
     * Like [updateVideoMetadata], but a blank or zero value never replaces a known one, and the
     * earlier publish time is kept. A song copied from a music page carries no upload date, views
     * or description, and must not erase what the video side already stored for the same id.
     */
    @Query(
        """
        UPDATE videos
        SET title = CASE WHEN :title != '' THEN :title ELSE title END,
            channelName = CASE WHEN :channelName != '' THEN :channelName ELSE channelName END,
            channelId = CASE WHEN :channelId != '' THEN :channelId ELSE channelId END,
            thumbnailUrl = CASE WHEN :thumbnailUrl != '' THEN :thumbnailUrl ELSE thumbnailUrl END,
            duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END,
            viewCount = CASE WHEN :viewCount > 0 THEN :viewCount ELSE viewCount END,
            uploadDate = CASE WHEN :uploadDate != '' THEN :uploadDate ELSE uploadDate END,
            timestamp = CASE WHEN timestamp > 0 AND timestamp < :timestamp THEN timestamp ELSE :timestamp END,
            description = CASE WHEN :description != '' THEN :description ELSE description END,
            channelThumbnailUrl = CASE WHEN :channelThumbnailUrl != '' THEN :channelThumbnailUrl ELSE channelThumbnailUrl END
        WHERE id = :id
    """,
    )
    suspend fun fillVideoMetadata(
        id: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String,
        duration: Int,
        viewCount: Long,
        uploadDate: String,
        timestamp: Long,
        description: String,
        channelThumbnailUrl: String,
    )

    /** Inserts new videos and fills in what known ones lack, as one change. */
    @Transaction
    suspend fun mergeMetadata(entities: List<VideoEntity>) {
        entities.forEach { entity ->
            insertVideoOrIgnore(entity)
            fillVideoMetadata(
                id = entity.id,
                title = entity.title,
                channelName = entity.channelName,
                channelId = entity.channelId,
                thumbnailUrl = entity.thumbnailUrl,
                duration = entity.duration,
                viewCount = entity.viewCount,
                uploadDate = entity.uploadDate,
                timestamp = entity.timestamp,
                description = entity.description,
                channelThumbnailUrl = entity.channelThumbnailUrl,
            )
        }
    }

    /** Inserts or refreshes many videos as one change, so a list observing them updates once. */
    @Transaction
    suspend fun upsertMetadata(entities: List<VideoEntity>) {
        entities.forEach { entity ->
            insertVideoOrIgnore(entity)
            updateVideoMetadata(
                id = entity.id,
                title = entity.title,
                channelName = entity.channelName,
                channelId = entity.channelId,
                thumbnailUrl = entity.thumbnailUrl,
                duration = entity.duration,
                viewCount = entity.viewCount,
                uploadDate = entity.uploadDate,
                timestamp = entity.timestamp,
                description = entity.description,
                channelThumbnailUrl = entity.channelThumbnailUrl,
            )
        }
    }

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideo(id: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE id IN (:ids)")
    suspend fun getVideosByIds(ids: List<String>): List<VideoEntity>

    @Query("SELECT * FROM videos")
    suspend fun getAllVideos(): List<VideoEntity>
}
