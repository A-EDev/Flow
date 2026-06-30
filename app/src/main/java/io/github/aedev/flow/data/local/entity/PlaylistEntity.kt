package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["syncId"])],
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val thumbnailUrl: String,
    val isPrivate: Boolean,
    val createdAt: Long,
    val videoCount: Int = 0, // Denormalized count for performance
    val isMusic: Boolean = false,
    val isUserCreated: Boolean = true,
    val syncId: String? = null,
)
