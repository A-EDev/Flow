package com.flow.youtube.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.flow.youtube.data.local.VideoHistoryEntry

/**
 * Room entity that replaces the previous DataStore-based watch history.
 *
 * The old DataStore approach stored ~8 preference keys per video, meaning
 * 46 000 watched videos → ~370 000 entries in a single serialised protobuf
 * file that was fully deserialized on every read.  SQLite handles millions of
 * rows efficiently with proper indexing.
 */
@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["videoId"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["isMusic"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val position: Long,        
    val duration: Long,        
    val timestamp: Long,      
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val channelId: String,
    val isMusic: Boolean
) {
    fun toDomain() = VideoHistoryEntry(
        videoId = videoId,
        position = position,
        duration = duration,
        timestamp = timestamp,
        title = title,
        thumbnailUrl = thumbnailUrl,
        channelName = channelName,
        channelId = channelId,
        isMusic = isMusic
    )

    companion object {
        fun fromDomain(entry: VideoHistoryEntry) = WatchHistoryEntity(
            videoId      = entry.videoId,
            position     = entry.position,
            duration     = entry.duration,
            timestamp    = entry.timestamp,
            title        = entry.title,
            thumbnailUrl = entry.thumbnailUrl,
            channelName  = entry.channelName,
            channelId    = entry.channelId,
            isMusic      = entry.isMusic
        )
    }
}
