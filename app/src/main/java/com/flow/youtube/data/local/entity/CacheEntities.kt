package com.flow.youtube.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_feed_cache")
data class SubscriptionFeedEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val uploadDate: String,
    val channelThumbnailUrl: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "music_home_cache")
data class MusicHomeCacheEntity(
    @PrimaryKey val sectionId: String, // e.g., "quick_picks", "trending", "section_0"
    val title: String,
    val subtitle: String?,
    val tracksJson: String, // Store list of tracks as JSON string
    val orderBy: Int
)

@Entity(tableName = "music_home_chips_cache")
data class MusicHomeChipEntity(
    @PrimaryKey val title: String,
    val browseId: String?,
    val params: String?,
    val deselectBrowseId: String?,
    val deselectParams: String?,
    val orderBy: Int
)
