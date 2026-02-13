package com.flow.youtube.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flow.youtube.data.model.Video

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val uploadDate: String,
    val description: String,
    val channelThumbnailUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val savedAt: Long = System.currentTimeMillis(), // For ordering in generic lists
    val isMusic: Boolean = false
) {
    fun toDomain(): Video {
        return Video(
            id = id,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            viewCount = viewCount,
            uploadDate = uploadDate,
            description = description,
            channelThumbnailUrl = channelThumbnailUrl,
            timestamp = timestamp,
            isMusic = isMusic
        )
    }

    companion object {
        fun fromDomain(video: Video): VideoEntity {
            return VideoEntity(
                id = video.id,
                title = video.title,
                channelName = video.channelName,
                channelId = video.channelId,
                thumbnailUrl = video.thumbnailUrl,
                duration = video.duration,
                viewCount = video.viewCount,
                uploadDate = video.uploadDate,
                description = video.description,
                channelThumbnailUrl = video.channelThumbnailUrl,
                timestamp = video.timestamp,
                isMusic = video.isMusic
            )
        }
    }
}
