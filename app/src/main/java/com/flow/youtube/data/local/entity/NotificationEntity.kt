package com.flow.youtube.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val type: String = "NEW_VIDEO" // NEW_VIDEO, GENERAL, etc.
)
