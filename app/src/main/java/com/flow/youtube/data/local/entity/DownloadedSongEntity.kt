package com.flow.youtube.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey val id: String,  
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: Int,  
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L,  
    val filePath: String? = null,  
    val downloadStatus: DownloadStatus = DownloadStatus.COMPLETED,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0
) {
    enum class DownloadStatus {
        PENDING,      
        DOWNLOADING,  
        COMPLETED,    
        FAILED,       
        EXPIRED       
    }
    
    companion object {
        fun create(
            id: String,
            title: String,
            artist: String,
            thumbnailUrl: String,
            duration: Int,
            fileSize: Long = 0L
        ): DownloadedSongEntity {
            return DownloadedSongEntity(
                id = id,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                fileSize = fileSize,
                downloadStatus = DownloadStatus.COMPLETED
            )
        }
    }
}
