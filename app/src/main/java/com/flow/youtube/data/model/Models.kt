package com.flow.youtube.data.model

import org.schabi.newpipe.extractor.Page

data class Video(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int, // in seconds
    val viewCount: Long,
    val likeCount: Long = 0,
    val uploadDate: String,
    val description: String = "",
    val channelThumbnailUrl: String = "",
    val isMusic: Boolean = false,
    val isLive: Boolean = false,
    val isShort: Boolean = false
)

data class Channel(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val description: String = "",
    val isSubscribed: Boolean = false,
    val url: String = "" // Full channel URL for navigation
)

data class Playlist(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val videoCount: Int,
    val description: String = "",
    val videos: List<Video> = emptyList(),
    val isLocal: Boolean = true
)
data class Comment(
    val id: String,
    val author: String,
    val authorThumbnail: String,
    val text: String,
    val likeCount: Int,
    val publishedTime: String,
    val replies: List<Comment> = emptyList(),
    val replyCount: Int = 0,
    val repliesPage: Page? = null
)

data class SearchResult(
    val videos: List<Video> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

enum class SearchFilter {
    ALL, VIDEOS, CHANNELS, PLAYLISTS
}

