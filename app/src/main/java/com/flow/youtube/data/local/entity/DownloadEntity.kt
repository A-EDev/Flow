package com.flow.youtube.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a downloaded media item (video or audio-only).
 * One DownloadEntity can have multiple DownloadItemEntity children
 * (e.g., separate video + audio files for DASH, or a single muxed file).
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val uploader: String,
    val duration: Long = 0L,
    val thumbnailUrl: String = "",
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
