package com.flow.youtube.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val thumbnailUrl: String,
    val isPrivate: Boolean,
    val createdAt: Long,
    val videoCount: Int = 0 // Denormalized count for performance
)
