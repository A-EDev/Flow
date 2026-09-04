package io.github.aedev.flow.data.model

data class PlaylistInfo(
    val id: String,
    val name: String,
    val description: String,
    val videoCount: Int,
    val thumbnailUrl: String,
    val isPrivate: Boolean,
    val createdAt: Long,
)
